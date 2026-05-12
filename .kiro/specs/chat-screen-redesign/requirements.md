# Requirements Document

## Introduction

This document defines the requirements for the WHERE app's Chat Screen Redesign feature. The redesign encompasses the Chat List screen (ChatsScreen), individual Chat screen, Group Creation flow, and GroupInfoScreen. The system uses a Socket.IO + REST hybrid architecture for messaging, Room local caching for offline support, Firestore read optimization (metadata only), optimistic message sending, and a dark theme with glassmorphism design language ("Glassy. Dynamic. Warm.").

## Glossary

- **ChatScreen**: The individual conversation view composable that displays messages, input bar, and conversation header
- **ChatsScreen**: The conversation list screen composable that displays all user conversations
- **CreateGroupScreen**: The group creation flow composable with member selection and avatar picker
- **GroupInfoScreen**: The group details screen composable showing members, settings, and shared media
- **MessageRepository**: The data layer component responsible for message persistence, retrieval, pagination, and real-time delivery
- **ChatSocketIoClient**: The Socket.IO client singleton managing real-time WebSocket communication for messages, reactions, typing, and presence
- **ConversationRepository**: The data layer component responsible for conversation metadata persistence and observation
- **ChatViewModel**: The ViewModel managing state for the individual chat screen
- **ChatsViewModel**: The ViewModel managing state for the conversation list screen
- **CreateGroupViewModel**: The ViewModel managing state for the group creation flow
- **Room_Cache**: The local SQLite database (via Room) used for offline message and conversation storage
- **Firestore_Listener**: The Firestore snapshot listener scoped to conversation metadata only
- **Optimistic_Message**: A message inserted locally with PENDING status before server acknowledgment
- **Cursor**: An opaque pagination token representing a position in the message history
- **MessagePage**: A data structure containing a list of messages, a next cursor, and a hasMore flag

## Requirements

### Requirement 1: Message Sending with Optimistic Updates

**User Story:** As a user, I want my messages to appear instantly in the chat when I tap send, so that the conversation feels responsive regardless of network conditions.

#### Acceptance Criteria

1. WHEN a user sends a text message, THE MessageRepository SHALL insert the message into the in-memory message cache with PENDING status and emit it to observers within 100 milliseconds of the send invocation.
2. WHEN the ChatSocketIoClient emits a MessageAck frame whose `tempId` matches a PENDING message, THE MessageRepository SHALL update that message's status from PENDING to SENT and replace the temporary ID with the server-assigned `id` from the ack.
3. IF a MessageAck is not received within 10 seconds of sending, or the ChatSocketIoClient emits a ServerFrame.Error for the message, THEN THE MessageRepository SHALL update the message status to FAILED and the UI SHALL display a tap-to-retry affordance on that message bubble.
4. WHEN a user taps a FAILED message, THE MessageRepository SHALL re-attempt sending via ChatSocketIoClient exactly once per tap, resetting the 10-second ack timeout for that attempt.
5. IF a message send fails on 3 consecutive retry attempts (initial send plus 2 retaps), THEN THE ChatScreen SHALL display a snackbar notification "Message could not be sent" for 4 seconds and the message SHALL remain in FAILED state with the retry affordance still visible.
6. WHILE the ChatSocketIoClient connectionState is DISCONNECTED or ERROR, THE MessageRepository SHALL enqueue outgoing messages in PENDING status in send order (FIFO) up to a maximum of 50 queued messages, and SHALL re-send them in order when connectionState transitions to CONNECTED.
7. IF the offline queue reaches 50 messages, THEN THE MessageRepository SHALL reject new send attempts and the ChatScreen SHALL display an inline indicator that messaging is unavailable until connectivity is restored.

### Requirement 2: Cursor-Based Message Pagination

**User Story:** As a user, I want to scroll up to load older messages without loading the entire history at once, so that the app remains performant on long conversations.

#### Acceptance Criteria

