package com.ovi.where.presentation.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.data.local.prefs.RecentSearchesStore
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.repository.InteractionRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.GetSuggestionsUseCase
import com.ovi.where.domain.usecase.SearchChatsUseCase
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.friend.ObserveOutgoingRequestsUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.ovi.where.presentation.common.search.SearchUiState
import com.ovi.where.presentation.common.search.SuggestionUiModel
import com.ovi.where.presentation.model.FriendshipActionUiModel
import com.ovi.where.presentation.model.SearchUserUiModel
import com.ovi.where.presentation.model.formatConversationTimestamp
import com.ovi.where.presentation.model.toUiModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the full-screen search screen.
 * - "people" source: searches ALL users via Firestore
 * - "chats" source: filters local conversations list
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val searchChatsUseCase: SearchChatsUseCase,
    private val recentSearchesStore: RecentSearchesStore,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val interactionRepository: InteractionRepository,
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val observeOutgoingRequestsUseCase: ObserveOutgoingRequestsUseCase,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val source: String = savedStateHandle.get<String>("source") ?: "people"

    private val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    private val _searchUiState = MutableStateFlow(SearchUiState(isFocused = true))
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    /** Cached conversations for chats search */
    private var conversationsList: List<Conversation> = emptyList()

    init {
        if (source == "chats") {
            observeConversations()
        }
        observeRecentSearches()
        observeSuggestions()
    }

    private fun observeConversations() {
        viewModelScope.launch {
            observeConversationsUseCase().collect { conversations ->
                conversationsList = conversations
                // Re-run search if query is active
                val query = _searchUiState.value.query
                if (query.isNotBlank()) {
                    searchChats(query)
                }
            }
        }
    }

    private fun observeRecentSearches() {
        viewModelScope.launch {
            recentSearchesStore.getRecentSearches(source).collect { recentSearches ->
                _searchUiState.value = _searchUiState.value.copy(
                    recentSearches = recentSearches
                )
            }
        }
    }

    private fun observeSuggestions() {
        viewModelScope.launch {
            getSuggestionsUseCase().collect { suggestions ->
                _searchUiState.value = _searchUiState.value.copy(
                    suggestions = suggestions
                )
            }
        }
    }

    private fun searchPeople(query: String) {
        viewModelScope.launch {
            _searchUiState.value = _searchUiState.value.copy(isLoading = true)
            when (val result = userRepository.searchUsers(query)) {
                is Resource.Success -> {
                    val users = result.data ?: emptyList()
                    // Derive friendship status from already-subscribed flows
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
                    Timber.d("SearchVM: people search '$query' -> ${uiModels.size} results")
                    _searchUiState.value = _searchUiState.value.copy(
                        searchResults = uiModels,
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    Timber.e("SearchVM: people search error: ${result.message}")
                    _searchUiState.value = _searchUiState.value.copy(
                        searchResults = emptyList(),
                        isLoading = false
                    )
                }
                else -> {}
            }
        }
    }

    private fun searchChats(query: String) {
        val uid = currentUserId ?: ""
        val results = searchChatsUseCase(query, conversationsList)
        val uiModels = results.map { it.toUiModel(uid, ::formatConversationTimestamp) }
        Timber.d("SearchVM: chats search '$query' -> ${uiModels.size} results")
        _searchUiState.value = _searchUiState.value.copy(
            searchResults = uiModels,
            isLoading = false
        )
    }

    // ── Search Actions ──────────────────────────────────────────────────────

    fun onQueryChanged(query: String) {
        _searchUiState.value = _searchUiState.value.copy(
            query = query,
            isLoading = query.isNotBlank()
        )
        if (query.isBlank()) {
            _searchUiState.value = _searchUiState.value.copy(
                searchResults = emptyList(),
                isLoading = false
            )
            return
        }
        when (source) {
            "people" -> searchPeople(query)
            "chats" -> searchChats(query)
        }
    }

    fun onQuerySubmitted(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            recentSearchesStore.addSearch(source, trimmed)
        }
        // Also trigger search immediately
        when (source) {
            "people" -> searchPeople(trimmed)
            "chats" -> searchChats(trimmed)
        }
    }

    fun onClearQuery() {
        _searchUiState.value = _searchUiState.value.copy(
            query = "",
            searchResults = emptyList(),
            isLoading = false
        )
    }

    fun onFocusChanged(focused: Boolean) {
        _searchUiState.value = _searchUiState.value.copy(isFocused = focused)
    }

    fun onRecentSearchTapped(query: String) {
        _searchUiState.value = _searchUiState.value.copy(
            query = query,
            isLoading = true
        )
        when (source) {
            "people" -> searchPeople(query)
            "chats" -> searchChats(query)
        }
    }

    fun onRecentSearchDeleted(query: String) {
        viewModelScope.launch {
            recentSearchesStore.removeSearch(source, query)
        }
    }

    fun onClearAllRecentSearches() {
        viewModelScope.launch {
            recentSearchesStore.clearAll(source)
            interactionRepository.clearAll()
        }
        // Clear UI immediately
        _searchUiState.value = _searchUiState.value.copy(
            suggestions = emptyList(),
            recentSearches = emptyList()
        )
    }

    fun onSuggestionTapped(suggestion: SuggestionUiModel) {
        // Navigation handled by the screen layer
    }

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            sendFriendRequestUseCase(userId)
            // Optimistically update the action for this user in results
            val currentResults = _searchUiState.value.searchResults
            _searchUiState.value = _searchUiState.value.copy(
                searchResults = currentResults.map { item ->
                    if (item is SearchUserUiModel && item.userId == userId) {
                        item.copy(friendshipAction = FriendshipActionUiModel.PENDING)
                    } else item
                }
            )
        }
    }

    fun getOrCreateDirectChat(otherUserId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = getOrCreateDirectConversationUseCase(otherUserId)) {
                is Resource.Success -> onResult(result.data?.id)
                else -> onResult(null)
            }
        }
    }
}
