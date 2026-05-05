package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.User
import com.ovi.where.domain.usecase.friend.AcceptFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.DeclineFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendRequestsUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendRequestsViewModel @Inject constructor(
    private val observeFriendRequestsUseCase: ObserveFriendRequestsUseCase,
    private val acceptFriendRequestUseCase: AcceptFriendRequestUseCase,
    private val declineFriendRequestUseCase: DeclineFriendRequestUseCase,
    private val getUsersUseCase: GetUsersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendRequestsUiState())
    val uiState: StateFlow<FriendRequestsUiState> = _uiState.asStateFlow()

    init { observeRequests() }

    private fun observeRequests() {
        viewModelScope.launch {
            observeFriendRequestsUseCase().collect { requests ->
                // Fetch requester user data
                val userIds = requests.map { it.requesterId }
                val usersMap = mutableMapOf<String, User>()
                if (userIds.isNotEmpty()) {
                    when (val result = getUsersUseCase(userIds)) {
                        is Resource.Success -> result.data?.forEach { usersMap[it.id] = it }
                        else -> {}
                    }
                }
                _uiState.value = _uiState.value.copy(
                    requests = requests,
                    requestUsers = usersMap,
                    isLoading = false
                )
            }
        }
    }

    fun acceptRequest(friendshipId: String) {
        viewModelScope.launch { acceptFriendRequestUseCase(friendshipId) }
    }

    fun declineRequest(friendshipId: String) {
        viewModelScope.launch { declineFriendRequestUseCase(friendshipId) }
    }
}

data class FriendRequestsUiState(
    val requests: List<Friendship> = emptyList(),
    val requestUsers: Map<String, User> = emptyMap(),
    val isLoading: Boolean = true
)