1. WHEN the ChatScreen is opened, THE ChatViewModel SHALL load the most recent 30 messages from Room_Cache and emit them to the UI, then request the most recent 30 messages from the server and merge them into Room_Cache replacing any stale entries with matching IDs and appending any new entries.
2. WHEN the user scrolls within 5 items of the top of the message list, THE ChatViewModel SHALL request the next page of 30 older messages using the timestamp of the oldest currently loaded message as the pagination cursor.
3. WHILE a pagination request is in flight, THE ChatViewModel SHALL expose a loading state and THE ChatScreen SHALL display a loading indicator at the top of the message list, and SHALL NOT issue additional pagination requests until the current one completes.
4. WHEN a page of older messages is loaded, THE MessageRepository SHALL persist all messages to Room_Cache and prepend them to the displayed list.
5. WHEN older messages are prepended, THE ChatScreen SHALL maintain the user's current scroll position so the visible message does not shift.
6. WHEN the server returns a MessagePage with hasMore equal to false, THE ChatViewModel SHALL stop requesting additional pages and SHALL NOT display the loading indicator on subsequent scrolls to the top.
7. IF a pagination request fails, THEN THE ChatScreen SHALL display an inline retry button at the top of the message list without clearing existing messages.

### Requirement 3: Message Reactions

**User Story:** As a user, I want to react to messages with emoji, so that I can express quick responses without typing a full message.

#### Acceptance Criteria

1. WHEN a user long-presses a message bubble, THE ChatScreen SHALL display a reaction picker overlay containing exactly 6 emoji options (👍, ❤️, 😂, 😮, 😢, 🙏) anchored to the long-pressed message bubble.
2. WHEN a user taps outside the reaction picker overlay or presses the system back gesture, THE ChatScreen SHALL dismiss the reaction picker without applying any reaction.
3. WHEN a user selects an emoji reaction from the picker, THE MessageRepository SHALL optimistically append the Caller's uid to that emoji's reactor list on the local message and emit the reaction to the server via ChatSocketIoClient.
4. WHEN a user selects an emoji they have already reacted with on the same message, THE MessageRepository SHALL remove the Caller's uid from that emoji's reactor list on the local message and emit the removal to the server via ChatSocketIoClient (toggle behavior).
5. WHEN a reaction toggle operation is performed twice with the same emoji on the same message, THE MessageRepository SHALL return the message to its original reaction state for that user.
6. IF the server does not acknowledge a reaction emit within 10 seconds or returns an error, THEN THE MessageRepository SHALL rollback the optimistic update to the previous reaction state.
7. WHEN a reaction update is received from the server, THE ChatScreen SHALL display each reacted emoji below the message bubble showing the emoji followed by the count of distinct reactors when the count is 2 or more, or the emoji alone when exactly one user has reacted.
8. WHEN multiple users react with the same emoji on a single message, THE ChatScreen SHALL display a single aggregated badge for that emoji with the total reactor count rather than separate badges per user.

### Requirement 4: Reply and Quote Messages

**User Story:** As a user, I want to reply to a specific message, so that I can maintain context in group conversations.

#### Acceptance Criteria

1. WHEN a user long-presses a message bubble, THE ChatScreen SHALL display a context action that allows initiating a reply to that message.
2. WHEN a user initiates a reply action on a message, THE ChatScreen SHALL display a reply preview bar above the input field showing the original sender name and up to 100 characters of the original message text, with a close button to dismiss the reply.
3. WHEN the user taps the close button on the reply preview bar, THE ChatScreen SHALL dismiss the reply preview and clear the reply-to state without affecting the input text.
4. WHEN a reply message is sent, THE MessageRepository SHALL include the replyToId, replyToText (truncated to 150 characters), and replyToSenderName in the message payload.
5. WHEN a message with a replyToId is displayed, THE ChatScreen SHALL render a quoted preview above the reply text showing the replyToSenderName and up to 100 characters of replyToText.
6. WHEN a user taps a quoted message preview, THE ChatScreen SHALL scroll to the original message in the conversation.
7. IF the original message referenced by replyToId is not present in the loaded message history, THEN THE ChatScreen SHALL still render the quoted preview using the stored replyToText and replyToSenderName, and tapping it SHALL have no scroll effect.

### Requirement 5: Read Receipts

**User Story:** As a user, I want to see when my messages have been read, so that I know my communication has been received.

