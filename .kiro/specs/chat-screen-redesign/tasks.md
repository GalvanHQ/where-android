# Implementation Plan: Chat Screen Redesign

## Overview

This plan implements the WHERE app's chat system redesign following Clean Architecture (Presentation → Domain → Data) with Jetpack Compose, Hilt DI, and a Socket.IO + REST hybrid for messaging. Tasks are ordered to build foundational data models and repositories first, then layer on UI components, and finally wire everything together with integration tests.

## Tasks

- [x] 1. Set up Room database schema and DAOs
  - [x] 1.1 Create MessageEntity and ConversationEntity Room entities with DAOs
    - Create `MessageEntity` with fields: id (PK), conversationId, senderId, senderName, senderPhotoUrl, text, type, timestamp, status, latitude, longitude, imageUrl, thumbnailUrl, replyToId, replyToText, replyToSenderName, reactionsJson, readByJson
    - Create `ConversationEntity` with fields: id (PK), name, type, photoUrl, groupId, lastMessageText, lastMessageTimestamp, lastMessageSenderId, unreadCount, memberIdsJson, lastSyncTimestamp
    - Add indices on conversationId and timestamp for MessageEntity
    - Enforce unique message ID primary key constraint (upsert on conflict)
    - Create `MessageDao` with: insert, upsertAll, getLatestMessages, getByConversationPaginated, updateStatus, updateReactions, updateReadBy, getById
    - Create `ConversationDao` with: observeAll, upsertAll, getAll, getById, updateUnreadCount
    - _Requirements: 12.1, 14.4, 2.4_

  - [x] 1.2 Create domain models (Message, Conversation, MessagePage) and mappers
    - Create `Message` data class with all fields including reactions map, readBy list, reply fields, location fields, image fields
    - Create `Conversation` data class with onlineMembers, typingMembers, mutedBy, pinnedBy
    - Create `MessagePage` data class with messages list, nextCursor, hasMore
    - Create `MessageType` and `MessageStatus` enums
    - Create entity-to-domain and domain-to-entity mapper extensions
    - Add validation rules: text non-empty for TEXT type, lat/lng non-null for LOCATION type, imageUrl non-null for IMAGE type
    - _Requirements: 14.1, 15.3, 15.4_

- [x] 2. Implement ChatSocketIoClient enhancements
  - [x] 2.1 Extend ChatSocketIoClient with new events (reactions, read receipts, presence, image)
    - Add `sendReaction(messageId, emoji)` and `removeReaction(messageId, emoji)` methods
    - Add `sendRead()` method for read receipt emission
    - Add `sendImage(imageUrl, tempId)` method
    - Add `sendLocation(latitude, longitude, tempId)` method
    - Add incoming frame types: `ReactionUpdate`, `ReadReceipt`, `Presence`, `MessageAck`
    - Expose `connectionState: StateFlow<ConnectionState>` with CONNECTED, DISCONNECTED, ERROR states
    - _Requirements: 3.3, 5.1, 8.1, 6.1, 15.1_

  - [x] 2.2 Implement exponential backoff reconnection logic
    - Implement reconnection with delays: 1s, 2s, 4s, 8s, 16s capped at 30s max
    - Maximum 10 reconnection attempts before stopping
    - Emit connection state changes to `connectionState` StateFlow
    - On exhausting attempts, stop automatic reconnection
    - _Requirements: 13.1, 13.2, 13.3_

  - [x] 2.3 Implement typing indicator debounce (emit at most once per 300ms window)
    - Throttle `sendTyping(true)` to at most one emission per 300ms
    - Emit stop-typing after 3 seconds of no keystrokes
    - Emit stop-typing immediately on message send or input clear
    - _Requirements: 7.1, 7.3, 7.4_

  - [x]* 2.4 Write property test for exponential backoff sequence
    - **Property 12: Exponential Backoff Sequence**
    - **Validates: Requirement 13.2**

  - [x]* 2.5 Write property test for typing indicator debounce
    - **Property 14: Typing Indicator Debounce**
    - **Validates: Requirement 7.1**

