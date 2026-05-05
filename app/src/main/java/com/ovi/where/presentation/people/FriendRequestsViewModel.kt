package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.usecase.friend.AcceptFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.DeclineFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendRequestsUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import com.ovi.where.presentation.model.FriendRequestUiModel
import com.ovi.where.presentation.model.toUiModel
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
                // Batch-fetch requester profiles
                val userIds = requests.map { it.requesterId }.distinct()
                val usersById = if (userIds.isNotEmpty()) {
                    when (val result = getUsersUseCase(userIds)) {
                        is Resource.Success -> result.data?.associateBy { it.id } ?: emptyMap()
                        else               -> emptyMap()
                    }
                } else emptyMap()

                // Map domain Friendship + User → FriendRequestUiModel
                _uiState.value = _uiState.value.copy(
                    requests  = requests.map { friendship ->
                        friendship.toUiModel(requester = usersById[friendship.requesterId])
                    },
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
    val requests: List<FriendRequestUiModel> = emptyList(),
    val isLoading: Boolean = true
)
