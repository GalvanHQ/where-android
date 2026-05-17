# Chat Messaging Fixes Bugfix Design

## Overview

The chat messaging system has 15 interrelated bugs spanning the chat header display, group creation UI, context menu actions, conversation list metadata, timestamp formatting, message delivery indicators, message duplication, Firestore security rules, and performance. This design formalizes the bug conditions, identifies root causes in the existing codebase, and outlines a minimal, targeted fix strategy that preserves all existing working behavior.

## Glossary

- **Bug_Condition (C)**: The set of conditions that trigger one or more of the 15 identified bugs — primarily: direct message conversations where participant metadata is not resolved, context menu operations that require Firestore field-level updates, message send flows where optimistic insert races with real-time delivery frames, and timestamp formatting that uses naive day subtraction
- **Property (P)**: The desired correct behavior — participant names/photos resolved from user profiles, online status propagated from presence tracking, context menu actions persisted to Firestore, messages appearing exactly once, timestamps using calendar-aware formatting
- **Preservation**: Existing working behaviors that must remain unchanged — group chat titles, message ordering, typing indicators, pull-to-refresh, offline queuing, search filtering, navigation, and retry mechanisms
- **`ConversationEntityMapper.toDomain()`**: The mapper in `data/local/entity/ConversationEntityMapper.kt` that converts Room entities to domain models — currently does not map `onlineMembers` or resolve participant names for direct messages
- **`toUiModel()`**: The extension function in `presentation/model/ConversationUiModel.kt` that converts domain `Conversation` to `ConversationUiModel` — relies on `participantNames` map parameter which is never populated by callers
- **`handleIncomingMessage()`**: The function in `MessageRepositoryImpl` that processes `ServerFrame.MessageDelivered` frames — currently inserts all incoming messages including the sender's own echoed messages, causing duplication
- **`formatConversationTimestamp()`**: The function in `presentation/model/TimestampFormatters.kt` that formats timestamps — uses `Calendar.DATE` subtraction which breaks across month boundaries

## Bug Details

### Bug Condition

The bugs manifest across multiple scenarios in the chat messaging system. The core bug condition encompasses any interaction where:
1. A direct message conversation is displayed (header or list) without resolved participant metadata
2. A context menu action (pin/mute/archive/delete) is performed on a conversation
3. A message is sent and the sender receives their own message back via the real-time delivery frame
4. A timestamp is formatted for a message from the previous month's last day

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type ChatInteraction
  OUTPUT: boolean
  
  RETURN (input.type == DISPLAY_CONVERSATION 
          AND input.conversation.type == DIRECT
          AND input.conversation.name.isBlank()
          AND participantNames[otherUserId] == null)
      OR (input.type == CONTEXT_MENU_ACTION
          AND input.action IN [PIN, MUTE, ARCHIVE, DELETE]
          AND (firestoreRulesRejectUpdate(input) OR deleteIsLocalOnly(input)))
      OR (input.type == MESSAGE_SENT
          AND senderReceivesOwnMessageDeliveredFrame(input)
          AND ackNotYetProcessed(input))
      OR (input.type == DISPLAY_TIMESTAMP
          AND input.timestamp.month != now.month
          AND input.timestamp.day == lastDayOfMonth
          AND now.day == 1)
      OR (input.type == DISPLAY_ONLINE_STATUS
          AND input.conversation.type == DIRECT
          AND onlineMembers.isEmpty()
          AND presenceFrameNotPropagated(input))
