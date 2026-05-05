package com.ovi.where.presentation.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.friend.GetFriendshipStatusUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
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
                    val status = getFriendshipStatusUseCase(userId)
                    _uiState.value = _uiState.value.copy(
                        user = result.data,
                        friendshipStatus = status,
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
            _uiState.value = _uiState.value.copy(friendshipStatus = FriendshipStatus.PENDING)
        }
    }

    fun removeFriend(userId: String) {
        viewModelScope.launch {
            removeFriendUseCase(userId)
            _uiState.value = _uiState.value.copy(friendshipStatus = null)
        }
    }
}

data class UserProfileUiState(
    val user: User? = null,
    val friendshipStatus: FriendshipStatus? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