- [x] 3. Implement MessageRepository with optimistic updates and pagination
  - [x] 3.1 Implement sendMessage with optimistic insert and ack handling
    - Generate tempId via UUID, insert message with PENDING status into Room immediately
    - Emit via ChatSocketIoClient.sendText
    - On MessageAck frame: update tempId → serverId, status → SENT
    - On timeout (10s) or error: update status to FAILED
    - Implement offline queue (FIFO, max 50 messages) when disconnected
    - Flush queue in order on reconnection
    - _Requirements: 1.1, 1.2, 1.3, 1.6, 1.7_

  - [x] 3.2 Implement cursor-based pagination (loadOlderMessages)
    - Implement `loadOlderMessages(conversationId, beforeCursor, limit=30)` calling REST API
    - Persist fetched messages to Room via upsertAll
    - Return `MessagePage` with nextCursor and hasMore flag
    - On initial load: serve from Room cache first, then background sync with server
    - Merge server results with local cache by unique ID (discard duplicates)
    - _Requirements: 2.1, 2.2, 2.4, 2.6, 14.3_

  - [x] 3.3 Implement reaction toggle with optimistic update and rollback
    - Toggle logic: if user already reacted with emoji, remove; otherwise add
    - Optimistic update: modify local reactions map immediately
    - On server failure/timeout (10s): rollback to previous state
    - Emit via ChatSocketIoClient.sendReaction or removeReaction
    - _Requirements: 3.3, 3.4, 3.5, 3.6_

  - [x] 3.4 Implement read receipt handling (markRead, readBy updates)
    - `markRead(conversationId, userId)`: emit read event via ChatSocketIoClient
    - On incoming ReadReceipt frame: append userId to message's readBy list (no duplicates)
    - Ensure readBy list only grows monotonically (never remove entries)
    - Defer read event emission if disconnected, send when connected
    - _Requirements: 5.1, 5.2, 5.4, 5.5_

  - [x] 3.5 Implement sendLocationMessage with coordinate validation
    - Validate latitude in range -90.0 to 90.0, longitude in range -180.0 to 180.0
    - Return Resource.Error if lat/lng is null or out of range
    - Create optimistic message with type LOCATION, emit via ChatSocketIoClient.sendLocation
    - _Requirements: 15.1, 15.3, 15.4_

  - [x] 3.6 Implement sendImageMessage with compression and upload
    - Compress image to max 1920px longest edge at 80% JPEG quality
    - Accept JPEG, PNG, WebP, HEIF source formats
    - Reject images exceeding 10MB before compression
    - Upload with progress tracking, 60-second timeout
    - Retry up to 3 attempts on failure
    - On success: emit via ChatSocketIoClient.sendImage
    - _Requirements: 6.1, 6.3, 6.4, 6.5, 6.7_

  - [x]* 3.7 Write property test for message ordering invariant
    - **Property 1: Message Ordering Invariant**
    - **Validates: Requirements 14.1, 2.3**

  - [x]* 3.8 Write property test for optimistic insert consistency
    - **Property 2: Optimistic Insert Consistency**
    - **Validates: Requirements 1.1, 1.6**

  - [x]* 3.9 Write property test for no duplicate messages on ack
    - **Property 3: No Duplicate Messages on Ack**
    - **Validates: Requirements 1.2, 14.2**

  - [x]* 3.10 Write property test for pagination completeness and deduplication
    - **Property 4: Pagination Completeness and Deduplication**
    - **Validates: Requirements 14.3, 2.3**

  - [x]* 3.11 Write property test for reaction toggle idempotency
    - **Property 5: Reaction Toggle Idempotency**
    - **Validates: Requirements 3.3, 3.4**

  - [x]* 3.12 Write property test for reaction rollback on failure
    - **Property 6: Reaction Rollback on Failure**
    - **Validates: Requirement 3.5**

  - [x]* 3.13 Write property test for read receipt monotonicity
    - **Property 7: Read Receipt Monotonicity**
    - **Validates: Requirements 5.2, 5.4**

  - [x]* 3.14 Write property test for offline queue ordering
    - **Property 13: Offline Queue Ordering**
    - **Validates: Requirements 1.6, 13.3**

  - [x]* 3.15 Write property test for location message validation
    - **Property 15: Location Message Validation**
    - **Validates: Requirement 15.3**

  - [x]* 3.16 Write property test for image compression bounds
    - **Property 16: Image Compression Bounds**
    - **Validates: Requirement 6.1**

