package com.ovi.where.presentation.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.location.LocationManager
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.data.repository.MessageRepositoryImpl
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.usecase.chat.MarkConversationReadUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.chat.ObserveMessagesUseCase
import com.ovi.where.domain.usecase.chat.SendLocationMessageUseCase
import com.ovi.where.domain.usecase.chat.SendMessageUseCase
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.MessageUiModel
import com.ovi.where.presentation.model.formatConversationTimestamp
import com.ovi.where.presentation.model.formatMessageDateKey
import com.ovi.where.presentation.model.formatMessageTime
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * ChatViewModel manages state for the individual chat screen.
 *
 * Responsibilities:
 * - Task 6.1: Observes messages flow from MessageRepository (sorted by timestamp ASC, ID as tiebreaker)
 * - Task 6.1: Loads initial 30 messages from Room, then syncs with server
 * - Task 6.2: Pagination trigger on scroll-to-top
 * - Task 6.3: Message send with input/reply clearing, retry, consecutive failure tracking, offline queue rejection
 *
 * The conversationId is obtained from SavedStateHandle (navigation argument).
 *
 * Requirements: 1.3, 1.4, 1.5, 1.7, 2.1, 2.2, 2.3, 2.6, 14.1, 14.5
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendLocationMessageUseCase: SendLocationMessageUseCase,
    private val markConversationReadUseCase: MarkConversationReadUseCase,
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val wsClient: ChatSocketIoClient,
    private val messageRepositoryImpl: MessageRepositoryImpl,
    private val firebaseAuth: FirebaseAuth,
    private val locationManager: LocationManager,
    private val friendshipRepository: FriendshipRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * One-shot snackbar events for the UI to observe and display.
     * Requirement 1.5: Show "Message could not be sent" for 4s after 3 consecutive failures.
     */
    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<SnackbarEvent> = _snackbarEvent.asSharedFlow()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    /**
     * Tracks consecutive send/retry failures for the snackbar trigger.
     * Requirement 1.5: 3 consecutive failures → snackbar "Message could not be sent" for 4s.
     */
    private var consecutiveFailureCount = 0

    /**
     * The conversationId from the navigation argument via SavedStateHandle.
     */
    private val conversationId: String? =
        savedStateHandle.get<String>("conversationId")

    init {
        conversationId?.let { convId ->
            _uiState.value = _uiState.value.copy(conversationId = convId)
            loadConversation(convId)
            loadInitialMessages(convId)
            connectWebSocket(convId)
            observeTyping()
            observeConnectionState()
            emitReadOnOpen(convId)
        }
    }

    /**
     * Legacy init method for backward compatibility with existing ChatScreen.
     * If conversationId was not available via SavedStateHandle (e.g., passed manually),
     * this method can still be used.
     */
    fun init(conversationId: String) {
        if (_uiState.value.conversationId == conversationId) return
        _uiState.value = _uiState.value.copy(conversationId = conversationId)
        loadConversation(conversationId)
        loadInitialMessages(conversationId)
        connectWebSocket(conversationId)
        observeTyping()
        observeConnectionState()
        emitReadOnOpen(conversationId)
    }

    // ─── Message Observation and Initial Load (Task 6.1) ──────────────────────

    /**
     * Loads initial messages and observes the messages flow.
     *
     * Flow:
     * 1. Set isLoading = true
     * 2. Call loadOlderMessages with null cursor (initial load) to get up to 30 messages
     *    from Room cache and trigger background server sync
     * 3. Observe messages flow from MessageRepository (Room is source of truth)
     * 4. Sort messages by timestamp ascending, using message ID as secondary sort key
     * 5. Update UI state with sorted messages, paginationCursor, hasMoreMessages
     *
     * Requirements: 2.1, 14.1, 14.5
     */
    private fun loadInitialMessages(conversationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load initial page (up to 30 messages from Room, then background sync with server)
            val initialPage = messageRepositoryImpl.loadOlderMessages(
                conversationId = conversationId,
                beforeCursor = null,
                limit = INITIAL_PAGE_SIZE
            )

            // Set pagination state from initial load
            _uiState.value = _uiState.value.copy(
                paginationCursor = initialPage.nextCursor,
                hasMoreMessages = initialPage.hasMore
            )

            // Observe messages flow from Room (single source of truth)
            // Messages are sorted by timestamp ASC with ID as secondary sort key
            val uid = currentUserId ?: ""
            observeMessagesUseCase(conversationId).collect { messages ->
                val sortedMessages = sortMessages(messages)
                _uiState.value = _uiState.value.copy(
                    messages = sortedMessages.map {
                        it.toUiModel(
                            currentUserId = uid,
                            timeFormatter = ::formatMessageTime,
                            dateKeyFormatter = ::formatMessageDateKey
                        )
                    },
                    isLoading = false
                )
            }
        }
    }

    /**
     * Sorts messages by timestamp ascending, using message ID as secondary sort key
     * for deterministic ordering when timestamps are equal.
     *
     * Requirement 14.1: Messages sorted by timestamp ascending at all times.
     * Requirement 14.5: Message ID as secondary sort key for same-timestamp messages.
     */
    private fun sortMessages(messages: List<Message>): List<Message> {
        return messages.sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id })
    }

    // ─── Conversation Observation ─────────────────────────────────────────────

    private fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val uid = currentUserId ?: ""
            observeConversationsUseCase().collect { conversations ->
                val conv = conversations.firstOrNull { it.id == conversationId }
                if (conv != null) {
                    val uiModel = conv.toUiModel(uid, ::formatConversationTimestamp)
                    _uiState.value = _uiState.value.copy(conversation = uiModel)
                    // Check friendship status for 1:1 conversations (Requirement 8.4)
                    if (!uiModel.isGroup && uiModel.otherUserId != null) {
                        checkFriendshipStatus(uiModel.otherUserId)
                    }
                }
            }
        }
    }

    // ─── Friendship Status Check (Task 11.3) ──────────────────────────────────

    /**
     * Checks whether the other user in a 1:1 conversation is in the caller's friends list.
     * Used to determine whether to show presence status in the header.
     *
     * Requirement 8.4: Hide presence status if other user not in friends list.
     */
    private fun checkFriendshipStatus(otherUserId: String) {
        viewModelScope.launch {
            try {
                val status = friendshipRepository.getFriendshipStatus(otherUserId)
                _uiState.value = _uiState.value.copy(
                    isOtherUserFriend = status == FriendshipStatus.ACCEPTED
                )
            } catch (_: Exception) {
                // On failure, default to not showing presence (safe default)
                _uiState.value = _uiState.value.copy(isOtherUserFriend = false)
            }
        }
    }

    // ─── WebSocket Connection ─────────────────────────────────────────────────

    private fun connectWebSocket(conversationId: String) {
        viewModelScope.launch {
            val token = firebaseAuth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
            wsClient.connect(conversationId, token)
        }
    }

    // ─── Typing Observation (Task 6.5) ──────────────────────────────────────────

    /**
     * Tracks currently typing users: userId → userName.
     * Used for group chats where multiple users can be typing simultaneously.
     */
    private val typingUsers = ConcurrentHashMap<String, String>()

    /**
     * Timeout jobs per typing user. Each job auto-hides the typing indicator
     * after [TYPING_TIMEOUT_MS] (5 seconds) of no typing event from that user.
     *
     * Requirement 7.5: Auto-hide after 5 seconds of no typing event from a user.
     */
    private val typingTimeoutJobs = ConcurrentHashMap<String, Job>()

    /**
     * Observes incoming typing frames from the WebSocket and manages the typing
     * indicator state for both 1:1 and group conversations.
     *
     * Behavior:
     * - On typing(true): add user to typingUsers map, start/reset 5s timeout
     * - On typing(false): remove user from typingUsers map, cancel timeout
     * - Auto-hide after 5 seconds of no typing event from a user (Requirement 7.5)
     * - Format display text per Requirement 7.2 and 7.6
     *
     * Requirements: 7.1, 7.2, 7.5, 7.6
     */
    private fun observeTyping() {
        viewModelScope.launch {
            wsClient.incomingFrames.collect { frame ->
                if (frame is ServerFrame.UserTyping) {
                    // Ignore typing events from the current user
                    if (frame.userId == currentUserId) return@collect

                    if (frame.isTyping) {
                        // Add user to typing map
                        typingUsers[frame.userId] = frame.userName

                        // Cancel existing timeout for this user and start a new one
                        typingTimeoutJobs[frame.userId]?.cancel()
                        typingTimeoutJobs[frame.userId] = viewModelScope.launch {
                            delay(TYPING_TIMEOUT_MS)
                            // Auto-hide after 5 seconds of no typing event (Requirement 7.5)
                            typingUsers.remove(frame.userId)
                            typingTimeoutJobs.remove(frame.userId)
                            updateTypingIndicatorState()
                        }
                    } else {
                        // Remove user from typing map
                        typingUsers.remove(frame.userId)
                        typingTimeoutJobs[frame.userId]?.cancel()
                        typingTimeoutJobs.remove(frame.userId)
                    }

                    updateTypingIndicatorState()
                }
            }
        }
    }

    /**
     * Updates the UI state with the current typing indicator text.
     * Formats the text based on the number of currently typing users.
     *
     * Requirements: 7.2, 7.6
     */
    private fun updateTypingIndicatorState() {
        val typerNames = typingUsers.values.toList()
        val typingText = formatTypingIndicatorText(typerNames)
        _uiState.value = _uiState.value.copy(
            typingIndicatorText = typingText,
            typingUserId = typerNames.firstOrNull()?.let { typingUsers.keys.firstOrNull() },
            typingUserName = typerNames.firstOrNull()
        )
    }

    // ─── Connection State Observation and Reconnection UI (Task 6.7) ──────────

    /** Job for the delayed banner show (500ms delay from disconnect). */
    private var bannerShowJob: Job? = null

    /** Job for the banner fade-out animation (300ms on reconnect). */
    private var bannerFadeJob: Job? = null

    /**
     * Observes ChatSocketIoClient.connectionState and manages reconnection UI state.
     *
     * Behavior:
     * - On DISCONNECTED or ERROR: show "Reconnecting..." banner within 500ms (Requirement 13.1)
     * - On ERROR after 10 attempts exhausted: show manual "Retry" action (Requirement 13.3)
     * - On CONNECTED (reconnect): fetch missed messages via REST, flush queue, hide banner
     *   with 300ms fade-out (Requirements 13.4, 13.5, 13.7)
     *
     * Requirements: 13.1, 13.3, 13.4, 13.5, 13.6, 13.7
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            var wasDisconnected = false

            wsClient.connectionState.collect { state ->
                when (state) {
                    ChatSocketIoClient.ConnectionState.DISCONNECTED,
                    ChatSocketIoClient.ConnectionState.ERROR -> {
                        // Cancel any ongoing fade-out
                        bannerFadeJob?.cancel()
                        bannerFadeJob = null

                        // Show banner within 500ms of disconnect (Requirement 13.1)
                        if (bannerShowJob == null || bannerShowJob?.isActive != true) {
                            bannerShowJob = viewModelScope.launch {
                                delay(RECONNECT_BANNER_DELAY_MS)
                                _uiState.value = _uiState.value.copy(
                                    showReconnectingBanner = true,
                                    isBannerFadingOut = false
                                )
                            }
                        }

                        // Check if all reconnection attempts are exhausted (Requirement 13.3)
                        // The ChatSocketIoClient transitions to ERROR after exhausting 10 attempts
                        if (state == ChatSocketIoClient.ConnectionState.ERROR) {
                            // Cancel the delayed banner show and show immediately with retry action
                            bannerShowJob?.cancel()
                            bannerShowJob = null
                            _uiState.value = _uiState.value.copy(
                                showReconnectingBanner = true,
                                showManualRetryAction = true,
                                isBannerFadingOut = false,
                                reconnectAttempts = ChatSocketIoClient.MAX_RECONNECT_ATTEMPTS
                            )
                        }

                        wasDisconnected = true
                    }

                    ChatSocketIoClient.ConnectionState.CONNECTING -> {
                        // Update reconnect attempts count while reconnecting
                        // The banner remains visible during reconnection attempts
                    }

                    ChatSocketIoClient.ConnectionState.CONNECTED -> {
                        // Cancel the delayed banner show if it hasn't fired yet
                        bannerShowJob?.cancel()
                        bannerShowJob = null

                        if (wasDisconnected) {
                            // On reconnect: fetch missed messages and hide banner with fade
                            onReconnected()
                            wasDisconnected = false
                        } else {
                            // Initial connection — no banner to hide
                            _uiState.value = _uiState.value.copy(
                                showReconnectingBanner = false,
                                showManualRetryAction = false,
                                isBannerFadingOut = false,
                                reconnectAttempts = 0,
                                missedMessagesFetchFailed = false
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when the connection is restored after a disconnection.
     *
     * Actions:
     * 1. Fetch missed messages via REST using the last cached message timestamp (Requirement 13.5)
     * 2. Queue flush is handled automatically by MessageRepositoryImpl (Requirement 13.4)
     * 3. Hide the reconnecting banner with a 300ms fade-out animation (Requirement 13.7)
     *
     * If the REST fetch fails, retries once after 2s. If still failing, shows inline retry.
     * (Requirement 13.6)
     */
    private fun onReconnected() {
        val convId = _uiState.value.conversationId ?: return

        // Start fade-out animation for the banner (Requirement 13.7)
        _uiState.value = _uiState.value.copy(
            isBannerFadingOut = true,
            showManualRetryAction = false,
            reconnectAttempts = 0
        )

        bannerFadeJob = viewModelScope.launch {
            // Fetch missed messages via REST (Requirement 13.5)
            val result = messageRepositoryImpl.fetchMissedMessages(convId)
            if (result is Resource.Error) {
                // Show inline retry for missed messages fetch failure (Requirement 13.6)
                _uiState.value = _uiState.value.copy(missedMessagesFetchFailed = true)
            } else {
                _uiState.value = _uiState.value.copy(missedMessagesFetchFailed = false)
            }

            // Wait for 300ms fade-out animation to complete (Requirement 13.7)
            delay(RECONNECT_BANNER_FADE_MS)

            // Hide the banner completely
            _uiState.value = _uiState.value.copy(
                showReconnectingBanner = false,
                isBannerFadingOut = false
            )
        }
    }

    /**
     * Manually triggers a reconnection attempt.
     * Called when the user taps the "Retry" button after automatic reconnection
     * has been exhausted (10 attempts).
     *
     * Requirement 13.3: After 10 failed attempts, show manual retry action.
     */
    fun manualRetry() {
        _uiState.value = _uiState.value.copy(
            showManualRetryAction = false,
            reconnectAttempts = 0
        )
        wsClient.manualReconnect()
    }

    /**
     * Retries fetching missed messages after a previous failure.
     * Requirement 13.6: If REST fetch fails, show inline retry action.
     */
    fun retryFetchMissedMessages() {
        val convId = _uiState.value.conversationId ?: return
        _uiState.value = _uiState.value.copy(missedMessagesFetchFailed = false)
        viewModelScope.launch {
            val result = messageRepositoryImpl.fetchMissedMessages(convId)
            if (result is Resource.Error) {
                _uiState.value = _uiState.value.copy(missedMessagesFetchFailed = true)
            }
        }
    }

    // ─── User Actions ─────────────────────────────────────────────────────────

    /**
     * Loads older messages when the user scrolls near the top of the list.
     * Guards against concurrent requests and stops when no more messages are available.
     *
     * This is the public API for task 6.2 pagination trigger.
     * Requirements: 2.2, 2.3, 2.6
     */
    fun loadOlderMessages() {
        val state = _uiState.value
        val convId = state.conversationId ?: return
        if (state.isLoadingMore || !state.hasMoreMessages) return

        _uiState.value = state.copy(isLoadingMore = true, paginationError = false)

        viewModelScope.launch {
            try {
                val page = messageRepositoryImpl.loadOlderMessages(
                    conversationId = convId,
                    beforeCursor = state.paginationCursor,
                    limit = INITIAL_PAGE_SIZE
                )
                _uiState.value = _uiState.value.copy(
                    paginationCursor = page.nextCursor,
                    hasMoreMessages = page.hasMore,
                    isLoadingMore = false,
                    paginationError = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    paginationError = true
                )
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        // Use TypingIndicatorManager for debounced outgoing typing events (300ms throttle)
        // Requirement 7.1: Emit at most one typing event per 300ms window
        if (text.isNotEmpty()) {
            wsClient.typingIndicatorManager.onKeystroke()
        } else {
            // Input cleared: emit stop-typing immediately (Requirement 7.4)
            wsClient.typingIndicatorManager.onMessageSentOrInputCleared()
        }
    }

    // ─── Message Send with Reply Clearing and Offline Queue Rejection (Task 6.3) ──

    /**
     * Sends a message, clearing input text and reply state on send.
     *
     * Rejects sends when the offline queue is full (50 messages) per Requirement 1.7.
     * Clears inputText and replyingToMessage on successful dispatch.
     *
     * Requirements: 1.3, 1.7
     */
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val convId = _uiState.value.conversationId ?: return

        // Requirement 1.7: Reject sends when offline queue is full (50 messages)
        if (messageRepositoryImpl.offlineQueueSize >= MAX_OFFLINE_QUEUE_SIZE) {
            _uiState.value = _uiState.value.copy(isOfflineQueueFull = true)
            return
        }

        // Clear input text and reply state on send
        val replyToId = _uiState.value.replyingToMessage?.id
        _uiState.value = _uiState.value.copy(
            inputText = "",
            replyingToMessage = null,
            isSending = false
        )

        // Emit stop-typing immediately on message send (Requirement 7.4)
        wsClient.typingIndicatorManager.onMessageSentOrInputCleared()

        viewModelScope.launch {
            val result = messageRepositoryImpl.sendMessage(convId, text, replyToId = replyToId)
            if (result is Resource.Error) {
                onSendFailure()
            } else {
                // Reset consecutive failures on successful dispatch
                consecutiveFailureCount = 0
                // Clear queue full indicator if it was set
                if (_uiState.value.isOfflineQueueFull) {
                    _uiState.value = _uiState.value.copy(isOfflineQueueFull = false)
                }
            }
        }
    }

    // ─── Retry Failed Message (Task 6.3) ──────────────────────────────────────

    /**
     * Retries sending a FAILED message.
     *
     * Requirement 1.4: On tap of a FAILED message, re-attempt sending exactly once per tap,
     * resetting the 10-second ack timeout for that attempt.
     *
     * Requirement 1.5: If a message send fails on 3 consecutive retry attempts
     * (initial send plus 2 retaps), display snackbar "Message could not be sent" for 4s.
     *
     * @param messageId The ID of the FAILED message to retry.
     */
    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            val result = messageRepositoryImpl.retryMessage(messageId)
            if (result is Resource.Error) {
                onSendFailure()
            } else {
                // The message is now PENDING again; reset consecutive failures
                consecutiveFailureCount = 0
            }
        }
    }

    /**
     * Called when a send or retry operation fails.
     * Tracks consecutive failures and triggers snackbar after 3.
     *
     * Requirement 1.5: 3 consecutive failures → snackbar "Message could not be sent" for 4s.
     */
    private fun onSendFailure() {
        consecutiveFailureCount++
        if (consecutiveFailureCount >= MAX_CONSECUTIVE_FAILURES) {
            _snackbarEvent.tryEmit(
                SnackbarEvent(
                    message = "Message could not be sent",
                    durationMs = SNACKBAR_DURATION_MS
                )
            )
            // Reset counter after showing snackbar so it can trigger again on further failures
            consecutiveFailureCount = 0
        }
    }

    // ─── Reply State Management (Task 6.3) ────────────────────────────────────

    /**
     * Sets the message being replied to. Shows reply preview bar above input.
     */
    fun setReplyingTo(message: MessageUiModel?) {
        _uiState.value = _uiState.value.copy(replyingToMessage = message)
    }

    /**
     * Clears the reply state without affecting input text.
     * Requirement 4.3: Close button dismisses reply preview without affecting input text.
     */
    fun clearReply() {
        _uiState.value = _uiState.value.copy(replyingToMessage = null)
    }

    // ─── Reaction Toggle (Task 6.4) ──────────────────────────────────────────

    /**
     * Toggles a reaction on a message via MessageRepository.reactToMessage.
     *
     * The repository handles the toggle logic internally:
     * - If the user already reacted with the emoji, it removes the reaction.
     * - If the user has not reacted with the emoji, it adds the reaction.
     * - Optimistic update is applied immediately; rolled back on server failure.
     *
     * Requirement 3.3: Toggle reaction via MessageRepository.reactToMessage.
     */
    fun toggleReaction(messageId: String, emoji: String) {
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch {
            messageRepositoryImpl.reactToMessage(convId, messageId, emoji)
        }
    }

    // ─── Read Receipt Emission (Task 6.4) ─────────────────────────────────────

    /**
     * Emits a read event on conversation open, covering all unread messages.
     *
     * If the socket is disconnected, defers the read event until the connection
     * is restored (observes connectionState and emits when CONNECTED).
     *
     * Requirement 5.1: Emit a single read event covering all unread messages on conversation open.
     * Requirement 5.5: Defer read event if disconnected, send when connected.
     */
    private fun emitReadOnOpen(conversationId: String) {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            val connectionState = wsClient.connectionState.value
            if (connectionState == ChatSocketIoClient.ConnectionState.CONNECTED) {
                // Connected: emit read event immediately
                markConversationReadUseCase(conversationId, uid)
                wsClient.sendRead()
            } else {
                // Disconnected: defer read event until connected (Requirement 5.5)
                // Mark locally via repository (which will defer the socket emission)
                messageRepositoryImpl.markRead(conversationId, uid)
                // Also wait for connection to emit the read event via socket
                wsClient.connectionState
                    .filter { it == ChatSocketIoClient.ConnectionState.CONNECTED }
                    .first()
                // Once connected, emit the read event
                markConversationReadUseCase(conversationId, uid)
                wsClient.sendRead()
            }
        }
    }

    // ─── Existing Actions ─────────────────────────────────────────────────────

    fun sendLocation(latitude: Double, longitude: Double) {
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch { sendLocationMessageUseCase(convId, latitude, longitude) }
    }

    /**
     * Called when the user taps the location share button.
     *
     * If location permission is not granted, sets locationPermissionNeeded = true
     * so the UI can request permission from the system.
     * If permission is granted, obtains device coordinates with a 10-second timeout
     * and invokes SendLocationMessageUseCase.
     *
     * Requirements: 15.1, 15.5, 15.6
     */
    fun onLocationButtonTap(hasLocationPermission: Boolean) {
        if (!hasLocationPermission) {
            _uiState.value = _uiState.value.copy(locationPermissionNeeded = true)
            return
        }
        requestCurrentLocationAndSend()
    }

    /**
     * Called after the user grants location permission.
     * Clears the permission-needed flag and proceeds to obtain location.
     *
     * Requirement: 15.5
     */
    fun onLocationPermissionGranted() {
        _uiState.value = _uiState.value.copy(locationPermissionNeeded = false)
        requestCurrentLocationAndSend()
    }

    /**
     * Called if the user denies location permission.
     * Clears the permission-needed flag without sending.
     *
     * Requirement: 15.5
     */
    fun onLocationPermissionDenied() {
        _uiState.value = _uiState.value.copy(locationPermissionNeeded = false)
    }

    /**
     * Obtains the device's current coordinates with a 10-second timeout.
     * On success, invokes SendLocationMessageUseCase.
     * On timeout or null location, shows a transient error to the user.
     *
     * Requirements: 15.1, 15.6
     */
    @Suppress("MissingPermission")
    fun requestCurrentLocationAndSend() {
        viewModelScope.launch {
            try {
                val location = withTimeout(LOCATION_TIMEOUT_MS) {
                    locationManager.getCurrentLocation()
                }
                if (location != null) {
                    sendLocation(location.latitude, location.longitude)
                } else {
                    showLocationError()
                }
            } catch (e: TimeoutCancellationException) {
                showLocationError()
            }
        }
    }

    /**
     * Shows a transient location error for LOCATION_ERROR_DISPLAY_MS then clears it.
     * Requirement: 15.6
     */
    private fun showLocationError() {
        _uiState.value = _uiState.value.copy(locationError = true)
        viewModelScope.launch {
            delay(LOCATION_ERROR_DISPLAY_MS)
            _uiState.value = _uiState.value.copy(locationError = false)
        }
    }

    /**
     * Clears the location error state (can be called by UI to dismiss early).
     */
    fun dismissLocationError() {
        _uiState.value = _uiState.value.copy(locationError = false)
    }

    fun markRead() {
        val convId = _uiState.value.conversationId ?: return
        val uid = currentUserId ?: return
        viewModelScope.launch {
            markConversationReadUseCase(convId, uid)
            wsClient.sendRead()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.disconnect()
    }

    // ─── Lifecycle: App Backgrounding/Foregrounding (Task 16.2) ───────────────

    /**
     * Called when the app returns to foreground while ChatScreen is active.
     * Reconnects the WebSocket with the current conversationId and Firebase token.
     *
     * Requirements: 13.4, 13.5
     */
    fun onForeground() {
        val convId = _uiState.value.conversationId ?: return
        connectWebSocket(convId)
    }

    /**
     * Called when the app goes to background while ChatScreen is active.
     * Disconnects the WebSocket to conserve resources and battery.
     *
     * Requirement: 13.1
     */
    fun onBackground() {
        wsClient.disconnect()
    }

    companion object {
        /** Number of messages to load on initial page and each subsequent page. */
        const val INITIAL_PAGE_SIZE = 30

        /** Maximum offline queue size before rejecting sends (Requirement 1.7). */
        const val MAX_OFFLINE_QUEUE_SIZE = 50

        /** Number of consecutive failures before showing snackbar (Requirement 1.5). */
        const val MAX_CONSECUTIVE_FAILURES = 3

        /** Duration in ms to show the failure snackbar (Requirement 1.5). */
        const val SNACKBAR_DURATION_MS = 4000L

        /** Auto-hide typing indicator after 5 seconds of no typing event (Requirement 7.5). */
        const val TYPING_TIMEOUT_MS = 5_000L

        /** Maximum number of typing user names to display before showing "+N" (Requirement 7.6). */
        const val MAX_DISPLAYED_TYPERS = 2

        /** Delay before showing the reconnecting banner (Requirement 13.1: within 500ms). */
        const val RECONNECT_BANNER_DELAY_MS = 500L

        /** Duration of the banner fade-out animation on reconnect (Requirement 13.7). */
        const val RECONNECT_BANNER_FADE_MS = 300L

        /** Timeout in milliseconds for obtaining device location (Requirement 15.6). */
        const val LOCATION_TIMEOUT_MS = 10_000L

        /** Duration in milliseconds to display the location error before auto-dismissing. */
        const val LOCATION_ERROR_DISPLAY_MS = 4_000L

        /**
         * Formats the typing indicator text based on the list of currently typing user names.
         *
         * Rules:
         * - 0 typers: returns null
         * - 1 typer: "{name} is typing…"
         * - 2 typers: "{name1}, {name2} are typing…"
         * - 3+ typers: "{name1}, {name2} +N are typing…" (N = remaining count)
         *
         * Requirements: 7.2, 7.6
         */
        fun formatTypingIndicatorText(typers: List<String>): String? {
            return when {
                typers.isEmpty() -> null
                typers.size == 1 -> "${typers[0]} is typing\u2026"
                typers.size == 2 -> "${typers[0]}, ${typers[1]} are typing\u2026"
                else -> {
                    val displayed = typers.take(MAX_DISPLAYED_TYPERS)
                    val remaining = typers.size - MAX_DISPLAYED_TYPERS
                    "${displayed.joinToString(", ")} +$remaining are typing\u2026"
                }
            }
        }
    }
}

