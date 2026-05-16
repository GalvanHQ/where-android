# Design Document: Chat and Group Overhaul

## Overview

This design extends the WHERE app's chat system beyond the foundational `chat-screen-redesign` spec (which delivered Room schema, Socket.IO client, MessageRepository, ConversationRepository, and basic ViewModel scaffolding). The overhaul adds five major capability areas:

1. **Live Location Sharing in Chat** — Start/stop sharing from the input bar, render real-time updating LiveLocationBubbles, surface sharing status in conversation list and headers.
2. **Advanced Chat Interactions** — Swipe-to-reply, long-press context menu, message status indicators, voice messages, link previews, in-conversation search, and @mentions.
3. **Firestore Read Optimization** — Batched queries, Socket.IO for ephemeral state, SnapshotDebouncer, aggressive Room caching, and staleness-based re-reads.
4. **Group Enhancements** — Admin controls from chat header, member online count, and group media gallery.
5. **Performance Optimization** — Compose stability annotations, image caching strategy, LazyColumn tuning, lazy initialization, memory management, WorkManager background sync, and message animations.

The app uses Kotlin, Jetpack Compose, MVVM + Clean Architecture, Hilt DI, Room, Firebase Firestore, and Socket.IO. This design preserves those layers and extends them.

---

## 1. Architecture Overview

### 1.1 Layer Responsibilities

```
┌─────────────────────────────────────────────────────────┐
│  Presentation Layer                                      │
│  ChatScreen, ChatsScreen, MediaGalleryScreen             │
│  ChatViewModel, ChatsViewModel                           │
│  UI Models: MessageUiModel (@Immutable),                 │
│             ConversationUiModel (@Immutable)              │
├─────────────────────────────────────────────────────────┤
│  Domain Layer                                            │
│  Use Cases: StartLocationSharingUseCase,                 │
│             StopLocationSharingUseCase,                   │
│             SearchMessagesUseCase,                        │
│             FetchLinkPreviewUseCase,                      │
│             SendVoiceMessageUseCase,                      │
│             ForwardMessageUseCase,                        │
│             DeleteMessageUseCase                          │
│  Repository Interfaces (unchanged contracts)             │
├─────────────────────────────────────────────────────────┤
│  Data Layer                                              │
│  LocationRepositoryImpl (Socket.IO + Firestore fallback) │
│  ConversationRepositoryImpl (batched queries, debouncer) │
│  MessageRepositoryImpl (link preview, voice, forward)    │
│  ChatSocketIoClient (location_update, voice events)      │
│  SnapshotDebouncer, ImageCacheManager                    │
│  Room: SharedLocationEntity, VoiceMessageEntity          │
│  WorkManager: BackgroundSyncWorker                        │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Socket.IO for ephemeral state, Firestore for durable state | Eliminates Firestore reads for typing, presence, reactions, read receipts (Req 6) |
| SnapshotDebouncer (500ms window) | Coalesces rapid Firestore emissions into single Room writes (Req 5.6) |
| Room as single source of truth for UI | All UI flows read from Room; network updates write to Room in background (Req 7.1) |
| @Immutable on UI models | Compose skips recomposition of unchanged items (Req 17.1, 17.2) |
| Lazy Hilt providers for chat singletons | Keeps startup path free of chat initialization (Req 20.1, 20.4) |
| LocationRepository dual-source (Socket.IO primary, Firestore fallback after 30s) | Minimizes Firestore reads while ensuring reliability (Req 6.4, 6.5) |
| Message window of 500 with eviction | Prevents OOM in long conversations while preserving pagination cursor (Req 19.3) |

---

## 2. Data Layer Design

### 2.1 Domain Model Extensions

```kotlin
// Extend MessageType enum
enum class MessageType { TEXT, LOCATION, IMAGE, SYSTEM, VOICE, LIVE_LOCATION }

// Extend MessageStatus enum
enum class MessageStatus { PENDING, SENT, DELIVERED, READ, FAILED }

// New fields on Message domain model
data class Message(
    // ... existing fields ...
    // Voice message
    val voiceUrl: String? = null,
    val voiceDurationMs: Long? = null,
    // Link preview
    val linkPreviewTitle: String? = null,
    val linkPreviewDescription: String? = null,
    val linkPreviewImageUrl: String? = null,
    val linkPreviewDomain: String? = null,
    val linkPreviewUrl: String? = null,
    // Mentions
    val mentionedUserIds: List<String> = emptyList(),
    // Live location
    val locationSharingSessionId: String? = null,
    val locationSharingDurationMinutes: Long? = null,
    // Forward
    val forwardedFrom: String? = null
)

