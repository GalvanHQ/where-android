package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.friend.GetFriendshipStatusUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.ovi.where.presentation.model.FriendshipActionUiModel
import com.ovi.where.presentation.model.SearchUserUiModel
import com.ovi.where.presentation.model.toActionUiModel
import com.ovi.where.presentation.model.toSearchUiModel
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
        else if (query.isEmpty()) _uiState.value = _uiState.value.copy(results = emptyList())
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            when (val result = userRepository.searchUsers(query)) {
                is Resource.Success -> {
                    val users = result.data ?: emptyList()
                    // Fetch friendship status and map to UI models in one step
                    val uiModels = users.map { user ->
                        val status = getFriendshipStatusUseCase(user.id)
                        user.toSearchUiModel(status)
                    }
                    _uiState.value = _uiState.value.copy(
                        results    = uiModels,
                        isSearching = false
                    )
                }
                is Resource.Error -> _uiState.value = _uiState.value.copy(isSearching = false)
                else              -> {}
            }
        }
    }

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            sendFriendRequestUseCase(userId)
            // Optimistically update the action for this user
            _uiState.value = _uiState.value.copy(
                results = _uiState.value.results.map { item ->
                    if (item.userId == userId)
                        item.copy(friendshipAction = FriendshipActionUiModel.PENDING)
                    else item
                }
            )
        }
    }
}

data class SearchUsersUiState(
    val query: String = "",
    val results: List<SearchUserUiModel> = emptyList(),
    val isSearching: Boolean = false
)