#### Acceptance Criteria

1. WHEN a user opens a conversation containing unread messages not sent by the Caller, THE ChatViewModel SHALL emit a single read event via ChatSocketIoClient covering all unread messages in that conversation up to and including the most recent one.
2. WHEN a read receipt ServerFrame is received from the server identifying a message ID and a user ID, THE MessageRepository SHALL append that user ID to the message's readBy list if not already present, ensuring no duplicate entries.
3. THE ChatScreen SHALL display a read receipt indicator (double-tick icon followed by up to 3 reader avatars) beneath each sent message whose readBy list contains at least one user ID other than the sender; IF readBy contains more than 3 readers, THEN THE ChatScreen SHALL display 3 avatars and a "+N" overflow count.
4. WHEN a message is marked as read by a user, THE MessageRepository SHALL ensure the readBy list only grows monotonically and SHALL NOT remove any user ID from the list.
5. IF the ChatSocketIoClient connection state is DISCONNECTED or ERROR when the user opens a conversation, THEN THE ChatViewModel SHALL defer emitting the read event until the connection state transitions to CONNECTED.
6. THE ChatScreen SHALL display read receipt indicators only on messages sent by the Caller and SHALL NOT display them on received messages.

### Requirement 6: Media and Image Messages

**User Story:** As a user, I want to send and receive images in chat, so that I can share visual content with my contacts.

#### Acceptance Criteria

1. WHEN a user selects an image to send, THE MessageRepository SHALL compress the image to a maximum of 1920px on the longest edge at 80% JPEG quality before uploading, accepting source images in JPEG, PNG, WebP, or HEIF format.
2. WHILE an image is being uploaded, THE ChatScreen SHALL display a determinate progress bar overlay on the image thumbnail showing upload percentage from 0 to 100%.
3. IF a selected image exceeds 10MB before compression, THEN THE ChatScreen SHALL display an inline error message below the image picker indicating the size limit was exceeded, without attempting upload.
4. IF an image upload fails, THEN THE ChatScreen SHALL display an error overlay with a retry icon on the image thumbnail; WHEN the user taps the retry icon, THE ChatScreen SHALL re-attempt the upload up to a maximum of 3 retry attempts.
5. IF an image upload does not complete within 60 seconds, THEN THE MessageRepository SHALL cancel the upload and THE ChatScreen SHALL display the failed-upload error overlay as defined in criterion 4.
6. WHEN an image message is received, THE ChatScreen SHALL render a solid placeholder matching the image's aspect ratio (or a 4:3 default when aspect ratio is unknown) until the image finishes loading.
7. IF all 3 retry attempts for an image upload are exhausted, THEN THE ChatScreen SHALL display a persistent error overlay on the thumbnail with a message indicating the send failed and offering a final retry action.

### Requirement 7: Typing Indicators

**User Story:** As a user, I want to see when other participants are typing, so that I know a response is being composed.

#### Acceptance Criteria

1. WHEN a user produces any keystroke in the chat input field that results in a non-empty text value, THE ChatSocketIoClient SHALL emit at most one typing event per 300-millisecond window to the server, suppressing additional emissions until the window elapses.
2. WHEN a typing event is received from another user, THE ChatScreen SHALL display an animated 3-dot typing indicator positioned below the last message and above the input bar, showing the typing user's display name in the format "{name} is typing…".
3. WHEN a user produces no keystroke for more than 3 seconds while the input field is non-empty, THE ChatSocketIoClient SHALL emit a stop-typing event to the server.
4. WHEN a user sends a message or clears the input field to empty, THE ChatSocketIoClient SHALL immediately emit a stop-typing event regardless of the debounce window.
5. IF no stop-typing event is received within 5 seconds of the last typing event from a given user, THEN THE ChatScreen SHALL automatically hide the typing indicator for that user.
6. WHEN typing events are received from multiple users in a group conversation, THE ChatScreen SHALL display at most 2 user names followed by a count suffix in the format "{name1}, {name2} +N are typing…" where N is the remaining count of additional typing users.

### Requirement 8: Online and Offline Presence

