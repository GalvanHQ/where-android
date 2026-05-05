package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.friend.GetFriendshipStatusUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchUsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val getFriendshipStatusUseCase: GetFriendshipStatusUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUsersUiState())
    val uiState: StateFlow<SearchUsersUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        if (query.length >= 2) search(query)
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            when (val result = userRepository.searchUsers(query)) {
                is Resource.Success -> {
                    val currentUid = firebaseAuth.currentUser?.uid
                    val users = result.data
                        ?.filter { it.id != currentUid }
                        ?: emptyList()
                    // Fetch friendship status for each result
                    val statuses = mutableMapOf<String, FriendshipStatus?>()
                    for (user in users) {
                        statuses[user.id] = getFriendshipStatusUseCase(user.id)
                    }
                    _uiState.value = _uiState.value.copy(
                        results = users,
                        friendshipStatuses = statuses,
                        isSearching = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSearching = false)
                }
                else -> {}
            }
        }
    }

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            sendFriendRequestUseCase(userId)
            _uiState.value = _uiState.value.copy(
                friendshipStatuses = _uiState.value.friendshipStatuses.toMutableMap().apply {
                    put(userId, FriendshipStatus.PENDING)
                }
            )
        }
    }
}

data class SearchUsersUiState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val friendshipStatuses: Map<String, FriendshipStatus?> = emptyMap(),
    val isSearching: Boolean = false
)
