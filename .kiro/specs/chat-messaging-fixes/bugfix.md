# Bugfix Requirements Document

## Introduction

The chat messaging system has multiple interrelated bugs affecting core functionality: the chat screen header displays "Unknown User" and "Offline" instead of resolving the actual participant name and online status; group creation fails; the chats screen context menu actions (pin, mute, archive, delete) may not function correctly; user profile pictures, names, and timestamps are not displaying properly on the chats list screen; message delivery/sent/seen indicators are broken; messages appear duplicated after sending (optimistic insert + real-time listener conflict); Firestore security rules may be insufficient for chat/group operations; and overall performance is degraded.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a user opens a 1:1 chat screen THEN the header displays "Unknown User" as the conversation title because the `Conversation.name` field is blank for direct messages and the `participantNames` map is not passed to the `toUiModel()` mapper from `ChatViewModel.loadConversation()`

1.2 WHEN a user opens a 1:1 chat screen THEN the header always displays "Offline" because `Conversation.onlineMembers` is never populated from Room (the `ConversationEntityMapper.toDomain()` does not map online status) and the `ChatsViewModel` presence tracking does not propagate to the individual `ChatScreen`'s conversation state

1.3 WHEN a user wants to create a group from the chats screen THEN there is no visible UI entry point (button, FAB menu, or navigation option) to reach the CreateGroupScreen — the FAB only navigates to the search/start conversation screen, and the `onNavigateToCreateGroup` callback is wired but never exposed to the user through any UI element

1.4 WHEN a user long-presses a conversation on the chats list screen and selects "Pin" THEN the pin operation may fail silently if the Firestore `conversations` collection rules do not allow updating the `pinnedBy` array field for the authenticated user

1.5 WHEN a user long-presses a conversation and selects "Mute" THEN the mute operation may fail silently if the Firestore rules do not allow updating the `mutedBy` array field

1.6 WHEN a user long-presses a conversation and selects "Archive" THEN the archive operation may not persist correctly or may not remove the conversation from the visible list

1.7 WHEN a user long-presses a conversation and selects "Delete" THEN the delete operation only removes from local Room database without deleting from Firestore, causing the conversation to reappear on next sync

1.8 WHEN the chats list screen loads conversations THEN user profile pictures are not displayed because the `photoUrl` field in `ConversationEntity` is not being populated from the Firestore conversation document or the other user's profile data

1.9 WHEN the chats list screen loads conversations THEN the conversation title shows "Unknown User" for direct messages because `participantNames` metadata is not resolved when mapping conversations in `ChatsViewModel.applySearchFilter()`

1.10 WHEN the chats list screen displays the last message timestamp THEN the formatting does not account for edge cases like same-year vs different-year dates, and the `formatConversationTimestamp` function uses a simplistic day-difference calculation that breaks across month boundaries

1.11 WHEN a user views message timestamps in the chat screen THEN only "HH:mm" format is shown without date context, making it unclear when messages from previous days were sent

1.12 WHEN a user sends a message THEN the message delivery/sent/seen status indicators do not update correctly because the `MessageStatus` transitions (PENDING → SENT → DELIVERED → READ) are not fully reflected in the UI via `ReadReceiptIndicator`