// SharedLocation gets a displayName for bubble rendering
data class SharedLocation(
    // ... existing fields ...
    val displayName: String = "",
    val sharingStartedAt: Long = 0L
)
```

### 2.2 Room Schema Extensions

**New Entity: `VoiceMessageCacheEntity`**
```kotlin
@Entity(tableName = "voice_message_cache")
data class VoiceMessageCacheEntity(
    @PrimaryKey val messageId: String,
    val localFilePath: String,
    val durationMs: Long,
    val downloadedAt: Long
)
```

**MessageEntity additions:**
- `voiceUrl`, `voiceDurationMs`, `linkPreviewTitle`, `linkPreviewDescription`, `linkPreviewImageUrl`, `linkPreviewDomain`, `linkPreviewUrl`, `mentionedUserIdsJson`, `locationSharingSessionId`, `locationSharingDurationMinutes`, `forwardedFrom`

**SharedLocationEntity additions:**
- `displayName`, `sharingStartedAt`

**New Entity: `LinkPreviewCacheEntity`**
```kotlin
@Entity(tableName = "link_preview_cache")
data class LinkPreviewCacheEntity(
    @PrimaryKey val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val domain: String,
    val fetchedAt: Long
)
```

### 2.3 SnapshotDebouncer

```kotlin
@Singleton
class SnapshotDebouncer @Inject constructor(
    private val scope: CoroutineScope
) {
    private val pendingWrites = ConcurrentHashMap<String, Any>()
    private var debounceJob: Job? = null

    fun <T> submit(documentId: String, data: T, writer: suspend (Map<String, T>) -> Unit) {
        pendingWrites[documentId] = data as Any
        if (debounceJob == null || debounceJob?.isActive != true) {
            debounceJob = scope.launch {
                delay(500L) // 500ms fixed window
                val batch = HashMap(pendingWrites)
                pendingWrites.clear()
                @Suppress("UNCHECKED_CAST")
                writer(batch as Map<String, T>)
            }
        }
    }
}
```

### 2.4 ConversationRepository Enhancements

**Batched Queries (Req 5.1, 5.2):**
```kotlin
// Chunk conversation IDs into groups of 30 for whereIn queries
suspend fun batchFetchConversations(conversationIds: List<String>) {
    conversationIds.chunked(30).forEach { chunk ->
        firestore.collection("conversations")
            .whereIn(FieldPath.documentId(), chunk)
            .get()
            .await()
            .documents
            .map { it.toConversation() }
            .let { conversationDao.upsertAll(it.map { c -> c.toEntity() }) }
    }
}
```

**Version-based skip (Req 5.7):**
- Store `documentUpdateTime` per conversation in Room
- On snapshot: compare incoming `updateTime` with stored; skip write if equal

**Staleness check (Req 7.4):**
- `lastSyncTimestamp` per conversation in Room
- Skip Firestore re-read if < 5 minutes old
- Trigger background re-read when >= 5 minutes

### 2.5 LocationRepository Enhancements

**Single collection-level query (Req 5.5):**
```kotlin
override fun observeActiveLocations(): Flow<List<SharedLocation>> = callbackFlow {
    val uid = currentUid ?: return@callbackFlow
    val registration = firestore.collection("activeLocations")
        .whereArrayContains("visibleTo", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val locations = snapshot?.documents?.map { it.toSharedLocation() } ?: emptyList()
            trySend(locations)
        }
    awaitClose { registration.remove() }
}
```

**Socket.IO primary, Firestore fallback (Req 6.4, 6.5):**
```kotlin
fun observeLocationsWithFallback(): Flow<List<SharedLocation>> = flow {
    val socketFlow = wsClient.incomingFrames
        .filterIsInstance<ServerFrame.LocationUpdate>()
        .map { it.toSharedLocation() }

    val firestoreFlow = observeActiveLocations()

    // Use Socket.IO while connected; fall back to Firestore after 30s disconnect
    wsClient.connectionState.collectLatest { state ->
        when (state) {
            CONNECTED -> socketFlow.collect { emit(it) }
            else -> {
                delay(30_000)
                if (wsClient.connectionState.value != CONNECTED) {
                    firestoreFlow.collect { emit(it) }
                }
            }
        }
    }
}
```

### 2.6 ChatSocketIoClient Extensions

New events to handle:
- `"location_update"` — real-time coordinate updates for active sharing sessions
- `"voice_message"` — incoming voice message notification
- `"mention"` — push notification trigger for @mentioned users
- `"delete_message"` — message deletion broadcast
- `"forward"` — forwarded message delivery

New ServerFrame types:
```kotlin
@Serializable data class LocationUpdate(val userId: String, val lat: Double, val lng: Double, val accuracy: Float, val timestamp: Long) : ServerFrame()
@Serializable data class MessageDeleted(val messageId: String, val conversationId: String) : ServerFrame()
```

### 2.7 Background Sync with WorkManager (Req 22)

```kotlin
@HiltWorker
class BackgroundSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val conversationRepository: ConversationRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!authRepository.isAuthenticated()) return Result.failure()
        return try {
            val result = conversationRepository.syncUnreadCounts()
            if (result is Resource.Error) Result.retry() else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
```

Schedule: periodic 15-minute interval, network constraint required, exponential backoff (1min initial, 30min max).

---

## 3. Presentation Layer Design

### 3.1 ChatViewModel Extensions

**Live Location State:**
```kotlin
data class ChatUiState(
    // ... existing fields ...
    // Live location
    val isLocationSharingActive: Boolean = false,
    val locationSharingTimeRemaining: String? = null,
    val activeLocationSharers: List<LocationSharerUiModel> = emptyList(),
    // Context menu
    val contextMenuMessage: MessageUiModel? = null,
    // Search
    val searchQuery: String = "",
    val searchResults: List<Int> = emptyList(), // indices into messages
    val currentSearchIndex: Int = -1,
    val isSearchActive: Boolean = false,
    // Voice recording
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val isRecordingLocked: Boolean = false,
    // Mention
    val mentionSuggestions: List<GroupMemberUiModel> = emptyList(),
    val isMentionPopupVisible: Boolean = false
)
```

**New ViewModel methods:**
- `startLiveLocationSharing(durationMinutes: Long)`
- `stopLiveLocationSharing()`
- `showContextMenu(message: MessageUiModel)`
- `dismissContextMenu()`
- `copyMessage(messageId: String)`
- `deleteMessage(messageId: String)`
- `forwardMessage(messageId: String, targetConversationIds: List<String>)`
- `searchMessages(query: String)`
- `navigateSearchResult(direction: SearchDirection)`
- `dismissSearch()`
- `startVoiceRecording()`
- `stopVoiceRecording(send: Boolean)`
- `lockVoiceRecording()`
- `cancelVoiceRecording()`
- `onMentionTrigger(query: String)`
- `selectMention(userId: String, displayName: String)`
- `dismissMentionPopup()`

### 3.2 Compose Stability Strategy (Req 17)

```kotlin
@Immutable
data class MessageUiModel(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val text: String,
    val type: MessageType,
    val formattedTime: String,
    val dateKey: String,
    val direction: BubbleDirection,
    val status: MessageStatus,
    val reactions: ImmutableMap<String, ImmutableList<String>>,
    val readBy: ImmutableList<String>,
    val replyToSenderName: String?,
    val replyToText: String?,
    // ... all val, all immutable types
)

@Immutable
data class ConversationUiModel(
    val id: String,
    val title: String,
    val photoUrl: String?,
    val lastMessagePreview: String,
    val formattedTimestamp: String,
    val unreadCount: Int,
    val isOnline: Boolean,
    val isGroup: Boolean,
    val isMuted: Boolean,
    val isPinned: Boolean,
    val hasActiveLocationSharing: Boolean,
    val locationSharingPreview: String?
)
```

### 3.3 LazyColumn Optimization (Req 19)

```kotlin
// ChatScreen message list
LazyColumn(
    state = listState,
    reverseLayout = true
) {
    items(
        items = messages,
        key = { it.id },
        contentType = { msg ->
            when {
                msg.type == MessageType.LIVE_LOCATION -> "live_location_bubble"
                msg.type == MessageType.VOICE -> "voice_bubble"
                msg.type == MessageType.IMAGE -> "image_bubble"
                msg.type == MessageType.SYSTEM -> "system_message"
                else -> "text_bubble"
            }
        }
    ) { message ->
        MessageBubble(
            message = message,
            onLongPress = remember { { showContextMenu(message) } },
            onSwipeToReply = remember { { setReplyingTo(message) } },
            onReactionTap = remember { { emoji -> toggleReaction(message.id, emoji) } },
            onLocationTap = remember { { navigateToGroupMap() } },
            onRetry = remember { { retryMessage(message.id) } }
        )
    }

    // Date separators as separate content type
    // Typing indicator as separate content type
    // Pagination indicator as separate content type
}
```

### 3.4 ImageCacheManager (Req 18)

```kotlin
@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // 25% of available heap
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250MB
                    .build()
            }
            .crossfade(200)
            .components {
                add(FirebaseStorageFetcher.Factory())
            }
            .build()
    }
}
```

### 3.5 Swipe-to-Reply Gesture (Req 8)

```kotlin
@Composable
fun SwipeToReplyContainer(
    onReply: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val threshold = 48.dp.toPx()
    val maxDrag = 100.dp.toPx()

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { /* suppress vertical scroll */ },
                    onDrag = { change, dragAmount ->
                        val newOffset = (offsetX.value + dragAmount).coerceIn(0f, maxDrag)
                        scope.launch { offsetX.snapTo(newOffset) }
                    },
                    onDragEnd = {
                        if (offsetX.value >= threshold) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReply()
                        }
                        scope.launch { offsetX.animateTo(0f, tween(200)) }
                    }
                )
            }
    ) {
        // Reply arrow icon with proportional fade
        Icon(
            imageVector = Icons.Default.Reply,
            alpha = (offsetX.value / threshold).coerceIn(0f, 1f)
        )
        // Message content offset
        Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }
    }
}
```

### 3.6 Context Menu (Req 9)

```kotlin
@Composable
fun MessageContextMenu(
    message: MessageUiModel,
    currentUserId: String,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onReact: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onDismiss: () -> Unit
) {
    // 40% opacity black scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss)
    ) {
        // Anchored menu with actions
        Column {
            if (message.type == MessageType.TEXT) {
                ContextMenuItem("Copy", Icons.Default.ContentCopy, onCopy)
            }
            ContextMenuItem("Reply", Icons.Default.Reply, onReply)
            ContextMenuItem("React", Icons.Default.EmojiEmotions, onReact)
            if (message.senderId == currentUserId) {
                ContextMenuItem("Delete", Icons.Default.Delete, onDelete)
            } else {
                ContextMenuItem("Delete for me", Icons.Default.DeleteOutline, onDelete)
            }
            ContextMenuItem("Forward", Icons.Default.Forward, onForward)
        }
    }
}
```

### 3.7 LiveLocationBubble (Req 1, 2)

```kotlin
@Composable
fun LiveLocationBubble(
    sharedLocation: SharedLocation,
    isActive: Boolean,
    timeRemaining: String?,
    lastUpdateAgo: String?,
    onTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(200.dp, 120.dp)
            .clickable(onClick = onTap)
    ) {
        Box {
            // Mini Google Map composable showing marker at lat/lng
            MiniMapPreview(
                latitude = sharedLocation.latitude,
                longitude = sharedLocation.longitude
            )
            // Overlay: status text
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(
                    text = if (isActive) "Sharing live location" else "Location sharing ended",
                    style = MaterialTheme.typography.labelSmall
                )
                if (isActive && lastUpdateAgo != null) {
                    Text("Updated $lastUpdateAgo", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
```

### 3.8 Voice Message UI (Req 11)

**Recording state:** Waveform visualization + elapsed time + slide-to-cancel hint + lock icon.

**Playback bubble:**
```kotlin
@Composable
fun VoiceMessageBubble(
    durationMs: Long,
    isPlaying: Boolean,
    progress: Float, // 0.0 to 1.0
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlayPause) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow)
        }
        Slider(value = progress, onValueChange = onSeek, modifier = Modifier.weight(1f))
        Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
    }
}
```

### 3.9 MentionEngine (Req 14)

```kotlin
class MentionEngine(
    private val groupMembers: List<GroupMember>,
    private val currentUserId: String
) {
    fun getSuggestions(query: String): List<GroupMember> {
        val filtered = groupMembers
            .filter { it.userId != currentUserId }
            .filter { it.displayName.startsWith(query, ignoreCase = true) }
            .sortedBy { it.displayName.lowercase() }
        return if (groupMembers.size > 10) filtered.take(5) else filtered
    }

    fun insertMention(inputText: String, cursorPosition: Int, member: GroupMember): MentionResult {
        // Replace @query with styled mention token
        // Return new text, new cursor position, mentioned userId
    }
}
```

### 3.10 Message Search (Req 13)

```kotlin
// In ChatViewModel
fun searchMessages(query: String) {
    if (query.length < 2) {
        _uiState.value = _uiState.value.copy(searchResults = emptyList(), currentSearchIndex = -1)
        return
    }
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
        delay(300) // debounce
        val results = messageDao.searchMessages(conversationId, "%$query%")
        _uiState.value = _uiState.value.copy(
            searchResults = results.map { it.id },
            currentSearchIndex = if (results.isNotEmpty()) 0 else -1
        )
    }
}
```

---

## 4. Firestore Read Budget Analysis

| Operation | Before (reads) | After (reads) | Savings |
|-----------|---------------|---------------|---------|
| Open conversation list (30 convos) | 30 individual docs | 1 whereIn query | ~97% |
| Presence updates (per minute) | 1 read per user per update | 0 (Socket.IO) | 100% |
| Typing indicators | 1 read per event | 0 (Socket.IO) | 100% |
| Reaction updates | 1 read per reaction | 0 (Socket.IO) | 100% |
| Read receipts | 1 read per receipt | 0 (Socket.IO) | 100% |
| Location updates (active sharing) | 1 read per 15s per sharer | 0 (Socket.IO, Firestore fallback only after 30s disconnect) | ~95% |
| Foreground sync (unread counts) | N individual reads | 1 REST API call | ~97% |
| Rapid snapshot bursts (10 updates in 500ms) | 10 Room writes | 1 Room write | 90% |

---

## 5. Performance Targets

| Metric | Target | Mechanism |
|--------|--------|-----------|
| Frame render time (500+ messages, fling scroll) | <= 16ms (60fps) | @Immutable models, stable keys, contentType, pre-computed formatting |
| Conversation list first paint (cached) | <= 200ms | Room as source of truth, lazy chat singleton init |
| Location cache serve time | <= 100ms | Room SharedLocationEntity cache |
| App first frame | <= 500ms | Lazy Hilt providers for chat singletons |
| Image memory cache | 25% heap | Coil MemoryCache configuration |
| Image disk cache | 250MB | Coil DiskCache configuration |
| Message window | 500 messages max in memory | Evict oldest beyond window, preserve pagination cursor |
| Typing indicator debounce | 300ms per recomposition | Debounce state updates in ViewModel |

---

## 6. Lifecycle and Memory Management (Req 21)

### 6.1 Coroutine Cancellation

- `ChatViewModel`: All coroutines in `viewModelScope` — cancelled on `onCleared()`
- `ChatSocketIoClient`: `SupervisorJob`-based scope — cancelled on logout or process death
- `LocationTrackingService`: `serviceScope` with `SupervisorJob` — cancelled on `onDestroy()`

### 6.2 App Background/Foreground Transitions

```
onStop (isChangingConfigurations == false):
  → ChatSocketIoClient.disconnect() within 5s
  → ConversationRepository removes Firestore snapshot listener