**User Story:** As a user, I want to see which contacts are currently online, so that I can know who is available for real-time conversation.

#### Acceptance Criteria

1. WHEN a presence update indicating online status is received via ChatSocketIoClient for a user, THE ChatsScreen SHALL display a 10dp circular online indicator anchored to the bottom-end of that user's avatar.
2. WHEN a presence update indicating offline status is received via ChatSocketIoClient for a user, THE ChatsScreen SHALL remove the online indicator from that user's avatar.
3. WHEN a user navigates to a ChatScreen for a direct conversation, THE ChatScreen header SHALL display the other user's current status as either "Online" or "Offline" text below the contact name.
4. IF the other user in a direct conversation is not in the Caller's friends list, THEN THE ChatScreen header SHALL display no presence status text.
5. WHEN the online indicator is rendered, THE ChatsScreen SHALL use the semantic `tertiary` color from the MaterialTheme color scheme and SHALL provide a content description of "{displayName} is online" for accessibility.

### Requirement 9: Conversation List (ChatsScreen)

**User Story:** As a user, I want to see all my conversations in a list sorted by most recent activity, so that I can quickly find and resume conversations.

#### Acceptance Criteria

1. THE ChatsScreen SHALL display conversations sorted by lastMessageTimestamp in descending order.
2. WHEN a conversation has unread messages, THE ChatsScreen SHALL highlight it with a Background Elevated tint and display an unread count badge showing the numeric count capped at "99+" for counts exceeding 99.
3. WHEN a user types at least 1 character into the search field, THE ChatsViewModel SHALL filter the conversation list locally to show only conversations whose name or last message content contains the query (case-insensitive substring match).
4. WHEN the user clears the search field or deletes all characters, THE ChatsViewModel SHALL restore the full unfiltered conversation list.
5. WHEN a user swipes a conversation item horizontally from right to left, THE ChatsScreen SHALL reveal archive and mute action buttons.
6. WHEN no conversations exist, THE ChatsScreen SHALL display an empty state containing an illustration icon, the headline "No chats yet", a supporting body text, and a primary CTA that navigates to Search_Users_Screen.
7. THE ChatsScreen SHALL display a 10dp online status dot on conversation avatars for direct conversations where the other participant is currently online.
8. THE ChatsScreen SHALL key each LazyColumn conversation row by the conversation id.

### Requirement 10: Group Creation

**User Story:** As a user, I want to create group conversations with selected members, so that I can communicate with multiple people simultaneously.

#### Acceptance Criteria

1. WHEN a user enters a group name shorter than 3 characters or longer than 50 characters, THE CreateGroupScreen SHALL prevent group creation and display a validation message indicating the allowed length range of 3 to 50 characters.
2. WHEN a user types at least 2 characters into the member search field, THE CreateGroupViewModel SHALL query the server after a 300-millisecond debounce and display up to 20 matching users excluding already-selected members and the current user.
3. WHEN a user selects a member, THE CreateGroupScreen SHALL add them to a chip row and remove them from search results; WHEN a user taps the remove action on a chip, THE CreateGroupScreen SHALL remove that member from the chip row and restore them to search results.
4. WHEN a user taps Create with a group name between 3 and 50 characters and at least 1 selected member (maximum 50 members), THE CreateGroupViewModel SHALL upload the avatar (if selected), create the group via the REST API, and navigate to the new group chat.
5. IF group creation fails, THEN THE CreateGroupScreen SHALL display an error message indicating the failure reason while preserving the group name, description, and selected members for retry.
6. WHEN a group is successfully created, THE CreateGroupScreen SHALL display the invite code and a share button that triggers the system share sheet with the invite code.
7. IF the user taps Create with zero selected members, THEN THE CreateGroupScreen SHALL display a validation message indicating that at least 1 member must be selected.

### Requirement 11: Group Info and Management

**User Story:** As a group admin, I want to manage group details and members, so that I can maintain the group's purpose and membership.

#### Acceptance Criteria

