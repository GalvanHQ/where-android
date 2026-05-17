# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Chat Messaging Multi-Bug Exploration
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bugs exist
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the 15 bugs exist
  - **Scoped PBT Approach**: Scope properties to concrete failing cases for each bug category
  - Test 1a: Create a `Conversation` with `name = ""`, `type = DIRECT`, call `toUiModel()` with empty `participantNames` map → assert title equals the other user's resolved display name (will FAIL because name resolution is missing)
  - Test 1b: Create a `Conversation` with `onlineMembers = emptySet()` for a DM where the other user IS online per `OnlineStatusDao` → assert `isOtherUserOnline == true` (will FAIL because online status is not propagated)
  - Test 1c: Call `formatConversationTimestamp()` with timestamp from Jan 31 23:59 when current date is Feb 1 → assert result is "Yesterday" (will FAIL due to naive `Calendar.DATE` subtraction)
  - Test 1d: Call `formatConversationTimestamp()` with timestamp from Dec 31 when current date is Jan 1 → assert result is "Yesterday" (will FAIL at year boundary)
  - Test 1e: Simulate `handleIncomingMessage()` with `MessageDelivered` frame where `senderId == currentUserId` → assert no new row is inserted (will FAIL because sender check is missing)
  - Test 1f: Call `deleteConversation()` → assert Firestore document is updated with `deletedBy` containing current user ID (will FAIL because delete is local-only)
  - Test 1g: Call `formatMessageTime()` for a message from yesterday → assert result includes date context (will FAIL because only "HH:mm" is returned)
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct - it proves the bugs exist)
  - Document counterexamples found to understand root causes
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.7, 1.10, 1.11, 1.13_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Existing Chat Behavior Preservation
  - **IMPORTANT**: Follow observation-first methodology
  - **Step 1 - Observe**: Run UNFIXED code with non-buggy inputs and record actual outputs
  - Observe: Group conversation with `name = "Team Chat"` and `type = GROUP` → `toUiModel()` returns title "Team Chat" with "{N} members" subtitle
  - Observe: `formatConversationTimestamp()` with same-day timestamp → returns "HH:mm" format
  - Observe: `formatConversationTimestamp()` with timestamp from 2 days ago (same month) → returns day-of-week name
  - Observe: `handleIncomingMessage()` with `senderId != currentUserId` → inserts message row correctly
  - Observe: `applySearchFilter()` with query matching conversation name → returns filtered list sorted with pinned first
  - Observe: Message with `status = PENDING` queued offline → remains in Room with PENDING status until connectivity restored
  - **Step 2 - Write property-based tests capturing observed behavior**:
  - Property: For all group conversations, `resolveConversationTitle()` returns the group name unchanged
  - Property: For all same-day timestamps, `formatConversationTimestamp()` returns "HH:mm" format
  - Property: For all messages from other users (senderId != currentUserId), `handleIncomingMessage()` inserts exactly one row
  - Property: For all conversation lists, `applySearchFilter()` preserves pinned-first ordering and 300ms debounce behavior
  - Property: For all offline-queued messages, status remains PENDING until send-on-reconnect succeeds
  - Property: For all non-DM conversations, `toUiModel()` produces the same output regardless of `participantNames` map content
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10_

- [x] 3. Fix participant name and photo resolution (Bugs 1.1, 1.8, 1.9)

  - [x] 3.1 Add participant metadata resolution in ChatsViewModel
    - In `ChatsViewModel`, create `resolveParticipantMetadata()` that queries `UserRepository` for all direct message conversation participants
    - Store resolved names in `participantNames: Map<String, String>` and photos in `participantPhotos: Map<String, String?>`
    - Cache resolved names locally to avoid repeated lookups
    - Pass populated `participantNames` and `participantPhotos` maps to `toUiModel()` in `applySearchFilter()`
    - _Bug_Condition: isBugCondition(input) where input.type == DISPLAY_CONVERSATION AND input.conversation.type == DIRECT AND participantNames[otherUserId] == null_
    - _Expected_Behavior: title == otherUser.displayName AND photoUrl == otherUser.profilePhotoUrl_
    - _Preservation: Group conversations continue to display group name unchanged_
    - _Requirements: 1.1, 1.8, 1.9, 2.1, 2.8, 2.9_

  - [x] 3.2 Add participant metadata resolution in ChatViewModel
    - In `ChatViewModel.loadConversation()`, if conversation is DIRECT with blank name, fetch other user's profile from `UserRepository`
    - Update UI model title with resolved display name
    - Update UI model photo URL with resolved profile photo
    - _Bug_Condition: isBugCondition(input) where input.conversation.name.isBlank() AND input.conversation.type == DIRECT_
    - _Expected_Behavior: header displays other participant's actual display name_
    - _Preservation: Group chat headers continue to show group name_
    - _Requirements: 1.1, 2.1_

  - [x] 3.3 Add participantNamesJson and participantPhotosJson to ConversationEntity
    - Add `participantNamesJson: String?` and `participantPhotosJson: String?` fields to `ConversationEntity`
    - Update `ConversationEntityMapper.toDomain()` to deserialize and populate participant metadata
    - Populate these fields when syncing conversations from Firestore
    - _Requirements: 1.8, 1.9, 2.8, 2.9_