onStart:
  → ChatSocketIoClient.reconnect() within 2s
  → ConversationRepository re-registers Firestore snapshot listener
  → If reconnection doesn't reach CONNECTED within 2s, continue with exponential backoff
```

### 6.3 SharedFlow Configuration

```kotlin
// One-shot UI events (snackbar, navigation)
private val _events = MutableSharedFlow<UiEvent>(replay = 0, extraBufferCapacity = 1)
```
- `replay = 0`: No replay on configuration change
- `extraBufferCapacity >= 1`: Prevents suspension on emit

---

## 7. New Use Cases

| Use Case | Input | Output | Repository |
|----------|-------|--------|------------|
| StartLocationSharingUseCase | groupId, durationMinutes | Resource<Unit> | LocationRepository |
| StopLocationSharingUseCase | groupId | Resource<Unit> | LocationRepository |
| FetchLinkPreviewUseCase | url | Resource<LinkPreview> | ChatApiService (REST) |
| SendVoiceMessageUseCase | conversationId, audioUri | Resource<Message> | MessageRepository |
| DeleteMessageUseCase | conversationId, messageId | Resource<Unit> | MessageRepository |
| ForwardMessageUseCase | messageId, targetConversationIds | Resource<Unit> | MessageRepository |
| SearchMessagesUseCase | conversationId, query | List<Message> | MessageDao (Room) |
| MuteGroupMemberUseCase | groupId, memberId | Resource<Unit> | GroupRepository |

---

## 8. Navigation Changes

New routes added to `Screen` sealed class:
- `Screen.MediaGallery(conversationId: String)` — media/files grid for a conversation
- `Screen.ConversationPicker(messageId: String)` — forward target selection (max 5)

Existing routes used:
- `Screen.GroupMap(groupId: String)` — navigate from LiveLocationBubble tap or header action
- `Screen.EditGroup(groupId: String)` — navigate from admin "Group Settings" action

---

## 9. Accessibility

- All message status indicators include content descriptions: "Message pending", "Message sent", "Message delivered", "Message read", "Message failed"
- Voice message play/pause buttons: "Play voice message" / "Pause voice message"
- Swipe-to-reply: accessible via long-press context menu "Reply" action as alternative
- LiveLocationBubble: content description includes sharer name and sharing status
- Reduced motion: skip all slide/scale animations when system accessibility setting enabled (Req 23.7)
- Context menu actions: each item has descriptive content description

---

## 10. Error Handling Strategy

| Scenario | Behavior |
|----------|----------|
| Location permission denied | Dismissible message, no session started (Req 1.10) |
| StartLocationSharing fails | Dismiss bottom sheet, show error message (Req 1.11) |
| Link preview API timeout (5s) | Send message without preview, render URL as hyperlink (Req 12.3) |
| Delete message API fails | Dismiss dialog, retain message, error snackbar 4s (Req 9.8) |
| Voice recording < 1s | Discard, show "Hold to record" tooltip 2s (Req 11.5) |
| Mute member API fails | Error snackbar, no state change (Req 15.3) |
| Invite link retrieval fails | Error snackbar, no clipboard copy (Req 15.6) |
| Background sync auth error | Cancel periodic schedule, no retry until re-auth (Req 22.7) |
| Offline write action | Non-modal 48dp banner "Queued for sync" (Req 7.6) |
| REST missed-messages fetch fails | Retain Room cache, show inline retry (Req 6.7) |

---

## 11. Testing Strategy

### Property-Based Tests

| Property | What it validates | Requirement |
|----------|-------------------|-------------|
| SnapshotDebouncer coalesces N updates into 1 write within 500ms | Batching correctness | Req 5.6 |
| Message window never exceeds 500 items | Memory bound | Req 19.3 |
| Typing indicator shows at most 2 names + count | Format correctness | Req 26.3 |
| Conversation batching produces ceil(N/30) queries | Chunking correctness | Req 5.1, 5.2 |
| Location update throttle: max 1 per 15s per user | Rate limiting | Req 2.2 |
| Mention suggestions: max 5 when group > 10 members | Limit enforcement | Req 14.5 |
| Swipe threshold: reply only triggered at >= 48dp | Gesture precision | Req 8.1 |
| Voice recording: discard if < 1s, send if >= 1s | Duration validation | Req 11.4, 11.5 |

### Integration Tests

- Message send → ack → status indicator transition
- Live location start → bubble render → coordinate update → session end
- Context menu → delete → confirmation → API call → message removal
- Search → highlight → navigate results → dismiss → restore scroll
- Background sync → Room update → UI reflects new unread counts
- Socket disconnect → 30s → Firestore fallback activates → socket reconnect → Firestore listener removed


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Duration Formatting Correctness

*For any* non-negative duration in minutes, the formatting function SHALL produce "Xh Ym" when the duration is 60 minutes or more, and "Xm" when the duration is under 60 minutes, where X and Y are the correct hour and minute components.

**Validates: Requirements 1.4, 2.6**

### Property 2: Location Update Throttling

*For any* sequence of location coordinate updates with timestamps, the LiveLocationBubble SHALL process at most one update per 15-second window, discarding intermediate updates and retaining only the most recent coordinate within each window.

**Validates: Requirement 2.2**

### Property 3: Active Sharers Ordering

*For any* set of active location sharing sessions in a conversation, the displayed LiveLocationBubbles SHALL be ordered by session start time in ascending order (earliest first), regardless of the order in which updates arrive.

**Validates: Requirement 2.4**

### Property 4: Multiple Sharers Preview Format

*For any* conversation with N > 1 other users sharing location (where the current user is not sharing), the preview text SHALL be formatted as "📍 {displayName} and {N-1} others sharing location" where displayName is the most recently started sharer's name.

**Validates: Requirement 3.4**

### Property 5: Group Header Subtitle Format

*For any* group conversation with totalCount members and onlineCount online members (excluding current user), the subtitle SHALL be formatted as "{totalCount} members · {onlineCount} online" when onlineCount > 0, and "{totalCount} members" when onlineCount == 0. When sharingCount > 0, the format SHALL be "{totalCount} members · {sharingCount} sharing location".

**Validates: Requirements 4.3, 4.4, 16.1, 16.3**

### Property 6: Conversation ID Batched Query Chunking

*For any* list of N conversation IDs, the batched query function SHALL produce exactly ceil(N/30) chunks, each containing at most 30 IDs, with all original IDs present exactly once across all chunks and no chunk being empty.

**Validates: Requirements 5.1, 5.2**

### Property 7: Snapshot Debouncer Coalescing

*For any* sequence of document updates arriving within a 500ms window (measured from the first update), the SnapshotDebouncer SHALL produce exactly one Room write operation containing the latest state of each affected document, with no intermediate states persisted.

**Validates: Requirement 5.6**

### Property 8: Version-Based Write Idempotency

*For any* incoming Firestore document snapshot, if the document's updateTime matches the version already stored in Room for that document ID, the repository SHALL skip the Room write; if the versions differ, the repository SHALL perform the write.

**Validates: Requirement 5.7**

### Property 9: Cache Staleness Read Skip

*For any* conversation with a lastSyncTimestamp, if the elapsed time since lastSyncTimestamp is less than 5 minutes, the repository SHALL NOT trigger a Firestore re-read for that conversation; if 5 minutes or more have elapsed, it SHALL trigger a background re-read.

**Validates: Requirement 7.4**

### Property 10: Context Menu Action Visibility

*For any* message in the context menu, the "Copy" action SHALL be visible if and only if the message type is TEXT, the "Delete" action SHALL be visible if and only if the message sender is the current user, and "Delete for me" SHALL be visible if and only if the message sender is NOT the current user.

**Validates: Requirements 9.4, 9.7, 9.9**

### Property 11: Message Status Icon Mapping

*For any* message sent by the current user, the MessageStatusIndicator SHALL display: clock icon in onSurfaceVariant for PENDING, single tick in onSurfaceVariant for SENT, double tick in onSurfaceVariant for DELIVERED, double tick in primary color for READ, and error icon in error color for FAILED. No indicator SHALL be displayed for received messages.

**Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.6, 10.7**

### Property 12: Voice Message Send Duration Threshold

*For any* voice recording with duration D milliseconds, the system SHALL send the voice message if D >= 1000ms and SHALL discard the recording if D < 1000ms, leaving the message list unchanged in the discard case.

**Validates: Requirements 11.4, 11.5**

### Property 13: Voice Playback Mutual Exclusion

*For any* sequence of play actions on voice messages, at most one voice message SHALL be in the playing state at any time; starting playback of a new message SHALL stop the currently playing message and reset its progress bar.

**Validates: Requirement 11.10**

### Property 14: URL Detection and First-URL Preview

*For any* message text, all substrings matching the pattern `https?://[^\s]+` SHALL be detected as URLs, and a link preview card SHALL be rendered for only the first detected URL in the text, with subsequent URLs rendered as tappable hyperlinks without preview cards.