- [x] 4. Implement ConversationRepository with Firestore optimization
  - [x] 4.1 Implement ConversationRepository with Room as single source of truth
    - Expose conversation list via Room database flows only
    - Firestore listener scoped to metadata fields only (name, participantIds, photoUrl, type, groupId, lastMessageText, lastMessageSenderId, lastMessageTimestamp, unreadCounts)
    - No Firestore listener on messages subcollection
    - On Firestore snapshot: write to Room, UI flow emits within 500ms
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

  - [x] 4.2 Implement foreground sync and initial load logic
    - On app foreground: sync unread counts via single REST API call (10s timeout)
    - On failure/timeout: retain Room-cached counts, emit recoverable error
    - On first launch (no Room records): fetch initial list from REST, persist to Room before Firestore listener starts
    - _Requirements: 12.5, 12.6, 12.7_

- [x] 5. Checkpoint - Ensure data layer compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement ChatViewModel
  - [x] 6.1 Implement ChatViewModel with message observation and initial load
    - Observe messages flow from MessageRepository
    - Load initial 30 messages from Room, then sync with server
    - Maintain messages sorted by timestamp ascending, use message ID as secondary sort key
    - Expose UI state with messages, isLoading, hasMoreMessages, paginationCursor
    - _Requirements: 2.1, 14.1, 14.5_

  - [x] 6.2 Implement pagination trigger (loadOlderMessages on scroll-to-top)
    - Trigger when user scrolls within 5 items of top
    - Guard against concurrent pagination requests (isLoadingMore flag)
    - Update paginationCursor and hasMoreMessages from server response
    - Stop requesting when hasMore is false
    - _Requirements: 2.2, 2.3, 2.6_

  - [ ] 6.3 Implement message send, retry, and offline queue in ViewModel
    - Clear input text and reply state on send
    - Handle FAILED status: expose retry action per message
    - Track consecutive failures (3 retries → snackbar "Message could not be sent" for 4s)
    - Reject sends when offline queue is full (50 messages)
    - _Requirements: 1.3, 1.4, 1.5, 1.7_

  - [ ] 6.4 Implement reaction toggle, reply state, and read receipt emission
    - Toggle reaction via MessageRepository.reactToMessage
    - Manage replyingToMessage state (set on long-press reply action, clear on dismiss/send)
    - Emit read event on conversation open (covering all unread messages)
    - Defer read event if disconnected
    - _Requirements: 3.3, 4.1, 4.2, 5.1, 5.5_

  - [ ] 6.5 Implement typing indicator state management
    - Observe incoming typing frames, display "{name} is typing…"
    - Auto-hide after 5 seconds of no typing event from a user
    - For groups: show at most 2 names + "+N are typing…"
    - Emit typing events on keystroke (debounced 300ms)
    - _Requirements: 7.1, 7.2, 7.5, 7.6_

  - [ ] 6.6 Implement location message sending in ViewModel
    - Obtain device coordinates on location button tap
    - Invoke SendLocationMessageUseCase with conversationId, lat, lng
    - Handle permission not granted: request permission
    - Handle location timeout (10s): show transient error
    - _Requirements: 15.1, 15.5, 15.6_

  - [ ] 6.7 Implement connection state observation and reconnection UI state
    - Observe ChatSocketIoClient.connectionState
    - Expose reconnecting banner state (show within 500ms of disconnect)
    - After 10 failed attempts: show manual retry action
    - On reconnect: fetch missed messages via REST, flush queue, hide banner with 300ms fade
    - _Requirements: 13.1, 13.3, 13.4, 13.5, 13.6, 13.7_