- [x] 4. Fix online status propagation (Bug 1.2)

  - [x] 4.1 Propagate online status in ChatsViewModel
    - In `applySearchFilter()`, use the existing `onlineUserIds` set to populate `isOtherUserOnline` in `ConversationUiModel` for DM conversations
    - Check if the other participant's user ID is in `onlineUserIds`
    - _Bug_Condition: isBugCondition(input) where input.type == DISPLAY_ONLINE_STATUS AND onlineMembers.isEmpty() AND presenceFrameNotPropagated_
    - _Expected_Behavior: isOtherUserOnline == true when other user is in onlineUserIds_
    - _Preservation: Group conversations do not show individual online status_
    - _Requirements: 1.2, 2.2_

  - [x] 4.2 Subscribe to presence frames in ChatViewModel
    - In `ChatViewModel.loadConversation()`, subscribe to WebSocket presence frames for the other user's ID
    - Update `isOtherUserOnline` in real-time when presence changes
    - Display "Active now" when online, "Offline" when genuinely offline
    - _Requirements: 1.2, 2.2_

- [x] 5. Fix group creation UI entry point (Bug 1.3)

  - [x] 5.1 Add expandable FAB menu to ChatsScreen
    - Replace single FAB with expandable FAB menu containing "New Chat" and "New Group" options
    - Wire "New Group" option to existing `onNavigateToCreateGroup` callback
    - Wire "New Chat" option to existing search/start conversation navigation
    - _Bug_Condition: No UI element exposes onNavigateToCreateGroup to the user_
    - _Expected_Behavior: User can access group creation from chats screen_
    - _Requirements: 1.3, 2.3_

- [x] 6. Fix Firestore security rules (Bugs 1.4, 1.5, 1.15)

  - [x] 6.1 Update Firestore conversations collection rules
    - Add `allow create: if isAuthenticated() && request.auth.uid in request.resource.data.participantIds`
    - Ensure `update` rule permits field-level updates to `pinnedBy`, `mutedBy`, `archivedBy`, and `deletedBy` arrays by participants
    - Validate that `request.auth.uid in resource.data.participantIds` works for existing documents
    - _Bug_Condition: firestoreRulesRejectUpdate(input) for legitimate participant operations_
    - _Expected_Behavior: All pin/mute/archive/delete operations by participants succeed_
    - _Preservation: Unauthorized users still cannot modify conversations they are not part of_
    - _Requirements: 1.4, 1.5, 1.15, 2.4, 2.5, 2.15_

- [x] 7. Fix context menu actions persistence (Bugs 1.6, 1.7)

  - [x] 7.1 Implement Firestore-backed delete (soft delete)
    - Change `ChatsViewModel.deleteConversation()` to update Firestore document by adding current user's ID to `deletedBy` array
    - Also remove from local Room database for immediate UI feedback
    - Filter out conversations where current user is in `deletedBy` when loading from Firestore
    - _Bug_Condition: deleteIsLocalOnly(input) — delete only removes from Room, not Firestore_
    - _Expected_Behavior: Conversation does not reappear after sync_
    - _Requirements: 1.7, 2.7_

  - [x] 7.2 Ensure archive persists to Firestore
    - Verify `archiveConversation()` updates Firestore `archivedBy` array with current user ID
    - Remove archived conversation from active list immediately
    - _Bug_Condition: Archive operation may not persist correctly_
    - _Expected_Behavior: Archived conversation removed from active list and persists across syncs_
    - _Requirements: 1.6, 2.6_

- [x] 8. Fix timestamp formatting (Bugs 1.10, 1.11)

  - [x] 8.1 Implement calendar-aware timestamp formatting
    - Replace naive `Calendar.DATE` subtraction in `formatConversationTimestamp()` with proper calendar day comparison
    - Compare year + day-of-year for "today" check
    - Subtract 1 calendar day from `now` and compare year + day-of-year for "yesterday" check
    - Check if within same calendar week for day-of-week display
    - Fall back to "dd/MM/yy" for older dates
    - _Bug_Condition: input.timestamp.month != now.month AND naive subtraction produces incorrect result_
    - _Expected_Behavior: "Yesterday" for previous calendar day regardless of month/year boundary_
    - _Preservation: Same-day timestamps continue to show "HH:mm", same-week shows day name_
    - _Requirements: 1.10, 2.10_

  - [x] 8.2 Add date context to message timestamps
    - Update `formatMessageTime()` to include date context for messages not from today
    - Format as "Mon 14:32" for messages within the current week, "12 Jan 14:32" for older messages
    - Keep "HH:mm" format for today's messages
    - _Bug_Condition: Messages from previous days show only "HH:mm" without date context_
    - _Expected_Behavior: Messages include date context when not from today_
    - _Requirements: 1.11, 2.11_