1. THE GroupInfoScreen SHALL display the group avatar, name, description, and member list ordered by role (admins first) then by display name ascending, with each member row showing a text role label ("Admin" or "Member").
2. WHEN an admin taps remove on a member row and confirms the action in a confirmation dialog, THE GroupInfoScreen SHALL call the kick-member server operation, remove the member from the displayed list on success, and show a snackbar indicating the member was removed.
3. WHILE the current user's role is not admin, THE GroupInfoScreen SHALL hide admin-only actions (remove member, delete group).
4. WHEN a user taps "Leave Group" and confirms the action in a confirmation dialog, THE GroupInfoScreen SHALL call the leave-group server operation and navigate back to the conversation list on success.
5. IF the current user is the only admin in the group, THEN THE GroupInfoScreen SHALL disable the "Leave Group" action and display a message indicating the user must promote another member to admin before leaving.
6. IF a server operation (remove member, leave group, or delete group) fails, THEN THE GroupInfoScreen SHALL display an error snackbar with a message indicating the failure and SHALL NOT modify the displayed member list or navigation state.
7. THE GroupInfoScreen SHALL display a shared media gallery section showing the most recent 20 media items (images and videos) exchanged in the group conversation, with a "See All" action to view the full gallery.

### Requirement 12: Firestore Read Optimization

**User Story:** As a system operator, I want to minimize Firestore read operations, so that the app remains cost-effective and performant at scale.

#### Acceptance Criteria

1. THE ConversationRepository SHALL expose conversation list data to the UI exclusively via Room database flows, treating the local Room database as the single source of truth for the conversation list UI.
2. THE Firestore_Listener on the conversations collection SHALL observe only the fields `name`, `participantIds`, `photoUrl`, `type`, `groupId`, `lastMessageText`, `lastMessageSenderId`, `lastMessageTimestamp`, and `unreadCounts`, and SHALL NOT open any listener on the messages subcollection or individual message documents.
3. WHEN the Firestore_Listener emits a conversation snapshot, THE ConversationRepository SHALL write the updated conversation metadata into the Room database so that the UI flow emits the change within 500 milliseconds of the Firestore event.
4. THE MessageRepository SHALL retrieve all message content exclusively from the Room database, populated via the REST API on history load and via ChatSocketIoClient for real-time delivery, and SHALL NOT issue any Firestore document reads for message content.
5. WHEN the app returns to foreground, THE ConversationRepository SHALL sync unread counts from the server via a single REST API call that returns all conversation unread counts in one response, completing within a timeout of 10 seconds.
6. IF the foreground unread-count sync fails or times out, THEN THE ConversationRepository SHALL retain the existing Room-cached unread counts and emit a recoverable error without blocking the conversation list UI.
7. WHEN the Room database contains no conversation records on first launch, THE ConversationRepository SHALL fetch the initial conversation list from the REST API and persist it to Room before the Firestore_Listener begins incremental updates.

### Requirement 13: Socket.IO Connection Management

**User Story:** As a user, I want the chat to automatically reconnect when my network drops, so that I do not lose messages during temporary connectivity issues.

#### Acceptance Criteria

1. WHEN the ChatSocketIoClient transitions to `DISCONNECTED` or `ERROR` state while the ChatScreen is active, THE ChatScreen SHALL display a "Reconnecting..." banner within 500 milliseconds of the state change.
2. WHEN the connection drops, THE ChatSocketIoClient SHALL attempt reconnection with exponential backoff starting at 1 second and doubling each attempt (1s, 2s, 4s, 8s, 16s) up to a maximum interval of 30 seconds, for a maximum of 10 attempts.
3. IF the ChatSocketIoClient exhausts all 10 reconnection attempts without success, THEN THE ChatScreen SHALL replace the "Reconnecting..." banner with an error banner containing a manual "Retry" action and SHALL stop automatic reconnection attempts.
4. WHEN the connection is restored, THE MessageRepository SHALL flush all queued messages that were submitted while disconnected, in submission-order (FIFO), with a maximum queue depth of 50 messages.
5. WHEN the connection is restored, THE MessageRepository SHALL fetch missed messages via REST API using the timestamp of the last locally cached message for the active conversation.
6. IF the REST API call to fetch missed messages fails, THEN THE MessageRepository SHALL retry the fetch once after 2 seconds and, if still failing, SHALL display an inline error with a manual retry action.
7. WHEN reconnection succeeds, THE ChatScreen SHALL remove the reconnecting banner with a fade-out animation lasting 300 milliseconds.

