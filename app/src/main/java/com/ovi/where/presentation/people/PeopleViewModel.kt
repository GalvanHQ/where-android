package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.data.local.prefs.RecentSearchesStore
import com.ovi.where.domain.usecase.GetSuggestionsUseCase
import com.ovi.where.domain.usecase.SearchPeopleUseCase
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.friend.BlockUserUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.friend.ObserveSocialSummaryUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.presentation.common.search.SearchUiState
import com.ovi.where.presentation.model.FriendUiModel
import com.ovi.where.presentation.model.toFriendUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PeopleViewModel — design §5.4.
 * Combines `observeFriends` + `observeSocialSummary` into a single UI state.
 * Exposes long-press actions (unfriend, block) and message navigation.
 * Also manages search state with debounced query, recent searches, and suggestions.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val observeSocialSummaryUseCase: ObserveSocialSummaryUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val blockUserUseCase: BlockUserUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val searchPeopleUseCase: SearchPeopleUseCase,
    private val recentSearchesStore: RecentSearchesStore,
    private val getSuggestionsUseCase: GetSuggestionsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState: StateFlow<PeopleUiState> = _uiState.asStateFlow()

    private val _navigateToChat = MutableStateFlow<String?>(null)
    val navigateToChat: StateFlow<String?> = _navigateToChat.asStateFlow()

    // --- Search state ---

    private val _query = MutableStateFlow("")

    private val _isFocused = MutableStateFlow(false)

    private val _searchUiState = MutableStateFlow(SearchUiState())

    private var searchJob: Job? = null

    init {
        observeData()
        observeSearchState()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                observeFriendsUseCase(),
                observeSocialSummaryUseCase()
            ) { friends, summary ->
                PeopleUiState(
                    friends = friends
                        .map { it.toFriendUiModel() }
                        .sortedBy { it.displayName.lowercase() },
                    pendingRequestCount = summary.pendingIncomingCount,
                    blockedCount = summary.blockedCount,
                    isLoading = false,
                    error = null
                )
            }
                .catch { e ->
                    emit(
                        _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load friends"
                        )
                    )
                }
                .collect { _uiState.value = it }
        }
    }

    private fun observeSearchState() {
        // Debounced search results flow
        searchJob = viewModelScope.launch {
            combine(
                _query,
                _isFocused,
                recentSearchesStore.getRecentSearches("people"),
                getSuggestionsUseCase(),
                _query
                    .debounce(300)
                    .distinctUntilChanged()
                    .flatMapLatest { query ->
                        if (query.isBlank()) {
                            flowOf(emptyList())
                        } else {
                            // Use the current friends list as the data source for search
                            val friends = _uiState.value.friends
                            val friendEntries = friends.map { uiModel ->
                                com.ovi.where.domain.model.FriendEntry(
                                    friendUid = uiModel.userId,
                                    displayName = uiModel.displayName,
                                    username = uiModel.username,
                                    photoUrl = uiModel.photoUrl,
                                    isOnline = uiModel.isOnline
                                )
                            }
                            val results = searchPeopleUseCase(query, friendEntries)
                            flowOf(results.map { it.toFriendUiModel() })
                        }
                    }
            ) { query, isFocused, recentSearches, suggestions, searchResults ->
                SearchUiState(
                    query = query,
                    isFocused = isFocused,
                    isLoading = false,
                    recentSearches = recentSearches,
                    suggestions = suggestions,
                    searchResults = searchResults,
                    showEmptyState = query.isNotBlank() && searchResults.isEmpty()
                )
            }
                .catch { e ->
                    emit(_searchUiState.value.copy(isLoading = false))
                }
                .collect { _searchUiState.value = it }
        }
    }

    // --- Existing actions ---

    fun removeFriend(userId: String) {
        viewModelScope.launch { removeFriendUseCase(userId) }
    }

    fun blockUser(userId: String) {
        viewModelScope.launch { blockUserUseCase(userId) }
    }

    fun openOrCreateDm(userId: String) {
        viewModelScope.launch {
            when (val result = getOrCreateDirectConversationUseCase(userId)) {
                is Resource.Success -> {
                    result.data?.id?.let { conversationId ->
                        _navigateToChat.value = conversationId
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> { /* Loading */ }
            }
        }
    }

    fun onRetry() {
        _uiState.value = PeopleUiState() // reset to loading
        observeData()
    }

    fun onChatNavigated() {
        _navigateToChat.value = null
    }
}

data class PeopleUiState(
    val friends: List<FriendUiModel> = emptyList(),
    val pendingRequestCount: Int = 0,
    /** Number of users the local user has blocked. Drives the People-tab
     *  Blocked entry-point row's count badge. */
    val blockedCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)
