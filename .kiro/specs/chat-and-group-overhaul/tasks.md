# Implementation Plan: Chat and Group Overhaul

## Overview

This plan implements the WHERE app's Chat and Group Overhaul following Clean Architecture (Presentation → Domain → Data) with Jetpack Compose, Hilt DI, Room, Socket.IO, and Firestore. It builds upon the existing `chat-screen-redesign` spec (data layer, basic UI, Socket.IO infrastructure already delivered) and adds: live location sharing in chat, advanced interactions (swipe-to-reply, context menu, voice messages, link previews, mentions, search), Firestore read optimization, group enhancements, and performance optimization.

## Tasks

- [x] 1. Extend domain models and Room schema for new message types
  - [x] 1.1 Extend Message domain model with voice, link preview, mention, live location, and forward fields
    - Add to `Message`: voiceUrl (String?), voiceDurationMs (Long?), linkPreviewTitle (String?), linkPreviewDescription (String?), linkPreviewImageUrl (String?), linkPreviewDomain (String?), linkPreviewUrl (String?), mentionedUserIds (List<String>), locationSharingSessionId (String?), locationSharingDurationMinutes (Long?), forwardedFrom (String?)
    - Add to `MessageType` enum: VOICE, LIVE_LOCATION
    - Add to `SharedLocation`: displayName (String), sharingStartedAt (Long)
    - Update `Message.isValid()` to handle VOICE (voiceUrl non-null) and LIVE_LOCATION (locationSharingSessionId non-null)
    - _Requirements: 1.3, 2.1, 11.6, 12.1, 14.3_

  - [x] 1.2 Extend MessageEntity and SharedLocationEntity with new fields and create new Room entities
    - Add to `MessageEntity`: voiceUrl, voiceDurationMs, linkPreviewTitle, linkPreviewDescription, linkPreviewImageUrl, linkPreviewDomain, linkPreviewUrl, mentionedUserIdsJson, locationSharingSessionId, locationSharingDurationMinutes, forwardedFrom
    - Add to `SharedLocationEntity`: displayName, sharingStartedAt
    - Create `VoiceMessageCacheEntity` (messageId PK, localFilePath, durationMs, downloadedAt)
    - Create `LinkPreviewCacheEntity` (url PK, title, description, imageUrl, domain, fetchedAt)
    - Update entity-to-domain and domain-to-entity mappers
    - Add Room migration for new columns
    - _Requirements: 11.6, 12.1, 2.1, 7.2_

  - [x] 1.3 Create new DAOs for voice message cache and link preview cache
    - Create `VoiceMessageCacheDao` with: insert, getByMessageId, deleteOlderThan
    - Create `LinkPreviewCacheDao` with: insert, getByUrl, deleteOlderThan
    - Add search query to `MessageDao`: searchMessages(conversationId, query) returning messages with case-insensitive LIKE match
    - _Requirements: 13.2, 11.8, 12.1_

- [x] 2. Implement SnapshotDebouncer and Firestore read optimization
  - [x] 2.1 Implement SnapshotDebouncer component
    - Create `SnapshotDebouncer` singleton with 500ms fixed window batching
    - On first update in window: start 500ms timer
    - Coalesce all updates within window into single Map<documentId, latestState>
    - Execute single Room write with the coalesced batch when timer fires
    - Thread-safe via ConcurrentHashMap and coroutine scope
    - _Requirements: 5.6_

  - [x] 2.2 Implement batched conversation queries in ConversationRepository
    - Implement `batchFetchConversations` chunking IDs into groups of 30 for `whereIn` queries
    - Execute at most one batch query set per foreground sync cycle
    - Store document `updateTime` per conversation in Room
    - Skip Room write for documents whose stored version matches incoming snapshot version
    - Implement staleness check: skip Firestore re-read if lastSyncTimestamp < 5 minutes old
    - _Requirements: 5.1, 5.2, 5.7, 7.4_

  - [x] 2.3 Implement Socket.IO for ephemeral state instead of Firestore
    - Ensure ConversationRepository does NOT open Firestore listeners for presence or typing
    - On presence frame from ChatSocketIoClient: update in-memory online status map within 1s, persist to Room
    - Add `location_update` event handler to ChatSocketIoClient
    - While socket CONNECTED: LocationRepository receives coordinates via Socket.IO, no Firestore listener
    - After 30s DISCONNECTED: LocationRepository falls back to Firestore snapshot listener
    - On reconnect: remove Firestore listener, resume Socket.IO
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 2.4 Implement aggressive local caching strategy
    - ConversationRepository serves conversation list exclusively from Room flows
    - LocationRepository caches last known location per sharer in Room, serves within 100ms
    - ChatViewModel displays cached locations from Room within 100ms, subscribes to Socket.IO updates
    - If Socket.IO fails to deliver location update within 10s: fall back to Firestore listener
    - While offline: display all cached data without loading indicators
    - On offline write action: display non-modal 48dp banner "Queued for sync"
    - On empty Room (first install): show loading state, fetch from Firestore before cache-first flow
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

