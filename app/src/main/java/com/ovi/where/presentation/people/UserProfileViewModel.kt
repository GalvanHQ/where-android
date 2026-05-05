package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.friend.GetFriendshipStatusUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.ovi.where.presentation.model.OtherUserProfileUiModel
import com.ovi.where.presentation.model.ProfileFriendshipAction
import com.ovi.where.presentation.model.toOtherProfileUiModel
import com.ovi.where.presentation.model.toProfileAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val getFriendshipStatusUseCase: GetFriendshipStatusUseCase,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = userRepository.getUser(userId)) {
                is Resource.Success -> {
                    val user   = result.data ?: return@launch
                    val status = getFriendshipStatusUseCase(userId)
                    // Map domain User + FriendshipStatus → OtherUserProfileUiModel
                    _uiState.value = _uiState.value.copy(
                        profile   = user.toOtherProfileUiModel(status),
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
                else -> {}
            }
        }
    }

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            sendFriendRequestUseCase(userId)
            // Optimistically update the action — no raw FriendshipStatus exposed to UI
            _uiState.value = _uiState.value.copy(
                profile = _uiState.value.profile?.copy(
                    friendshipAction = ProfileFriendshipAction.RequestSent
                )
            )
        }
    }

    fun removeFriend(userId: String) {
        viewModelScope.launch {
            removeFriendUseCase(userId)
            _uiState.value = _uiState.value.copy(
                profile = _uiState.value.profile?.copy(
                    friendshipAction = ProfileFriendshipAction.AddFriend
                )
            )
        }
    }
}

data class UserProfileUiState(
    val profile: OtherUserProfileUiModel? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
