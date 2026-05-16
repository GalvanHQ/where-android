package com.ovi.where.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.local.dao.OnlineStatusDao
import com.ovi.where.data.local.entity.OnlineStatusEntity
import com.ovi.where.data.local.prefs.RecentSearchesStore
import com.ovi.where.data.network.ConnectivityObserver
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.usecase.GetSuggestionsUseCase
import com.ovi.where.domain.usecase.SearchChatsUseCase
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.location.ObserveActiveLocationsUseCase
import com.ovi.where.presentation.common.search.SearchUiState
import com.ovi.where.presentation.common.search.SuggestionUiModel
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.formatConversationTimestamp
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dagger.Lazy
import javax.inject.Inject
import com.ovi.where.data.util.Resource as DataResource

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val lazyChatSocketIoClient: Lazy<ChatSocketIoClient>,
    private val firebaseAuth: FirebaseAuth,
    private val conversationRepository: ConversationRepository,
    private val searchChatsUseCase: SearchChatsUseCase,
    private val recentSearchesStore: RecentSearchesStore,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val onlineStatusDao: OnlineStatusDao,
    private val connectivityObserver: ConnectivityObserver,
    private val observeActiveLocationsUseCase: ObserveActiveLocationsUseCase
) : ViewModel() {

    /**
     * Lazily-resolved ChatSocketIoClient instance.
     * Not instantiated until first access, keeping app startup free of chat initialization (Req 20.1, 20.4, 20.5).
     */
    private val chatSocketIoClient: ChatSocketIoClient get() = lazyChatSocketIoClient.get()
    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    private val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    /** Full unfiltered conversation list from the repository. */
    private var allConversations: List<Conversation> = emptyList()

    /** Tracks which user IDs are currently online (from presence events). */
    private val onlineUserIds = mutableSetOf<String>()

    /** Active location sharing sessions visible to the current user (Req 3.6). */
    private var activeLocations: List<SharedLocation> = emptyList()

    /**
     * Debounce job for reverting location sharing status.
     * Ensures icon/preview removal within 5s of all sessions ending (Req 3.5).
     */
    private var locationRevertJob: kotlinx.coroutines.Job? = null

    // ── Search State ────────────────────────────────────────────────────────

    private val _query = MutableStateFlow("")
    private val _isFocused = MutableStateFlow(false)

    /**
     * Debounced search results flow.
     * Waits 300ms after the last keystroke, filters out blank queries,
     * then invokes SearchChatsUseCase against the current conversation list.
     */
    private val debouncedSearchResults: StateFlow<List<Conversation>> =
        _query
            .debounce(300L)
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    flowOf(searchChatsUseCase(query, allConversations))
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Combined search UI state exposed to the Chats screen.
     * Merges query, focus, recent searches, suggestions, and debounced search results.
     */
    val searchUiState: StateFlow<SearchUiState> = combine(
        _query,
        _isFocused,
        recentSearchesStore.getRecentSearches("chats"),
        getSuggestionsUseCase(),
        debouncedSearchResults
    ) { query, focused, recentSearches, suggestions, results ->
        val uid = currentUserId ?: ""
        SearchUiState(
            query = query,
            isFocused = focused,
            isLoading = query.isNotBlank() && results.isEmpty() && allConversations.isNotEmpty(),
            recentSearches = recentSearches,
            suggestions = suggestions,
            searchResults = if (query.isBlank()) emptyList()
            else results.map { it.toUiModel(uid, ::formatConversationTimestamp) },
            showEmptyState = query.isNotBlank() && results.isEmpty() && allConversations.isNotEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, SearchUiState())

    init {
        // Network requests are initiated ONLY from this init block (Requirement 11.1)
        // This ensures no duplicate API calls from Compose recomposition
        loadConversationsWithFastFirstPaint()
        observePresenceUpdates()
        observeOfflineState()
        observeLocationSharingStatus()
    }

    // ── Initial Load with Fast First Paint (Requirements 20.2, 20.3) ────────

    /**
     * Loads conversations with fast first paint optimization:
     *
     * - Cache non-empty: emits cached conversations from Room within 200ms of init (Req 20.2)
     * - Cache empty (first install): emits loading state within 100ms, waits for network
     *   fetch with a 10-second timeout before showing empty list (Req 20.3)
     *
     * Architecture:
     * 1. Immediately starts collecting from Room's conversation flow (no blocking calls first)
     * 2. If Room has cached data, it emits within 200ms (Room Flow is synchronous on first emit)
     * 3. If Room is empty, shows loading and triggers network fetch with timeout
     * 4. After initial paint, continues with the NetworkBoundResource pattern for staleness checks
     */
    private fun loadConversationsWithFastFirstPaint() {
        // Phase 1: Immediately collect from Room for fast first paint (Req 20.2)
        viewModelScope.launch {
            var initialEmitHandled = false

            conversationRepository.getConversationsResource().collect { resource ->
                when (resource) {
                    is DataResource.Loading -> {
                        val conversations = resource.data ?: emptyList()
                        allConversations = conversations

                        if (!initialEmitHandled) {
                            initialEmitHandled = true
                            if (conversations.isNotEmpty()) {
                                // Cache non-empty: emit immediately (within 200ms of init) (Req 20.2)
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    errorMessage = null
                                )
                                applySearchFilter()
                            } else {
                                // Cache empty: emit loading state within 100ms (Req 20.3)
                                val shouldShowLoading = !_uiState.value.isOffline
                                _uiState.value = _uiState.value.copy(
                                    isLoading = shouldShowLoading,
                                    errorMessage = null
                                )
                                applySearchFilter()
                                // Start network fetch with 10s timeout (Req 20.3)
                                startNetworkFetchWithTimeout()
                            }
                        } else {
                            // Subsequent Loading emissions (e.g., during refresh)
                            val shouldShowLoading = conversations.isEmpty() && !_uiState.value.isOffline
                            _uiState.value = _uiState.value.copy(
                                isLoading = shouldShowLoading,
                                errorMessage = null
                            )
                            applySearchFilter()
                        }
                    }
                    is DataResource.Success -> {
                        initialEmitHandled = true
                        allConversations = resource.data
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = null
                        )
                        applySearchFilter()
                    }
                    is DataResource.Error -> {
                        initialEmitHandled = true
                        // Serve stale cache on failure (Requirement 11.3)
                        val conversations = resource.data ?: emptyList()
                        allConversations = conversations
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = resource.throwable.message
                        )
                        applySearchFilter()
                    }
                }
            }
        }
    }

    /**
     * Handles the empty-cache scenario (first install):
     * Fetches initial conversations from network with a 10-second timeout.
     * If the timeout elapses without data arriving, shows an empty list (Req 20.3).
     */
    private fun startNetworkFetchWithTimeout() {
        viewModelScope.launch {
            // Trigger the initial fetch (writes to Room, which triggers the flow above)
            val fetchJob = launch {
                conversationRepository.fetchInitialConversationsIfNeeded()
            }

            // 10-second timeout: if no data arrives, stop loading and show empty list
            val timeoutJob = launch {
                delay(NETWORK_FETCH_TIMEOUT_MS)
                if (_uiState.value.isLoading && allConversations.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = null
                    )
                    applySearchFilter()
                }
            }

            // Cancel timeout if fetch completes first
            fetchJob.invokeOnCompletion { timeoutJob.cancel() }
        }
    }

    // ── Foreground Sync (Task 16.3) ─────────────────────────────────────────

    /**
     * Observes network connectivity state.
     * Requirement 7.5: While offline, display all cached data without loading indicators.
     */
    private fun observeOfflineState() {
        viewModelScope.launch {
            connectivityObserver.isConnected.collect { isConnected ->
                _uiState.value = _uiState.value.copy(
                    isOffline = !isConnected
                )
                // Requirement 7.5: While offline, suppress loading indicators
                if (!isConnected && _uiState.value.isLoading) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    // ── Location Sharing Status (Task 3.4, Req 3.1-3.6) ────────────────────

    /**
     * Observes active location sharing sessions from LocationRepository.
     * Reuses the existing consolidated observeActiveLocations flow (Req 3.6).
     * Updates conversation list previews with location sharing status.
     * Reverts preview within 5s of all sessions ending (Req 3.5).
     */
    private fun observeLocationSharingStatus() {
        viewModelScope.launch {
            observeActiveLocationsUseCase()
                .distinctUntilChanged()
                .collect { locations ->
                    val previousLocations = activeLocations
                    activeLocations = locations

                    // Req 3.5: If all sessions just ended, delay removal by up to 5s
                    if (locations.none { it.isSharingActive } && previousLocations.any { it.isSharingActive }) {
                        locationRevertJob?.cancel()
                        locationRevertJob = viewModelScope.launch {
                            delay(5_000L)
                            activeLocations = emptyList()
                            applySearchFilter()
                        }
                    } else {
                        // Cancel any pending revert if new sharing started
                        if (locations.any { it.isSharingActive }) {
                            locationRevertJob?.cancel()
                        }
                        applySearchFilter()
                    }
                }
        }
    }

    /**
     * Triggers foreground sync of unread counts on app resume.
     * Called from ChatsScreen lifecycle observer when the app returns to foreground.
     *
     * Requirement 12.5: Sync unread counts via single REST call on app foreground.
     * Requirement 12.6: On failure/timeout, retain Room-cached counts.
     */
    fun onForegroundSync() {
        viewModelScope.launch {
            conversationRepository.syncUnreadCounts()
        }
    }

    /**
     * Explicit user-triggered refresh (e.g., pull-to-refresh).
     * Bypasses staleness check and always fetches from network (Requirement 11.1).
     */
    fun onRefresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            conversationRepository.refreshConversations().collect { resource ->
                when (resource) {
                    is DataResource.Loading -> { /* Already showing refresh indicator */ }
                    is DataResource.Success -> {
                        allConversations = resource.data
                        _uiState.value = _uiState.value.copy(
                            isRefreshing = false,
                            errorMessage = null
                        )
                        applySearchFilter()
                    }
                    is DataResource.Error -> {
                        val conversations = resource.data ?: emptyList()
                        allConversations = conversations
                        _uiState.value = _uiState.value.copy(
                            isRefreshing = false,
                            errorMessage = resource.throwable.message
                        )
                        applySearchFilter()
                    }
                }
            }
        }
    }

    // ── Observation ─────────────────────────────────────────────────────────

    /**
     * Observes presence frames from ChatSocketIoClient to track online status
     * per conversation avatar (Requirement 9.7).
     * Persists presence state to Room for offline access (Requirement 6.3).
     */
    private fun observePresenceUpdates() {
        viewModelScope.launch {
            chatSocketIoClient.incomingFrames.collect { frame ->
                if (frame is ServerFrame.Presence) {
                    val changed = if (frame.status == "online") {
                        onlineUserIds.add(frame.userId)
                    } else {
                        onlineUserIds.remove(frame.userId)
                    }
                    if (changed) {
                        // Persist to Room for offline access (Req 6.3)
                        onlineStatusDao.upsert(
                            OnlineStatusEntity(
                                userId = frame.userId,
                                isOnline = frame.status == "online",
                                lastUpdatedAt = System.currentTimeMillis()
                            )
                        )
                        applySearchFilter()
                    }
                }
            }
        }
    }

    // ── Search / Filter ─────────────────────────────────────────────────────

    /**
     * Updates the search query and filters conversations locally.
     * Case-insensitive substring match on name or lastMessageText (Requirement 9.3).
     * Restores full list when search is cleared (Requirement 9.4).
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applySearchFilter()
    }

    // ── Messenger-Style Search Actions ──────────────────────────────────────

    /**
     * Called when the user types in the search bar.
     * Empty/whitespace-only queries cancel pending searches.
     */
    fun onQueryChanged(query: String) {
        _query.value = query
        // Also update the legacy search filter for backward compatibility
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applySearchFilter()
    }

    /**
     * Called when the user submits the search query (e.g., presses enter).
     * Persists the trimmed query to recent searches if non-blank.
     */
    fun onQuerySubmitted(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            recentSearchesStore.addSearch("chats", trimmed)
        }
    }

    /**
     * Called when the user taps the clear button in the search bar.
     * Resets the query and cancels any pending search.
     */
    fun onClearQuery() {
        _query.value = ""
        _uiState.value = _uiState.value.copy(searchQuery = "")
        applySearchFilter()
    }

    /**
     * Called when the search bar focus state changes.
     */
    fun onFocusChanged(focused: Boolean) {
        _isFocused.value = focused
    }

    /**
     * Called when the user taps a recent search chip.
     * Sets the query and triggers a search.
     */
    fun onRecentSearchTapped(query: String) {
        _query.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applySearchFilter()
    }

    /**
     * Called when the user deletes a single recent search entry.
     */
    fun onRecentSearchDeleted(query: String) {
        viewModelScope.launch {
            recentSearchesStore.removeSearch("chats", query)
        }
    }

    /**
     * Called when the user taps "Clear all" on recent searches.
     */
    fun onClearAllRecentSearches() {
        viewModelScope.launch {
            recentSearchesStore.clearAll("chats")
        }
    }

    /**
     * Called when the user taps a suggestion avatar.
     * Navigates to the conversation with that user.
     */
    fun onSuggestionTapped(suggestion: SuggestionUiModel) {
        getOrCreateDirectChat(suggestion.userId) { /* navigation handled by screen */ }
    }

    /**
     * Applies the current search query to the full conversation list and updates UI state.
     * Pinned conversations are sorted to the top, maintaining timestamp sort within each group (Req 24.4).
     */
    private fun applySearchFilter() {
        val uid = currentUserId ?: ""
        val query = _uiState.value.searchQuery

        val filtered = if (query.isBlank()) {
            allConversations
        } else {
            allConversations.filter { conversation ->
                conversation.name.contains(query, ignoreCase = true) ||
                    conversation.lastMessageText.contains(query, ignoreCase = true)
            }
        }

        val uiModels = filtered.map { conversation ->
            conversation.toUiModel(uid, ::formatConversationTimestamp, activeLocations)
        }

        // Sort: pinned conversations at top (timestamp sort within pinned group),
        // then unpinned conversations sorted by timestamp (Req 24.4)
        val sorted = uiModels.sortedWith(
            compareByDescending<ConversationUiModel> { it.isPinned }
                .thenByDescending { it.lastMessageTime } // Already formatted, but we need raw timestamp for proper sort
        )

        // Re-sort using the raw domain model timestamps for correct ordering
        val pinnedIds = allConversations
            .filter { uid in it.pinnedBy }
            .sortedByDescending { it.lastMessageTimestamp }
            .map { it.id }
        val unpinnedIds = allConversations
            .filter { uid !in it.pinnedBy }
            .sortedByDescending { it.lastMessageTimestamp }
            .map { it.id }

        val orderedIds = pinnedIds + unpinnedIds
        val uiModelMap = uiModels.associateBy { it.id }
        val orderedUiModels = orderedIds.mapNotNull { id -> uiModelMap[id] }
            .filter { model ->
                if (query.isBlank()) true
                else {
                    val conv = allConversations.find { it.id == model.id }
                    conv != null && (conv.name.contains(query, ignoreCase = true) ||
                        conv.lastMessageText.contains(query, ignoreCase = true))
                }
            }

        _uiState.value = _uiState.value.copy(
            conversations = orderedUiModels,
            isLoading = false
        )
    }

    // ── Unread Count Formatting ─────────────────────────────────────────────

    /**
     * Formats an unread count for display, capping at "99+" for counts exceeding 99
     * (Requirement 9.2).
     */
    fun formatUnreadCount(count: Int): String {
        return when {
            count <= 0 -> ""
            count > 99 -> "99+"
            else -> count.toString()
        }
    }

    // ── Online Status ───────────────────────────────────────────────────────

    /**
     * Returns whether the other participant in a direct conversation is online.
     * For group conversations, returns false (online dot is only for direct chats).
     * (Requirement 9.7)
     */
    fun isConversationOnline(conversation: ConversationUiModel): Boolean {
        if (conversation.isGroup) return false
        val uid = currentUserId ?: return false
        // Find the matching domain conversation to get participantIds
        val domainConversation = allConversations.find { it.id == conversation.id } ?: return false
        if (domainConversation.type == ConversationType.GROUP) return false
        // The other participant is the one who isn't the current user
        val otherUserId = domainConversation.participantIds.firstOrNull { it != uid }
        return otherUserId != null && otherUserId in onlineUserIds
    }

    // ── Direct Chat Creation ────────────────────────────────────────────────

    fun getOrCreateDirectChat(otherUserId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = getOrCreateDirectConversationUseCase(otherUserId)) {
                is Resource.Success -> onResult(result.data?.id)
                else -> onResult(null)
            }
        }
    }

    // ── Delete Conversation ─────────────────────────────────────────────────

    /**
     * Deletes a conversation from the local database.
     * Removes it from Room so it disappears from the list immediately.
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
        }
    }

    // ── Pin / Mute / Archive Actions (Requirement 24.3, 24.4, 24.5) ────────

    /**
     * Shows the long-press context menu for a conversation (Req 24.3).
     */
    fun showConversationContextMenu(conversationId: String) {
        _uiState.value = _uiState.value.copy(contextMenuConversationId = conversationId)
    }

    /**
     * Dismisses the conversation context menu.
     */
    fun dismissConversationContextMenu() {
        _uiState.value = _uiState.value.copy(contextMenuConversationId = null)
    }

    /**
     * Pins or unpins a conversation (Req 24.4).
     * Pinned conversations appear at the top of the list (max 3).
     * Shows error if attempting to pin a 4th conversation.
     */
    fun togglePinConversation(conversationId: String) {
        val conversation = _uiState.value.conversations.find { it.id == conversationId } ?: return
        viewModelScope.launch {
            val result = if (conversation.isPinned) {
                conversationRepository.unpinConversation(conversationId)
            } else {
                conversationRepository.pinConversation(conversationId)
            }
            when (result) {
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        pinLimitError = result.message,
                        contextMenuConversationId = null
                    )
                }
                is Resource.Success -> {
                    // Update local state optimistically
                    val updatedConversation = allConversations.find { it.id == conversationId }
                    if (updatedConversation != null) {
                        val uid = currentUserId ?: return@launch
                        val updatedPinnedBy = if (conversation.isPinned) {
                            updatedConversation.pinnedBy - uid
                        } else {
                            updatedConversation.pinnedBy + uid
                        }
                        allConversations = allConversations.map {
                            if (it.id == conversationId) it.copy(pinnedBy = updatedPinnedBy) else it
                        }
                        applySearchFilter()
                    }
                    _uiState.value = _uiState.value.copy(contextMenuConversationId = null)
                }
                else -> {
                    _uiState.value = _uiState.value.copy(contextMenuConversationId = null)
                }
            }
        }
    }

    /**
     * Mutes or unmutes a conversation (Req 24.5).
     * Muted conversations show a mute icon and no unread badge.
     */
    fun toggleMuteConversation(conversationId: String) {
        val conversation = _uiState.value.conversations.find { it.id == conversationId } ?: return
        viewModelScope.launch {
            val result = if (conversation.isMuted) {
                conversationRepository.unmuteConversation(conversationId)
            } else {
                conversationRepository.muteConversation(conversationId)
            }
            when (result) {
                is Resource.Success -> {
                    val uid = currentUserId ?: return@launch
                    val updatedConversation = allConversations.find { it.id == conversationId }
                    if (updatedConversation != null) {
                        val updatedMutedBy = if (conversation.isMuted) {
                            updatedConversation.mutedBy - uid
                        } else {
                            updatedConversation.mutedBy + uid
                        }
                        allConversations = allConversations.map {
                            if (it.id == conversationId) it.copy(mutedBy = updatedMutedBy) else it
                        }
                        applySearchFilter()
                    }
                    _uiState.value = _uiState.value.copy(contextMenuConversationId = null)
                }
                else -> {
                    _uiState.value = _uiState.value.copy(contextMenuConversationId = null)
                }
            }
        }
    }

    /**
     * Archives a conversation (Req 24.3).
     * Removes it from the active conversation list.
     */
    fun archiveConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.archiveConversation(conversationId)
            _uiState.value = _uiState.value.copy(contextMenuConversationId = null)
        }
    }

    /**
     * Dismisses the pin limit error message.
     */
    fun dismissPinLimitError() {
        _uiState.value = _uiState.value.copy(pinLimitError = null)
    }

    companion object {
        /**
         * Timeout for network fetch when cache is empty (first install).
         * After this duration, loading state is dismissed and empty list is shown (Req 20.3).
         */
        const val NETWORK_FETCH_TIMEOUT_MS = 10_000L
    }
}

data class ChatsUiState(
    val conversations: List<ConversationUiModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /** Whether the device is currently offline (Requirement 7.5). */
    val isOffline: Boolean = false,
    /** Conversation ID for which the context menu is currently shown (Req 24.3). */
    val contextMenuConversationId: String? = null,
    /** Error message for pin limit exceeded (Req 24.4). */
    val pinLimitError: String? = null
)