**Validates: Requirements 12.1, 12.5**

### Property 15: Message Search Substring Match

*For any* search query of 2 or more characters and any set of messages in a conversation, the search results SHALL contain exactly those messages whose text contains the query as a case-insensitive substring, and no messages whose text does not contain the query.

**Validates: Requirement 13.2**

### Property 16: Mention Suggestion Prefix Match with Cap

*For any* group member list and any query string typed after "@", the suggestion popup SHALL display members whose display name starts with the query (case-insensitive prefix match), excluding the current user, sorted by display name ascending, with at most 5 results shown regardless of how many members match.

**Validates: Requirements 14.1, 14.5, 14.6**

### Property 17: Mention Token Insertion

*For any* input text with cursor at position P after an "@" trigger and typed filter characters, selecting a member from the suggestion popup SHALL replace the "@" and all filter characters with the member's display name as a styled token, and place the cursor immediately after the inserted token.

**Validates: Requirement 14.2**

### Property 18: Mentioned User ID Deduplication

*For any* message containing mention tokens, the extracted mentionedUserIds array SHALL contain each distinct user ID exactly once, regardless of how many times that user is mentioned in the message text.

**Validates: Requirement 14.3**

### Property 19: Mention Token Atomic Removal

*For any* input text containing mention tokens, if the user deletes any character within a mention token's range, the entire token SHALL be removed from the text and the corresponding user ID SHALL be removed from the mentioned users list.