1.13 WHEN a user sends a message THEN the message appears twice in the chat screen because the optimistic insert into Room creates one entry with a temp ID, and the real-time WebSocket `MessageDelivered` frame (echoed back for the sender's own message) inserts a second entry with the server ID before the `MessageAck` handler can update the temp ID to the server ID

1.14 WHEN the app loads conversations or messages THEN performance is noticeably slow because: (a) the `observeConversationsUseCase` flow triggers recomposition of the entire list on any change, (b) presence updates cause full list re-mapping via `applySearchFilter()`, and (c) there is no pagination or lazy loading optimization for the conversation list

1.15 WHEN a user performs chat operations (create conversation, send message, update pin/mute) THEN Firestore security rules may reject legitimate operations because the `conversations` collection rules require `request.auth.uid in resource.data.participantIds` for updates, which fails on newly created conversations where the document doesn't yet exist in the security rule evaluation context

### Expected Behavior (Correct)

2.1 WHEN a user opens a 1:1 chat screen THEN the header SHALL display the other participant's actual display name by resolving it from the user profile data (either cached in Room or fetched from Firestore) and passing it through the `participantNames` map to the `toUiModel()` mapper

2.2 WHEN a user opens a 1:1 chat screen THEN the header SHALL display "Active now" in green/tertiary color when the other user is online (determined via WebSocket presence frames or cached online status from `OnlineStatusDao`) and "Offline" only when the user is genuinely not online

2.3 WHEN a user wants to create a group THEN the system SHALL provide a visible and accessible UI entry point (such as an expandable FAB menu with "New Chat" and "New Group" options, or a dedicated "Create Group" option in the chats screen) that navigates to the CreateGroupScreen, and upon valid input the group SHALL be created successfully in Firestore with the associated conversation document

2.4 WHEN a user long-presses a conversation and selects "Pin" THEN the system SHALL persist the pin state to Firestore by updating the conversation's `pinnedBy` array, reflect the change immediately in the UI via optimistic update, and sort pinned conversations to the top of the list

2.5 WHEN a user long-presses a conversation and selects "Mute" THEN the system SHALL persist the mute state to Firestore, suppress unread badge display for the muted conversation, and show a mute icon indicator on the conversation row

2.6 WHEN a user long-presses a conversation and selects "Archive" THEN the system SHALL move the conversation to an archived state in Firestore and remove it from the active conversation list immediately

2.7 WHEN a user long-presses a conversation and selects "Delete" THEN the system SHALL remove the conversation from both the local Room database and Firestore (or mark it as deleted for the current user in Firestore) so it does not reappear on subsequent syncs

2.8 WHEN the chats list screen loads conversations THEN the system SHALL display the other user's profile picture by resolving the `photoUrl` from the participant's user profile data and passing it to the `ConversationUiModel`

2.9 WHEN the chats list screen loads direct message conversations THEN the system SHALL display the other participant's actual display name by resolving participant metadata and never show "Unknown User" for conversations where the other user's profile is available

2.10 WHEN the chats list screen displays the last message timestamp THEN the system SHALL use proper calendar-aware date comparison (not simple day subtraction) and format as: "HH:mm" for today, "Yesterday" for the previous calendar day, day-of-week name for the current week, and "dd/MM/yy" for older dates

2.11 WHEN a user views messages in the chat screen THEN timestamps SHALL include date context for messages not from today, and date separators SHALL clearly delineate messages from different days using "Today", "Yesterday", or the formatted date

2.12 WHEN a user sends a message THEN the delivery status indicator SHALL update progressively: show a clock icon for PENDING, a single checkmark for SENT (server acknowledged), double checkmarks for DELIVERED, and blue/filled double checkmarks for READ

2.13 WHEN a user sends a message THEN the message SHALL appear exactly once in the chat screen by ensuring the `MessageAck` handler updates the temp ID to the server ID atomically before any `MessageDelivered` frame for the same message can insert a duplicate, or by filtering out the sender's own messages from the `MessageDelivered` handler

2.14 WHEN the app loads conversations or messages THEN the system SHALL respond within acceptable performance thresholds by: (a) using `distinctUntilChanged` on presence updates to avoid unnecessary recomposition, (b) debouncing rapid state changes, (c) using stable keys in LazyColumn for efficient diffing, and (d) avoiding full list re-mapping when only a single conversation's online status changes

2.15 WHEN a user performs chat operations THEN Firestore security rules SHALL permit all legitimate operations including: conversation creation by authenticated users, conversation updates (pin/mute/archive) by participants, message creation by the sender, and group conversation creation with the initial participant list

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a user opens a group chat screen THEN the system SHALL CONTINUE TO display the group name as the conversation title and show "{N} members" as the subtitle

3.2 WHEN a user receives a message from another user THEN the system SHALL CONTINUE TO display the message in the correct position (sorted by timestamp ascending) with the sender's name and avatar

3.3 WHEN a user is in a conversation and the other user starts typing THEN the system SHALL CONTINUE TO display the typing indicator with the correct user name and auto-hide after 5 seconds of inactivity

3.4 WHEN a user pulls to refresh on the chats list screen THEN the system SHALL CONTINUE TO fetch updated conversations from the server and update the list without losing local state (pinned, muted status)

3.5 WHEN a user sends a message while offline THEN the system SHALL CONTINUE TO queue the message locally with PENDING status and send it when connectivity is restored

3.6 WHEN a user navigates between the chats list and individual chat screens THEN the system SHALL CONTINUE TO preserve scroll position and conversation state without unnecessary data reloading

3.7 WHEN the app resumes from background THEN the system SHALL CONTINUE TO sync unread counts via the foreground sync mechanism without triggering duplicate API calls

3.8 WHEN a user searches for conversations on the chats list screen THEN the system SHALL CONTINUE TO filter conversations by name and last message text with 300ms debounce

3.9 WHEN a user taps on a conversation in the chats list THEN the system SHALL CONTINUE TO navigate to the correct chat screen with the conversation ID passed as a navigation argument

3.10 WHEN a message send fails after 3 consecutive attempts THEN the system SHALL CONTINUE TO display the "Message could not be sent" snackbar for 4 seconds and mark the message as FAILED with a retry affordance