END FUNCTION
```

### Examples

- **Bug 1.1/1.9**: User opens a DM where `Conversation.name` is blank → header shows "Unknown User" because `participantNames` map is empty when `toUiModel()` is called from both `ChatViewModel.loadConversation()` and `ChatsViewModel.applySearchFilter()`
- **Bug 1.2**: User opens a DM → header shows "Offline" because `ConversationEntityMapper.toDomain()` never populates `onlineMembers` and `ChatsViewModel` presence tracking doesn't propagate to `ChatViewModel`'s conversation state
- **Bug 1.7**: User deletes a conversation → `ChatsViewModel.deleteConversation()` only calls `conversationRepository.deleteConversation()` which removes from Room but not Firestore → conversation reappears on next sync
- **Bug 1.10**: Message sent at 23:59 on Jan 31, viewed on Feb 1 → `Calendar.DATE` (31) - `Calendar.DATE` (1) = 30, not 1, so it shows "dd/MM/yy" instead of "Yesterday"
- **Bug 1.13**: User sends message → optimistic insert creates row with `tempId` → server echoes `MessageDelivered` frame with `serverId` before `MessageAck` arrives → `handleIncomingMessage()` inserts second row with `serverId` → two messages visible until ack updates `tempId` to `serverId`

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Group chat titles continue to display the group name with "{N} members" subtitle (Req 3.1)
- Messages continue to be sorted by timestamp ascending with ID as tiebreaker (Req 3.2)
- Typing indicators continue to display with correct user name and 5-second auto-hide (Req 3.3)
- Pull-to-refresh continues to fetch updated conversations without losing local state (Req 3.4)
- Offline message queuing continues with PENDING status and send-on-reconnect (Req 3.5)
- Scroll position and conversation state preserved during navigation (Req 3.6)
- Foreground sync continues without duplicate API calls (Req 3.7)
- Search filtering continues with 300ms debounce on name and last message text (Req 3.8)
- Conversation tap navigation continues with correct conversation ID argument (Req 3.9)
- Failed message retry with snackbar after 3 consecutive failures continues (Req 3.10)

**Scope:**
All inputs that do NOT involve the 15 identified bug conditions should be completely unaffected by this fix. This includes:
- All group conversation display logic (names, member counts, avatars)
- Message receiving from other users (non-sender echo)
- Voice message recording and playback
- Image upload and compression
- Location sharing display and tracking
- Reaction toggle and rollback
- WebSocket reconnection and missed message fetch

## Hypothesized Root Cause

Based on the bug description and code analysis, the root causes are:

1. **Missing Participant Name Resolution (Bugs 1.1, 1.9)**: The `toUiModel()` function accepts a `participantNames: Map<String, String>` parameter but both `ChatViewModel.loadConversation()` and `ChatsViewModel.applySearchFilter()` call it with the default empty map. The `Conversation.name` field is blank for direct messages (only groups have names), so `resolveConversationTitle()` falls through to "Unknown User".

2. **Missing Online Status Propagation (Bug 1.2)**: `ConversationEntityMapper.toDomain()` does not map `onlineMembers` (the field stays as `emptySet()`). The `ChatsViewModel` tracks `onlineUserIds` in memory but this set is not accessible from `ChatViewModel`. The `toUiModel()` checks `otherUid in onlineMembers` which is always false.

3. **Missing Group Creation UI Entry Point (Bug 1.3)**: The `onNavigateToCreateGroup` callback exists in the navigation graph but no UI element (FAB menu option, toolbar action) exposes it to the user on the chats screen.

4. **Firestore Security Rules for Conversations (Bugs 1.4, 1.5, 1.15)**: The rule `allow update: if isAuthenticated() && request.auth.uid in resource.data.participantIds` correctly requires the user to be a participant. However, for newly created conversations, the `create` rule only checks `isAuthenticated()` without validating the creator is in `participantIds`. The pin/mute operations update `pinnedBy`/`mutedBy` arrays which should work if the user is a participant — the issue may be that the Firestore document's `participantIds` field name doesn't match what the client writes.

5. **Local-Only Delete (Bug 1.7)**: `ChatsViewModel.deleteConversation()` calls `conversationRepository.deleteConversation()` which only removes from Room. No Firestore update marks the conversation as deleted for the user.

6. **Missing Profile Photo Resolution (Bug 1.8)**: The `ConversationEntity.photoUrl` field exists but is not populated from the other user's profile data for direct messages. The Firestore conversation document likely doesn't store individual user photos — they need to be resolved from the user profile.

7. **Naive Timestamp Calculation (Bug 1.10)**: `formatConversationTimestamp()` compares `Calendar.DATE` values directly (`now.DATE - msg.DATE == 1`). This breaks across month boundaries (e.g., Jan 31 → Feb 1 gives 1 - 31 = -30, not 1).

8. **Missing Date Context in Chat Messages (Bug 1.11)**: `formatMessageTime()` only returns "HH:mm" without any date context for messages from previous days.

9. **Incomplete Message Status UI (Bug 1.12)**: The `ReadReceiptIndicator` component may not handle all `MessageStatus` transitions (PENDING → SENT → DELIVERED → READ) with the correct icons.

10. **Message Duplication Race Condition (Bug 1.13)**: When the sender sends a message, the server echoes it back as a `MessageDelivered` frame. `handleIncomingMessage()` does `messageDao.insert(entity)` with the server ID. If this arrives before `handleAck()` updates the temp ID to the server ID, two rows exist in Room (one with tempId, one with serverId). The `insert` uses `OnConflictStrategy.REPLACE` on primary key, but since the IDs are different, no conflict is detected.

11. **Performance: Full List Re-mapping (Bug 1.14)**: Every presence update triggers `applySearchFilter()` which re-maps the entire conversation list. No `distinctUntilChanged` on the presence-specific state, and no debouncing of rapid presence changes.

## Correctness Properties

Property 1: Bug Condition - Participant Name Resolution

_For any_ direct message conversation where `Conversation.name` is blank and the other participant's user ID is known, the fixed system SHALL resolve and display the other participant's actual display name by looking up their profile data (from Room cache or Firestore), never showing "Unknown User" when profile data is available.

**Validates: Requirements 2.1, 2.8, 2.9**

Property 2: Preservation - Existing Conversation Display

_For any_ input that does NOT involve a direct message conversation with a blank name (i.e., group conversations, DMs with pre-populated names), the fixed code SHALL produce exactly the same display output as the original code, preserving group titles, member counts, and all existing formatting.

**Validates: Requirements 3.1, 3.2, 3.8, 3.9**

Property 3: Bug Condition - Online Status Display

_For any_ direct message conversation where the other user is online (as determined by WebSocket presence frames or cached `OnlineStatusDao` data), the fixed system SHALL display "Active now" in the chat header, and "Offline" only when the user is genuinely not online.

**Validates: Requirements 2.2**

Property 4: Bug Condition - Message Uniqueness

_For any_ message sent by the current user, the fixed system SHALL ensure the message appears exactly once in the chat screen, regardless of the ordering of `MessageAck` and `MessageDelivered` frame processing.

**Validates: Requirements 2.13**

Property 5: Bug Condition - Timestamp Formatting

_For any_ message timestamp, the fixed `formatConversationTimestamp()` function SHALL use calendar-aware date comparison (same calendar day, previous calendar day, same week, older) and never produce incorrect results due to month/year boundary crossings.

**Validates: Requirements 2.10, 2.11**

Property 6: Bug Condition - Context Menu Persistence

_For any_ context menu action (pin, mute, archive, delete) performed by an authenticated participant, the fixed system SHALL persist the change to both local Room database and Firestore, and the Firestore security rules SHALL permit the operation.

**Validates: Requirements 2.4, 2.5, 2.6, 2.7, 2.15**

Property 7: Preservation - Message Ordering and Offline Behavior

_For any_ input that does NOT involve the sender's own echoed message delivery frame, the fixed message handling code SHALL produce the same insertion behavior as the original code, preserving message ordering, offline queuing, and retry mechanisms.

**Validates: Requirements 3.2, 3.5, 3.7, 3.10**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `presentation/model/ConversationUiModel.kt`

**Function**: `resolveConversationTitle()` and `toUiModel()`

**Specific Changes**:
1. **Participant Name Resolution Pipeline**: Add a `UserRepository` lookup step in `ChatsViewModel` and `ChatViewModel` that resolves participant names for direct message conversations. Populate the `participantNames` map before calling `toUiModel()`. Cache resolved names in a local map to avoid repeated lookups.

---

**File**: `presentation/chat/ChatsViewModel.kt`

**Function**: `applySearchFilter()` and new `resolveParticipantMetadata()`

**Specific Changes**:
2. **Resolve Names and Photos in ChatsViewModel**: When `allConversations` is updated, resolve participant display names and photo URLs for all direct message conversations by querying `UserRepository` (Room-cached user profiles). Store in a `participantNames: Map<String, String>` and `participantPhotos: Map<String, String?>`. Pass these to `toUiModel()`.

3. **Propagate Online Status**: Use the existing `onlineUserIds` set to populate `isOtherUserOnline` in `ConversationUiModel` directly in `applySearchFilter()` instead of relying on `Conversation.onlineMembers`.

---

**File**: `presentation/chat/ChatViewModel.kt`

**Function**: `loadConversation()`

**Specific Changes**:
4. **Resolve Header Name and Online Status**: After loading the conversation, if it's a direct message with a blank name, fetch the other user's profile from `UserRepository` and update the UI model's title. Subscribe to presence frames for the other user's ID to update online status in real-time.

---

**File**: `presentation/chat/ChatsScreen.kt` (or equivalent composable)

**Specific Changes**:
5. **Add Group Creation UI Entry Point**: Add an expandable FAB menu or a second FAB option that navigates to `CreateGroupScreen`. Wire the existing `onNavigateToCreateGroup` callback to this new UI element.

---

**File**: `firestore.rules`

**Function**: Conversations collection rules

**Specific Changes**:
6. **Fix Firestore Security Rules**: 
   - Add `allow create: if isAuthenticated() && request.auth.uid in request.resource.data.participantIds` to validate creator is a participant
   - Ensure `update` rule permits field-level updates to `pinnedBy`, `mutedBy`, and `archivedBy` arrays by participants
   - Add a `deletedBy` field approach: instead of document deletion, allow participants to add themselves to a `deletedBy` array

---

**File**: `presentation/chat/ChatsViewModel.kt`

**Function**: `deleteConversation()`

**Specific Changes**:
7. **Persist Delete to Firestore**: Change `deleteConversation()` to also update the Firestore document by adding the current user's ID to a `deletedBy` array field (soft delete per user). Filter out conversations where the current user is in `deletedBy` when loading.

---

**File**: `presentation/model/TimestampFormatters.kt`

**Function**: `formatConversationTimestamp()`

**Specific Changes**:
8. **Calendar-Aware Timestamp Formatting**: Replace naive `Calendar.DATE` subtraction with proper calendar day comparison:
   - Compare year + day-of-year for "today" check
   - Subtract 1 calendar day from `now` and compare for "yesterday" check
   - Check if within the same calendar week for day-of-week display
   - Fall back to "dd/MM/yy" for older dates

---

**File**: `presentation/model/TimestampFormatters.kt`

**Function**: `formatMessageTime()`

**Specific Changes**:
9. **Add Date Context to Message Timestamps**: For messages not from today, include date context (e.g., "Mon 14:32" or "12 Jan 14:32"). The existing `formatDateSeparatorLabel()` already handles date separators correctly.

---

**File**: `data/repository/MessageRepositoryImpl.kt`

**Function**: `handleIncomingMessage()`

**Specific Changes**:
10. **Prevent Sender's Own Message Duplication**: Before inserting a `MessageDelivered` frame, check if `frame.senderId == currentUserId`. If so, skip the insert — the message already exists locally from the optimistic insert. The `handleAck()` will update the temp ID to the server ID. This eliminates the race condition entirely.

---

**File**: `data/repository/MessageRepositoryImpl.kt`

**Function**: `handleIncomingMessage()` (alternative safeguard)

**Specific Changes**:
11. **Deduplication Safeguard**: As a secondary defense, before inserting any `MessageDelivered` frame, check if a message with the same `conversationId`, `senderId`, `text`, and `timestamp` (within 1-second tolerance) already exists in Room. If so, update the existing row's ID to the server ID instead of inserting a new row.

---

**File**: `presentation/chat/ChatsViewModel.kt`

**Function**: `observePresenceUpdates()` and `applySearchFilter()`

**Specific Changes**:
12. **Performance: Debounce Presence Updates**: Instead of calling `applySearchFilter()` on every presence change, debounce presence-triggered re-renders by 500ms. Use `distinctUntilChanged` on the online status of each conversation to avoid unnecessary recomposition. Only update the specific conversation's `isOtherUserOnline` field rather than re-mapping the entire list.

---

**File**: `presentation/chat/ChatsViewModel.kt`

**Function**: `applySearchFilter()`

**Specific Changes**:
13. **Performance: Targeted Updates**: When only a presence change occurs (not a full conversation list update), update only the affected `ConversationUiModel.isOtherUserOnline` field in the existing list rather than re-mapping all conversations.

---

**File**: `data/local/entity/ConversationEntity.kt` and `ConversationEntityMapper.kt`

**Specific Changes**:
14. **Store Participant Metadata**: Add `participantNamesJson` and `participantPhotosJson` fields to `ConversationEntity`. Populate these when syncing conversations from Firestore by resolving user profiles. Map them in `toDomain()` so the domain model carries participant metadata.

---

**File**: `presentation/chat/components/ReadReceiptIndicator.kt` (or equivalent)

**Specific Changes**:
15. **Complete Message Status Indicators**: Ensure the indicator component renders all states: clock icon for PENDING, single checkmark for SENT, double checkmarks (grey) for DELIVERED, double checkmarks (blue/filled) for READ.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bugs on unfixed code, then verify the fixes work correctly and preserve existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bugs BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write unit tests that exercise the specific code paths identified as buggy. Run these tests on the UNFIXED code to observe failures and confirm root causes.

**Test Cases**:
1. **Name Resolution Test**: Create a `Conversation` with blank `name`, type `DIRECT`, and call `toUiModel()` with empty `participantNames` → assert title is "Unknown User" (will confirm bug on unfixed code)
2. **Online Status Test**: Create a `Conversation` with `onlineMembers = emptySet()` and verify `isOtherUserOnline` is false even when presence frames indicate online (will confirm bug on unfixed code)
3. **Timestamp Month Boundary Test**: Call `formatConversationTimestamp()` with a timestamp from Jan 31 when current date is Feb 1 → assert result is NOT "Yesterday" (will confirm bug on unfixed code)
4. **Message Duplication Test**: Simulate `handleIncomingMessage()` with `senderId == currentUserId` and verify a second row is inserted (will confirm bug on unfixed code)
5. **Delete Persistence Test**: Call `deleteConversation()` and verify Firestore is NOT updated (will confirm bug on unfixed code)

**Expected Counterexamples**:
- `resolveConversationTitle()` returns "Unknown User" for all DMs with blank names
- `formatConversationTimestamp()` returns "dd/MM/yy" for yesterday's messages at month boundaries
- `handleIncomingMessage()` inserts duplicate rows for sender's own messages
- Possible causes: missing participant name lookup, naive date arithmetic, missing sender check in message handler

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed functions produce the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedSystem(input)
  ASSERT expectedBehavior(result)
END FOR
```