**Validates: Requirement 14.7**

### Property 20: Admin Overflow Menu Visibility

*For any* group conversation, the overflow menu icon with admin actions SHALL be visible if and only if the current user has the admin role for that group; non-admin users SHALL see only standard navigation actions.

**Validates: Requirements 15.1, 15.7**

### Property 21: Message Window Eviction

*For any* message list exceeding 500 messages, when new messages arrive the system SHALL evict the oldest messages beyond the 500-message window while preserving the pagination cursor, ensuring the list size never exceeds 500 in memory and evicted messages remain retrievable via backward pagination.

**Validates: Requirement 19.3**

### Property 22: Media Type Preview Mapping

*For any* conversation whose last message has a known media type, the preview text SHALL be: "📷 Photo" for IMAGE, "🎤 Voice message" for VOICE, "📍 Location" for LOCATION, "🎥 Video" for VIDEO, "📄 Document" for DOCUMENT; for TEXT or unknown types, the raw message text SHALL be displayed.

**Validates: Requirement 24.1**

### Property 23: Conversation List Status Indicator

*For any* conversation where the last message was sent by the current user, the conversation list SHALL display a status indicator matching the message's delivery status (single grey tick for SENT, double grey tick for DELIVERED, double blue tick for READ, red error icon for FAILED); no indicator SHALL be shown when the last message was sent by another user.