- [x] 7. Implement ChatsViewModel
  - [x] 7.1 Implement ChatsViewModel with conversation observation and search
    - Observe conversations from ConversationRepository (sorted by lastMessageTimestamp DESC)
    - Implement local search/filter: case-insensitive substring match on name or lastMessageText
    - Restore full list when search cleared
    - Expose unread counts (capped at "99+")
    - Expose online status per conversation avatar
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.7_

  - [x]* 7.2 Write property test for conversation sort order invariant
    - **Property 8: Conversation Sort Order Invariant**
    - **Validates: Requirement 9.1**

  - [x]* 7.3 Write property test for conversation search filter correctness
    - **Property 9: Conversation Search Filter Correctness**
    - **Validates: Requirement 9.3**

- [ ] 8. Implement CreateGroupViewModel
  - [x] 8.1 Implement CreateGroupViewModel with member search and selection
    - Search users after 300ms debounce when query >= 2 characters
    - Display up to 20 results excluding already-selected members and current user
    - Add/remove members from chip row, maintain consistency with search results
    - _Requirements: 10.2, 10.3_

  - [ ] 8.2 Implement group creation with validation and invite code
    - Validate group name length (3-50 characters)
    - Validate at least 1 member selected (max 50)
    - Upload avatar if selected, then POST /groups
    - On success: display invite code with share button, navigate to group chat
    - On failure: show error, preserve form data for retry
    - _Requirements: 10.1, 10.4, 10.5, 10.6, 10.7_

  - [x]* 8.3 Write property test for member selection state consistency
    - **Property 10: Member Selection State Consistency**
    - **Validates: Requirements 10.2, 10.3**

  - [x]* 8.4 Write property test for group name validation
    - **Property 11: Group Name Validation**
    - **Validates: Requirement 10.1**

- [ ] 9. Implement GroupInfoViewModel
  - [ ] 9.1 Implement GroupInfoViewModel with member management and admin actions
    - Display group avatar, name, description, member list (admins first, then alphabetical)
    - Show role labels ("Admin" or "Member") per row
    - Admin actions: remove member (with confirmation dialog), delete group
    - Hide admin actions for non-admin users
    - Leave group (with confirmation), disable if sole admin
    - Show shared media gallery (most recent 20 items, "See All" action)
    - Handle server operation failures with error snackbar, no state mutation on failure
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_

  - [ ]* 9.2 Write property test for non-admin action hiding
    - **Property 17: Non-Admin Action Hiding**
    - **Validates: Requirement 11.3**

