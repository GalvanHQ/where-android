package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.User
import com.ovi.where.domain.usecase.friend.ObserveFriendRequestsUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val observeFriendRequestsUseCase: ObserveFriendRequestsUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState: StateFlow<PeopleUiState> = _uiState.asStateFlow()

    init {
        observeFriends()
        observeRequests()
    }

    private fun observeFriends() {
        viewModelScope.launch {
            observeFriendsUseCase().collect { friends ->
                _uiState.value = _uiState.value.copy(
                    friends = friends.sortedBy { it.displayName.lowercase() },
                    isLoading = false
                )
            }
        }
    }

    private fun observeRequests() {
        viewModelScope.launch {
            observeFriendRequestsUseCase().collect { requests ->
                _uiState.value = _uiState.value.copy(
                    pendingRequestCount = requests.size
                )
            }
        }
    }

    fun removeFriend(userId: String) {
        viewModelScope.launch {
            removeFriendUseCase(userId)
        }
    }
}

data class PeopleUiState(
    val friends: List<User> = emptyList(),
    val pendingRequestCount: Int = 0,
    val isLoading: Boolean = true
)
