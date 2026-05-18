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
import kotlinx.coroutines.tasks.await
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
    private val conversationRepository: com.ovi.where.domain.repository.ConversationRepository,
    private val firebaseAuth: FirebaseAuth,
    private val friendshipRepository: com.ovi.where.domain.repository.FriendshipRepository
) : ViewModel() {

    private val groupId: String = savedStateHandle["groupId"] ?: ""

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private val _currentUserId: String? get() = firebaseAuth.currentUser?.uid
    val currentUserId: String? get() = _currentUserId

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
                        // If no conversationId on the group, resolve it via Room + Firestore
                        if (conversationId == null) {
                            conversationId = conversationRepository.getConversationIdByGroupId(groupId)
                        }
                        _uiState.update {
                            it.copy(
                                groupName = group.name,
                                groupPhotoUrl = group.avatarUrl,
                                memberCount = group.memberCount,
                                isLoading = false,
                                error = null
                            )
                        }
                        // Sync group photo to conversation if needed
                        if (!group.avatarUrl.isNullOrBlank()) {
                            syncGroupPhotoToConversation(group.avatarUrl)
                        }
                        // Load additional data in parallel
                        loadMembers()
                        loadInviteLink()
                        conversationId?.let {
                            loadSharedMedia(it)
                            loadNicknames(it)
                        }
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
                        username = user?.username ?: "",
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
     * Resolves the conversationId by looking up conversations with this groupId.
     * Uses Room first, then Firestore direct query as fallback.
     */
    private fun resolveConversationId(groupId: String) {
        viewModelScope.launch {
            val resolved = conversationRepository.getConversationIdByGroupId(groupId)
            if (resolved != null && conversationId == null) {
                conversationId = resolved
                loadSharedMedia(resolved)
                loadNicknames(resolved)
            }
        }
    }

    /**
     * Ensures the conversation document's photoUrl matches the group's avatarUrl.
     * This handles the case where the group was created with a photo but the
     * conversation document was never updated (legacy data or server didn't sync).
     */
    private fun syncGroupPhotoToConversation(avatarUrl: String) {
        viewModelScope.launch {
            conversationRepository.updateConversationPhotoUrlByGroupId(groupId, avatarUrl)
        }
    }

    /**
     * Loads nicknames from the conversation document.
     */
    private fun loadNicknames(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.observeConversation(conversationId).collect { conversation ->
                if (conversation != null) {
                    _uiState.update {
                        it.copy(
                            nicknames = conversation.nicknames,
                            themeColor = conversation.themeColor,
                            emojiShortcut = conversation.emojiShortcut
                        )
                    }
                }
            }
        }
    }

    /**
     * Loads shared media thumbnails from the conversation's messages.
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

    /**
     * Updates the group name. Optimistic update + Firestore persist.
     */
    fun updateGroupName(newName: String) {
        if (newName.isBlank() || newName.length < 3) return
        val previousName = _uiState.value.groupName
        _uiState.update { it.copy(groupName = newName) }

        viewModelScope.launch {
            when (groupRepository.updateGroup(groupId, newName, "")) {
                is Resource.Error -> {
                    _uiState.update { it.copy(groupName = previousName) }
                }
                else -> {}
            }
        }
    }

    /**
     * Adds multiple members to the group.
     */
    fun addMembers(userIds: List<String>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            var allSuccess = true
            for (userId in userIds) {
                when (groupRepository.addMember(groupId, userId)) {
                    is Resource.Error -> { allSuccess = false }
                    else -> {}
                }
            }
            if (allSuccess) onSuccess()
            else _uiState.update { it.copy(error = "Failed to add some members") }
        }
    }

    /**
     * Loads the current user's friends list for the Add Members screen.
     */
    fun loadFriendsForAdding(onResult: (List<com.ovi.where.domain.model.FriendEntry>) -> Unit) {
        viewModelScope.launch {
            friendshipRepository.observeFriends().collect { friends ->
                onResult(friends)
            }
        }
    }

    /**
     * Updates the group photo URL in Firestore and the linked conversation.
     * Uses groupId-based Room update for immediate UI feedback on ChatsScreen/ChatScreen.
     */
    fun updateGroupPhoto(photoUrl: String) {
        _uiState.update { it.copy(groupPhotoUrl = photoUrl) }
        viewModelScope.launch {
            try {
                timber.log.Timber.d("GroupInfoVM: updateGroupPhoto called, groupId=$groupId, url=$photoUrl")

                // 1. Persist avatarUrl to the group Firestore document
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("groups")
                    .document(groupId)
                    .update("avatarUrl", photoUrl)
                    .await()
                timber.log.Timber.d("GroupInfoVM: group avatarUrl updated in Firestore")

                // 2. Update conversation photo by groupId (Room + Firestore)
                val result = conversationRepository.updateConversationPhotoUrlByGroupId(groupId, photoUrl)
                timber.log.Timber.d("GroupInfoVM: updateConversationPhotoUrlByGroupId result=$result")

                // 3. Also try by conversationId as fallback
                if (conversationId == null) {
                    conversationId = conversationRepository.getConversationIdByGroupId(groupId)
                }
                timber.log.Timber.d("GroupInfoVM: resolved conversationId=$conversationId")
                conversationId?.let { convId ->
                    conversationRepository.updateConversationPhotoUrl(convId, photoUrl)
                    timber.log.Timber.d("GroupInfoVM: also updated by conversationId=$convId")
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "GroupInfoVM: updateGroupPhoto failed")
                _uiState.update { it.copy(groupPhotoUrl = null) }
            }
        }
    }

    /**
     * Updates a nickname for a group member.
     */
    fun updateNickname(userId: String, nickname: String) {
        viewModelScope.launch {
            // Ensure conversationId is resolved
            if (conversationId == null) {
                conversationId = conversationRepository.getConversationIdByGroupId(groupId)
            }
            val convId = conversationId ?: return@launch

            val currentNicknames = _uiState.value.nicknames.toMutableMap()
            if (nickname.isBlank()) {
                currentNicknames.remove(userId)
            } else {
                currentNicknames[userId] = nickname
            }
            _uiState.update { it.copy(nicknames = currentNicknames) }
            conversationRepository.updateNicknames(convId, currentNicknames)
        }
    }

    /**
     * Updates the theme color for the group's conversation.
     */
    fun updateThemeColor(colorHex: String?) {
        _uiState.update { it.copy(themeColor = colorHex) }
        viewModelScope.launch {
            // Ensure conversationId is resolved
            if (conversationId == null) {
                conversationId = conversationRepository.getConversationIdByGroupId(groupId)
            }
            val convId = conversationId ?: return@launch
            conversationRepository.updateThemeColor(convId, colorHex)
        }
    }

    /**
     * Updates the emoji shortcut for the group's conversation.
     */
    fun updateEmojiShortcut(emoji: String?) {
        _uiState.update { it.copy(emojiShortcut = emoji) }
        viewModelScope.launch {
            if (conversationId == null) {
                conversationId = conversationRepository.getConversationIdByGroupId(groupId)
            }
            val convId = conversationId ?: return@launch
            conversationRepository.updateEmojiShortcut(convId, emoji)
        }
    }

    companion object {
        private const val SHARED_MEDIA_LIMIT = 20
    }
}