**Validates: Requirement 24.2**

### Property 24: Pinned Conversation Ordering with Limit

*For any* conversation list, all pinned conversations SHALL appear before all unpinned conversations, pinned conversations SHALL maintain timestamp sort order within the pinned group, and the system SHALL enforce a maximum of 3 pinned conversations — rejecting any pin attempt when 3 are already pinned.

**Validates: Requirement 24.4**

### Property 25: Muted Conversation Badge Hiding

*For any* muted conversation, the unread count badge SHALL NOT be displayed regardless of the actual unread count; unmuted conversations SHALL display the badge when unread count > 0.

**Validates: Requirement 24.5**

### Property 26: Live Location Option Visibility

*For any* conversation, the "Share Live Location" option SHALL be visible in the location bottom sheet if and only if the conversation has a non-null groupId; conversations without a groupId (1:1 direct) SHALL only show "Share Current Location".

**Validates: Requirement 1.12**

## Error Handling

### Network Errors

| Scenario | Behavior |
|----------|----------|
| Location sharing start fails | Dismiss bottom sheet, show dismissible error, do not start LocationTrackingService (Req 1.11) |
| Message delete API fails | Dismiss confirmation dialog, retain message, show error snackbar for 4s (Req 9.8) |
| Mute member API fails | Show error snackbar, member status unchanged (Req 15.3) |
| Invite link retrieval fails | Show error snackbar, do not copy to clipboard (Req 15.6) |
| Link preview API timeout (5s) | Send message without preview, render URL as tappable hyperlink (Req 12.3) |
| REST missed messages fetch fails | Retain Room-cached messages, emit recoverable error, retry on next connection (Req 6.7) |
| Background sync fails | WorkManager retries with exponential backoff (1min → 30min, max 5 attempts) (Req 22.3) |
| Background sync auth error | Cancel periodic schedule, do not retry until re-auth (Req 22.7) |
| Image load fails all layers | Display error placeholder, no auto-retry until scroll out/in or pull-to-refresh (Req 18.6) |
| Firebase Storage 403 | Refresh token and retry once; if still 403, permanent failure with placeholder (Req 18.5) |