Specifically:
- For all DM conversations with blank names: assert title == other user's display name
- For all timestamps at month boundaries: assert correct "Yesterday"/"Today" classification
- For all sent messages: assert exactly one row in Room after both Ack and Delivered frames arrive
- For all context menu actions by participants: assert Firestore update succeeds

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed functions produce the same result as the original functions.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalFunction(input) = fixedFunction(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for group conversations, non-sender messages, and same-day timestamps, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Group Title Preservation**: Verify group conversations continue to display group name correctly after fix
2. **Message Ordering Preservation**: Verify messages from other users continue to be inserted and sorted correctly
3. **Search Filter Preservation**: Verify conversation search continues to filter by name and last message text
4. **Offline Queue Preservation**: Verify offline message queuing and send-on-reconnect continues working
5. **Timestamp Same-Day Preservation**: Verify same-day timestamps continue to show "HH:mm" format

### Unit Tests

- Test `resolveConversationTitle()` with populated `participantNames` map returns correct name
- Test `resolveConversationTitle()` with blank name and empty map returns "Unknown User" (pre-fix baseline)
- Test `formatConversationTimestamp()` across month boundaries (Jan 31 → Feb 1, Dec 31 → Jan 1)
- Test `formatConversationTimestamp()` across year boundaries
- Test `formatConversationTimestamp()` for same-day, yesterday, same-week, and older dates
- Test `handleIncomingMessage()` skips insert when `senderId == currentUserId`
- Test `handleAck()` correctly updates tempId to serverId
- Test `deleteConversation()` updates both Room and Firestore
- Test pin/mute/archive operations persist to Firestore
- Test online status resolution from `OnlineStatusDao` and presence frames

### Property-Based Tests

- Generate random `Conversation` instances with various `name`/`type`/`participantIds` combinations and verify `resolveConversationTitle()` never returns blank
- Generate random timestamps across all months/years and verify `formatConversationTimestamp()` produces valid output matching calendar-aware rules
- Generate random message send sequences with interleaved Ack/Delivered frames and verify exactly one message row per logical message exists in Room
- Generate random conversation lists and verify `applySearchFilter()` produces correctly sorted results with pinned conversations at top
- Generate random presence update sequences and verify only the affected conversation's online status changes (no unrelated conversation mutations)

### Integration Tests

- Test full flow: create DM conversation → open chat screen → verify header shows resolved name and correct online status
- Test full flow: send message → receive Ack → receive Delivered echo → verify single message in UI
- Test full flow: long-press conversation → select Pin → verify pinned state persists across app restart
- Test full flow: long-press conversation → select Delete → verify conversation does not reappear after sync
- Test full flow: FAB menu → select "New Group" → verify navigation to CreateGroupScreen
- Test full flow: send message at 23:59 → view next day → verify "Yesterday" timestamp