### Requirement 14: Message Ordering and Deduplication

**User Story:** As a user, I want messages to always appear in chronological order without duplicates, so that conversations are coherent and readable.

#### Acceptance Criteria

1. THE ChatViewModel SHALL maintain messages sorted by timestamp in ascending order at all times, regardless of the order in which messages arrive from pagination, Socket.IO, or optimistic inserts.
2. WHEN a server acknowledgment maps a temporary ID to a server ID, THE MessageRepository SHALL update the existing message in-place (same list position) without creating a duplicate entry, ensuring the total message count does not increase.
3. WHEN messages are loaded from pagination and real-time sources simultaneously, THE MessageRepository SHALL merge them by unique message ID, discarding any incoming message whose ID already exists in the local cache.
4. THE Room_Cache SHALL enforce unique message IDs as the primary key constraint, causing any insert of a duplicate ID to perform an upsert (update existing row) rather than creating a second row.
5. WHEN two messages share the same timestamp, THE ChatViewModel SHALL use the message ID as a secondary sort key to maintain a deterministic display order.

### Requirement 15: Location Message Sharing

**User Story:** As a user, I want to share my current location in a chat, so that others can see where I am.

#### Acceptance Criteria

1. WHEN a user taps the location share button and location permission is granted, THE ChatViewModel SHALL obtain the device's current coordinates and invoke SendLocationMessageUseCase with the active conversationId, latitude, and longitude.
2. WHEN a location message is displayed, THE ChatScreen SHALL render a location bubble containing a location icon, the text "Shared a location", and a coordinate label formatted as "latitude, longitude" to four decimal places.
3. IF the MessageRepository receives a sendLocationMessage call where latitude is outside the range -90.0 to 90.0 or longitude is outside the range -180.0 to 180.0, THEN THE MessageRepository SHALL return Resource.Error with a message indicating invalid coordinates and SHALL NOT transmit the message.
4. IF the MessageRepository receives a sendLocationMessage call where latitude or longitude is null, THEN THE MessageRepository SHALL return Resource.Error with a message indicating missing coordinates and SHALL NOT transmit the message.
5. IF the user taps the location share button and location permission is not granted, THEN THE ChatScreen SHALL request location permission from the system and SHALL NOT invoke SendLocationMessageUseCase until permission is granted.
6. IF the device cannot determine the current location within 10 seconds of the user tapping the location share button, THEN THE ChatViewModel SHALL not send a location message and the ChatScreen SHALL display a transient error indication to the user.

### Requirement 16: Dark Theme with Glassmorphism Design Language

**User Story:** As a user, I want the chat interface to follow the "Glassy. Dynamic. Warm." design language, so that the experience feels modern and visually cohesive.

#### Acceptance Criteria

1. THE ChatScreen SHALL render sent message bubbles using the Accent Primary color with Radius Large on all corners except bottom-right which uses Radius XS (4dp) for the tail effect, and received message bubbles using the Background Elevated color with Radius Large on all corners except bottom-left which uses Radius XS (4dp) for the mirrored tail effect, with a maximum bubble width of 75% of screen width.
2. THE ChatScreen input bar SHALL use a pill-shaped text input with Background Elevated background, a leading attachment icon, a trailing location share icon, and a circular Accent Primary send button whose icon scales down on press.
3. WHILE a conversation in the ChatsScreen list has unread messages, THE ChatsScreen SHALL render that conversation row with a Background Elevated tint to distinguish it from read conversations.
4. WHEN the ChatScreen input field receives focus, THE ChatScreen SHALL change the input field border from 1dp Divider color to 1.5dp Accent Primary.
5. WHILE the ChatScreen displays a one-on-one conversation, THE ChatScreen header SHALL display the conversation avatar at 40dp, the contact name, and the online status text below the name.
6. WHILE the ChatScreen displays a group conversation, THE ChatScreen header SHALL display the group avatar at 40dp, the group name, and the member count text below the name.