### Offline Behavior

| Scenario | Behavior |
|----------|----------|
| Device offline, user views data | Display all cached data from Room without loading indicators (Req 7.5) |
| Device offline, user attempts write | Show non-modal offline banner (≤48dp) indicating action queued for sync (Req 7.6) |
| Offline queue full (50 messages) | Reject send, expose isOfflineQueueFull flag, do not clear input (Req 26.1) |
| Socket disconnected > 30s | LocationRepository falls back to Firestore listener (Req 6.5) |
| Socket reconnects | Disable Firestore fallback, resume Socket.IO for location (Req 6.4) |

### Permission Errors

| Scenario | Behavior |
|----------|----------|
| Location permission denied | Show dismissible message, do not start sharing session (Req 1.10) |
| Microphone permission denied | Do not record, show error indicating mic access required (Req 11.11) |

### Lifecycle Errors

| Scenario | Behavior |
|----------|----------|
| ChatScreen removed from back stack | Cancel all coroutines via viewModelScope, no leaks (Req 21.1) |
| User logs out | Cancel SupervisorJob scope, disconnect socket, reset typing manager (Req 21.2) |
| App backgrounded | Disconnect socket within 5s, remove Firestore listener (Req 21.4) |
| App foregrounded | Reconnect socket and re-register listener within 2s (Req 21.5) |
| Foreground reconnect timeout (2s) | Continue with exponential backoff, no crash (Req 21.6) |

