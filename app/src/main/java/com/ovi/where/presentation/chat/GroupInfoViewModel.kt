package com.ovi.where.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.MemberRole
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.repository.GroupRepository
import com.ovi.where.domain.repository.MessageRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.presentation.model.GroupInfoUiState
import com.ovi.where.presentation.model.GroupMemberUiModel
import com.ovi.where.presentation.model.MediaThumbnail
import com.ovi.where.presentation.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Messenger-style Group Info Screen.
 *
 * Loads group details, member list with online status, shared media, and invite link.
 * Exposes [GroupInfoUiState] as StateFlow.
 * Implements admin actions: make admin, remove member, delete group.
 * Handles error states with retry capability.
 *
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11
 */
@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val groupId: String = savedStateHandle["groupId"] ?: ""

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    /** Cached conversationId for loading shared media */
    private var conversationId: String? = null

    init {
        if (groupId.isNotBlank()) {
            loadGroupInfo()
        }
    }

    /**
     * Loads all group info: details, members, invite link, and shared media.
     * Called on init and on retry after error.
     */
    fun loadGroupInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = groupRepository.getGroup(groupId)) {
                is Resource.Success -> {
                    val group = result.data
                    if (group != null) {
                        conversationId = group.conversationId
                        _uiState.update {
                            it.copy(
                                groupName = group.name,
                                groupPhotoUrl = group.avatarUrl,
                                memberCount = group.memberCount,
                                isLoading = false,
                                error = null
                            )
                        }
                        // Load additional data in parallel
                        loadMembers()
                        loadInviteLink()
                        group.conversationId?.let { loadSharedMedia(it) }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Group not found"
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message ?: "Failed to load group"
                        )
                    }
                }
                is Resource.Loading -> {
                    // Already showing loading state
                }
            }
        }
    }

    /**
     * Retry loading group info after an error.
     */
    fun retry() {
        loadGroupInfo()
    }

    /**
     * Observes group members and enriches with user details (display name, photo, online status).
     * Sorts admins first, then alphabetical by display name.
     * Requirement 9.3, 9.11
     */
    private fun loadMembers() {
        viewModelScope.launch {
            groupRepository.observeGroupMembers(groupId).collect { members ->
                val userIds = members.map { it.userId }
                val users = when (val usersResult = userRepository.getUsers(userIds)) {
                    is Resource.Success -> usersResult.data ?: emptyList()
                    else -> emptyList()
                }
                val userMap = users.associateBy { it.id }
                val uid = currentUserId

                val memberUiModels = members.map { member ->
                    val user = userMap[member.userId]
                    GroupMemberUiModel(
                        id = member.id,
                        userId = member.userId,
                        displayName = user?.displayName?.ifBlank { member.userId } ?: member.userId,
                        photoUrl = user?.photoUrl,
                        isOnline = user?.isOnline ?: false,
                        roleText = if (member.role == MemberRole.ADMIN) "Admin" else "Member",
                        isAdmin = member.role == MemberRole.ADMIN,
                        isSharingLocation = member.isSharingLocation
                    )
                }

                // Sort: admins first, then alphabetical by display name
                val sortedMembers = memberUiModels.sortedWith(
                    compareByDescending<GroupMemberUiModel> { it.isAdmin }
                        .thenBy { it.displayName.lowercase() }
                )

                val isCurrentUserAdmin = members.any {
                    it.userId == uid && it.role == MemberRole.ADMIN
                }

                _uiState.update {
                    it.copy(
                        members = sortedMembers,
                        memberCount = sortedMembers.size,
                        isCurrentUserAdmin = isCurrentUserAdmin
                    )
                }
            }
        }
    }

    /**
     * Loads the group invite link (admin only).
     * Requirement 9.10
     */
    private fun loadInviteLink() {
        viewModelScope.launch {
            when (val result = groupRepository.getInviteLink(groupId)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(inviteLink = result.data) }
                }
                is Resource.Error -> {
                    // Invite link failure is non-critical, don't show error
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Loads shared media thumbnails from the conversation's messages.
     * Shows the most recent media items.
     * Requirement 9.7
     */
    private fun loadSharedMedia(conversationId: String) {
        viewModelScope.launch {
            messageRepository.observeMessages(conversationId).collect { messages ->
                val mediaThumbnails = messages
                    .filter { it.type == MessageType.IMAGE && it.imageUrl != null }
                    .sortedByDescending { it.timestamp }
                    .take(SHARED_MEDIA_LIMIT)
                    .map { message ->
                        MediaThumbnail(
                            id = message.id,
                            thumbnailUrl = message.imageUrl ?: "",
                            type = MediaType.IMAGE
                        )
                    }
                _uiState.update { it.copy(sharedMedia = mediaThumbnails) }
            }
        }
    }

    // ── Admin Actions ───────────────────────────────────────────────────────

    /**
     * Makes a member an admin (optimistic update).
     * Requirement 9.4
     */
    fun makeAdmin(userId: String) {
        val previousMembers = _uiState.value.members

        // Optimistic update
        _uiState.update { state ->
            state.copy(
                members = state.members.map { member ->
                    if (member.userId == userId) {
                        member.copy(isAdmin = true, roleText = "Admin")
                    } else member
                }
            )
        }

        viewModelScope.launch {
            when (groupRepository.updateMemberRole(groupId, userId, MemberRole.ADMIN)) {
                is Resource.Success -> {
                    // Optimistic update already applied
                }
                is Resource.Error -> {
                    // Revert optimistic update
                    _uiState.update { it.copy(members = previousMembers) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Removes a member from the group (optimistic update).
     * Requirement 9.4
     */
    fun removeMember(userId: String) {
        val previousMembers = _uiState.value.members
        val previousMemberCount = _uiState.value.memberCount

        // Optimistic update
        _uiState.update { state ->
            val updatedMembers = state.members.filter { it.userId != userId }
            state.copy(
                members = updatedMembers,
                memberCount = updatedMembers.size
            )
        }

        viewModelScope.launch {
            when (groupRepository.removeMember(groupId, userId)) {
                is Resource.Success -> {
                    // Optimistic update already applied
                }
                is Resource.Error -> {
                    // Revert optimistic update
                    _uiState.update {
                        it.copy(
                            members = previousMembers,
                            memberCount = previousMemberCount
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Deletes the group entirely (admin action).
     * Requirement 9.9
     */
    fun deleteGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (groupRepository.deleteGroup(groupId)) {
                is Resource.Success -> {
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(error = "Failed to delete group")
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Leaves the group.
     * Requirement 9.8
     */
    fun leaveGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (groupRepository.leaveGroup(groupId)) {
                is Resource.Success -> {
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(error = "Failed to leave group")
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Toggles mute state for the group.
     * Requirement 9.2
     */
    fun toggleMute() {
        val currentMuted = _uiState.value.isMuted
        _uiState.update { it.copy(isMuted = !currentMuted) }
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val SHARED_MEDIA_LIMIT = 20
    }
}
