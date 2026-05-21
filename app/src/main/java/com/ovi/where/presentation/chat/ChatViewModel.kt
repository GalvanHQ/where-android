package com.ovi.where.presentation.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.audio.VoiceRecorder
import com.ovi.where.data.local.dao.OnlineStatusDao
import com.ovi.where.data.local.entity.OnlineStatusEntity
import com.ovi.where.data.location.LocationManager
import com.ovi.where.data.network.ConnectivityObserver
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.data.repository.MessageRepositoryImpl
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.InteractionType
import com.ovi.where.domain.model.MemberRole
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.repository.GroupRepository
import com.ovi.where.domain.repository.InteractionRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.chat.FetchLinkPreviewUseCase
import com.ovi.where.domain.usecase.chat.MarkConversationReadUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.chat.ObserveMessagesUseCase
import com.ovi.where.domain.usecase.chat.SendLocationMessageUseCase
import com.ovi.where.domain.usecase.chat.SendMessageUseCase
import com.ovi.where.domain.usecase.group.MuteGroupMemberUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import com.ovi.where.presentation.chat.ChatViewModel.Companion.TYPING_TIMEOUT_MS
import com.ovi.where.presentation.chat.components.VoicePlaybackController
import com.ovi.where.presentation.model.BubbleDirection
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.MessageUiModel
import com.ovi.where.presentation.model.formatConversationTimestamp
import com.ovi.where.presentation.model.formatDateSeparatorLabel
import com.ovi.where.presentation.model.formatMessageDateKey
import com.ovi.where.presentation.model.formatMessageTime
import com.ovi.where.presentation.model.toUiModel
import com.ovi.where.service.LocationTrackingService
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
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
import java.util.UUID
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
    val savedStateHandle: SavedStateHandle,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendLocationMessageUseCase: SendLocationMessageUseCase,
    private val markConversationReadUseCase: MarkConversationReadUseCase,
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val lazyWsClient: Lazy<ChatSocketIoClient>,
    private val messageRepositoryImpl: MessageRepositoryImpl,
    private val firebaseAuth: FirebaseAuth,
    private val locationManager: LocationManager,
    private val friendshipRepository: FriendshipRepository,
    private val interactionRepository: InteractionRepository,
    private val groupRepository: GroupRepository,
    private val locationRepository: LocationRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val startLocationSharingUseCase: StartLocationSharingUseCase,
    private val stopLocationSharingUseCase: StopLocationSharingUseCase,
    private val fetchLinkPreviewUseCase: FetchLinkPreviewUseCase,
    private val muteGroupMemberUseCase: MuteGroupMemberUseCase,
    private val voiceRecorder: VoiceRecorder,
    private val onlineStatusDao: OnlineStatusDao,
    private val userRepository: UserRepository,
    private val getOrCreateDirectConversationUseCase: com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
) : AndroidViewModel(application) {

    /**
     * Lazily-resolved ChatSocketIoClient instance.
     * Not instantiated until first access, keeping app startup free of chat initialization (Req 20.1, 20.4, 20.5).
     */
    private val wsClient: ChatSocketIoClient get() = lazyWsClient.get()

    /**
     * Clock function for testability. In production uses System.currentTimeMillis().
     * Tests can override this to control time.
     */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    val voicePlaybackController: VoicePlaybackController = VoicePlaybackController()

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
            observeCachedLocations()
            observeOfflineState()
            observePresenceForOnlineCount()
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
        observeCachedLocations()
        observeOfflineState()
        observePresenceForOnlineCount()
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
            // Requirement 7.5: While offline, skip loading indicators — display cached data directly
            val isOffline = !connectivityObserver.isConnected.value
            _uiState.value = _uiState.value.copy(isLoading = !isOffline)

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
                // Remove pre-existing duplicates from before the deduplication fix (Task 9.1/9.2)
                val deduplicatedMessages = deduplicateMessages(sortedMessages)
                // Requirement 19.3: Evict oldest messages beyond 500-message window.
                // Preserve pagination cursor so user can paginate backward to retrieve evicted messages.
                val windowedMessages = if (deduplicatedMessages.size > MESSAGE_WINDOW_SIZE) {
                    deduplicatedMessages.takeLast(MESSAGE_WINDOW_SIZE)
                } else {
                    deduplicatedMessages
                }
                val uiModels = windowedMessages.map {
                    it.toUiModel(
                        currentUserId = uid,
                        timeFormatter = ::formatMessageTime,
                        dateKeyFormatter = ::formatMessageDateKey,
                        nicknames = _uiState.value.conversation?.nicknames ?: emptyMap()
                    ).let { ui ->
                        // Resolve reader photo URLs from the participant metadata cache so
                        // the ReadReceiptIndicator can render avatar(s) instead of empty
                        // placeholders. Falls back to nulls when no photos are known.
                        if (ui.readBy.isEmpty()) ui
                        else ui.copy(
                            readByPhotoUrls = ui.readBy
                                .map { userId -> participantPhotos[userId] }
                                .toImmutableList()
                        )
                    }
                }
                // Compute message grouping metadata (Requirements 4.6, 4.7, 10.3)
                val timestamps = windowedMessages.map { it.timestamp }
                val groupedUiModels = computeMessageGrouping(uiModels, timestamps)

                // Messenger-style read receipt: only show on the LAST sent message
                // that has been read. Find its index and set showReadReceipt = true
                // only on that one message.
                val lastReadIndex = groupedUiModels.indexOfLast {
                    it.direction == BubbleDirection.SENT && it.readBy.isNotEmpty()
                }
                // Also find the absolute last sent message for the status indicator.
                val lastSentIndex = groupedUiModels.indexOfLast {
                    it.direction == BubbleDirection.SENT
                }
                val finalModels = groupedUiModels.mapIndexed { index, model ->
                    when {
                        index == lastReadIndex -> model.copy(showReadReceipt = true)
                        // Only show status circle on the very last sent message when
                        // it hasn't been read yet (no read receipt to show).
                        index == lastSentIndex && lastReadIndex != lastSentIndex ->
                            model.copy(showStatusIndicator = true)
                        else -> model
                    }
                }
                // Requirement 17.7 / 19.1: Pre-compute grouped messages by dateKey
                // so the LazyColumn items block performs no inline grouping/sorting/formatting.
                val grouped = finalModels.groupBy { it.dateKey }
                    .entries
                    .map { (key, msgs) -> key to msgs }
                _uiState.value = _uiState.value.copy(
                    messages = finalModels,
                    groupedMessages = grouped,
                    isLoading = false,
                    // When messages are evicted, there are always more messages to paginate back to
                    hasMoreMessages = _uiState.value.hasMoreMessages || deduplicatedMessages.size > MESSAGE_WINDOW_SIZE
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

    /**
     * Removes duplicate messages that have the same senderId, text, and timestamp
     * (within 1-second tolerance). This handles pre-existing duplicates in Room
     * that were created before the deduplication fix (Task 9.1/9.2) was applied.
     *
     * Keeps the message with the non-temp ID (server ID) when possible.
     * Also schedules background cleanup to remove duplicates from Room permanently.
     */
    private fun deduplicateMessages(messages: List<Message>): List<Message> {
        if (messages.size <= 1) return messages

        val result = mutableListOf<Message>()
        val duplicateIds = mutableListOf<String>()

        // Group by (senderId, text, timestamp rounded to 1-second buckets)
        val groups = messages.groupBy { msg ->
            Triple(msg.senderId, msg.text, msg.timestamp / 1000L)
        }

        for ((_, group) in groups) {
            if (group.size == 1) {
                result.add(group.first())
            } else {
                // Keep the message with the server ID (non-temp ID, i.e., doesn't start with "temp_")
                val preferred = group.firstOrNull { !it.id.startsWith("temp_") } ?: group.first()
                result.add(preferred)
                // Mark others as duplicates for cleanup
                group.filter { it.id != preferred.id }.forEach { duplicateIds.add(it.id) }
            }
        }

        // Background cleanup: remove duplicate rows from Room
        if (duplicateIds.isNotEmpty()) {
            viewModelScope.launch {
                for (id in duplicateIds) {
                    try {
                        messageRepositoryImpl.deleteMessageLocally(id)
                    } catch (_: Exception) {
                        // Best-effort cleanup
                    }
                }
            }
        }

        return result.sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id })
    }

    // ─── Conversation Observation ─────────────────────────────────────────────

    /** Cached resolved display names for DM participants (userId -> displayName). */
    private val participantNames = mutableMapOf<String, String>()

    /** Cached resolved profile photo URLs for DM participants (userId -> photoUrl). */
    private val participantPhotos = mutableMapOf<String, String?>()

    /** Cached set of user IDs that are friends with the current user. */
    private val cachedFriendIds = mutableSetOf<String>()

    /** Cached friendship status per user ID for the avatar-tap sheet. */
    private val cachedFriendshipStatus = mutableMapOf<String, com.ovi.where.presentation.chat.components.FriendshipAction>()

    /**
     * Tracks which user IDs are currently online (from presence frames).
     * Used to populate isOtherUserOnline for DM conversations in real-time.
     *
     * Bug fix (Task 4.2): Without this, the chat header always shows "Offline" for DM
     * conversations because onlineMembers is never populated and no presence subscription
     * exists in ChatViewModel for the other user.
     *
     * Requirements: 1.2, 2.2
     */
    private val onlineUserIds = mutableSetOf<String>()

    /**
     * Last-seen timestamps (epoch millis) for resolved DM participants.
     * Keyed by userId. Drives the Messenger-style "Active 5m ago" subtitle when offline.
     */
    private val lastSeenByUser = mutableMapOf<String, Long>()

    /** Whether presence subscription for DM has been started (to avoid duplicate subscriptions). */
    private var dmPresenceSubscribed = false

    private fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val uid = currentUserId ?: ""
            var adminObserved = false
            observeConversationsUseCase().collect { conversations ->
                val conv = conversations.firstOrNull { it.id == conversationId }
                if (conv != null) {
                    // For DIRECT conversations resolve participant metadata so we have
                    // displayName, photo, and lastSeen for the header. Skip if already cached.
                    if (conv.type == ConversationType.DIRECT) {
                        val otherUid = conv.participantIds.firstOrNull { it != uid }
                        if (otherUid != null
                            && (otherUid !in participantNames || otherUid !in lastSeenByUser)
                        ) {
                            resolveParticipantProfile(otherUid)
                        }
                    }

                    // For DIRECT conversations, subscribe to presence for the other user (Task 4.2)
                    if (conv.type == ConversationType.DIRECT && !dmPresenceSubscribed) {
                        val otherUid = conv.participantIds.firstOrNull { it != uid }
                        if (otherUid != null) {
                            dmPresenceSubscribed = true
                            subscribeToDmPresence(otherUid)
                        }
                    }

                    val uiModel = conv.toUiModel(
                        uid,
                        ::formatConversationTimestamp,
                        participantNames,
                        participantPhotos,
                        onlineUserIds,
                        lastSeenByUser
                    )

                    // Check if nicknames changed before updating state
                    val previousNicknames = _uiState.value.conversation?.nicknames ?: emptyMap()
                    _uiState.value = _uiState.value.copy(conversation = uiModel)

                    // Re-map messages if nicknames changed (so bubble sender names update)
                    if (uiModel.nicknames != previousNicknames && _uiState.value.messages.isNotEmpty()) {
                        remapMessagesWithNicknames(uiModel.nicknames, uid)
                    }
                    // Check friendship status for 1:1 conversations (Requirement 8.4)
                    if (!uiModel.isGroup && uiModel.otherUserId != null) {
                        checkFriendshipStatus(uiModel.otherUserId)
                    }
                    // Observe admin status for group conversations (Requirement 15.1)
                    if (uiModel.isGroup && uiModel.groupId != null && !adminObserved) {
                        adminObserved = true
                        observeAdminStatus(uiModel.groupId)
                        // Observe group members for @mention suggestions (Requirement 14.1)
                        observeGroupMembersForMentions(uiModel.groupId)
                    }
                }
            }
        }
    }

    /**
     * Subscribes to WebSocket presence frames for the other user in a DM conversation.
     * Updates [onlineUserIds] in real-time and refreshes the conversation UI model
     * so the chat header displays "Active now" or "Offline" correctly.
     *
     * Also loads the initial online status from [OnlineStatusDao] so the header
     * shows the correct state immediately on screen open.
     *
     * Bug fix (Task 4.2): The existing observePresenceForOnlineCount() only handles
     * group conversations. This method provides equivalent functionality for DMs.
     *
     * Requirements: 1.2, 2.2
     */
    private fun subscribeToDmPresence(otherUserId: String) {
        val uid = currentUserId ?: return

        // Load initial online status from Room (persisted presence state)
        viewModelScope.launch {
            try {
                val cached = onlineStatusDao.getStatus(otherUserId)
                if (cached != null) {
                    if (cached.isOnline) onlineUserIds.add(otherUserId)
                    if (cached.lastSeen > 0L) lastSeenByUser[otherUserId] = cached.lastSeen
                    refreshConversationOnlineStatus(uid)
                }
            } catch (_: Exception) {
                // On failure, keep default offline state (safe default)
            }
        }

        // Subscribe to real-time presence frames for the other user
        viewModelScope.launch {
            wsClient.incomingFrames.collect { frame ->
                if (frame is ServerFrame.Presence && frame.userId == otherUserId) {
                    val isOnline = frame.status == "online"
                    val changed = if (isOnline) {
                        onlineUserIds.add(frame.userId)
                    } else {
                        onlineUserIds.remove(frame.userId)
                    }

                    // Always update lastSeen on transition: when going offline, capture
                    // the moment they left; when coming online, treat the current time as
                    // the most recent activity.
                    val now = System.currentTimeMillis()
                    lastSeenByUser[frame.userId] = now

                    if (changed) {
                        // Persist to Room for offline access
                        onlineStatusDao.upsert(
                            OnlineStatusEntity(
                                userId = frame.userId,
                                isOnline = isOnline,
                                lastUpdatedAt = now,
                                lastSeen = now
                            )
                        )
                        refreshConversationOnlineStatus(uid)
                    }
                }
            }
        }
    }

    /**
     * Refreshes the conversation UI model's online status after a presence change.
     * Re-creates the [ConversationUiModel] with the updated [onlineUserIds] set
     * so the chat header reflects the current "Active now" / "Offline" state.
     *
     * Requirements: 1.2, 2.2
     */
    private fun refreshConversationOnlineStatus(currentUserId: String) {
        val currentConv = _uiState.value.conversation ?: return
        val otherUid = currentConv.otherUserId ?: return
        val isOnline = otherUid in onlineUserIds
        val lastSeen = lastSeenByUser[otherUid] ?: currentConv.otherUserLastSeen
        if (currentConv.isOtherUserOnline != isOnline || currentConv.otherUserLastSeen != lastSeen) {
            _uiState.value = _uiState.value.copy(
                conversation = currentConv.copy(
                    isOtherUserOnline = isOnline,
                    otherUserLastSeen = lastSeen
                )
            )
        }
    }

    /**
     * Resolves the display name and profile photo for a DM participant.
     * Fetches the user profile from [UserRepository] and caches the result
     * in [participantNames] and [participantPhotos].
     *
     * Bug fix (Task 3.2): Without this, the chat header shows "Unknown User" for DM
     * conversations because the conversation name is blank and no participant name
     * resolution happens in ChatViewModel.
     *
     * Requirements: 1.1, 2.1
     */
    private suspend fun resolveParticipantProfile(userId: String) {
        when (val result = userRepository.getUser(userId)) {
            is Resource.Success -> {
                result.data?.let { user ->
                    participantNames[user.id] = user.displayName
                    participantPhotos[user.id] = user.photoUrl
                    if (user.lastSeen > 0L) {
                        lastSeenByUser[user.id] = user.lastSeen
                    }
                    // Persist presence snapshot from authoritative profile so offline reopens
                    // show "Active 5m ago" without waiting for a socket frame.
                    try {
                        onlineStatusDao.upsert(
                            OnlineStatusEntity(
                                userId = user.id,
                                isOnline = user.isOnline,
                                lastUpdatedAt = System.currentTimeMillis(),
                                lastSeen = user.lastSeen
                            )
                        )
                    } catch (_: Exception) {
                        // Best-effort; falls back to in-memory map.
                    }
                }
            }
            else -> {
                // Silently fail — will retry on next conversation update
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
     * Debounce job for typing indicator state updates.
     * Requirement 19.5: Max 1 recomposition per 300ms for typing indicator in groups.
     */
    private var typingIndicatorDebounceJob: Job? = null

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
     * Requirement 19.5: Debounce typing indicator state updates to emit at most one
     * recomposition-triggering state change per 300ms when multiple typing events arrive
     * in group conversations.
     *
     * Requirements: 7.2, 7.6, 19.5
     */
    private fun updateTypingIndicatorState() {
        typingIndicatorDebounceJob?.cancel()
        typingIndicatorDebounceJob = viewModelScope.launch {
            delay(TYPING_INDICATOR_DEBOUNCE_MS)
            val typerNames = typingUsers.values.toList()
            val typingText = formatTypingIndicatorText(typerNames)
            _uiState.value = _uiState.value.copy(
                typingIndicatorText = typingText,
                typingUserId = typerNames.firstOrNull()?.let { typingUsers.keys.firstOrNull() },
                typingUserName = typerNames.firstOrNull()
            )
        }
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

    // ─── Member Online Count Observation (Task 10.2) ────────────────────────

    /**
     * In-memory set of online member IDs for the current group conversation.
     * Excludes the current user per Requirement 16.1.
     */
    private val onlineMemberIds = mutableSetOf<String>()

    /**
     * Observes presence frames from ChatSocketIoClient to maintain the online
     * member count for group conversations.
     *
     * Behavior:
     * - On screen open: loads initial online count from Room (OnlineStatusDao) within 2s (Req 16.4)
     * - On presence update: updates count within 1s (Req 16.2)
     * - If no members online (other than current user): displays only "{totalCount} members" (Req 16.3)
     * - On socket disconnect: continues displaying last known count (Req 16.5)
     *
     * Requirements: 16.1, 16.2, 16.3, 16.4, 16.5
     */
    private fun observePresenceForOnlineCount() {
        val uid = currentUserId ?: return

        // Requirement 16.4: Determine initial count from presence state within 2s of screen entry
        viewModelScope.launch {
            val conversation = _uiState.value.conversation
            if (conversation?.isGroup == true) {
                loadInitialOnlineCount(uid, conversation.memberCount)
            } else {
                // Wait for conversation to load, then check if it's a group
                // This handles the case where conversation hasn't loaded yet at init time
                var attempts = 0
                while (attempts < 10) {
                    delay(200L)
                    attempts++
                    val conv = _uiState.value.conversation
                    if (conv != null) {
                        if (conv.isGroup) {
                            loadInitialOnlineCount(uid, conv.memberCount)
                        }
                        break
                    }
                }
            }
        }

        // Requirement 16.2: Update online count within 1s of presence update
        viewModelScope.launch {
            wsClient.incomingFrames.collect { frame ->
                if (frame is ServerFrame.Presence) {
                    // Skip current user
                    if (frame.userId == uid) return@collect

                    // Only track members that are part of this conversation
                    val conversation = _uiState.value.conversation
                    if (conversation?.isGroup != true) return@collect

                    if (frame.status == "online") {
                        onlineMemberIds.add(frame.userId)
                    } else {
                        onlineMemberIds.remove(frame.userId)
                    }

                    // Requirement 16.3: If no members online, count is 0
                    _uiState.value = _uiState.value.copy(
                        onlineMemberCount = onlineMemberIds.size
                    )
                }
            }
        }
    }

    /**
     * Loads the initial online member count from Room (persisted presence state).
     * Called on screen open to provide immediate count within 2s (Requirement 16.4).
     */
    private suspend fun loadInitialOnlineCount(currentUserId: String, memberCount: Int) {
        try {
            val onlineUsers = onlineStatusDao.getOnlineUsers()
            // Get participant IDs from the conversation to filter relevant online users
            val conversation = _uiState.value.conversation
            val participantIds = conversation?.let {
                // We use memberCount as a proxy; the actual participant filtering
                // happens via the presence frames which are scoped to the conversation
                onlineUsers
                    .filter { user -> user.userId != currentUserId }
                    .map { it.userId }
            } ?: emptyList()

            onlineMemberIds.clear()
            onlineMemberIds.addAll(participantIds)
            _uiState.value = _uiState.value.copy(
                onlineMemberCount = onlineMemberIds.size
            )
        } catch (_: Exception) {
            // On failure, keep count at 0 (safe default)
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
        _uiState.value = _uiState.value.copy(
            inputText = text,
            mentionRanges = mentionEngine.getMentionRanges(text)
        )
        // Use TypingIndicatorManager for debounced outgoing typing events (300ms throttle)
        if (text.isNotEmpty()) {
            wsClient.typingIndicatorManager.onKeystroke()
        } else {
            wsClient.typingIndicatorManager.onMessageSentOrInputCleared()
        }
        // Trigger mention detection for group chats (Requirement 14.1)
        onMentionTrigger(text, text.length)
    }

    // ─── Message Send with Reply Clearing and Offline Queue Rejection (Task 6.3) ──

    /**
     * Sends a message, clearing input text and reply state on send.
     *
     * Rejects sends when the offline queue is full (50 messages) per Requirement 1.7.
     * Clears inputText and replyingToMessage on successful dispatch.
     *
     * Requirements: 1.3, 1.7, 12.1, 12.2, 12.3, 12.5
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

        // Requirement 7.6: Show "Queued for sync" banner when offline
        if (_uiState.value.isOffline) {
            showQueuedForSyncBanner()
        }

        // Clear input text and reply state on send
        val replyToId = _uiState.value.replyingToMessage?.id
        val replyToText = _uiState.value.replyingToMessage?.text
        val replyToSenderName = _uiState.value.replyingToMessage?.senderName
        _uiState.value = _uiState.value.copy(
            inputText = "",
            replyingToMessage = null,
            isSending = false
        )

        // Clear mention state on send (Requirement 14.3)
        val mentionedIds = mentionEngine.mentionedUserIds.toList()
        clearMentionState()

        // Emit stop-typing immediately on message send (Requirement 7.4)
        wsClient.typingIndicatorManager.onMessageSentOrInputCleared()

        viewModelScope.launch {
            // Requirement 12.1, 12.2, 12.5: Detect first URL and fetch link preview before send
            val firstUrl = fetchLinkPreviewUseCase.extractFirstUrl(text)
            val result = if (mentionedIds.isNotEmpty() && firstUrl == null) {
                // Requirement 14.3: Include mentionedUserIds in message payload
                messageRepositoryImpl.sendMessageWithMentions(
                    conversationId = convId,
                    text = text,
                    replyToId = replyToId,
                    mentionedUserIds = mentionedIds
                )
            } else if (firstUrl != null) {
                // Attempt to fetch link preview (5s timeout enforced by use case)
                val previewResult = fetchLinkPreviewUseCase(firstUrl)
                if (previewResult is Resource.Success && previewResult.data != null) {
                    // Requirement 12.2: Attach preview metadata to message payload on success
                    val preview = previewResult.data
                    messageRepositoryImpl.sendMessageWithLinkPreview(
                        conversationId = convId,
                        text = text,
                        replyToId = replyToId,
                        linkPreviewUrl = preview.url,
                        linkPreviewTitle = preview.title,
                        linkPreviewDescription = preview.description,
                        linkPreviewImageUrl = preview.imageUrl,
                        linkPreviewDomain = preview.domain
                    )
                } else {
                    // Requirement 12.3: On timeout/error, send without preview
                    if (mentionedIds.isNotEmpty()) {
                        messageRepositoryImpl.sendMessageWithMentions(convId, text, replyToId, mentionedIds)
                    } else {
                        messageRepositoryImpl.sendMessage(convId, text, replyToId = replyToId)
                    }
                }
            } else {
                // No URL detected, no mentions, send normally
                messageRepositoryImpl.sendMessage(convId, text, replyToId = replyToId)
            }

            if (result is Resource.Error) {
                onSendFailure()
            } else {
                // Reset consecutive failures on successful dispatch
                consecutiveFailureCount = 0
                // Clear queue full indicator if it was set
                if (_uiState.value.isOfflineQueueFull) {
                    _uiState.value = _uiState.value.copy(isOfflineQueueFull = false)
                }
                // Record interaction for suggestions (fire-and-forget, 1:1 chats only)
                recordMessageInteraction()
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

    /**
     * Records a MESSAGE_SENT interaction for the recipient in a 1:1 conversation.
     * Fire-and-forget: does not block the send flow or surface errors to the user.
     * Skipped for group chats (otherUserId is null for groups).
     *
     * Requirement 8.3: Record interaction when a message is sent.
     */
    private fun recordMessageInteraction() {
        val conversation = _uiState.value.conversation ?: return
        // Only record for 1:1 (direct) conversations
        if (conversation.isGroup) return
        val recipientId = conversation.otherUserId ?: return
        val displayName = conversation.title
        val photoUrl = conversation.photoUrl

        viewModelScope.launch {
            try {
                interactionRepository.recordInteraction(
                    userId = recipientId,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    type = InteractionType.MESSAGE_SENT
                )
            } catch (_: Exception) {
                // Fire-and-forget: silently ignore failures
            }
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
     * Sends an image message using the provided content URI.
     *
     * Called from ChatScreen after the user picks an image from gallery
     * or captures a photo with the camera. Delegates to
     * [MessageRepositoryImpl.sendImageMessage] which handles upload + message creation.
     */
    fun sendImageMessage(imageUri: Uri) {
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showImageSizeError = false)
            val result = messageRepositoryImpl.sendImageMessage(convId, imageUri)
            if (result is Resource.Error && result.message?.contains("10MB") == true) {
                _uiState.value = _uiState.value.copy(showImageSizeError = true)
                // Auto-dismiss after 4 seconds
                delay(4000)
                _uiState.value = _uiState.value.copy(showImageSizeError = false)
            } else if (result is Resource.Error) {
                _snackbarEvent.tryEmit(SnackbarEvent(result.message ?: "Failed to send image"))
            }
        }
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

    // ─── Live Location Sharing UI Flow (Task 3.2) ─────────────────────────────

    /** Job for the countdown timer that updates timeRemaining every 60 seconds. */
    private var locationSharingTimerJob: Job? = null

    /**
     * Called when the user taps the location button in the input bar.
     * Shows the location bottom sheet with options based on conversation type.
     *
     * Requirement 1.1: Display bottom sheet with "Share Current Location" and "Share Live Location".
     * Requirement 1.12: Hide "Share Live Location" if conversation has no groupId (1:1 direct).
     */
    fun onLocationShareButtonTap() {
        val conversation = _uiState.value.conversation
        val hasGroupId = conversation?.groupId != null
        _uiState.value = _uiState.value.copy(
            locationBottomSheetState = LocationBottomSheetState.OPTIONS,
            showLiveLocationOption = hasGroupId
        )
    }

    /**
     * Called when the user selects "Share Live Location" from the bottom sheet.
     * Shows the duration picker with default 1 hour selected.
     *
     * Requirement 1.2: Display duration picker (15min, 1h, 2h, 4h, 8h, default 1h).
     */
    fun onShareLiveLocationSelected() {
        _uiState.value = _uiState.value.copy(
            locationBottomSheetState = LocationBottomSheetState.DURATION_PICKER,
            selectedDurationMinutes = DEFAULT_LIVE_LOCATION_DURATION_MINUTES
        )
    }

    /**
     * Called when the user selects a duration from the duration picker.
     *
     * @param durationMinutes The selected duration in minutes (15, 60, 120, 240, or 480).
     */
    fun onDurationSelected(durationMinutes: Long) {
        _uiState.value = _uiState.value.copy(selectedDurationMinutes = durationMinutes)
    }

    /**
     * Called when the user confirms the live location sharing duration.
     * Checks permission, invokes StartLocationSharingUseCase, starts service, inserts message.
     *
     * Requirement 1.3: Invoke StartLocationSharingUseCase, dismiss sheet within 300ms,
     * start LocationTrackingService, insert live location message.
     * Requirement 1.8: Request permissions if not granted.
     * Requirement 1.10: Show error if permission denied.
     * Requirement 1.11: Show error if use case fails.
     */
    fun onConfirmLiveLocationSharing(hasLocationPermission: Boolean) {
        if (!hasLocationPermission) {
            // Requirement 1.8: Request ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION
            _uiState.value = _uiState.value.copy(
                liveLocationPermissionNeeded = true
            )
            return
        }
        startLiveLocationSharing()
    }

    /**
     * Called after the user grants location permission for live sharing.
     * Clears the permission-needed flag and proceeds to start sharing.
     *
     * Requirement 1.8: After permission granted, start the session.
     */
    fun onLiveLocationPermissionGranted() {
        _uiState.value = _uiState.value.copy(liveLocationPermissionNeeded = false)
        startLiveLocationSharing()
    }

    /**
     * Called if the user denies location permission for live sharing.
     * Shows a dismissible error and does not start the session.
     *
     * Requirement 1.10: Show dismissible error, do not start session.
     */
    fun onLiveLocationPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            liveLocationPermissionNeeded = false,
            locationBottomSheetState = LocationBottomSheetState.HIDDEN,
            liveLocationError = "Location permission is required to share live location"
        )
    }

    /**
     * Starts the live location sharing session.
     * Invokes the use case, dismisses the bottom sheet, starts the service, and inserts a message.
     *
     * Requirements: 1.3, 1.11
     */
    private fun startLiveLocationSharing() {
        val conversation = _uiState.value.conversation ?: return
        val targetId = conversation.groupId ?: "direct:${conversation.otherUserId}"
        val durationMinutes = _uiState.value.selectedDurationMinutes

        viewModelScope.launch {
            // Dismiss bottom sheet immediately (responsive UX)
            _uiState.value = _uiState.value.copy(
                locationBottomSheetState = LocationBottomSheetState.HIDDEN
            )

            // If already sharing with other targets, ADD this target to the session
            // (preserving existing targets' expiries). Otherwise start fresh.
            val alreadySharing = locationRepository.isSharingLocation()
            val result = if (alreadySharing && targetId !in locationRepository.getSharingTargetIds()) {
                locationRepository.addSharingTarget(targetId, durationMinutes)
            } else {
                startLocationSharingUseCase(targetId, durationMinutes)
            }

            when (result) {
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        liveLocationError = result.message ?: "Could not start location sharing"
                    )
                }
                is Resource.Success -> {
                    // Start/ensure the foreground service is running.
                    // If it's already running (other targets active), this is a no-op
                    // because the service uses START_STICKY and deduplicates.
                    val context = getApplication<Application>()
                    context.startForegroundService(
                        LocationTrackingService.createStartIntent(context, durationMinutes)
                    )

                    // Insert live location message into conversation
                    insertLiveLocationMessage(targetId, durationMinutes)

                    // Show the persistent banner with THIS target's countdown
                    val expiresAt = if (durationMinutes > 0) {
                        clock() + durationMinutes * 60_000L
                    } else null // continuous
                    _uiState.value = _uiState.value.copy(
                        isLiveLocationSharingActive = true,
                        liveLocationExpiresAt = expiresAt,
                        liveLocationTimeRemaining = if (durationMinutes > 0) formatTimeRemaining(durationMinutes) else null
                    )
                    if (expiresAt != null) {
                        startLocationSharingTimer(expiresAt)
                    }
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * Inserts a LIVE_LOCATION message into the conversation.
     */
    private suspend fun insertLiveLocationMessage(groupId: String, durationMinutes: Long) {
        val convId = _uiState.value.conversationId ?: return
        val currentUser = firebaseAuth.currentUser
        val sessionId = UUID.randomUUID().toString()

        val message = Message(
            id = sessionId,
            conversationId = convId,
            senderId = currentUser?.uid ?: "",
            senderName = currentUser?.displayName ?: "",
            senderPhotoUrl = currentUser?.photoUrl?.toString(),
            text = "📍 Live location",
            type = MessageType.LIVE_LOCATION,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            locationSharingSessionId = sessionId,
            locationSharingDurationMinutes = durationMinutes
        )

        messageRepositoryImpl.sendMessage(
            conversationId = convId,
            text = "📍 Live location",
            replyToId = null
        )
    }

    /**
     * Called when the user taps "Stop" on the live location banner.
     * Removes ONLY this conversation's target from the active session.
     * The foreground service keeps running if other targets remain — it's the
     * GlobalMapViewModel's ticker that stops the service when all targets expire.
     *
     * This is the correct multi-target-aware approach: each screen only manages
     * its own target, never kills the shared service.
     */
    fun stopLiveLocationSharing() {
        val conversation = _uiState.value.conversation ?: return
        val targetId = conversation.groupId ?: "direct:${conversation.otherUserId}"

        viewModelScope.launch {
            // Remove this conversation's target from the active session.
            // If it's the last one, the repo stops the session entirely.
            val result = stopLocationSharingUseCase(targetId)

            // Remove banner immediately (responsive UX)
            _uiState.value = _uiState.value.copy(
                isLiveLocationSharingActive = false,
                liveLocationExpiresAt = null,
                liveLocationTimeRemaining = null
            )
            locationSharingTimerJob?.cancel()
            locationSharingTimerJob = null

            // Only stop the service if NO other targets remain.
            // Check the repo's live state — if empty, the session is fully over.
            val remainingTargets = locationRepository.getSharingTargetIds()
            if (remainingTargets.isEmpty()) {
                val context = getApplication<Application>()
                context.startService(LocationTrackingService.createStopIntent(context))
            }

            if (result is Resource.Error) {
                _uiState.value = _uiState.value.copy(
                    liveLocationError = result.message ?: "Could not stop location sharing"
                )
            }
        }
    }

    /**
     * Dismisses the location bottom sheet.
     */
    fun dismissLocationBottomSheet() {
        _uiState.value = _uiState.value.copy(
            locationBottomSheetState = LocationBottomSheetState.HIDDEN
        )
    }

    /**
     * Toggles the mini-map overlay visibility on the chat screen.
     * Shows active location sharers in this conversation on a compact map.
     */
    fun toggleMiniMap() {
        _uiState.value = _uiState.value.copy(showMiniMap = !_uiState.value.showMiniMap)
    }

    /**
     * Dismisses the live location error message.
     */
    fun dismissLiveLocationError() {
        _uiState.value = _uiState.value.copy(liveLocationError = null)
    }

    /**
     * Countdown timer for THIS conversation's sharing target.
     * When the per-target expiry hits, removes the banner but does NOT stop the service
     * (other targets may still be active — the map screen's ticker handles service lifecycle).
     */
    private fun startLocationSharingTimer(expiresAt: Long) {
        locationSharingTimerJob?.cancel()
        if (expiresAt == Long.MAX_VALUE) return // Continuous — no countdown needed

        locationSharingTimerJob = viewModelScope.launch {
            while (true) {
                val remainingMs = expiresAt - clock()
                if (remainingMs <= 0) {
                    // This conversation's target expired — remove banner.
                    // The repo's pruneExpired() + map ticker handle the actual cleanup.
                    _uiState.value = _uiState.value.copy(
                        isLiveLocationSharingActive = false,
                        liveLocationExpiresAt = null,
                        liveLocationTimeRemaining = null
                    )
                    break
                }
                val remainingMinutes = (remainingMs / 60_000L) + 1 // Round up
                _uiState.value = _uiState.value.copy(
                    liveLocationTimeRemaining = formatTimeRemaining(remainingMinutes)
                )
                delay(LOCATION_TIMER_UPDATE_INTERVAL_MS)
            }
        }
    }

    fun markRead() {
        val convId = _uiState.value.conversationId ?: return
        val uid = currentUserId ?: return
        viewModelScope.launch {
            markConversationReadUseCase(convId, uid)
            wsClient.sendRead()
        }
    }

    // ─── Aggressive Local Caching: Location Observation (Task 2.4) ────────────

    /**
     * Observes cached locations from Room within 100ms of screen open,
     * then subscribes to real-time updates via Socket.IO with Firestore fallback.
     *
     * Requirement 7.2: Cache last known location per sharer in Room, serve within 100ms.
     * Requirement 7.3: Display cached locations within 100ms, subscribe to Socket.IO,
     * fall back to Firestore after 10s.
     */
    private fun observeCachedLocations() {
        // Immediately serve cached locations from Room (within 100ms)
        viewModelScope.launch {
            locationRepository.observeCachedLocations().collect { cachedLocations ->
                val sharingCount = computeActiveSharingCount(cachedLocations)
                val isOtherSharing = computeIsOtherUserSharing(cachedLocations)
                _uiState.value = _uiState.value.copy(
                    cachedLocations = cachedLocations,
                    activeLocationSharingCount = sharingCount,
                    isOtherUserSharingLocation = isOtherSharing
                )
            }
        }

        // Check if user is currently sharing with this conversation's target.
        // Uses per-target expiry (not global max) so the timer is accurate for THIS conversation.
        // This makes the banner appear even if sharing was started from the map screen.
        viewModelScope.launch {
            val conversation = _uiState.value.conversation ?: return@launch
            val targetId = conversation.groupId ?: "direct:${conversation.otherUserId}"
            val activeTargets = locationRepository.checkSharingStatus()
            val isSharingHere = targetId in activeTargets
            if (isSharingHere) {
                // Use the per-target expiry for THIS conversation, not the global max.
                val targetExpiries = locationRepository.getTargetExpiries()
                val thisExpiry = targetExpiries[targetId]
                val expiresAt = if (thisExpiry == Long.MAX_VALUE) null else thisExpiry
                _uiState.value = _uiState.value.copy(
                    isLiveLocationSharingActive = true,
                    liveLocationExpiresAt = expiresAt,
                    liveLocationTimeRemaining = if (expiresAt != null) {
                        val remaining = (expiresAt - System.currentTimeMillis()) / 60_000L
                        if (remaining >= 60) "${remaining / 60}h ${remaining % 60}m"
                        else "${remaining}m"
                    } else null
                )
                startLocationSharingTimer(expiresAt ?: Long.MAX_VALUE)
            }
        }

        // Subscribe to real-time updates with Socket.IO primary / Firestore fallback (10s timeout)
        viewModelScope.launch {
            locationRepository.observeLocationsWithCacheFallback(
                fallbackTimeoutMs = LOCATION_CACHE_FALLBACK_TIMEOUT_MS
            ).collect { liveLocations ->
                val sharingCount = computeActiveSharingCount(liveLocations)
                val isOtherSharing = computeIsOtherUserSharing(liveLocations)
                _uiState.value = _uiState.value.copy(
                    cachedLocations = liveLocations,
                    activeLocationSharingCount = sharingCount,
                    isOtherUserSharingLocation = isOtherSharing
                )
            }
        }
    }

    /**
     * Computes the number of active location sharers in this conversation.
     * Matches against both legacy targetId and the new targetIds list.
     */
    private fun computeActiveSharingCount(locations: List<SharedLocation>): Int {
        val conversation = _uiState.value.conversation ?: return 0
        val targetId = conversation.groupId ?: "direct:${conversation.otherUserId}"
        return locations.count { loc ->
            loc.isSharingActive && (loc.groupId == targetId || targetId in loc.targetIds)
        }
    }

    /**
     * Computes whether the other user in a 1:1 conversation is sharing their location.
     *
     * Requirement 4.5: Show "Sharing location" for 1:1 with friend sharing.
     */
    private fun computeIsOtherUserSharing(locations: List<SharedLocation>): Boolean {
        val conversation = _uiState.value.conversation ?: return false
        if (conversation.isGroup) return false
        val otherUserId = conversation.otherUserId ?: return false
        return locations.any { it.isSharingActive && it.userId == otherUserId }
    }

    // ─── Aggressive Local Caching: Offline State (Task 2.4) ───────────────────

    /** Job for auto-dismissing the offline sync banner. */
    private var offlineBannerDismissJob: Job? = null

    /**
     * Observes network connectivity state and updates UI accordingly.
     *
     * Requirement 7.5: While offline, display all cached data without loading indicators.
     * Requirement 7.6: On offline write action, display non-modal 48dp banner "Queued for sync".
     */
    private fun observeOfflineState() {
        viewModelScope.launch {
            connectivityObserver.isConnected.collect { isConnected ->
                _uiState.value = _uiState.value.copy(
                    isOffline = !isConnected
                )
                // When coming back online, dismiss the queued-for-sync banner
                if (isConnected && _uiState.value.showQueuedForSyncBanner) {
                    dismissQueuedForSyncBanner()
                }
            }
        }
    }

    /**
     * Shows the "Queued for sync" banner when a write action is attempted while offline.
     * The banner is non-modal, 48dp height, and auto-dismisses when connectivity is restored.
     *
     * Requirement 7.6: On offline write action, display non-modal 48dp banner.
     */
    fun showQueuedForSyncBanner() {
        _uiState.value = _uiState.value.copy(showQueuedForSyncBanner = true)
    }

    /**
     * Dismisses the "Queued for sync" banner.
     */
    fun dismissQueuedForSyncBanner() {
        offlineBannerDismissJob?.cancel()
        _uiState.value = _uiState.value.copy(showQueuedForSyncBanner = false)
    }

    // ─── Message Search (Task 8.1) ───────────────────────────────────────────

    /** Job for the debounced search query (300ms debounce, Requirement 13.2). */
    private var searchJob: Job? = null

    /** Saved scroll position index before search was activated (Requirement 13.7). */
    private var preSearchScrollIndex: Int = 0

    /** Saved scroll offset before search was activated (Requirement 13.7). */
    private var preSearchScrollOffset: Int = 0

    /**
     * Activates the search bar. Saves the current scroll position for later restoration.
     *
     * Requirement 13.1: Search icon in header opens search bar.
     * Requirement 13.7: On dismiss, restore previous scroll position.
     *
     * @param currentScrollIndex The current first visible item index in the LazyColumn.
     * @param currentScrollOffset The current scroll offset of the first visible item.
     */
    fun activateSearch(currentScrollIndex: Int, currentScrollOffset: Int) {
        preSearchScrollIndex = currentScrollIndex
        preSearchScrollOffset = currentScrollOffset
        _uiState.value = _uiState.value.copy(
            isSearchActive = true,
            searchQuery = "",
            searchResultIds = emptyList(),
            currentSearchResultIndex = -1
        )
    }

    /**
     * Called when the search query text changes.
     * Debounces 300ms after the last keystroke, then queries Room for matching messages.
     * Only queries when the input is >= 2 characters.
     *
     * Requirement 13.2: 300ms debounce, case-insensitive substring match, >= 2 chars.
     *
     * @param query The current search text (max 100 chars enforced by UI).
     */
    fun onSearchQueryChange(query: String) {
        val trimmedQuery = query.take(SEARCH_MAX_CHARS)
        _uiState.value = _uiState.value.copy(searchQuery = trimmedQuery)

        searchJob?.cancel()

        if (trimmedQuery.length < SEARCH_MIN_CHARS) {
            // Clear results when query is too short
            _uiState.value = _uiState.value.copy(
                searchResultIds = emptyList(),
                currentSearchResultIndex = -1
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val convId = _uiState.value.conversationId ?: return@launch
            val results = messageRepositoryImpl.searchMessages(convId, "%$trimmedQuery%")
            // Results are ordered by timestamp ASC (oldest first) from the DAO
            val resultIds = results.map { it.id }
            _uiState.value = _uiState.value.copy(
                searchResultIds = resultIds,
                currentSearchResultIndex = if (resultIds.isNotEmpty()) 0 else -1
            )
        }
    }

    /**
     * Navigates to the next search result (down arrow — next match chronologically).
     * Disabled at the last result.
     *
     * Requirement 13.4: Down arrow navigates to next match; disabled at last result.
     */
    fun navigateSearchNext() {
        val state = _uiState.value
        if (state.searchResultIds.isEmpty()) return
        val nextIndex = state.currentSearchResultIndex + 1
        if (nextIndex >= state.searchResultIds.size) return // Already at last result
        _uiState.value = state.copy(currentSearchResultIndex = nextIndex)
    }

    /**
     * Navigates to the previous search result (up arrow — previous match).
     * Disabled at the first result.
     *
     * Requirement 13.5: Up arrow navigates to previous match; disabled at first result.
     */
    fun navigateSearchPrevious() {
        val state = _uiState.value
        if (state.searchResultIds.isEmpty()) return
        val prevIndex = state.currentSearchResultIndex - 1
        if (prevIndex < 0) return // Already at first result
        _uiState.value = state.copy(currentSearchResultIndex = prevIndex)
    }

    /**
     * Re-maps existing messages with updated nicknames so sender names reflect the change.
     */
    private fun remapMessagesWithNicknames(nicknames: Map<String, String>, currentUserId: String) {
        val updatedMessages = _uiState.value.messages.map { msg ->
            val resolvedName = nicknames[msg.senderId]?.takeIf { it.isNotBlank() } ?: msg.senderName
            if (resolvedName != msg.senderName) {
                msg.copy(senderName = resolvedName)
            } else {
                msg
            }
        }
        val grouped = updatedMessages.groupBy { it.dateKey }
            .entries
            .map { (key, msgs) -> key to msgs }
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            groupedMessages = grouped
        )
    }

    /**
     * Dismisses the search bar, removes highlights, and returns the saved scroll position.
     * The UI should restore the scroll position to the returned values.
     *
     * Requirement 13.7: On dismiss (X or back), remove highlights, restore previous scroll position.
     *
     * @return Pair of (scrollIndex, scrollOffset) to restore.
     */
    fun dismissSearch(): Pair<Int, Int> {
        searchJob?.cancel()
        searchJob = null
        _uiState.value = _uiState.value.copy(
            isSearchActive = false,
            searchQuery = "",
            searchResultIds = emptyList(),
            currentSearchResultIndex = -1
        )
        return Pair(preSearchScrollIndex, preSearchScrollOffset)
    }

    /**
     * Called when the ViewModel is cleared (ChatScreen removed from back stack).
     * All coroutines launched in viewModelScope are automatically cancelled by the
     * framework when this method is called, including pagination, typing emission,
     * and presence observation coroutines.
     *
     * Requirement 21.1: Cancel all active coroutines by relying on viewModelScope
     * cancellation. No coroutine launched within viewModelScope shall continue
     * executing after onCleared() returns.
     */
    override fun onCleared() {
        super.onCleared()
        // Release audio playback resources
        voicePlaybackController.release()
        // Disconnect the WebSocket for this conversation
        wsClient.disconnect()
        // Cancel the location sharing timer explicitly (belt-and-suspenders with viewModelScope)
        locationSharingTimerJob?.cancel()
        // Note: All coroutines in viewModelScope (typing observation, connection state,
        // presence observation, pagination, search debounce, etc.) are automatically
        // cancelled by the framework's viewModelScope.cancel() call.
    }

    // ─── Context Menu Actions (Task 4.2) ──────────────────────────────────────

    /**
     * Shows the context menu for the given message.
     * Called on 300ms long-press of a message bubble.
     *
     * Requirement 9.1: Display floating context menu anchored to pressed message.
     */
    fun showContextMenu(message: MessageUiModel) {
        _uiState.value = _uiState.value.copy(
            contextMenuMessage = message,
            isContextMenuFadingOut = false
        )
    }

    /**
     * Dismisses the context menu with a 200ms fade-out animation.
     * Called on tap outside or back gesture.
     *
     * Requirement 9.11: Dismiss with 200ms fade-out.
     */
    fun dismissContextMenu() {
        _uiState.value = _uiState.value.copy(isContextMenuFadingOut = true)
        viewModelScope.launch {
            delay(CONTEXT_MENU_FADE_MS)
            _uiState.value = _uiState.value.copy(
                contextMenuMessage = null,
                isContextMenuFadingOut = false,
                showDeleteConfirmation = false
            )
        }
    }

    /**
     * Copies the message text to the system clipboard.
     * Dismisses the menu and shows a "Copied" toast for 2 seconds.
     *
     * Requirement 9.3: Copy text to clipboard, dismiss, show "Copied" toast 2s.
     */
    fun copyMessageText(text: String) {
        val clipboard = getApplication<Application>()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("message", text)
        clipboard.setPrimaryClip(clip)
        // Dismiss menu immediately
        _uiState.value = _uiState.value.copy(
            contextMenuMessage = null,
            isContextMenuFadingOut = false
        )
    }

    /**
     * Initiates a reply from the context menu.
     * Dismisses the menu and populates the reply preview bar.
     *
     * Requirement 9.5: Dismiss menu, populate reply preview bar.
     */
    fun replyFromContextMenu() {
        val message = _uiState.value.contextMenuMessage ?: return
        _uiState.value = _uiState.value.copy(
            contextMenuMessage = null,
            isContextMenuFadingOut = false,
            replyingToMessage = message
        )
    }

    /**
     * Shows the reaction picker from the context menu.
     * Dismisses the context menu first.
     *
     * Requirement 9.6: Dismiss context menu, show reaction picker overlay.
     * Requirement 27.1: Show ReactionPickerOverlay centered with scrim.
     */
    fun reactFromContextMenu() {
        val messageId = _uiState.value.contextMenuMessage?.id
        _uiState.value = _uiState.value.copy(
            contextMenuMessage = null,
            isContextMenuFadingOut = false,
            showReactionPicker = true,
            reactionPickerMessageId = messageId
        )
    }

    /**
     * Shows the reaction picker for a message via long-press (500ms).
     * Called directly from the message bubble long-press gesture.
     *
     * Requirement 27.1: Long-press (500ms) message → ReactionPickerOverlay centered with scrim.
     */
    fun showReactionPicker(messageId: String) {
        _uiState.value = _uiState.value.copy(
            showReactionPicker = true,
            reactionPickerMessageId = messageId
        )
    }

    /**
     * Dismisses the reaction picker without selecting a reaction.
     * Called on tap outside or back gesture.
     *
     * Requirement 27.1: Tap outside/back dismisses without reaction.
     */
    fun dismissReactionPicker() {
        _uiState.value = _uiState.value.copy(
            showReactionPicker = false,
            reactionPickerMessageId = null
        )
    }

    /**
     * Handles emoji selection from the reaction picker.
     * Calls toggleReaction and dismisses the picker.
     *
     * Requirement 27.1: On emoji select call toggleReaction, dismiss.
     */
    fun onReactionSelected(emoji: String) {
        val messageId = _uiState.value.reactionPickerMessageId ?: return
        toggleReaction(messageId, emoji)
        dismissReactionPicker()
    }

    /**
     * Opens the bottom sheet listing everyone who reacted to a message,
     * grouped by emoji. Triggered by long-pressing a [ReactionBadges] pill.
     */
    fun showReactionDetails(messageId: String) {
        _uiState.value = _uiState.value.copy(reactionDetailsMessageId = messageId)
    }

    /**
     * Toggles the tap-to-reveal timestamp for a message.
     * Messenger hides timestamps by default; tapping a bubble reveals its time + status.
     * Tapping again (or tapping a different bubble) hides it.
     */
    fun toggleMessageTimestamp(messageId: String) {
        val current = _uiState.value.tappedMessageId
        _uiState.value = _uiState.value.copy(
            tappedMessageId = if (current == messageId) null else messageId
        )
    }

    /** Dismisses the reaction-details bottom sheet. */
    fun dismissReactionDetails() {
        _uiState.value = _uiState.value.copy(reactionDetailsMessageId = null)
    }

    /** Shows the user details bottom sheet for a sender avatar tap. */
    fun showUserDetailsSheet(userId: String) {
        _uiState.value = _uiState.value.copy(avatarSheetUserId = userId)
    }

    /** Dismisses the user details bottom sheet. */
    fun dismissUserDetailsSheet() {
        _uiState.value = _uiState.value.copy(avatarSheetUserId = null)
    }

    /**
     * Builds [com.ovi.where.presentation.chat.components.SheetUserDetails] for the
     * avatar-tap bottom sheet from cached participant metadata.
     */
    fun buildSheetUserDetails(userId: String): com.ovi.where.presentation.chat.components.SheetUserDetails {
        return com.ovi.where.presentation.chat.components.SheetUserDetails(
            userId = userId,
            displayName = participantNames[userId] ?: "",
            photoUrl = participantPhotos[userId],
            isOnline = userId in onlineUserIds,
            friendshipStatus = cachedFriendshipStatus[userId]
                ?: com.ovi.where.presentation.chat.components.FriendshipAction.NONE
        )
    }

    /**
     * Sends a friend request to the given user from the avatar sheet.
     */
    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            friendshipRepository.sendFriendRequest(userId)
            cachedFriendshipStatus[userId] = com.ovi.where.presentation.chat.components.FriendshipAction.PENDING_OUTGOING
            dismissUserDetailsSheet()
        }
    }

    /**
     * Accepts an incoming friend request from the avatar sheet.
     */
    fun acceptFriendRequest(userId: String) {
        viewModelScope.launch {
            friendshipRepository.acceptFriendRequest(userId)
            cachedFriendIds.add(userId)
            cachedFriendshipStatus[userId] = com.ovi.where.presentation.chat.components.FriendshipAction.ACCEPTED
            dismissUserDetailsSheet()
        }
    }

    /**
     * Opens or creates a direct conversation with the given user and emits a
     * navigation event so ChatScreen can navigate to it.
     */
    private val _navigateToChatEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToChatEvent: kotlinx.coroutines.flow.SharedFlow<String> = _navigateToChatEvent.asSharedFlow()

    fun openDirectChat(userId: String) {
        viewModelScope.launch {
            dismissUserDetailsSheet()
            when (val result = getOrCreateDirectConversationUseCase(userId)) {
                is Resource.Success -> {
                    result.data?.id?.let { conversationId ->
                        _navigateToChatEvent.emit(conversationId)
                    }
                }
                else -> { /* silently fail */ }
            }
        }
    }

    /**
     * Builds a flat list of [com.ovi.where.presentation.chat.components.Reactor] entries
     * for the currently-open reaction-details sheet, joined with cached participant
     * metadata (name + photo).
     */
    fun buildReactorsForDetails(messageId: String):
        List<com.ovi.where.presentation.chat.components.Reactor> {
        val message = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return emptyList()
        val nicknames = _uiState.value.conversation?.nicknames ?: emptyMap()
        val uid = currentUserId
        return message.reactions.flatMap { (emoji, userIds) ->
            userIds.map { userId ->
                val displayName = nicknames[userId]?.takeIf { it.isNotBlank() }
                    ?: participantNames[userId]
                    ?: ""
                com.ovi.where.presentation.chat.components.Reactor(
                    userId = userId,
                    displayName = displayName,
                    photoUrl = participantPhotos[userId],
                    emoji = emoji,
                    isCurrentUser = userId == uid
                )
            }
        }
    }

    /**
     * Shows the delete confirmation dialog.
     *
     * Requirement 9.7: Display confirmation dialog before deleting.
     */
    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = true)
    }

    /**
     * Cancels the delete confirmation dialog.
     */
    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
    }

    /**
     * Deletes the current user's own message after confirmation.
     * Calls the delete API and removes from local cache.
     * On API failure: shows error snackbar for 4 seconds.
     *
     * Requirement 9.7: Call delete API, remove from cache.
     * Requirement 9.8: On API fail, error snackbar 4s.
     */
    fun confirmDeleteMessage() {
        val message = _uiState.value.contextMenuMessage ?: return
        val convId = _uiState.value.conversationId ?: return

        // Dismiss menu and dialog
        _uiState.value = _uiState.value.copy(
            contextMenuMessage = null,
            isContextMenuFadingOut = false,
            showDeleteConfirmation = false
        )

        viewModelScope.launch {
            val result = messageRepositoryImpl.deleteMessage(convId, message.id)
            if (result is Resource.Error) {
                // Requirement 9.8: Error snackbar for 4 seconds
                _snackbarEvent.tryEmit(
                    SnackbarEvent(
                        message = "Failed to delete message",
                        durationMs = DELETE_ERROR_SNACKBAR_MS
                    )
                )
            }
        }
    }

    /**
     * Removes a message from local display only (for messages sent by others).
     *
     * Requirement 9.9: "Delete for me" removes from local display only.
     */
    fun deleteMessageForMe() {
        val message = _uiState.value.contextMenuMessage ?: return

        // Dismiss menu
        _uiState.value = _uiState.value.copy(
            contextMenuMessage = null,
            isContextMenuFadingOut = false
        )

        viewModelScope.launch {
            messageRepositoryImpl.deleteMessageLocally(message.id)
        }
    }

    /**
     * Navigates to the conversation picker for forwarding a message.
     * Dismisses the context menu.
     *
     * Requirement 9.10: Navigate to conversation picker (max 5 targets).
     */
    fun forwardMessage(): String? {
        val message = _uiState.value.contextMenuMessage ?: return null
        val messageId = message.id

        // Dismiss menu
        _uiState.value = _uiState.value.copy(
            contextMenuMessage = null,
            isContextMenuFadingOut = false
        )

        return messageId
    }

    // ─── Voice Message Playback (Task 6.2) ──────────────────────────────────────

    /**
     * Toggles play/pause for a voice message.
     * If another voice message is playing, stops it and plays the new one (Requirement 11.10).
     *
     * @param messageId The ID of the voice message.
     * @param audioUrl The URL or local path of the audio file.
     * @param durationMs The total duration of the voice message in milliseconds.
     */
    fun toggleVoicePlayback(messageId: String, audioUrl: String, durationMs: Long) {
        voicePlaybackController.togglePlayPause(
            messageId = messageId,
            audioUrl = audioUrl,
            durationMs = durationMs,
            context = getApplication(),
            scope = viewModelScope
        )
    }

    /**
     * Seeks to a specific position in the currently playing voice message.
     *
     * @param messageId The ID of the voice message being seeked.
     * @param progress Target progress from 0.0 to 1.0.
     * @param durationMs Total duration for calculating seek position.
     */
    fun seekVoiceMessage(messageId: String, progress: Float, durationMs: Long) {
        voicePlaybackController.seekTo(messageId, progress, durationMs)
    }

    /**
     * Pauses voice playback when navigating away from ChatScreen (Requirement 11.9).
     */
    fun pauseVoicePlayback() {
        voicePlaybackController.pauseIfPlaying()
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

    // ─── Voice Recording (Task 6.1) ──────────────────────────────────────────

    /** Job for observing VoiceRecorder state changes. */
    private var voiceRecorderObserveJob: Job? = null

    /** Job for auto-dismissing the "Hold to record" tooltip. */
    private var tooltipDismissJob: Job? = null

    /**
     * Starts voice recording after verifying microphone permission.
     *
     * If permission is not granted, sets microphonePermissionNeeded = true
     * so the UI can request it.
     *
     * Requirements: 11.1, 11.11
     */
    fun startVoiceRecording() {
        val convId = _uiState.value.conversationId ?: return

        // Check microphone permission
        val context = getApplication<android.app.Application>()
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _uiState.value = _uiState.value.copy(microphonePermissionNeeded = true)
            return
        }

        val started = voiceRecorder.startRecording()
        if (!started) {
            _uiState.value = _uiState.value.copy(
                voiceRecordingError = "Failed to start recording"
            )
            return
        }

        // Observe recorder state
        voiceRecorderObserveJob?.cancel()
        voiceRecorderObserveJob = viewModelScope.launch {
            voiceRecorder.state.collect { recorderState ->
                _uiState.value = _uiState.value.copy(
                    isVoiceRecording = recorderState.isRecording,
                    voiceRecordingDurationMs = recorderState.durationMs,
                    voiceWaveformAmplitudes = recorderState.waveformAmplitudes,
                    isVoiceRecordingLocked = recorderState.isLocked
                )

                // Requirement 11.7: Auto-stop and send at max duration
                if (recorderState.maxDurationReached) {
                    stopVoiceRecordingAndSend()
                }
            }
        }
    }

    /**
     * Stops voice recording and sends the message if duration >= 1s.
     * If duration < 1s, discards and shows "Hold to record" tooltip for 2s.
     *
     * Requirements: 11.4, 11.5
     */
    fun stopVoiceRecordingAndSend() {
        val convId = _uiState.value.conversationId ?: return

        val result = voiceRecorder.stopRecording()
        voiceRecorderObserveJob?.cancel()
        voiceRecorderObserveJob = null

        if (result == null) {
            // Duration < 1s: show tooltip (Requirement 11.5)
            _uiState.value = _uiState.value.copy(
                isVoiceRecording = false,
                voiceRecordingDurationMs = 0L,
                voiceWaveformAmplitudes = emptyList(),
                isVoiceRecordingLocked = false,
                showHoldToRecordTooltip = true
            )
            // Auto-dismiss tooltip after 2 seconds
            tooltipDismissJob?.cancel()
            tooltipDismissJob = viewModelScope.launch {
                delay(HOLD_TO_RECORD_TOOLTIP_MS)
                _uiState.value = _uiState.value.copy(showHoldToRecordTooltip = false)
            }
            return
        }

        // Reset recording UI state
        _uiState.value = _uiState.value.copy(
            isVoiceRecording = false,
            voiceRecordingDurationMs = 0L,
            voiceWaveformAmplitudes = emptyList(),
            isVoiceRecordingLocked = false
        )

        // Send the voice message
        viewModelScope.launch {
            val sendResult = messageRepositoryImpl.sendVoiceMessage(
                conversationId = convId,
                audioFilePath = result.filePath,
                durationMs = result.durationMs
            )
            if (sendResult is Resource.Error) {
                onSendFailure()
            } else {
                consecutiveFailureCount = 0
            }
        }
    }

    /**
     * Cancels the current voice recording and discards the audio.
     *
     * Requirement 11.2: Slide left > 100dp cancels recording.
     */
    fun cancelVoiceRecording() {
        voiceRecorder.cancelRecording()
        voiceRecorderObserveJob?.cancel()
        voiceRecorderObserveJob = null

        _uiState.value = _uiState.value.copy(
            isVoiceRecording = false,
            voiceRecordingDurationMs = 0L,
            voiceWaveformAmplitudes = emptyList(),
            isVoiceRecordingLocked = false
        )
    }

    /**
     * Locks voice recording into hands-free mode.
     * The user can release the button while recording continues.
     *
     * Requirement 11.3: Slide up > 48dp locks into hands-free mode.
     */
    fun lockVoiceRecording() {
        voiceRecorder.lockRecording()
    }

    /**
     * Called when microphone permission is granted after being requested.
     * Retries starting the recording.
     *
     * Requirement 11.11
     */
    fun onMicrophonePermissionGranted() {
        _uiState.value = _uiState.value.copy(microphonePermissionNeeded = false)
        startVoiceRecording()
    }

    /**
     * Called when microphone permission is denied.
     * Shows an error message.
     *
     * Requirement 11.11
     */
    fun onMicrophonePermissionDenied() {
        _uiState.value = _uiState.value.copy(
            microphonePermissionNeeded = false,
            voiceRecordingError = "Microphone access is required to record voice messages"
        )
        // Auto-dismiss error after 4 seconds
        viewModelScope.launch {
            delay(4_000L)
            _uiState.value = _uiState.value.copy(voiceRecordingError = null)
        }
    }

    /**
     * Dismisses the voice recording error message.
     */
    fun dismissVoiceRecordingError() {
        _uiState.value = _uiState.value.copy(voiceRecordingError = null)
    }

    /**
     * Called when the app goes to background while ChatScreen is active.
     * Disconnects the WebSocket to conserve resources and battery.
     * Pauses voice message playback (Requirement 11.9).
     * Cancels any active voice recording.
     *
     * Requirement: 13.1, 11.9
     */
    fun onBackground() {
        // Cancel active recording on background
        if (_uiState.value.isVoiceRecording) {
            cancelVoiceRecording()
        }
        voicePlaybackController.pauseIfPlaying()
        wsClient.disconnect()
    }

    // ─── Admin Overflow Menu (Task 10.1) ──────────────────────────────────────

    /**
     * Observes group members to determine if the current user is an admin.
     * Called when the conversation is a group conversation.
     *
     * Requirement 15.1: Admin users see overflow menu icon.
     * Requirement 15.7: Non-admin users hide overflow menu.
     */
    fun observeAdminStatus(groupId: String) {
        viewModelScope.launch {
            groupRepository.observeGroupMembers(groupId).collect { members ->
                val uid = currentUserId ?: return@collect
                val currentMember = members.firstOrNull { it.userId == uid }
                val isAdmin = currentMember?.role == MemberRole.ADMIN

                // Resolve display names for the member picker (Requirement 15.2)
                val otherMembers = members.filter { it.userId != uid }
                val userIds = otherMembers.map { it.userId }
                val usersResult = userRepository.getUsers(userIds)
                val userMap = if (usersResult is Resource.Success && usersResult.data != null) {
                    usersResult.data.associateBy { it.id }
                } else {
                    emptyMap()
                }

                val pickerItems = otherMembers.map { member ->
                    val displayName = userMap[member.userId]?.displayName ?: member.userId
                    GroupMemberPickerItem(userId = member.userId, displayName = displayName)
                }

                _uiState.value = _uiState.value.copy(
                    isCurrentUserAdmin = isAdmin,
                    groupMembersForPicker = pickerItems
                )
            }
        }
    }

    /**
     * Toggles the admin overflow menu visibility.
     *
     * Requirement 15.1: Overflow menu reveals three admin actions.
     */
    fun toggleAdminOverflowMenu() {
        _uiState.value = _uiState.value.copy(
            showAdminOverflowMenu = !_uiState.value.showAdminOverflowMenu
        )
    }

    /**
     * Dismisses the admin overflow menu.
     */
    fun dismissAdminOverflowMenu() {
        _uiState.value = _uiState.value.copy(showAdminOverflowMenu = false)
    }

    /**
     * Shows the member picker dialog for the "Mute Member" action.
     *
     * Requirement 15.2: Member picker dialog lists all group members except current user.
     */
    fun showMuteMemberPicker() {
        _uiState.value = _uiState.value.copy(
            showAdminOverflowMenu = false,
            showMemberPickerDialog = true
        )
    }

    /**
     * Dismisses the member picker dialog.
     */
    fun dismissMemberPickerDialog() {
        _uiState.value = _uiState.value.copy(showMemberPickerDialog = false)
    }

    /**
     * Mutes a selected member in the group.
     *
     * Requirement 15.2: On confirm, call mute API and show confirmation snackbar.
     * Requirement 15.3: On fail, show error snackbar, member status unchanged.
     */
    fun muteMember(memberId: String, memberDisplayName: String) {
        val groupId = _uiState.value.conversation?.groupId ?: return
        _uiState.value = _uiState.value.copy(showMemberPickerDialog = false)

        viewModelScope.launch {
            val result = muteGroupMemberUseCase(groupId, memberId)
            if (result is Resource.Success) {
                _snackbarEvent.tryEmit(
                    SnackbarEvent(message = "$memberDisplayName has been muted")
                )
            } else {
                _snackbarEvent.tryEmit(
                    SnackbarEvent(message = "Failed to mute member")
                )
            }
        }
    }

    /**
     * Handles the "Invite Link" action from the admin overflow menu.
     * Copies the invite link to clipboard, shows a toast, and presents the share sheet.
     *
     * Requirement 15.5: Copy to clipboard, show "Link copied" toast 2s, present share sheet.
     * Requirement 15.6: On fail, show error snackbar, no clipboard copy.
     */
    fun handleInviteLink() {
        val groupId = _uiState.value.conversation?.groupId ?: return
        _uiState.value = _uiState.value.copy(showAdminOverflowMenu = false)

        viewModelScope.launch {
            val result = groupRepository.getInviteLink(groupId)
            if (result is Resource.Success && result.data != null) {
                _inviteLinkEvent.tryEmit(result.data)
            } else {
                _snackbarEvent.tryEmit(
                    SnackbarEvent(message = "Failed to retrieve invite link")
                )
            }
        }
    }

    /**
     * One-shot event for invite link sharing.
     * The UI collects this to copy to clipboard and show the share sheet.
     *
     * Requirement 15.5
     */
    private val _inviteLinkEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val inviteLinkEvent: SharedFlow<String> = _inviteLinkEvent.asSharedFlow()

    // ─── Mention Engine (Task 9.1) ────────────────────────────────────────────

    /**
     * The MentionEngine instance for managing @mention state in the input field.
     * Handles detection, filtering, insertion, and deletion of mention tokens.
     *
     * Requirements: 14.1, 14.2, 14.3, 14.5, 14.6, 14.7, 14.8
     */
    private val mentionEngine = MentionEngine()

    /**
     * Cached group members for mention suggestions.
     * Populated when a group conversation is loaded.
     */
    private var cachedMentionMembers: List<MentionEngine.MentionMember> = emptyList()

    /** Total group size for determining suggestion limit (Requirement 14.5). */
    private var totalGroupSize: Int = 0

    /** Job for debouncing mention query updates (Requirement 14.1: within 300ms). */
    private var mentionQueryJob: Job? = null

    /**
     * Observes group members and caches them for mention suggestions.
     * Called when the conversation is identified as a group conversation.
     *
     * Resolves display names via UserRepository for accurate mention matching.
     */
    fun observeGroupMembersForMentions(groupId: String) {
        viewModelScope.launch {
            groupRepository.observeGroupMembers(groupId).collect { members ->
                totalGroupSize = members.size
                // Resolve display names for all members
                val userIds = members.map { it.userId }
                val usersResult = userRepository.getUsers(userIds)
                val userMap = if (usersResult is Resource.Success && usersResult.data != null) {
                    usersResult.data.associateBy { it.id }
                } else {
                    emptyMap()
                }

                // Populate participant metadata so message UI models (read receipts,
                // sender avatars, nicknames) can resolve photos and names for any
                // group member without an extra fetch.
                userMap.values.forEach { user ->
                    participantNames[user.id] = user.displayName
                    participantPhotos[user.id] = user.photoUrl
                }

                // Cache friendship status for each member (for avatar-tap sheet)
                val uid = currentUserId ?: ""
                for (userId in userIds) {
                    if (userId == uid) continue
                    try {
                        val status = friendshipRepository.getFriendshipStatus(userId)
                        val action = when (status) {
                            FriendshipStatus.ACCEPTED -> {
                                cachedFriendIds.add(userId)
                                com.ovi.where.presentation.chat.components.FriendshipAction.ACCEPTED
                            }
                            FriendshipStatus.PENDING -> {
                                // Need to determine direction: did I send it or did they?
                                val friendship = friendshipRepository.getFriendship(userId)
                                if (friendship != null && friendship.requesterId == uid) {
                                    com.ovi.where.presentation.chat.components.FriendshipAction.PENDING_OUTGOING
                                } else {
                                    com.ovi.where.presentation.chat.components.FriendshipAction.PENDING_INCOMING
                                }
                            }
                            else -> com.ovi.where.presentation.chat.components.FriendshipAction.NONE
                        }
                        cachedFriendshipStatus[userId] = action
                    } catch (_: Exception) { /* best-effort */ }
                }

                cachedMentionMembers = members.map { member ->
                    val user = userMap[member.userId]
                    MentionEngine.MentionMember(
                        userId = member.userId,
                        displayName = user?.displayName ?: member.userId,
                        photoUrl = user?.photoUrl
                    )
                }
            }
        }
    }

    /**
     * Called when the input text changes to detect "@" trigger and update mention suggestions.
     * Debounces the query to update within 300ms per Requirement 14.1.
     *
     * This should be called from onInputChange when the conversation is a group.
     *
     * @param text The current input text
     * @param cursorPosition The current cursor position in the text
     */
    fun onMentionTrigger(text: String, cursorPosition: Int) {
        val conversation = _uiState.value.conversation ?: return
        if (!conversation.isGroup) return

        mentionQueryJob?.cancel()
        mentionQueryJob = viewModelScope.launch {
            delay(MENTION_DEBOUNCE_MS)

            val queryResult = mentionEngine.detectMentionQuery(text, cursorPosition)

            if (queryResult.isActive) {
                val uid = currentUserId ?: ""
                val suggestions = mentionEngine.getSuggestions(
                    members = cachedMentionMembers,
                    query = queryResult.query,
                    currentUserId = uid,
                    totalGroupSize = totalGroupSize
                )
                _uiState.value = _uiState.value.copy(
                    isMentionPopupVisible = suggestions.isNotEmpty(),
                    mentionSuggestions = suggestions
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isMentionPopupVisible = false,
                    mentionSuggestions = emptyList()
                )
            }
        }
    }

    /**
     * Called when a member is selected from the mention suggestion popup.
     * Replaces "@query" with a styled mention token and places cursor after the token.
     *
     * Requirement 14.2: Replace "@" and typed filter characters with styled mention token,
     * place cursor immediately after the token, dismiss popup.
     *
     * @param member The selected member to mention
     */
    fun selectMention(member: MentionEngine.MentionMember) {
        val text = _uiState.value.inputText
        val conversation = _uiState.value.conversation ?: return
        if (!conversation.isGroup) return

        // Find the active mention query to determine where to insert
        // We need to find the "@" trigger position
        // Use the last known cursor position (end of text as fallback)
        val cursorPosition = text.length
        val queryResult = mentionEngine.detectMentionQuery(text, cursorPosition)

        if (queryResult.isActive) {
            val result = mentionEngine.insertMention(
                text = text,
                triggerStartIndex = queryResult.triggerStartIndex,
                cursorPosition = cursorPosition,
                member = member
            )

            _uiState.value = _uiState.value.copy(
                inputText = result.newText,
                isMentionPopupVisible = false,
                mentionSuggestions = emptyList(),
                mentionedUserIds = mentionEngine.mentionedUserIds
            )
        } else {
            dismissMentionPopup()
        }
    }

    /**
     * Dismisses the mention suggestion popup without inserting a mention.
     *
     * Requirement 14.8: Dismiss popup on back, tap outside, or delete "@" trigger.
     */
    fun dismissMentionPopup() {
        mentionQueryJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isMentionPopupVisible = false,
            mentionSuggestions = emptyList()
        )
    }

    /**
     * Handles text deletion that may affect mention tokens.
     * If a character within a mention token is deleted, the entire token is removed.
     *
     * Requirement 14.7: Delete within mention token → remove entire token and userId from list.
     *
     * @param oldText The previous input text
     * @param newText The new input text after deletion
     * @param cursorPosition The cursor position after the change
     * @return The corrected text if a token was removed, or null if no correction needed
     */
    fun handleMentionTextChange(oldText: String, newText: String, cursorPosition: Int): String? {
        val correctedText = mentionEngine.handleTextChange(oldText, newText, cursorPosition)
        if (correctedText != null) {
            _uiState.value = _uiState.value.copy(
                mentionedUserIds = mentionEngine.mentionedUserIds
            )
        }
        return correctedText
    }

    /**
     * Returns the mention ranges in the current input text for styled rendering.
     * Used by the input field to apply primary color + bold to mention tokens.
     *
     * Requirement 14.2: Styled mention token (primary color, bold).
     */
    fun getMentionRangesInInput(): List<IntRange> {
        return mentionEngine.getMentionRanges(_uiState.value.inputText)
    }

    /**
     * Clears mention state when a message is sent.
     * Called from sendMessage() to reset the mention engine.
     */
    private fun clearMentionState() {
        mentionEngine.clear()
        _uiState.value = _uiState.value.copy(
            isMentionPopupVisible = false,
            mentionSuggestions = emptyList(),
            mentionedUserIds = emptyList()
        )
    }

    // ─── Message Grouping Computation (Requirements 4.6, 4.7, 10.3) ──────────

    /**
     * Computes message grouping metadata for a list of MessageUiModels.
     *
     * Grouping rules:
     * - Consecutive messages from the same sender within 2 minutes (120,000ms) form a group.
     * - `isFirstInGroup` is true for the first message in each group.
     * - `isLastInGroup` is true for the last message in each group.
     * - `showDateSeparator` is true when the dateKey differs from the previous message (or for the first message).
     * - `dateSeparatorLabel` is "Today", "Yesterday", or a formatted date string.
     *
     * The input list must be sorted by timestamp ascending.
     *
     * Requirements: 4.6, 4.7, 10.3
     */
    internal fun computeMessageGrouping(
        messages: List<MessageUiModel>,
        timestamps: List<Long>
    ): List<MessageUiModel> {
        if (messages.isEmpty()) return emptyList()

        val result = mutableListOf<MessageUiModel>()

        for (i in messages.indices) {
            val current = messages[i]
            val currentTimestamp = timestamps[i]
            val prev = if (i > 0) messages[i - 1] else null
            val prevTimestamp = if (i > 0) timestamps[i - 1] else null
            val next = if (i < messages.size - 1) messages[i + 1] else null
            val nextTimestamp = if (i < messages.size - 1) timestamps[i + 1] else null

            // Determine if this message is in the same group as the previous message
            val sameGroupAsPrev = prev != null && prevTimestamp != null &&
                current.senderId == prev.senderId &&
                (currentTimestamp - prevTimestamp) <= MESSAGE_GROUP_THRESHOLD_MS

            // Determine if this message is in the same group as the next message
            val sameGroupAsNext = next != null && nextTimestamp != null &&
                current.senderId == next.senderId &&
                (nextTimestamp - currentTimestamp) <= MESSAGE_GROUP_THRESHOLD_MS

            val isFirstInGroup = !sameGroupAsPrev
            val isLastInGroup = !sameGroupAsNext

            // Messenger-style timestamp visibility:
            // Show a centered timestamp separator when there's a significant gap (5+ min)
            // to the PREVIOUS message, or it's the first message in the conversation.
            // This replaces per-bubble timestamps entirely.
            val showTimestamp = isFirstInGroup && (
                prev == null || prevTimestamp == null ||
                (currentTimestamp - prevTimestamp) >= TIMESTAMP_DISPLAY_THRESHOLD_MS
            )

            // Date separator: show when dateKey differs from previous message or for the first message
            // OR when there's a time gap (showTimestamp). The label includes time.
            val showDateSeparator = showTimestamp || (prev == null || current.dateKey != prev.dateKey)
            val dateSeparatorLabel = if (showDateSeparator) {
                formatMessageTime(currentTimestamp)
            } else {
                null
            }

            result.add(
                current.copy(
                    isFirstInGroup = isFirstInGroup,
                    isLastInGroup = isLastInGroup,
                    showTimestamp = showTimestamp,
                    showDateSeparator = showDateSeparator,
                    dateSeparatorLabel = dateSeparatorLabel
                )
            )
        }

        // Second pass: detect consecutive image groups and mark collage metadata
        val finalResult = mutableListOf<MessageUiModel>()
        var i = 0
        while (i < result.size) {
            val msg = result[i]
            if (msg.isImage && !msg.imageUrl.isNullOrBlank()) {
                // Collect consecutive images from the same sender
                val collageUrls = mutableListOf(msg.imageUrl!!)
                var j = i + 1
                while (j < result.size && j - i < 5) {
                    val next = result[j]
                    if (next.isImage && !next.imageUrl.isNullOrBlank()
                        && next.senderId == msg.senderId
                        && !next.showDateSeparator
                    ) {
                        collageUrls.add(next.imageUrl!!)
                        j++
                    } else break
                }

                if (collageUrls.size > 1) {
                    // First image gets the collage URLs
                    finalResult.add(msg.copy(imageCollageUrls = collageUrls))
                    // Remaining images are hidden
                    for (k in (i + 1) until j) {
                        finalResult.add(result[k].copy(isHiddenInCollage = true))
                    }
                    i = j
                } else {
                    finalResult.add(msg)
                    i++
                }
            } else {
                finalResult.add(msg)
                i++
            }
        }

        return finalResult
    }

    companion object {
        /** Number of messages to load on initial page and each subsequent page. */
        const val INITIAL_PAGE_SIZE = 30

        /** Maximum offline queue size before rejecting sends (Requirement 1.7). */
        const val MAX_OFFLINE_QUEUE_SIZE = 50

        /** Maximum messages kept in memory; oldest evicted beyond this (Requirement 19.3). */
        const val MESSAGE_WINDOW_SIZE = 500

        /** Debounce interval for typing indicator state updates in groups (Requirement 19.5). */
        const val TYPING_INDICATOR_DEBOUNCE_MS = 300L

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

        /** Timeout for Socket.IO location update before Firestore fallback (Requirement 7.3). */
        const val LOCATION_CACHE_FALLBACK_TIMEOUT_MS = 10_000L

        /** Default live location sharing duration in minutes (Requirement 1.2). */
        const val DEFAULT_LIVE_LOCATION_DURATION_MINUTES = 60L

        /** Interval for updating the live location time remaining display (Requirement 1.4). */
        const val LOCATION_TIMER_UPDATE_INTERVAL_MS = 60_000L

        /** Duration of the context menu fade-out animation (Requirement 9.11). */
        const val CONTEXT_MENU_FADE_MS = 200L

        /** Duration of the delete error snackbar (Requirement 9.8). */
        const val DELETE_ERROR_SNACKBAR_MS = 4000L

        /** Debounce delay for message search (Requirement 13.2). */
        const val SEARCH_DEBOUNCE_MS = 300L

        /** Minimum characters required to trigger a search query (Requirement 13.2). */
        const val SEARCH_MIN_CHARS = 2

        /** Maximum characters allowed in the search input (Requirement 13.1). */
        const val SEARCH_MAX_CHARS = 100

        /** Duration to show "Hold to record" tooltip in milliseconds (Requirement 11.5). */
        const val HOLD_TO_RECORD_TOOLTIP_MS = 2_000L

        /** Debounce delay for mention suggestion updates (Requirement 14.1: within 300ms). */
        const val MENTION_DEBOUNCE_MS = 300L

        /** Maximum time difference (ms) between consecutive messages to form a group (Requirements 4.6, 4.7). */
        const val MESSAGE_GROUP_THRESHOLD_MS = 120_000L

        /** Minimum time gap (ms) between groups to show a timestamp (Messenger-style: 5 minutes). */
        const val TIMESTAMP_DISPLAY_THRESHOLD_MS = 300_000L

        /**
         * Formats the remaining time for the live location sharing banner.
         *
         * Requirement 1.4: "Xh Ym" when >= 60 minutes, "Xm" when < 60 minutes.
         *
         * @param remainingMinutes The remaining time in minutes.
         * @return Formatted string like "1h 30m" or "45m".
         */
        fun formatTimeRemaining(remainingMinutes: Long): String {
            return if (remainingMinutes >= 60) {
                val hours = remainingMinutes / 60
                val minutes = remainingMinutes % 60
                if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            } else {
                "${remainingMinutes}m"
            }
        }

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
    /**
     * Pre-computed grouped messages by dateKey for the LazyColumn.
     * Requirement 17.7 / 19.1: No inline collection grouping/sorting/formatting in items block.
     * Each entry is a pair of (dateKey, list of messages for that date).
     */
    val groupedMessages: List<Pair<String, List<MessageUiModel>>> = emptyList(),
    /**
     * ID of the message whose timestamp is currently revealed (tap-to-show).
     * Messenger hides timestamps by default; tapping a bubble reveals it.
     * Null means no message has its timestamp expanded.
     */
    val tappedMessageId: String? = null,
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
    val isOtherUserFriend: Boolean = false,
    // ─── Aggressive Local Caching State (Task 2.4) ────────────────────────────
    /** Cached locations from Room, served within 100ms of screen open (Requirement 7.2). */
    val cachedLocations: List<SharedLocation> = emptyList(),
    /** Whether the device is currently offline (Requirement 7.5). */
    val isOffline: Boolean = false,
    /** Whether to show the "Queued for sync" banner for offline write actions (Requirement 7.6). */
    val showQueuedForSyncBanner: Boolean = false,
    // ─── Quick Location Actions State (Task 3.5) ─────────────────────────────
    /** Number of members actively sharing location in this conversation (Requirements 4.1, 4.3). */
    val activeLocationSharingCount: Int = 0,
    /** Whether the other user in a 1:1 conversation is sharing their location (Requirement 4.5). */
    val isOtherUserSharingLocation: Boolean = false,
    // ─── Member Online Count State (Task 10.2) ────────────────────────────────
    /** Number of group members currently online, excluding the current user (Requirement 16.1). */
    val onlineMemberCount: Int = 0,
    // ─── Live Location Sharing State (Task 3.2) ───────────────────────────────
    /** Current state of the location bottom sheet (Requirement 1.1). */
    val locationBottomSheetState: LocationBottomSheetState = LocationBottomSheetState.HIDDEN,
    /** Whether to show the "Share Live Location" option (hidden for 1:1 direct, Requirement 1.12). */
    val showLiveLocationOption: Boolean = false,
    /** Selected duration in minutes for live location sharing (Requirement 1.2). */
    val selectedDurationMinutes: Long = 60L,
    /** Whether live location sharing is currently active (Requirement 1.4). */
    val isLiveLocationSharingActive: Boolean = false,
    /** Timestamp when the live location sharing session expires. */
    val liveLocationExpiresAt: Long? = null,
    /** Formatted time remaining for the live location banner (Requirement 1.4). */
    val liveLocationTimeRemaining: String? = null,
    /** Whether the UI should request live location permissions (Requirement 1.8). */
    val liveLocationPermissionNeeded: Boolean = false,
    /** Dismissible error message for live location sharing (Requirements 1.10, 1.11). */
    val liveLocationError: String? = null,
    // ─── Context Menu State (Task 4.2) ────────────────────────────────────────
    /** The message currently shown in the context menu (null when menu is hidden). */
    val contextMenuMessage: MessageUiModel? = null,
    /** Whether the delete confirmation dialog is visible. */
    val showDeleteConfirmation: Boolean = false,
    /** Whether the context menu is in the process of fading out (200ms). */
    val isContextMenuFadingOut: Boolean = false,
    // ─── Reaction Picker State (Task 19.1) ────────────────────────────────────
    /** Whether the reaction picker overlay is visible (Requirement 27.1). */
    val showReactionPicker: Boolean = false,
    /** The message ID for which the reaction picker is shown. */
    val reactionPickerMessageId: String? = null,
    /** When non-null, a bottom sheet listing who reacted to this message is shown. */
    val reactionDetailsMessageId: String? = null,
    /** When non-null, a user details bottom sheet is shown for this sender ID. */
    val avatarSheetUserId: String? = null,
    // ─── Message Search State (Task 8.1) ──────────────────────────────────────
    /** Whether the search bar is currently active/visible (Requirement 13.1). */
    val isSearchActive: Boolean = false,
    /** Current search query text (max 100 chars, Requirement 13.1). */
    val searchQuery: String = "",
    /** List of message IDs matching the search query, ordered oldest first (Requirement 13.3). */
    val searchResultIds: List<String> = emptyList(),
    /** Current index within searchResultIds that is focused (-1 if no results, Requirement 13.4). */
    val currentSearchResultIndex: Int = -1,
    // ─── Voice Recording State (Task 6.1) ─────────────────────────────────────
    /** Whether voice recording is currently active (Requirement 11.1). */
    val isVoiceRecording: Boolean = false,
    /** Current recording duration in milliseconds (Requirement 11.1). */
    val voiceRecordingDurationMs: Long = 0L,
    /** Normalized waveform amplitudes for visualization (Requirement 11.1). */
    val voiceWaveformAmplitudes: List<Float> = emptyList(),
    /** Whether recording is locked in hands-free mode (Requirement 11.3). */
    val isVoiceRecordingLocked: Boolean = false,
    /** Whether to show "Hold to record" tooltip (Requirement 11.5). */
    val showHoldToRecordTooltip: Boolean = false,
    /** Whether microphone permission is needed (Requirement 11.11). */
    val microphonePermissionNeeded: Boolean = false,
    /** Error message for voice recording (Requirement 11.11). */
    val voiceRecordingError: String? = null,
    // ─── Admin Overflow Menu State (Task 10.1) ────────────────────────────────
    /** Whether the current user is an admin of the active group conversation (Requirement 15.1). */
    val isCurrentUserAdmin: Boolean = false,
    /** Group members for the member picker dialog (excludes current user, Requirement 15.2). */
    val groupMembersForPicker: List<GroupMemberPickerItem> = emptyList(),
    /** Whether the member picker dialog is visible (Requirement 15.2). */
    val showMemberPickerDialog: Boolean = false,
    /** Whether the overflow menu is expanded. */
    val showAdminOverflowMenu: Boolean = false,
    // ─── Mention State (Task 9.1) ─────────────────────────────────────────────
    /** Whether the mention suggestion popup is visible (Requirement 14.1). */
    val isMentionPopupVisible: Boolean = false,
    /** List of member suggestions for the mention popup (Requirements 14.1, 14.5, 14.6). */
    val mentionSuggestions: List<MentionEngine.MentionMember> = emptyList(),
    /** List of mentioned user IDs for the current message being composed (Requirement 14.3). */
    val mentionedUserIds: List<String> = emptyList(),
    /** Character ranges of mention tokens in the input text for blue styling. */
    val mentionRanges: List<IntRange> = emptyList(),
    // ─── Image Size Error State ───────────────────────────────────────────────
    /** Whether to show the image size limit error inline (Requirement 6.7). */
    val showImageSizeError: Boolean = false,
    // ─── Mini Map Overlay State ───────────────────────────────────────────────
    /** Whether the mini-map overlay is visible on top of the chat. */
    val showMiniMap: Boolean = false
)

/**
 * States for the location sharing bottom sheet.
 *
 * Requirement 1.1: Bottom sheet with "Share Current Location" and "Share Live Location".
 * Requirement 1.2: Duration picker with 15min, 1h, 2h, 4h, 8h options.
 */
enum class LocationBottomSheetState {
    /** Bottom sheet is not visible. */
    HIDDEN,
    /** Showing options: "Share Current Location" and "Share Live Location". */
    OPTIONS,
    /** Showing duration picker for live location sharing. */
    DURATION_PICKER
}

/**
 * Simplified member representation for the mute member picker dialog.
 *
 * Requirement 15.2: Member picker lists all group members except the current user.
 */
data class GroupMemberPickerItem(
    val userId: String,
    val displayName: String
)