- [x] 9. Fix message duplication (Bug 1.13)

  - [x] 9.1 Skip sender's own messages in handleIncomingMessage
    - In `MessageRepositoryImpl.handleIncomingMessage()`, check if `frame.senderId == currentUserId`
    - If sender is current user, skip the insert — message already exists from optimistic insert
    - The `handleAck()` will update the temp ID to the server ID
    - _Bug_Condition: senderReceivesOwnMessageDeliveredFrame(input) AND ackNotYetProcessed(input)_
    - _Expected_Behavior: Message appears exactly once regardless of Ack/Delivered frame ordering_
    - _Preservation: Messages from other users continue to be inserted normally_
    - _Requirements: 1.13, 2.13_

  - [x] 9.2 Add deduplication safeguard
    - As secondary defense, before inserting any `MessageDelivered` frame, check if a message with same `conversationId`, `senderId`, `text`, and `timestamp` (within 1-second tolerance) already exists in Room
    - If duplicate found, update existing row's ID to server ID instead of inserting new row
    - _Requirements: 1.13, 2.13_

- [x] 10. Fix message delivery status indicators (Bug 1.12)

  - [x] 10.1 Complete ReadReceiptIndicator component
    - Ensure `ReadReceiptIndicator` renders all states correctly:
      - Clock icon for `PENDING`
      - Single checkmark for `SENT`
      - Double checkmarks (grey) for `DELIVERED`
      - Double checkmarks (blue/filled) for `READ`
    - Verify all `MessageStatus` transitions are handled
    - _Bug_Condition: MessageStatus transitions not fully reflected in UI_
    - _Expected_Behavior: Progressive status indicator updates as message state changes_
    - _Requirements: 1.12, 2.12_

- [x] 11. Fix performance degradation (Bug 1.14)

  - [x] 11.1 Debounce presence updates
    - In `ChatsViewModel.observePresenceUpdates()`, debounce presence-triggered re-renders by 500ms
    - Use `distinctUntilChanged` on the online status of each conversation
    - Avoid calling `applySearchFilter()` on every presence change
    - _Bug_Condition: Every presence update triggers full list re-mapping_
    - _Expected_Behavior: Only affected conversation's online status updates, debounced by 500ms_
    - _Preservation: Conversation list still reflects presence changes, just debounced_
    - _Requirements: 1.14, 2.14_

  - [x] 11.2 Implement targeted presence updates
    - When only a presence change occurs (not a full conversation list update), update only the affected `ConversationUiModel.isOtherUserOnline` field
    - Avoid re-mapping the entire conversation list for single-user presence changes
    - _Requirements: 1.14, 2.14_

- [x] 12. Verify bug condition exploration test now passes

  - [x] 12.1 Re-run bug condition exploration test
    - **Property 1: Expected Behavior** - Chat Messaging Bugs Fixed
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior for all 15 bugs
    - When this test passes, it confirms the expected behavior is satisfied:
      - DM conversations display resolved participant names (not "Unknown User")
      - Online status shows "Active now" when other user is online
      - Timestamps use calendar-aware formatting across month/year boundaries
      - Messages appear exactly once (no duplication)
      - Delete persists to Firestore (soft delete)
      - Message timestamps include date context for non-today messages
    - Run bug condition exploration test from task 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bugs are fixed)
    - _Requirements: 2.1, 2.2, 2.7, 2.10, 2.11, 2.13_

  - [x] 12.2 Verify preservation tests still pass
    - **Property 2: Preservation** - Existing Chat Behavior Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from task 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all preservation properties hold:
      - Group conversations display group name unchanged
      - Same-day timestamps return "HH:mm" format
      - Messages from other users insert correctly
      - Search filtering preserves pinned-first ordering
      - Offline message queuing continues working
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10_

- [x] 13. Checkpoint - Ensure all tests pass
  - Run full test suite to verify no regressions
  - Verify bug condition exploration test passes (all 15 bugs fixed)
  - Verify preservation property tests pass (no behavior regressions)
  - Verify Firestore security rules permit all legitimate operations
  - Verify performance improvements (debounced presence, targeted updates)
  - Ensure all tests pass, ask the user if questions arise.