/**
 * One-shot snackbar event emitted by the ViewModel.
 * The UI collects this and shows a Snackbar for the specified duration.
 */
data class SnackbarEvent(
    val message: String,
    val durationMs: Long = 4000L
)

data class ChatUiState(
    val conversationId: String? = null,
    val conversation: ConversationUiModel? = null,
    val messages: List<MessageUiModel> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    /** Whether there are more older messages available for pagination. */
    val hasMoreMessages: Boolean = false,
    /** Cursor for loading the next page of older messages (timestamp of oldest loaded message). */
    val paginationCursor: String? = null,
    /** Whether a pagination request is currently in flight. */
    val isLoadingMore: Boolean = false,
    val typingUserId: String? = null,
    val typingUserName: String? = null,
    /** Formatted typing indicator text for display (e.g., "{name} is typing…" or "{name1}, {name2} +N are typing…"). */
    val typingIndicatorText: String? = null,
    /** The message currently being replied to (shown in reply preview bar). */
    val replyingToMessage: MessageUiModel? = null,
    /** Whether the offline queue is full (50 messages) — messaging unavailable indicator. */
    val isOfflineQueueFull: Boolean = false,
    // ─── Reconnection UI State (Task 6.7) ─────────────────────────────────────
    /** Whether to show the "Reconnecting..." banner (shown within 500ms of disconnect). */
    val showReconnectingBanner: Boolean = false,
    /** Whether to show the manual "Retry" action (after 10 failed reconnection attempts). */
    val showManualRetryAction: Boolean = false,
    /** Current number of reconnection attempts (for UI display if needed). */
    val reconnectAttempts: Int = 0,
    /** Whether the banner is in the process of fading out (300ms fade on reconnect). */
    val isBannerFadingOut: Boolean = false,
    /** Whether fetching missed messages failed (show inline retry). */
    val missedMessagesFetchFailed: Boolean = false,
    // ─── Location Sending State (Task 6.6) ────────────────────────────────────
    /** True when the UI should request location permission from the system (Requirement 15.5). */
    val locationPermissionNeeded: Boolean = false,
    /** True when a transient location error should be displayed (Requirement 15.6). */
    val locationError: Boolean = false,
    // ─── Pagination Error State (Task 11.1) ───────────────────────────────────
    /** Whether the last pagination request failed (show inline retry at top of list). */
    val paginationError: Boolean = false,
    // ─── Header State (Task 11.3) ─────────────────────────────────────────────
    /** Whether the other user in a 1:1 conversation is in the caller's friends list (Requirement 8.4). */
    val isOtherUserFriend: Boolean = false
)
