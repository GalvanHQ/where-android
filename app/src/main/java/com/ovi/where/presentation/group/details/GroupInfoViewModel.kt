package com.ovi.where.presentation.group.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.usecase.group.DeleteGroupUseCase
import com.ovi.where.domain.usecase.group.GetGroupUseCase
import com.ovi.where.domain.usecase.group.KickMemberUseCase
import com.ovi.where.domain.usecase.group.LeaveGroupUseCase
import com.ovi.where.domain.usecase.group.ObserveGroupMembersUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import com.ovi.where.domain.repository.MessageRepository
import com.ovi.where.presentation.model.GroupMemberUiModel
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * GroupInfoViewModel — manages group info display, member management, and admin actions.
 *
 * - Displays group avatar, name, description, member list sorted by role (admins first)
 *   then alphabetical by display name (Requirement 11.1).
 * - Shows role labels ("Admin" or "Member") per row (Requirement 11.1).
 * - Admin actions: remove member (with confirmation), delete group (Requirement 11.2).
 * - Hides admin actions for non-admin users (Requirement 11.3).
 * - Leave group (with confirmation), disabled if sole admin (Requirement 11.4, 11.5).
 * - Shows shared media gallery (most recent 20 items, "See All" action) (Requirement 11.7).
 * - Handles server operation failures with error snackbar, no state mutation on failure (Requirement 11.6).
 */
@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    application: Application,
    private val getGroupUseCase: GetGroupUseCase,
    private val observeGroupMembersUseCase: ObserveGroupMembersUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase,
    private val kickMemberUseCase: KickMemberUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val getUsersUseCase: GetUsersUseCase,
    private val messageRepository: MessageRepository,
    private val firebaseAuth: FirebaseAuth
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    /**
     * Loads group details (avatar, name, description) and determines admin status.
     */
    fun loadGroupInfo(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = getGroupUseCase(groupId)) {
                is Resource.Success -> {
                    val group = result.data
                    if (group != null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            groupId = group.id,
                            groupName = group.name,
                            groupDescription = group.description,
                            groupAvatarUrl = group.avatarUrl,
                            conversationId = group.conversationId,
                            error = null
                        )
                        // Load shared media if we have a conversationId
                        group.conversationId?.let { loadSharedMedia(it) }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = getApplication<Application>().getString(R.string.error_loading_group)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Resource.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    /**
     * Observes group members and sorts them: admins first, then alphabetical by display name.
     * Also determines if the current user is an admin and if they are the sole admin.
     */
    fun observeMembers(groupId: String) {
        viewModelScope.launch {
            val adminText = getApplication<Application>().getString(R.string.status_admin)
            val memberText = getApplication<Application>().getString(R.string.status_member)

            observeGroupMembersUseCase(groupId).collect { members ->
                // Fetch display names for all members
                val userIds = members.map { it.userId }
                val userNames = mutableMapOf<String, String>()
                if (userIds.isNotEmpty()) {
                    when (val usersResult = getUsersUseCase(userIds)) {
                        is Resource.Success -> usersResult.data?.forEach { user ->
                            userNames[user.userId()] = user.displayName
                        }
                        else -> {}
                    }
                }

                val currentUid = currentUserId

                // Map to UI models
                val memberUiModels = members.map { member ->
                    member.toUiModel(
                        displayName = userNames[member.userId] ?: member.userId,
                        adminText = adminText,
                        memberText = memberText
                    )
                }

                // Sort: admins first, then alphabetical by display name
                val sortedMembers = memberUiModels.sortedWith(
                    compareByDescending<GroupMemberUiModel> { it.isAdmin }
                        .thenBy { it.displayName.lowercase() }
                )

                // Determine admin status
                val isCurrentUserAdmin = members.any { it.userId == currentUid && it.isAdmin() }
                val adminCount = members.count { it.isAdmin() }
                val isSoleAdmin = isCurrentUserAdmin && adminCount == 1

                _uiState.value = _uiState.value.copy(
                    members = sortedMembers,
                    isCurrentUserAdmin = isCurrentUserAdmin,
                    isSoleAdmin = isSoleAdmin
                )
            }
        }
    }

    /**
     * Loads the most recent 20 media items (IMAGE type messages) for the shared media gallery.
     * Requirement 11.7.
     */
    private fun loadSharedMedia(conversationId: String) {
        viewModelScope.launch {
            messageRepository.observeMessages(conversationId).collect { messages ->
                val mediaMessages = messages
                    .filter { it.type == MessageType.IMAGE && it.imageUrl != null }
                    .sortedByDescending { it.timestamp }
                    .take(SHARED_MEDIA_LIMIT)
                _uiState.value = _uiState.value.copy(sharedMedia = mediaMessages)
            }
        }
    }

    /**
     * Removes a member from the group (admin action).
     * Shows confirmation dialog before executing.
     * On failure: shows error snackbar, does NOT modify displayed member list.
     * Requirement 11.2, 11.6.
     */
    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            when (kickMemberUseCase(groupId, userId)) {
                is Resource.Success -> {
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.StringResource(R.string.toast_member_removed)
                        )
                    )
                }
                is Resource.Error -> {
                    // Requirement 11.6: show error snackbar, no state mutation
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.StringResource(R.string.error_kick_member_failed)
                        )
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Leaves the group (with confirmation).
     * Disabled if the current user is the sole admin (Requirement 11.5).
     * On success: navigates back to conversation list.
     * On failure: shows error snackbar, no navigation (Requirement 11.6).
     */
    fun leaveGroup(groupId: String) {
        // Guard: sole admin cannot leave
        if (_uiState.value.isSoleAdmin) return

        viewModelScope.launch {
            when (leaveGroupUseCase(groupId)) {
                is Resource.Success -> {
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.StringResource(R.string.toast_left_group)
                        )
                    )
                    _uiEvent.send(UiEvent.NavigateBack)
                }
                is Resource.Error -> {
                    // Requirement 11.6: show error snackbar, no navigation
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.StringResource(R.string.error_leave_group_failed)
                        )
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Deletes the group (admin action).
     * On success: navigates back.
     * On failure: shows error snackbar, no navigation (Requirement 11.6).
     */
    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            when (deleteGroupUseCase(groupId)) {
                is Resource.Success -> {
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.StringResource(R.string.toast_group_deleted)
                        )
                    )
                    _uiEvent.send(UiEvent.NavigateBack)
                }
                is Resource.Error -> {
                    // Requirement 11.6: show error snackbar, no navigation
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.StringResource(R.string.error_delete_group_failed)
                        )
                    )
                }
                else -> {}
            }
        }
    }

    companion object {
        /** Maximum number of shared media items to display in the gallery preview */
        const val SHARED_MEDIA_LIMIT = 20
    }
}

/**
 * Helper extension to check if a GroupMember has admin role.
 */
private fun com.ovi.where.domain.model.GroupMember.isAdmin() =
    role == com.ovi.where.domain.model.MemberRole.ADMIN

/**
 * Helper extension to get userId from User (the User model uses `id` field).
 */
private fun com.ovi.where.domain.model.User.userId(): String = id

/**
 * UI state for GroupInfoScreen.
 */
data class GroupInfoUiState(
    val groupId: String = "",
    val groupName: String = "",
    val groupDescription: String = "",
    val groupAvatarUrl: String? = null,
    val conversationId: String? = null,
    val members: List<GroupMemberUiModel> = emptyList(),
    val isCurrentUserAdmin: Boolean = false,
    val isSoleAdmin: Boolean = false,
    val sharedMedia: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