- [x] 3. Implement live location sharing from chat
  - [x] 3.1 Create StartLocationSharingUseCase and StopLocationSharingUseCase
    - `StartLocationSharingUseCase(groupId, durationMinutes)`: validates groupId non-null, invokes LocationRepository.startLocationSharing, returns Resource<Unit>
    - `StopLocationSharingUseCase(groupId)`: invokes LocationRepository.stopLocationSharing, returns Resource<Unit>
    - _Requirements: 1.3, 1.5_

  - [x] 3.2 Implement live location sharing UI flow in ChatViewModel
    - On location button tap: expose bottom sheet state with "Share Current Location" and "Share Live Location" options
    - Hide "Share Live Location" if conversation has no groupId (1:1 direct)
    - On "Share Live Location" selection: show duration picker (15min, 1h, 2h, 4h, 8h, default 1h)
    - On confirm: invoke StartLocationSharingUseCase, dismiss bottom sheet within 300ms, start LocationTrackingService, insert live location message
    - If permission not granted: request ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION
    - If permission denied: show dismissible error, do not start session
    - If StartLocationSharingUseCase errors: dismiss sheet, show error, do not start service
    - Expose persistent banner state: "Sharing live location - {timeRemaining}" with Stop button
    - On Stop tap: invoke StopLocationSharingUseCase, remove banner within 300ms
    - Update timeRemaining every 60 seconds (format: "Xh Ym" if >= 60min, "Xm" if < 60min)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.8, 1.9, 1.10, 1.11, 1.12_

  - [x] 3.3 Implement LiveLocationBubble composable
    - Render mini-map preview (200dp x 120dp) showing sender's position marker
    - Update marker position every 15 seconds while session active (throttle updates)
    - Display "Updated {N}s ago" below mini-map, refresh every 10 seconds
    - If no update received within 60s: show "Waiting for location..." with fade animation (0.4-1.0 opacity)
    - On session end: show "Location sharing ended - {duration}" with frozen final position
    - On tap in group conversation: navigate to Screen.GroupMap centered on sharer
    - _Requirements: 1.6, 1.7, 1.9, 2.1, 2.2, 2.3, 2.5, 2.6_

  - [x] 3.4 Implement location sharing status in conversation list
    - Observe active locations from LocationRepository.observeActiveLocations (reuse existing flow)
    - Display location pin icon (tertiary) next to timestamp when any member sharing
    - Current user sharing: preview "📍 Sharing live location" (takes precedence)
    - Other user sharing: preview "📍 {displayName} is sharing location"
    - Multiple others: "📍 {displayName} and {count} others sharing location"
    - Remove icon and revert preview within 5s of all sessions ending
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 3.5 Implement quick location actions in chat header
    - Group with active sharing: animated location pulse icon (opacity 0.3-1.0, 1.5s interval) next to map button
    - On map action tap: navigate to Screen.GroupMap, viewport fits all active sharers
    - Header subtitle with sharing: "{memberCount} members - {sharingCount} sharing location"
    - Header subtitle without sharing: "{memberCount} members"
    - 1:1 with friend sharing: show "Sharing location" in tertiary below name (replaces Online/Offline)
    - 1:1 with non-friend sharing: hide sharing status entirely
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [ ] 4. Implement swipe-to-reply and context menu
  - [-] 4.1 Implement swipe-to-reply gesture on message bubbles
    - Detect horizontal left-to-right drag on message bubble
    - Show reply arrow icon fading in proportionally to drag distance (full opacity at 48dp)
    - Limit horizontal displacement to 100dp max
    - On release beyond 48dp: trigger haptic feedback, populate reply preview bar, focus input
    - On release before 48dp: animate back to original position within 200ms
    - Suppress vertical scrolling while swipe in progress
    - If reply preview already visible: replace with newly swiped message
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [-] 4.2 Implement long-press context menu
    - On 300ms long-press: show floating context menu anchored to message
    - Apply 40% opacity black scrim behind menu and message
    - Actions: Copy (text only), Reply, React, Delete/Delete for me, Forward
    - Copy: copy text to clipboard, dismiss, show "Copied" toast 2s
    - Reply: dismiss menu, populate reply preview bar
    - React: dismiss menu, show reaction picker overlay
    - Delete (own message): confirmation dialog, call delete API, remove from cache; on API fail: error snackbar 4s
    - Delete for me (others' message): remove from local display only
    - Forward: navigate to conversation picker (max 5 targets)
    - Dismiss on tap outside or back gesture with 200ms fade-out
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11_

- [ ] 5. Implement message status indicators
  - [-] 5.1 Implement MessageStatusIndicator composable
    - PENDING: clock icon, 14dp, onSurfaceVariant color, 4dp after timestamp
    - SENT: single tick icon, 14dp, onSurfaceVariant
    - DELIVERED: double tick icon, 14dp, onSurfaceVariant
    - READ: double tick icon, 14dp, primary (accent) color
    - FAILED: error icon, 14dp, error color
    - Animate transitions with 150ms crossfade
    - Display only on sent messages (BubbleDirection.SENT)
    - Accessibility content descriptions: "Message pending", "Message sent", "Message delivered", "Message read", "Message failed"
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8_

- [ ] 6. Implement voice message recording and playback
  - [~] 6.1 Implement VoiceRecorder component
    - Long-press microphone button (300ms) to start recording
    - Display recording indicator: elapsed time "m:ss" + waveform visualization
    - Slide left > 100dp: cancel recording, discard audio
    - Slide up > 48dp: lock into hands-free mode (stop + send buttons)
    - Normal release (< 20dp movement): stop recording, send if >= 1s duration
    - If < 1s: discard, show "Hold to record" tooltip 2s
    - Encode AAC format, 64kbps, 16kHz sample rate
    - Max duration 5 minutes; auto-stop and send at max
    - Request microphone permission if not granted; show error if denied
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.11_

  - [~] 6.2 Implement voice message playback bubble
    - Render play/pause button, seekable progress bar, duration label "m:ss"
    - Update progress bar every 100ms during playback
    - Pause on navigate away from ChatScreen
    - On playback complete: reset to beginning, show play button
    - If another voice message tapped while playing: stop current, reset, play new
    - _Requirements: 11.8, 11.9, 11.10_

- [ ] 7. Implement link preview in messages
  - [~] 7.1 Implement FetchLinkPreviewUseCase and link preview rendering
    - Detect URLs matching `https?://[^\s]+` in outgoing messages before send
    - Fetch Open Graph metadata (title, description, image URL) from server-side API
    - 5-second timeout: on timeout/error, send without preview, render URL as tappable hyperlink
    - Attach preview metadata to message payload on success
    - Render link preview card: title (80 char max with ellipsis), domain, thumbnail (160dp max height)
    - Multiple URLs: preview only the first
    - No title in metadata: use domain as title, omit description
    - On tap: open URL in system browser via implicit intent
    - Cache previews in LinkPreviewCacheEntity
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

- [ ] 8. Implement message search within conversation
  - [~] 8.1 Implement in-conversation message search
    - Search icon in ChatScreen header opens search bar (max 100 chars input)
    - Display result position indicator ("3 of 10") and up/down navigation arrows
    - Debounce 300ms after last keystroke, query Room (case-insensitive substring match) when >= 2 chars
    - Scroll to first match (oldest first), highlight matching text (primary color bg, 30% opacity)
    - Down arrow: next match chronologically; disabled at last result
    - Up arrow: previous match; disabled at first result
    - No results: display "No results", disable both arrows
    - On dismiss (X or back): remove highlights, restore previous scroll position
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_

- [ ] 9. Implement group @mention support
  - [~] 9.1 Implement MentionEngine and mention UI
    - On "@" typed in group conversation input: show suggestion popup above input bar
    - Filter members by prefix match (case-insensitive) on characters after "@", update within 300ms
    - On member selection: replace "@query" with styled mention token (primary color, bold), cursor after token
    - Include deduplicated mentionedUserIds array in message payload on send
    - Render mentions in primary color + bold in displayed messages
    - Group > 10 members: show max 5 suggestions sorted by displayName ascending
    - "@" with no additional chars: show first 5 members (excluding current user) sorted by displayName
    - Delete within mention token: remove entire token and userId from list
    - Dismiss popup on back, tap outside, or delete "@" trigger
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8_

- [ ] 10. Implement group admin controls and member online count
  - [~] 10.1 Implement admin overflow menu in GroupChatHeader
    - Admin users see overflow menu icon with 3 actions: "Mute Member", "Group Settings", "Invite Link"
    - Mute Member: member picker dialog (all except current user), on confirm call mute API, show confirmation snackbar; on fail show error snackbar
    - Group Settings: navigate to EditGroupScreen
    - Invite Link: copy to clipboard, show "Link copied" toast 2s, present system share sheet; on fail show error snackbar, no clipboard copy
    - Non-admin users: hide overflow menu, show only standard group info and map actions
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7_

  - [~] 10.2 Implement member online count in group header
    - Display "{totalCount} members - {onlineCount} online" in subtitle (middle dot separator)
    - Update online count within 1s of presence update via ChatSocketIoClient
    - If no members online (other than current user): display only "{totalCount} members"
    - On screen open: determine initial count from ChatSocketIoClient presence state within 2s
    - On socket disconnect: continue displaying last known count until reconnection
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5_

- [ ] 11. Implement Compose stability and performance optimization
  - [~] 11.1 Apply @Immutable annotations and stable collections to UI models
    - Annotate MessageUiModel with @Immutable, all properties val, collections use ImmutableList/ImmutableMap
    - Annotate ConversationUiModel with @Immutable, all properties val
    - Pre-compute all display values (timestamps, date keys, bubble direction, sender initials) in ViewModel/mapper
    - Zero formatting or computation inside composable functions
    - _Requirements: 17.1, 17.2, 19.2_

  - [~] 11.2 Optimize ChatScreen and ChatsScreen LazyColumns
    - ChatScreen LazyColumn: unique contentType per item type (message bubble, date separator, pagination indicator, typing indicator)
    - Stable key per item (message ID, fixed strings for indicators)
    - Stable lambda references (method references or remembered lambdas) for all handlers
    - ChatsScreen LazyColumn: contentType for conversation rows and dividers, stable keys
    - animateItem modifier with 250ms tween for ChatsScreen items
    - No inline collection grouping/sorting/formatting in items block
    - Debounce typing indicator state: max 1 recomposition per 300ms in groups
    - Message window: evict oldest beyond 500 messages, preserve pagination cursor
    - _Requirements: 17.3, 17.4, 17.5, 17.6, 17.7, 19.1, 19.3, 19.4, 19.5, 19.6_

  - [~] 11.3 Implement ImageCacheManager with Coil configuration
    - Memory cache: 25% of available heap
    - Disk cache: 250MB in app cache directory
    - Priority: memory → disk → network
    - Crossfade 200ms from disk/network; instant from memory
    - Thumbnails: downscale to layout dimensions, max 512px longest edge
    - Firebase Storage fetcher: append download token, handle 403 with token refresh + single retry
    - On all-layer failure: error placeholder, no auto-retry until scroll out/in or pull-to-refresh
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6_

- [ ] 12. Implement startup optimization and lazy initialization
  - [~] 12.1 Implement lazy Hilt providers for chat singletons
    - ChatSocketIoClient, ConversationRepository Firestore listener, ImageCacheManager: lazy via dagger.Lazy or javax.inject.Provider
    - None instantiated until first method invoked by chat feature
    - While user hasn't navigated to chat screens: no chat singleton instantiation
    - App reaches first rendered frame within 500ms (auth gate or main nav)
    - _Requirements: 20.1, 20.4, 20.5_

  - [~] 12.2 Implement fast conversation list first paint
    - ChatsViewModel emits cached conversations from Room within 200ms of init (when cache non-empty)
    - If cache empty: emit loading state within 100ms, wait for network (10s timeout before empty-list)
    - _Requirements: 20.2, 20.3_

- [ ] 13. Implement memory management and coroutine lifecycle
  - [~] 13.1 Implement proper coroutine cancellation and lifecycle management
    - ChatViewModel: all coroutines in viewModelScope, cancelled on onCleared()
    - ChatSocketIoClient on logout: cancel SupervisorJob scope, disconnect socket, cancel reconnection job, reset TypingIndicatorManager
    - On process destroy: cancel scope, release socket reference
    - On background (onStop, !isChangingConfigurations): disconnect socket within 5s, remove Firestore listener
    - On foreground (onStart): reconnect socket and re-register listener within 2s
    - If reconnection doesn't reach CONNECTED within 2s: continue with exponential backoff
    - SharedFlow for one-shot events: replay=0, extraBufferCapacity>=1
    - _Requirements: 21.1, 21.2, 21.3, 21.4, 21.5, 21.6, 21.7_

- [ ] 14. Implement WorkManager background sync
  - [~] 14.1 Implement BackgroundSyncWorker
    - Schedule periodic sync (15min interval) when app in background > 5 minutes
    - Worker: single REST API call to fetch unread counts, update Room, complete within 30s
    - On network/server failure: retry with exponential backoff (1min initial, 30min max, 5 attempts max)
    - Require network connectivity constraint
    - On new unread messages detected: display notification (sender name, conversation, count)
    - On auth error: cancel periodic schedule, no retry until re-auth
    - ChatsViewModel displays synced counts from Room within 500ms of screen render
    - _Requirements: 22.1, 22.2, 22.3, 22.4, 22.5, 22.6, 22.7_

- [ ] 15. Implement message send and receive animations
  - [~] 15.1 Implement message animations
    - Sent message: slide up 48dp + fade-in 0→100%, 200ms decelerate easing
    - Received message: slide in 32dp from left + fade-in 0→100%, 250ms decelerate easing
    - Auto-scroll to new message if within 150dp of last visible, 300ms decelerate animation
    - If > 150dp above: show "new message" indicator, tap scrolls to latest with 300ms animation
    - Status indicator transitions: 150ms crossfade
    - Reaction picker appear: 200ms scale-up 80→100% + fade-in
    - Reduced motion accessibility: skip all slide/scale animations, apply instantly
    - _Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7_

- [ ] 16. Implement conversation list enhancements
  - [~] 16.1 Implement rich conversation list previews and actions
    - Media type previews: image→"📷 Photo", voice→"🎤 Voice message", location→"📍 Location", video→"🎥 Video", document→"📄 Document"
    - Message status indicator before preview for own last message (ticks or error icon)
    - Long-press context menu (within 200ms): Pin/Unpin, Mute/Unmute, Archive, Delete
    - Pinned conversations at top (max 3, pin icon, timestamp sort within pinned group); error on 4th pin attempt
    - Muted conversations: mute icon next to timestamp, no unread badge
    - _Requirements: 24.1, 24.2, 24.3, 24.4, 24.5_

- [ ] 17. Implement media gallery from chat header
  - [~] 17.1 Implement MediaGallery screen
    - Accessible from ChatScreen header (both 1:1 and group)
    - Two tabs: "Media" (images/videos, default) and "Files" (documents)
    - Media tab: 3-column grid, timestamp descending, paginated (30 per page), loading indicator at bottom
    - Image tap: full-screen viewer with pinch-to-zoom, swipe-to-dismiss, left/right navigation
    - Video tap: full-screen viewer with play/pause, seek bar, swipe-to-dismiss, left/right navigation
    - Grid thumbnails: 240px width; full-resolution only in viewer
    - On load failure: placeholder error icon, tap to retry
    - Empty state: "No media shared yet" / "No files shared yet"
    - _Requirements: 25.1, 25.2, 25.3, 25.4, 25.5, 25.6, 25.7_

- [ ] 18. Complete ViewModel implementations (stubbed methods)
  - [~] 18.1 Implement ChatViewModel send retry, reaction, typing, and connection features
    - Message send retry: clear input/reply on send, retry per FAILED message (once per tap), 3 consecutive failures → snackbar 4s, reset counter after snackbar, reject if offline queue >= 50
    - Reaction toggle: optimistic update via MessageRepository, rollback on failure
    - Reply state: set on reply action, clear on dismiss/send
    - Read event on open: emit covering all unread; defer if disconnected, send when connected
    - Typing observation: "{name} is typing..." (1:1), "{name1}, {name2} are typing..." (2 typers), "{name1}, {name2} +N are typing..." (3+), ignore self, auto-hide after 5s
    - Connection state: reconnecting banner within 500ms, manual retry after 10 attempts, fetch missed on reconnect, 300ms fade-out on success
    - _Requirements: 26.1, 26.2, 26.3, 26.4_

  - [~] 18.2 Implement CreateGroupViewModel group creation with validation
    - Name validation: 3-50 characters, blank rejected
    - Member count: min 1, max 50 (excluding creator)
    - Avatar upload via image URI before REST API call
    - REST API call to create group
    - On success: display invite code with share action
    - On failure: error message, preserve all form fields for retry
    - _Requirements: 26.5_

  - [~] 18.3 Implement GroupInfoViewModel with member management
    - Member list: sorted by role (admins first) then alphabetical (case-insensitive)
    - Role labels per row
    - Admin actions: remove member (confirmation), delete group (confirmation)
    - Leave group: disabled if sole admin
    - Shared media gallery: 20 most recent IMAGE messages
    - On server operation failure: error indication, preserve state
    - _Requirements: 26.6_

- [ ] 19. Wire incomplete UI components to ViewModels
  - [~] 19.1 Wire ReactionPicker, ReplyPreviewBar, ReadReceiptIndicator, TypingIndicator, and message bubbles
    - Long-press (500ms) message → ReactionPickerOverlay centered with scrim; on emoji select call toggleReaction, dismiss
    - Tap outside/back: dismiss without reaction
    - ReplyPreviewBar: show when replyingToMessage non-null (sender + 100 chars + close button); close calls clearReply
    - ReadReceiptIndicator: below sent messages with readBy >= 1 non-sender; DoneAll icon primary + up to 3 avatars + "+N"
    - TypingIndicator: show when typingIndicatorText non-null; animated 3-dot + formatted text
    - ImageMessageBubble: progress bar during PENDING, error overlay with retry on FAILED, 4:3 placeholder while loading
    - LocationMessageBubble: LocationOn icon + text + locationLabel; tap navigates to group map if groupId non-null
    - ReconnectionBanner: show on showReconnectingBanner=true with spinner; error variant with Retry on showManualRetryAction; 300ms fade-out on isBannerFadingOut
    - _Requirements: 27.1, 27.2, 27.3, 27.4, 27.5, 27.6, 27.7, 27.8_

- [~] 20. Checkpoint - Ensure all code compiles and tests pass
  - Verify all new and modified files compile without errors
  - Run existing test suite to confirm no regressions
  - Ask the user if questions arise

## Notes

- This spec builds on `chat-screen-redesign` which already delivered: Room schema (MessageEntity, ConversationEntity, DAOs), ChatSocketIoClient (reactions, read receipts, presence, reconnection, typing debounce), MessageRepository (optimistic updates, pagination, reactions, read receipts, image/location send), ConversationRepository (Room source of truth, Firestore listener, foreground sync), ChatsViewModel (observation, search)
- Tasks are ordered: data layer extensions → optimization infrastructure → feature implementations → performance → wiring → checkpoint
- Each task references specific requirements for traceability
- The implementation uses Kotlin with Jetpack Compose, Hilt, Room, Socket.IO, Firestore, Coil, and WorkManager

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "2.4"] },
    { "id": 2, "tasks": ["3.1", "3.2", "3.3", "3.4", "3.5"] },
    { "id": 3, "tasks": ["4.1", "4.2", "5.1"] },
    { "id": 4, "tasks": ["6.1", "6.2", "7.1", "8.1"] },
    { "id": 5, "tasks": ["9.1", "10.1", "10.2"] },
    { "id": 6, "tasks": ["11.1", "11.2", "11.3"] },
    { "id": 7, "tasks": ["12.1", "12.2", "13.1", "14.1"] },
    { "id": 8, "tasks": ["15.1", "16.1", "17.1"] },
    { "id": 9, "tasks": ["18.1", "18.2", "18.3"] },
    { "id": 10, "tasks": ["19.1"] },
    { "id": 11, "tasks": ["20"] }
  ]
}
```
