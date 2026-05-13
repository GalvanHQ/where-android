package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.friend.ObserveOutgoingRequestsUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.CancelFriendRequestUseCase
import com.ovi.where.presentation.model.FriendshipActionUiModel
import com.ovi.where.presentation.model.SearchUserUiModel
import com.ovi.where.core.constants.AppConstants.MIN_SEARCH_QUERY_LENGTH
import com.ovi.where.core.constants.AppConstants.SEARCH_DEBOUNCE_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SearchUsersViewModel — design §5.4, §6.3.
 *
 * - 300ms debounce on query changes (Requirement 16.1).
 * - IME Search bypasses debounce (Requirement 16.3).
 * - Derives friendship status from already-subscribed `observeFriends` +
 *   `observeOutgoingRequests` flows — zero extra Firestore reads per search
 *   (Requirement 16.4, 18.4).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchUsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val cancelFriendRequestUseCase: CancelFriendRequestUseCase,
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val observeOutgoingRequestsUseCase: ObserveOutgoingRequestsUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUsersUiState())
    val uiState: StateFlow<SearchUsersUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        // Debounced search
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .filter { it.length >= MIN_SEARCH_QUERY_LENGTH }
                .collect { query -> executeSearch(query) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        queryFlow.value = query
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), error = null)
        }
    }

    /** Bypass debounce — triggered by IME Search action (Requirement 16.3). */
    fun onSearchImmediate() {
        val query = _uiState.value.query
        if (query.length >= MIN_SEARCH_QUERY_LENGTH) {
            viewModelScope.launch { executeSearch(query) }
        }
    }

    private suspend fun executeSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        when (val result = userRepository.searchUsers(query)) {
            is Resource.Success -> {
                val users = result.data ?: emptyList()
                // Derive friendship status from already-subscribed flows — zero reads.
                val friendIds = try {
                    observeFriendsUseCase().first().map { it.friendUid }.toSet()
                } catch (_: Exception) { emptySet() }
                val outgoingIds = try {
                    observeOutgoingRequestsUseCase().first().map { it.uid }.toSet()
                } catch (_: Exception) { emptySet() }

                val uiModels = users.map { user ->
                    val action = when {
                        user.id in friendIds -> FriendshipActionUiModel.FRIENDS
                        user.id in outgoingIds -> FriendshipActionUiModel.PENDING
                        else -> FriendshipActionUiModel.ADD
                    }
                    SearchUserUiModel(
                        userId = user.id,
                        displayName = user.displayName,
                        username = user.username,
                        photoUrl = user.photoUrl,
                        avatarInitial = user.displayName.take(1).uppercase().ifEmpty { "?" },
                        friendshipAction = action
                    )
                }
                _uiState.value = _uiState.value.copy(
                    results = uiModels,
                    isSearching = false
                )
            }
            is Resource.Error -> {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = result.message
                )
            }
            else -> {}
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

    fun cancelFriendRequest(userId: String) {
        viewModelScope.launch {
            cancelFriendRequestUseCase(userId)
            // Optimistically revert to ADD
            _uiState.value = _uiState.value.copy(
                results = _uiState.value.results.map { item ->
                    if (item.userId == userId)
                        item.copy(friendshipAction = FriendshipActionUiModel.ADD)
                    else item
                }
            )
        }
    }
}

data class SearchUsersUiState(
    val query: String = "",
    val results: List<SearchUserUiModel> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)