- [ ] 10. Checkpoint - Ensure ViewModel layer compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 11. Implement ChatScreen UI
  - [ ] 11.1 Implement ChatScreen message list with bubble styling per DESIGN.md
    - Sent bubbles: Accent Primary, Radius Large all corners except bottom-right (Radius XS 4dp)
    - Received bubbles: Background Elevated, Radius Large all corners except bottom-left (Radius XS 4dp)
    - Max bubble width 75% of screen width
    - Key LazyColumn items by message ID
    - Maintain scroll position on prepend (pagination)
    - Show loading indicator at top during pagination
    - Show inline retry button on pagination failure
    - _Requirements: 16.1, 2.5, 2.3, 2.7, 14.1_

  - [ ] 11.2 Implement ChatScreen input bar (pill-shaped, attachment, location, send button)
    - Pill-shaped input with Background Elevated background
    - Leading attachment icon, trailing location share icon
    - Circular Accent Primary send button with scale-down on press
    - On focus: border changes from 1dp Divider to 1.5dp Accent Primary
    - _Requirements: 16.2, 16.4_

  - [ ] 11.3 Implement ChatScreen header (avatar, name, status/member count)
    - 1:1 conversation: 40dp avatar, contact name, online status text ("Online"/"Offline")
    - Group conversation: 40dp avatar, group name, member count text
    - Hide presence status if other user not in friends list
    - Navigation actions: back, profile/group info, group map
    - _Requirements: 16.5, 16.6, 8.3, 8.4_

  - [ ] 11.4 Implement reaction picker overlay and reaction badges
    - Long-press message → show picker with 6 emojis (👍, ❤️, 😂, 😮, 😢, 🙏)
    - Dismiss on tap outside or back gesture
    - Display reaction badges below message: emoji + count (show count when >= 2)
    - Aggregate same emoji from multiple users into single badge
    - _Requirements: 3.1, 3.2, 3.7, 3.8_

  - [ ] 11.5 Implement reply preview bar and quoted message display
    - Reply preview above input: sender name + up to 100 chars + close button
    - Close button dismisses reply without affecting input text
    - Quoted preview above reply text: replyToSenderName + up to 100 chars of replyToText
    - Tap quoted preview → scroll to original message (no-op if not loaded)
    - _Requirements: 4.1, 4.2, 4.3, 4.5, 4.6, 4.7_

  - [ ] 11.6 Implement read receipt indicators (double-tick + avatars)
    - Show on sent messages only when readBy has at least 1 non-sender user
    - Double-tick icon + up to 3 reader avatars
    - "+N" overflow when more than 3 readers
    - _Requirements: 5.3, 5.6_

  - [ ] 11.7 Implement typing indicator UI (animated 3-dot)
    - Animated 3-dot indicator below last message, above input
    - Show "{name} is typing…" for 1:1
    - Show "{name1}, {name2} +N are typing…" for groups (max 2 names)
    - _Requirements: 7.2, 7.6_

  - [ ] 11.8 Implement media/image message bubbles with upload progress
    - Determinate progress bar overlay (0-100%) during upload
    - Error overlay with retry icon on failure
    - Solid placeholder matching aspect ratio (4:3 default) while loading received images
    - Size limit error inline below image picker for >10MB
    - _Requirements: 6.2, 6.3, 6.4, 6.6, 6.7_

  - [ ] 11.9 Implement location message bubble UI
    - Location icon + "Shared a location" text + coordinate label (lat, lng to 4 decimal places)
    - _Requirements: 15.2_

  - [ ] 11.10 Implement reconnection banner and failed message retry UI
    - "Reconnecting..." banner within 500ms of disconnect
    - Replace with error banner + manual "Retry" after 10 failed attempts
    - Fade-out animation (300ms) on reconnect
    - Failed message: red indicator + "Tap to retry"
    - Snackbar "Message could not be sent" for 4s after 3 consecutive failures
    - _Requirements: 13.1, 13.3, 13.7, 1.3, 1.5_

- [ ] 12. Implement ChatsScreen UI
  - [ ] 12.1 Implement ChatsScreen conversation list with unread highlighting and search
    - Conversations sorted by lastMessageTimestamp DESC
    - Unread rows: Background Elevated tint + unread count badge (capped "99+")
    - Search field: filter locally on name or lastMessageText (case-insensitive)
    - Key LazyColumn rows by conversation id
    - Online status dot (10dp, tertiary color) on avatars for direct conversations
    - Content description: "{displayName} is online" for accessibility
    - _Requirements: 9.1, 9.2, 9.3, 9.8, 9.7, 8.1, 8.2, 8.5_

  - [ ] 12.2 Implement ChatsScreen swipe actions and empty state
    - Swipe right-to-left: reveal archive and mute buttons
    - Empty state: illustration icon, "No chats yet" headline, body text, primary CTA → Search_Users_Screen
    - _Requirements: 9.5, 9.6_

- [ ] 13. Implement CreateGroupScreen UI
  - [ ] 13.1 Implement CreateGroupScreen with avatar picker, member search, and chip row
    - Group avatar picker (camera, gallery, emoji)
    - Group name input (validation: 3-50 chars) and description input
    - Member search field with real-time results (debounced 300ms, min 2 chars)
    - Selected members chip row with remove action
    - Create button with loading state
    - Validation messages for name length and minimum 1 member
    - On failure: show error, preserve all form data
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.7_

  - [ ] 13.2 Implement post-creation invite code display with share action
    - Show invite code after successful creation
    - Share button triggers system share sheet with invite code
    - _Requirements: 10.6_

