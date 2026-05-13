package com.ovi.where.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.formatConversationTimestamp
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ovi.where.data.util.Resource as DataResource

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val chatSocketIoClient: ChatSocketIoClient,
    private val firebaseAuth: FirebaseAuth,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    private val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    /** Full unfiltered conversation list from the repository. */
    private var allConversations: List<Conversation> = emptyList()

    /** Tracks which user IDs are currently online (from presence events). */
    private val onlineUserIds = mutableSetOf<String>()

    init {
        // Network requests are initiated ONLY from this init block (Requirement 11.1)
        // This ensures no duplicate API calls from Compose recomposition
        loadConversationsWithNetworkBoundResource()
        observePresenceUpdates()
    }

    // ── Initial Load (NetworkBoundResource Requirement 11.2) ────────────────

    /**
     * Loads conversations using the NetworkBoundResource pattern:
     * 1. Serves Room cache immediately (Loading state with cached data)
     * 2. Checks staleness and fetches from network if stale
     * 3. Updates Room on success, serves stale cache on failure
     *
     * Network request is triggered ONLY from this init block, not from
     * Compose recomposition (Requirement 11.1).
     */
    private fun loadConversationsWithNetworkBoundResource() {
        viewModelScope.launch {
            conversationRepository.getConversationsResource().collect { resource ->
                when (resource) {
                    is DataResource.Loading -> {
                        val conversations = resource.data ?: emptyList()
                        allConversations = conversations
                        _uiState.value = _uiState.value.copy(
                            isLoading = conversations.isEmpty(),
                            errorMessage = null
                        )
                        applySearchFilter()
                    }
                    is DataResource.Success -> {
                        allConversations = resource.data
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = null
                        )
                        applySearchFilter()
                    }
                    is DataResource.Error -> {
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

    // ── Foreground Sync (Task 16.3) ─────────────────────────────────────────

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

    /**
     * Applies the current search query to the full conversation list and updates UI state.
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

        _uiState.value = _uiState.value.copy(
            conversations = filtered.map { conversation ->
                conversation.toUiModel(uid, ::formatConversationTimestamp)
            },
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
}

data class ChatsUiState(
    val conversations: List<ConversationUiModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)
