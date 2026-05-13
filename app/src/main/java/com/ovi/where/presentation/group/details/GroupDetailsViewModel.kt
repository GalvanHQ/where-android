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
import com.ovi.where.domain.repository.MessageRepository
import com.ovi.where.domain.usecase.group.DeleteGroupUseCase
import com.ovi.where.domain.usecase.group.GetGroupUseCase
import com.ovi.where.domain.usecase.group.KickMemberUseCase
import com.ovi.where.domain.usecase.group.LeaveGroupUseCase
import com.ovi.where.domain.usecase.group.ObserveGroupMembersUseCase
import com.ovi.where.domain.usecase.group.PromoteMemberUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import com.ovi.where.presentation.model.GroupMemberUiModel
import com.ovi.where.presentation.model.GroupUiModel
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupDetailsViewModel @Inject constructor(
    application: Application,
    private val getGroupUseCase: GetGroupUseCase,
    private val observeGroupMembersUseCase: ObserveGroupMembersUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase,
    private val kickMemberUseCase: KickMemberUseCase,
    private val promoteMemberUseCase: PromoteMemberUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val getUsersUseCase: GetUsersUseCase,
    private val messageRepository: MessageRepository,
    private val firebaseAuth: FirebaseAuth
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GroupDetailsUiState())
    val uiState: StateFlow<GroupDetailsUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    fun loadGroupDetails(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = getGroupUseCase(groupId)) {
                is Resource.Success -> {
                    val group = result.data
                    val resources = getApplication<Application>().resources
                    val isAdmin = group?.createdBy == currentUserId
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        group = group?.toUiModel(
                            memberCountText = resources.getQuantityString(
                                R.plurals.member_count, group.memberCount, group.memberCount
                            )
                        ),
                        inviteCode = group?.inviteCode ?: "",
                        groupName = group?.name ?: "",
                        groupDescription = group?.description ?: "",
                        groupAvatarUrl = group?.avatarUrl,
                        groupConversationId = group?.conversationId,
                        isCurrentUserAdmin = isAdmin,
                        error = null
                    )
                    // Load shared media if conversation exists
                    group?.conversationId?.let { loadSharedMedia(it) }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> _uiState.value = _uiState.value.copy(isLoading = true)
            }
        }
    }

    fun observeMembers(groupId: String) {
        viewModelScope.launch {
            val adminText = getApplication<Application>().getString(R.string.status_admin)
            val memberText = getApplication<Application>().getString(R.string.status_member)
            observeGroupMembersUseCase(groupId).collect { members ->
                // Fetch display names
                val userIds = members.map { it.userId }
                val userNames = mutableMapOf<String, String>()
                if (userIds.isNotEmpty()) {
                    when (val usersResult = getUsersUseCase(userIds)) {
                        is Resource.Success -> usersResult.data?.forEach { user ->
                            userNames[user.id] = user.displayName
                        }
                        else -> {}
                    }
                }

                val adminId = currentUserId

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

                // Determine admin status and sole admin check
                val isCurrentUserAdmin = members.any { it.userId == adminId && it.isAdmin() }
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
     * Loads the most recent shared media (IMAGE type messages) for the gallery preview.
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

    fun leaveGroup(groupId: String) {
        // Guard: sole admin cannot leave
        if (_uiState.value.isSoleAdmin) return

        viewModelScope.launch {
            when (leaveGroupUseCase(groupId)) {
                is Resource.Success -> {
                    _uiEvent.send(UiEvent.ShowSnackbar(
                        UiText.StringResource(R.string.toast_left_group)
                    ))
                    _uiEvent.send(UiEvent.NavigateBack)
                }
                is Resource.Error -> _uiEvent.send(UiEvent.ShowSnackbar(
                    UiText.StringResource(R.string.error_unknown)
                ))
                else -> {}
            }
        }
    }

    fun kickMember(groupId: String, userId: String) {
        viewModelScope.launch {
            when (kickMemberUseCase(groupId, userId)) {
                is Resource.Success -> _uiEvent.send(UiEvent.ShowSnackbar(
                    UiText.StringResource(R.string.toast_member_removed)
                ))
                is Resource.Error -> _uiEvent.send(UiEvent.ShowSnackbar(
                    UiText.StringResource(R.string.error_kick_member_failed)
                ))
                else -> {}
            }
        }
    }

    fun promoteMember(groupId: String, userId: String) {
        viewModelScope.launch {
            when (promoteMemberUseCase(groupId, userId)) {
                is Resource.Success -> _uiEvent.send(UiEvent.ShowSnackbar(
                    UiText.StringResource(R.string.toast_member_promoted)
                ))
                is Resource.Error -> _uiEvent.send(UiEvent.ShowSnackbar(
                    UiText.StringResource(R.string.error_unknown)
                ))
                else -> {}
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            when (deleteGroupUseCase(groupId)) {
                is Resource.Success -> {
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.toast_group_deleted)))
                    _uiEvent.send(UiEvent.NavigateBack)
                }
                is Resource.Error -> _uiEvent.send(UiEvent.ShowSnackbar(
                    UiText.StringResource(R.string.error_delete_group_failed)
                ))
                else -> {}
            }
        }
    }

    companion object {
        /** Maximum number of shared media items to display in the gallery preview */
        const val SHARED_MEDIA_LIMIT = 20
    }
}

private fun com.ovi.where.domain.model.GroupMember.isAdmin() =
    role == com.ovi.where.domain.model.MemberRole.ADMIN

data class GroupDetailsUiState(
    val group: GroupUiModel? = null,
    val members: List<GroupMemberUiModel> = emptyList(),
    val inviteCode: String = "",
    val groupName: String = "",
    val groupDescription: String = "",
    val groupAvatarUrl: String? = null,
    val groupConversationId: String? = null,
    val sharedMedia: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isCurrentUserAdmin: Boolean = false,
    val isSoleAdmin: Boolean = false,
    val error: String? = null
)