- [ ] 14. Implement GroupInfoScreen UI
  - [ ] 14.1 Implement GroupInfoScreen with member list, admin actions, and shared media
    - Group avatar, name, description display
    - Member list: admins first, then alphabetical, role labels
    - Admin actions: remove member (confirmation dialog), delete group
    - Non-admin: hide admin actions
    - Leave group (confirmation dialog), disabled if sole admin with message
    - Shared media gallery (20 most recent, "See All")
    - Error snackbar on server operation failure
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_

- [ ] 15. Checkpoint - Ensure UI layer compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 16. Wire navigation and integrate all screens
  - [ ] 16.1 Add navigation routes for ChatScreen, ChatsScreen, CreateGroupScreen, GroupInfoScreen
    - Register composable destinations in NavHost
    - Pass conversationId, groupId arguments via navigation
    - Wire onNavigateToChat, onNavigateToGroupInfo, onNavigateToCreateGroup, onNavigateToUserProfile callbacks
    - _Requirements: 9.6, 10.4, 11.4_

  - [ ] 16.2 Wire ChatSocketIoClient lifecycle to ChatScreen (connect/disconnect on enter/exit)
    - Connect with conversationId and Firebase token on ChatScreen entry
    - Disconnect on ChatScreen exit
    - Handle app backgrounding/foregrounding
    - _Requirements: 13.1, 13.4, 13.5_

  - [ ] 16.3 Wire ConversationRepository Firestore listener and foreground sync
    - Start Firestore listener on ChatsScreen entry
    - Trigger foreground sync on app resume
    - Ensure initial load from REST on first launch before listener starts
    - _Requirements: 12.2, 12.5, 12.7_

  - [ ]* 16.4 Write integration tests for message send → ack → display flow
    - Test optimistic insert, ack update, status transitions
    - Test offline queue flush on reconnect
    - _Requirements: 1.1, 1.2, 1.6, 13.4_

  - [ ]* 16.5 Write integration tests for pagination and deduplication
    - Test initial load + scroll-to-top pagination
    - Test concurrent real-time messages during pagination
    - Verify no duplicates and correct ordering
    - _Requirements: 2.1, 2.2, 14.3, 14.4_

- [ ] 17. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The implementation uses Kotlin with Jetpack Compose, Hilt, Room, and Socket.IO as specified in the design
- All message operations maintain the ordering invariant (sorted by timestamp, ID as tiebreaker)
- Firestore reads are minimized by using Room as the single source of truth for UI

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "4.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "4.2"] },
    { "id": 3, "tasks": ["2.4", "2.5", "3.1", "3.2"] },
    { "id": 4, "tasks": ["3.3", "3.4", "3.5", "3.6"] },
    { "id": 5, "tasks": ["3.7", "3.8", "3.9", "3.10", "3.11", "3.12", "3.13", "3.14", "3.15", "3.16"] },
    { "id": 6, "tasks": ["6.1", "6.2", "7.1", "8.1"] },
    { "id": 7, "tasks": ["6.3", "6.4", "6.5", "6.6", "6.7", "7.2", "7.3", "8.2", "8.3", "8.4"] },
    { "id": 8, "tasks": ["9.1"] },
    { "id": 9, "tasks": ["9.2", "11.1", "11.2", "11.3"] },
    { "id": 10, "tasks": ["11.4", "11.5", "11.6", "11.7", "11.8", "11.9", "11.10"] },
    { "id": 11, "tasks": ["12.1", "12.2", "13.1", "13.2", "14.1"] },
    { "id": 12, "tasks": ["16.1", "16.2", "16.3"] },
    { "id": 13, "tasks": ["16.4", "16.5"] }
  ]
}
```