## Testing Strategy

### Testing Approach

This feature uses a **dual testing approach**:

- **Property-based tests** (PBT): Verify universal correctness properties across randomized inputs. Each property test runs a minimum of 100 iterations.
- **Unit tests**: Verify specific examples, edge cases, error conditions, and integration points.
- **Integration tests**: Verify end-to-end flows involving multiple components (Socket.IO, Room, Firestore).

### Property-Based Testing Configuration

- **Library**: [Kotest Property Testing](https://kotest.io/docs/proptest/property-based-testing.html) (already available in the Kotlin ecosystem)
- **Minimum iterations**: 100 per property
- **Tag format**: `Feature: chat-and-group-overhaul, Property {number}: {title}`

### Property Test Coverage

| Property | Component Under Test | Generator Strategy |
|----------|---------------------|-------------------|
| 1: Duration Formatting | `formatDuration(minutes: Long)` | Random Long in [0, 480] |
| 2: Location Update Throttling | `LocationUpdateThrottler` | Random sequences of (timestamp, coordinate) pairs |
| 3: Active Sharers Ordering | `sortSharersByStartTime()` | Random lists of SharedLocation with varying startTimes |
| 4: Multiple Sharers Preview | `formatSharingPreview()` | Random lists of sharer names with size > 1 |
| 5: Header Subtitle Format | `formatGroupSubtitle()` | Random (totalCount, onlineCount, sharingCount) triples |
| 6: Query Chunking | `chunkConversationIds()` | Random lists of String IDs with size [1, 200] |
| 7: Snapshot Debouncing | `SnapshotDebouncer` | Random sequences of (documentId, timestamp, data) |
| 8: Version Write Skip | `shouldWriteToRoom()` | Random pairs of (storedVersion, incomingVersion) |
| 9: Staleness Check | `isStale(lastSyncTimestamp)` | Random timestamps relative to current time |
| 10: Context Menu Actions | `getContextMenuActions()` | Random Message with varying type and senderId |
| 11: Status Icon Mapping | `mapStatusToIcon()` | All MessageStatus values × isSentByCurrentUser |
| 12: Voice Duration Threshold | `shouldSendVoice(durationMs)` | Random Long in [0, 300_000] |
| 13: Voice Mutual Exclusion | `VoicePlaybackManager` | Random sequences of play(messageId) calls |
| 14: URL Detection | `detectUrls(text)` | Random strings with embedded URLs |
| 15: Search Substring | `searchMessages(query)` | Random message lists and query strings |
| 16: Mention Suggestions | `MentionEngine.getSuggestions()` | Random member lists and query prefixes |
| 17: Mention Insertion | `MentionEngine.insertMention()` | Random text + cursor positions + member selections |
| 18: Mention Deduplication | `extractMentionedUserIds()` | Random texts with repeated mention tokens |
| 19: Mention Token Removal | `MentionEngine.removeMentionAtCursor()` | Random texts with tokens + cursor positions within tokens |
| 20: Admin Menu Visibility | `shouldShowAdminMenu()` | Random (userId, groupAdminIds) pairs |
| 21: Message Window Eviction | `evictOldMessages()` | Random message lists with size [400, 700] |
| 22: Media Type Preview | `formatMediaPreview()` | All MessageType values |
| 23: Conversation Status Indicator | `getStatusIndicator()` | Random conversations with varying lastMessage states |
| 24: Pinned Ordering | `sortConversations()` | Random conversation lists with [0, 5] pinned items |
| 25: Muted Badge Hiding | `shouldShowBadge()` | Random (isMuted, unreadCount) pairs |
| 26: Live Location Visibility | `shouldShowLiveLocationOption()` | Random conversations with/without groupId |

### Unit Test Coverage (Key Areas)

- **Swipe-to-reply gesture**: Threshold detection at 48dp, snap-back below threshold
- **Context menu timing**: 300ms long-press detection
- **Voice recorder states**: Recording → Locked → Cancelled transitions
- **Link preview fallback**: Domain used when title missing
- **Search debounce**: 300ms debounce, minimum 2 characters
- **WorkManager scheduling**: Periodic interval, constraints, retry policy
- **Animation parameters**: Slide distances, durations, easing curves, accessibility override
- **Permission flows**: Location and microphone permission request/denial paths

### Integration Test Coverage

- **Socket.IO location flow**: Connect → receive location frame → update Room → UI reflects
- **Firestore fallback**: Disconnect > 30s → Firestore listener activates → reconnect → listener removed
- **Background sync**: WorkManager executes → REST call → Room updated → UI reflects on foreground
- **Message forward**: Select conversations → forward API → messages appear in targets
- **Media gallery pagination**: Load page → scroll → load next page → correct ordering
- **Mention end-to-end**: Type @ → select member → send → server receives mentionedUserIds

### Performance Tests (Macrobenchmark)

- **ChatScreen 500+ messages fling**: Verify ≤16ms frame time during fling scroll
- **Startup time**: Verify first frame within 500ms excluding chat initialization
- **Cache read latency**: Verify Room reads complete within 100ms for locations
- **ChatsScreen initial render**: Verify cached conversations emitted within 200ms
