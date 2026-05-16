# Requirements Document

## Introduction

This document defines the requirements for the WHERE app's Chat and Group Overhaul feature. The WHERE app's core value proposition is real-time location sharing between friends and groups — the chat system serves as the primary communication layer around this location-sharing experience. This spec focuses on: (1) deeply integrating live location sharing into the chat experience, (2) elevating chat UI/UX to WhatsApp/Instagram quality through smooth animations and advanced interactions, (3) aggressively reducing Firestore reads to control costs, (4) deeper group integration (@mentions, admin controls, member online count), and (5) comprehensive performance optimization (Compose stability, image caching, LazyColumn tuning, startup time). It builds upon the existing `chat-screen-redesign` spec (which covers data layer, basic UI, and Socket.IO infrastructure) without duplicating already-implemented functionality.

## Glossary

- **ChatScreen**: The individual conversation view composable displaying messages, input bar, and header
- **ChatsScreen**: The conversation list screen composable displaying all user conversations
- **MessageBubble**: A composable rendering a single message with text, media, reactions, and status indicators
- **ContextMenu**: A floating overlay displayed on long-press of a message bubble offering actions (copy, reply, react, delete, forward)
- **SwipeToReply**: A horizontal drag gesture on a message bubble that initiates a reply to that message
- **VoiceRecorder**: The component responsible for capturing, encoding, and sending audio messages
- **LinkPreview**: A rich preview card rendered inline within a message bubble showing title, description, and thumbnail for URLs detected in message text
- **MentionEngine**: The component responsible for detecting @-prefix input, querying group members, and inserting mention tokens into message text
- **ImageCacheManager**: The Coil-based image loading configuration managing memory cache, disk cache, and placeholder strategies
- **ComposeStabilityAnnotation**: Kotlin annotations (@Stable, @Immutable) applied to data classes to prevent unnecessary recompositions
- **LazyColumnOptimizer**: The set of techniques (item keys, content types, stable lambdas) applied to LazyColumn for smooth scrolling
- **MessageStatusIndicator**: A composable showing message delivery state as clock (pending), single tick (sent), double tick (delivered), or blue double tick (read)
- **GroupChatHeader**: An enhanced chat header for group conversations showing admin badge, online member count, and quick-action overflow menu
- **MediaGallery**: A grid view showing all media shared in a conversation, accessible from the chat header
- **LiveLocationBubble**: A special message bubble type that displays a real-time updating map preview showing the sender's live location with remaining sharing duration
- **LocationSharingSession**: An active period during which a user's device coordinates are broadcast to a group or friend at regular intervals via the LocationTrackingService
- **LocationRepository**: The data layer component managing location sharing sessions, coordinate updates, and observation of active locations via Firestore
- **LocationTrackingService**: A foreground service that obtains periodic GPS fixes and pushes them to Firestore for active sharing sessions
- **FirestoreReadBudget**: The strategy of minimizing Firestore document reads by batching queries, caching aggressively in Room, and using Socket.IO for real-time updates instead of Firestore listeners where possible
- **SnapshotDebouncer**: A component that batches rapid Firestore snapshot emissions into a single Room write per 500ms window to reduce write amplification and UI churn

## Requirements

### Requirement 1: Live Location Sharing from Chat

**User Story:** As a user, I want to start sharing my live location directly from the chat input bar, so that my friends or group members can see where I am in real-time without leaving the conversation.

#### Acceptance Criteria

1. WHEN a user taps the location button in the ChatScreen input bar, THE ChatScreen SHALL display a bottom sheet with two options: "Share Current Location" (one-time pin) and "Share Live Location" (continuous tracking).
2. WHEN a user selects "Share Live Location", THE ChatScreen SHALL display a duration picker with options of 15 minutes, 1 hour, 2 hours, 4 hours, and 8 hours, defaulting to 1 hour.
3. WHEN a user confirms the live location sharing duration, THE ChatViewModel SHALL invoke StartLocationSharingUseCase with the conversation's groupId and selected duration, dismiss the bottom sheet within 300ms, start the LocationTrackingService, and insert a live location message into the conversation.
4. WHILE a live location sharing session is active in the current conversation, THE ChatScreen SHALL display a persistent banner below the header showing "Sharing live location · {timeRemaining}" formatted as "Xh Ym" when remaining time is 60 minutes or more, or "Xm" when under 60 minutes, updated every 60 seconds, with a "Stop" button.
5. WHEN a user taps "Stop" on the live location banner, THE ChatViewModel SHALL invoke StopLocationSharingUseCase and THE ChatScreen SHALL remove the banner within 300ms.
6. WHEN a live location message is displayed in the conversation, THE LiveLocationBubble SHALL render a mini-map preview (200dp × 120dp) showing the sender's current position, updated every 15 seconds while the sharing session is active.
7. WHEN a user taps a LiveLocationBubble in a group conversation, THE ChatScreen SHALL navigate to the GroupMap screen (Screen.GroupMap) centered on the sharing user's location.
8. IF location permission is not granted when the user selects "Share Live Location", THEN THE ChatScreen SHALL request both ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION permissions before starting the session.
9. WHEN a live location sharing session expires (duration elapsed), THE LocationTrackingService SHALL stop broadcasting coordinates and THE LiveLocationBubble SHALL display "Location sharing ended" with the last known position frozen on the mini-map.
10. IF the user denies the location permission request, THEN THE ChatScreen SHALL display a dismissible message indicating that location permission is required to share live location, and SHALL NOT start the sharing session.
11. IF StartLocationSharingUseCase returns an error when the user confirms live location sharing, THEN THE ChatScreen SHALL dismiss the bottom sheet and display a dismissible error message indicating the sharing session could not be started, and SHALL NOT start the LocationTrackingService.
12. IF the conversation does not have an associated groupId (1:1 direct conversation), THEN THE ChatScreen SHALL hide the "Share Live Location" option in the bottom sheet and only display "Share Current Location".

### Requirement 2: Live Location Bubble Display and Updates

**User Story:** As a user, I want to see my friends' live locations updating in real-time within the chat, so that I can track their movement without switching to the map screen.

#### Acceptance Criteria

1. WHEN a group member starts sharing their live location, THE ChatScreen SHALL display a LiveLocationBubble for that member showing their display name, a mini-map with their marker, and the text "Sharing live location".
2. WHILE a group member's SharedLocation has isSharingActive set to true, THE LiveLocationBubble SHALL update the marker position on the mini-map each time a new coordinate is received from the LocationRepository, processing at most one update per 15 seconds.
3. WHILE a group member's SharedLocation has isSharingActive set to true, THE LiveLocationBubble SHALL display the time elapsed since the last coordinate update in the format "Updated {N}s ago" below the mini-map, refreshing the displayed value every 10 seconds.
4. WHEN multiple group members are sharing live locations simultaneously, THE ChatScreen SHALL display a separate LiveLocationBubble for each active sharer, ordered by session start time in ascending order (earliest first).
5. IF the LocationRepository does not receive a coordinate update for a sharing member within 60 seconds, THEN THE LiveLocationBubble SHALL display "Waiting for location..." and apply a repeating fade-in/fade-out animation (opacity cycling between 0.4 and 1.0) on the marker.
6. WHEN a live location sharing session ends (either by expiry or manual stop), THE LiveLocationBubble SHALL transition to a static state showing "Location sharing ended · {duration shared}" with the duration formatted as "{H}h {M}m" if 60 minutes or longer or "{M}m" if under 60 minutes, and the final position frozen on the mini-map.

### Requirement 3: Location Sharing Status in Conversation List

**User Story:** As a user, I want to see which conversations have active location sharing, so that I can quickly identify where friends are sharing their location.

#### Acceptance Criteria

1. WHEN one or more members in a conversation have an active SharedLocation (isSharingActive is true) associated with that conversation, THE ChatsScreen SHALL display a location pin icon (tertiary color) next to the conversation timestamp.
2. WHEN the current user has an active SharedLocation in a conversation, THE ChatsScreen SHALL display "📍 Sharing live location" as the last message preview text for that conversation, taking precedence over criterion 3.
3. WHEN another user starts sharing their live location in a conversation and the current user is not actively sharing in that same conversation, THE ChatsScreen SHALL update the last message preview to "📍 {displayName} is sharing location" for that conversation, where {displayName} is the sharer's conversation display name.
4. IF multiple other users are sharing their live location in a conversation and the current user is not actively sharing, THEN THE ChatsScreen SHALL display "📍 {displayName} and {count} others sharing location" where {displayName} is the most recently started sharer's display name and {count} is the number of additional sharers.
5. WHEN all active SharedLocations in a conversation expire or are stopped, THE ChatsScreen SHALL remove the location pin icon and revert the last message preview to the actual last message text within 5 seconds of the observeActiveLocations flow emitting the updated state.
6. THE ChatsScreen SHALL observe active location sessions from the LocationRepository by reusing the existing consolidated observeActiveLocations flow without registering additional Firestore snapshot listeners.

### Requirement 4: Quick Location Actions from Chat Header

**User Story:** As a user, I want to quickly access the group map and see who is sharing their location from the chat header, so that I can switch between chatting and viewing locations seamlessly.

#### Acceptance Criteria

1. WHILE the ChatScreen displays a group conversation with at least one active LocationSharingSession, THE ChatHeader SHALL display an animated location pulse icon in tertiary color adjacent to the group map action button, where the pulse animation cycles opacity between 0.3 and 1.0 over a 1.5-second repeating interval.
2. WHEN a user taps the group map action in the ChatScreen header, THE ChatScreen SHALL navigate to Screen.GroupMap with the group's ID, and the MapScreen SHALL adjust its viewport to fit all active sharing members within the visible map bounds.
3. WHILE at least one active LocationSharingSession exists in the group conversation, THE ChatHeader subtitle SHALL display the member count and sharing count in the format "{memberCount} members · {sharingCount} sharing location".
4. IF no active LocationSharingSession exists in the group conversation, THEN THE ChatHeader subtitle SHALL display only the member count in the format "{memberCount} members".
5. WHILE a user in a 1:1 conversation is sharing their live location AND the other user is in the current user's friends list, THE ChatHeader SHALL display "Sharing location" in tertiary color below the contact name, replacing the "Online"/"Offline" status text.
6. IF a user in a 1:1 conversation is sharing their live location but the other user is NOT in the current user's friends list, THEN THE ChatHeader SHALL NOT display the "Sharing location" status or any presence indicator.

### Requirement 5: Firestore Read Optimization — Batched Queries

**User Story:** As a system operator, I want to minimize Firestore document reads to control costs, so that the app scales without excessive billing.

#### Acceptance Criteria

1. THE ConversationRepository SHALL batch all conversation metadata reads into a single Firestore query using `whereIn` on the conversation IDs the user participates in, chunking into groups of at most 30 IDs per query (the Firestore `whereIn` limit), executing at most one query per foreground sync cycle rather than individual document reads.
2. IF the user participates in more than 30 conversations, THEN THE ConversationRepository SHALL split the conversation IDs into chunks of 30 and execute one `whereIn` query per chunk, totaling ceil(N/30) queries per foreground sync cycle.
3. WHEN the app returns to foreground, THE ConversationRepository SHALL execute a single REST API call to fetch all conversation unread counts and metadata changes since the last sync timestamp stored in Room, rather than relying on Firestore snapshot listeners for unread count updates.
4. IF no last sync timestamp exists in Room (first launch), THEN THE ConversationRepository SHALL treat the sync timestamp as epoch zero and fetch the full unread count set from the REST API.
5. THE LocationRepository SHALL use a single Firestore collection-level query with a `whereArrayContains` filter on the current user's ID to observe all active location sessions, rather than opening one listener per group.
6. WHEN the Firestore snapshot listener emits multiple updates within a 500ms fixed window (measured from the first update in the batch), THE SnapshotDebouncer SHALL coalesce them into a single Room database write operation containing the latest state of each affected document.
7. THE ConversationRepository SHALL store the Firestore snapshot metadata version (document updateTime) per conversation in Room and SHALL skip the Room write for any document whose stored version matches the incoming snapshot version.

### Requirement 6: Firestore Read Optimization — Socket.IO for Real-Time Instead of Firestore

**User Story:** As a system operator, I want real-time updates (typing, presence, reactions, read receipts) to flow through Socket.IO instead of Firestore, so that Firestore reads are reserved for durable state only.

#### Acceptance Criteria

1. THE ChatSocketIoClient SHALL handle all real-time ephemeral events (typing indicators, presence updates, reaction updates, read receipts, message delivery acknowledgments) without any Firestore document reads.
2. THE ConversationRepository SHALL NOT open Firestore listeners for presence or typing state, relying exclusively on Socket.IO frames relayed through the ChatSocketIoClient for these ephemeral states.
3. WHEN the ChatSocketIoClient receives a presence update for a user, THE ChatsViewModel SHALL update the in-memory online status map within 1 second of frame receipt without querying Firestore, persisting only to Room for offline access.
4. WHILE the ChatSocketIoClient connectionState is CONNECTED, THE LocationRepository SHALL receive real-time coordinate updates via Socket.IO and SHALL NOT open a Firestore listener for location data.
5. IF the ChatSocketIoClient connectionState remains DISCONNECTED for more than 30 seconds, THEN THE LocationRepository SHALL fall back to a Firestore snapshot listener for location data until the socket reconnects.
6. WHEN the ChatSocketIoClient reconnects after any disconnection period, THE MessageRepository SHALL fetch missed messages via a single REST API call (using last-synced timestamp) with a timeout of 10 seconds rather than replaying Firestore document reads.
7. IF the REST API call for missed messages fails or times out after reconnection, THEN THE MessageRepository SHALL retain the existing Room-cached messages, emit a recoverable error state, and retry the sync on the next successful socket connection.

### Requirement 7: Firestore Read Optimization — Aggressive Local Caching

**User Story:** As a user, I want the app to show data instantly from cache and sync in the background, so that I never wait for network requests to see my conversations.

#### Acceptance Criteria

1. THE ConversationRepository SHALL serve the conversation list exclusively from Room database flows, treating Room as the single source of truth, and SHALL update Room in the background from Firestore without causing UI frame drops (main thread work per frame shall not exceed 16ms).
2. THE LocationRepository SHALL cache the last known location for each sharing member in Room, serving cached positions within 100ms of screen open, and updating them as new coordinates arrive from the active location listener.
3. WHEN the user opens a group conversation, THE ChatViewModel SHALL display cached member locations from Room within 100ms, then subscribe to real-time updates via Socket.IO; IF the Socket.IO connection fails to deliver a location update within 10 seconds of subscription, THEN THE ChatViewModel SHALL fall back to Firestore snapshot listener for location updates.
4. THE ConversationRepository SHALL store the lastSyncTimestamp per conversation in Room and SHALL skip Firestore re-reads for conversations whose lastSyncTimestamp is less than 5 minutes old; WHEN the lastSyncTimestamp is 5 minutes or older, THE ConversationRepository SHALL trigger a background re-read from Firestore.
5. WHILE the device is offline, THE ChatsScreen and ChatScreen SHALL display all cached data from Room without showing loading indicators or spinners.
6. IF the user attempts a write action (send message, create group, update profile, or start location sharing) while the device is offline, THEN THE App SHALL display a non-modal offline banner not exceeding 48dp in height at the top of the screen, indicating that the action has been queued for sync when connectivity is restored.
7. WHEN the app launches with an empty Room cache (first install or cleared data), THE ConversationRepository SHALL display a loading state and fetch the initial conversation list from Firestore before transitioning to the cache-first flow.

### Requirement 8: Swipe-to-Reply Gesture

**User Story:** As a user, I want to swipe right on a message to reply to it, so that I can quickly reference specific messages without navigating a menu.

#### Acceptance Criteria

1. WHEN a user swipes a message bubble horizontally from left to right beyond a 48dp threshold, THE ChatScreen SHALL initiate a reply to that message by populating the reply preview bar above the input field with the sender name and message content (text truncated to 100 characters, or a descriptor indicating the media type if the message contains no text).
2. WHILE a user is dragging a message bubble horizontally, THE ChatScreen SHALL display a reply arrow icon that fades in proportionally to the drag distance, reaching full opacity at the 48dp threshold, and SHALL limit the horizontal displacement of the message bubble to a maximum of 100dp.
3. WHEN the swipe distance exceeds the 48dp threshold and the user releases, THE ChatScreen SHALL trigger a haptic feedback pulse and move keyboard focus to the message input field.
4. IF the user releases the swipe before reaching the 48dp threshold, THEN THE ChatScreen SHALL animate the message bubble back to its original position within 200ms without initiating a reply.
5. WHILE a swipe-to-reply gesture is in progress, THE ChatScreen SHALL suppress vertical scrolling of the message list to prevent conflicting gestures.
6. WHEN the reply preview bar is visible and the user taps the dismiss button on the reply preview bar, THE ChatScreen SHALL remove the reply preview bar and disassociate the pending reply from the input field.
7. WHEN a user swipes a message bubble beyond the 48dp threshold while a reply preview bar is already visible, THE ChatScreen SHALL replace the existing reply preview content with the newly swiped message sender name and content.

### Requirement 9: Long-Press Context Menu

**User Story:** As a user, I want to long-press a message to access actions like copy, reply, react, delete, and forward, so that I can interact with messages in multiple ways.

#### Acceptance Criteria

1. WHEN a user long-presses a message bubble for 300 milliseconds, THE ChatScreen SHALL display a floating context menu anchored to the pressed message with actions: Copy, Reply, React, Delete, and Forward.
2. WHEN the context menu is displayed, THE ChatScreen SHALL apply a dimmed scrim overlay (40% opacity black) behind the menu and the anchored message bubble.
3. WHEN a user taps "Copy" in the context menu on a text message, THE ChatScreen SHALL copy the message text content to the system clipboard, dismiss the menu, and display a "Copied" toast for 2 seconds.
4. IF a user taps "Copy" in the context menu on a non-text message (image or location), THEN THE ContextMenu SHALL hide the "Copy" action from the menu for that message type.
5. WHEN a user taps "Reply" in the context menu, THE ChatScreen SHALL dismiss the menu and populate the reply preview bar above the input field with the selected message.
6. WHEN a user taps "React" in the context menu, THE ChatScreen SHALL dismiss the context menu and display the reaction picker overlay anchored to the message.
7. IF the long-pressed message was sent by the current user, THEN THE ContextMenu SHALL display a "Delete" action; WHEN the user taps "Delete", THE ChatScreen SHALL display a confirmation dialog; on confirmation, THE ChatViewModel SHALL call the delete message API and remove the message from the local cache.
8. IF the delete message API call fails, THEN THE ChatScreen SHALL dismiss the confirmation dialog, retain the message in the conversation, and display an error snackbar indicating the deletion failed for 4 seconds.
9. IF a user taps "Delete" on a message not sent by the current user, THEN THE ContextMenu SHALL hide the "Delete" action and display "Delete for me" instead, which removes the message from local display only.
10. WHEN a user taps "Forward" in the context menu, THE ChatScreen SHALL navigate to a conversation picker screen where the user can select up to 5 conversations to forward the message to.
11. WHEN a user taps outside the context menu or presses the system back gesture, THE ChatScreen SHALL dismiss the context menu and remove the scrim overlay with a 200ms fade-out animation.

### Requirement 10: Message Status Indicators

**User Story:** As a user, I want to see the delivery status of my sent messages (pending, sent, delivered, read), so that I know whether my messages reached the recipient.

#### Acceptance Criteria

1. WHEN a message is in PENDING status, THE MessageStatusIndicator SHALL display a clock icon of 14dp size in onSurfaceVariant color, positioned immediately after the message timestamp with 4dp horizontal spacing.
2. WHEN a message transitions to SENT status (server acknowledged), THE MessageStatusIndicator SHALL display a single tick icon of 14dp size in onSurfaceVariant color.
3. WHEN a message transitions to DELIVERED status (recipient device received), THE MessageStatusIndicator SHALL display a double tick icon of 14dp size in onSurfaceVariant color.
4. WHEN a message transitions to READ status (readBy list contains at least one non-sender user), THE MessageStatusIndicator SHALL display a double tick icon of 14dp size in primary (accent) color.
5. WHEN a message status transitions between states, THE MessageStatusIndicator SHALL animate the icon change with a 150ms crossfade transition.
6. THE MessageStatusIndicator SHALL display only on messages sent by the current user (BubbleDirection.SENT) and SHALL NOT appear on received messages.
7. IF a message is in FAILED status, THEN THE MessageStatusIndicator SHALL display an error icon of 14dp size in error color to indicate the message was not sent.
8. THE MessageStatusIndicator SHALL provide an accessibility content description indicating the current status (e.g., "Message pending", "Message sent", "Message delivered", "Message read", "Message failed").

### Requirement 11: Voice Message Recording and Playback

**User Story:** As a user, I want to record and send voice messages, so that I can communicate hands-free or convey tone that text cannot.

#### Acceptance Criteria

1. WHEN a user long-presses the microphone button in the input bar for at least 300ms, THE VoiceRecorder SHALL begin recording audio using the device microphone and THE ChatScreen SHALL display a recording indicator showing elapsed time in "m:ss" format and a waveform visualization.
2. WHILE recording is active, THE ChatScreen SHALL display a "Slide left to cancel" hint and a lock icon above the microphone button; IF the user slides left beyond 100dp, THEN THE VoiceRecorder SHALL cancel the recording and discard the audio data.
3. WHEN the user slides up beyond 48dp while recording, THE VoiceRecorder SHALL lock into hands-free recording mode, allowing the user to release the button while recording continues, and THE ChatScreen SHALL display a stop button and a send button.
4. WHEN the user releases the microphone button without sliding beyond 20dp in any direction (normal release), THE VoiceRecorder SHALL stop recording and THE ChatViewModel SHALL send the voice message if the recording duration is at least 1 second.
5. IF the recording duration is less than 1 second on release, THEN THE VoiceRecorder SHALL discard the recording and THE ChatScreen SHALL display a "Hold to record" tooltip for 2 seconds.
6. THE VoiceRecorder SHALL encode audio in AAC format at 64kbps bitrate with a sample rate of 16kHz and a maximum recording duration of 5 minutes.
7. IF the recording reaches the maximum duration of 5 minutes, THEN THE VoiceRecorder SHALL automatically stop recording and THE ChatViewModel SHALL send the voice message as if the user had released the button normally.
8. WHEN a voice message is received or displayed, THE ChatScreen SHALL render an audio playback bubble with a play/pause button, a seekable progress bar, and the duration label formatted as "m:ss".
9. WHILE a voice message is playing, THE ChatScreen SHALL update the progress bar every 100ms and SHALL pause playback when the user navigates away from the ChatScreen; WHEN playback completes, THE ChatScreen SHALL reset the progress bar to the beginning and display the play button.
10. IF a user taps play on a voice message while another voice message is already playing, THEN THE ChatScreen SHALL stop the currently playing message, reset its progress bar, and begin playback of the newly selected message.
11. IF the microphone permission has not been granted when the user long-presses the microphone button, THEN THE App SHALL request microphone permission before initiating recording; IF permission is denied, THEN THE VoiceRecorder SHALL not record and THE ChatScreen SHALL display an error message indicating that microphone access is required.

### Requirement 12: Link Preview in Messages

**User Story:** As a user, I want URLs in messages to show a rich preview with title and thumbnail, so that I can understand linked content without opening a browser.

#### Acceptance Criteria

1. WHEN a message contains one or more URLs matching the pattern `https?://[^\s]+`, THE ChatScreen SHALL render a link preview card below the message text showing the page title (truncated to 80 characters with ellipsis if longer), domain name, and a thumbnail image with a maximum height of 160dp (if an image URL is available from Open Graph metadata).
2. WHEN the ChatViewModel detects a URL in an outgoing message before sending, THE ChatViewModel SHALL fetch Open Graph metadata (title, description, image URL) from the server-side link preview API and attach it to the message payload.
3. IF the link preview API does not respond within 5 seconds or returns a non-success response, THEN THE ChatViewModel SHALL send the message without preview metadata and THE ChatScreen SHALL render the URL as a tappable hyperlink without a preview card.
4. WHEN a user taps a link preview card, THE ChatScreen SHALL open the URL in the system browser via an implicit intent.
5. IF a message contains multiple URLs, THEN THE ChatScreen SHALL render a link preview card for only the first URL in the message text.
6. IF the fetched Open Graph metadata does not include a title, THEN THE ChatScreen SHALL display the domain name as the card title and omit the description.

### Requirement 13: Message Search Within Conversation

**User Story:** As a user, I want to search for specific messages within a conversation, so that I can find past information without scrolling through the entire history.

#### Acceptance Criteria

1. WHEN a user taps the search icon in the ChatScreen header, THE ChatScreen SHALL display a search bar at the top of the screen with a text input field (maximum 100 characters), a result position indicator (e.g., "3 of 10"), and navigation arrows (up/down) for cycling through results.
2. WHEN a user types at least 2 characters into the conversation search field, THE ChatViewModel SHALL wait for a 300ms debounce after the last keystroke, then query the local Room database for messages in the current conversation containing the search text (case-insensitive substring match) and highlight all matching messages in the list.
3. WHEN search results are found, THE ChatScreen SHALL scroll to the first matching message in chronological order (oldest first) and highlight the matching text within the message bubble with a primary color background at 30% opacity.
4. WHEN a user taps the down arrow, THE ChatScreen SHALL navigate to the next matching message in chronological order (older to newer); WHEN the last result is reached, the down arrow SHALL appear disabled and tapping it SHALL have no effect.
5. WHEN a user taps the up arrow, THE ChatScreen SHALL navigate to the previous matching message in reverse chronological order (newer to older); WHEN the first result is reached, the up arrow SHALL appear disabled and tapping it SHALL have no effect.
6. WHEN no results are found for the search query, THE ChatScreen SHALL display "No results" text in the search bar area and disable both navigation arrows.
7. WHEN a user dismisses the search bar (tap X or back gesture), THE ChatScreen SHALL remove all highlights and restore the message list to its previous scroll position (the position held before the search icon was tapped).

### Requirement 14: Group @Mention Support

**User Story:** As a group member, I want to @mention other members in messages, so that I can direct messages to specific people and they receive a notification.

#### Acceptance Criteria

1. WHEN a user types the "@" character in the input field within a group conversation, THE MentionEngine SHALL display a suggestion popup above the input bar listing group members (excluding the current user) whose display name matches the characters typed after "@" (case-insensitive prefix match), updating results within 300 milliseconds of each keystroke.
2. WHEN a user selects a member from the mention suggestion popup, THE MentionEngine SHALL replace the "@" and any typed filter characters with the member's display name rendered as a styled mention token (primary color, bold) in the input field, place the cursor immediately after the token, and dismiss the popup.
3. WHEN a message containing mention tokens is sent, THE ChatViewModel SHALL include a deduplicated array of mentioned user IDs (one entry per distinct user regardless of how many times they are mentioned) in the message payload so the server can trigger push notifications to mentioned users.
4. WHEN a message containing mentions is displayed, THE ChatScreen SHALL render mentioned names in primary color and bold weight within the message bubble text.
5. IF the group has more than 10 members, THEN THE MentionEngine SHALL display at most 5 matching suggestions in the popup, sorted by display name ascending.
6. WHEN a user types "@" followed by no additional characters, THE MentionEngine SHALL display the first 5 members of the group (excluding the current user) sorted by display name ascending.
7. IF the user deletes any character within a mention token, THEN THE MentionEngine SHALL remove the entire mention token from the input field and remove the corresponding user ID from the mentioned users list.
8. IF the user presses the back button, taps outside the suggestion popup, or deletes the "@" trigger character, THEN THE MentionEngine SHALL dismiss the suggestion popup without inserting a mention token.

### Requirement 15: Group Admin Controls in Chat Header

**User Story:** As a group admin, I want quick access to admin actions from the chat header, so that I can manage the group without navigating away from the conversation.

#### Acceptance Criteria

1. WHILE the current user has the admin role for the active group conversation, THE GroupChatHeader SHALL display an overflow menu icon that reveals exactly three admin actions: "Mute Member", "Group Settings", and "Invite Link".
2. WHEN an admin taps "Mute Member" from the overflow menu, THE ChatScreen SHALL display a member picker dialog listing all group members except the current user; WHEN the admin selects a member and confirms, THE ChatViewModel SHALL call the mute-member API and display a confirmation snackbar indicating which member was muted.
3. IF the mute-member API call fails, THEN THE ChatScreen SHALL display an error snackbar indicating the mute operation failed and the member's mute status SHALL remain unchanged.
4. WHEN an admin taps "Group Settings" from the overflow menu, THE ChatScreen SHALL navigate to the EditGroupScreen for the active group.
5. WHEN an admin taps "Invite Link" from the overflow menu, THE ChatScreen SHALL copy the group invite link to the clipboard, display a "Link copied" toast for a minimum of 2 seconds, and present the system share sheet for the invite link.
6. IF the invite link retrieval fails, THEN THE ChatScreen SHALL display an error snackbar indicating the link could not be retrieved and SHALL NOT copy any content to the clipboard.
7. WHILE the current user does not have the admin role for the active group conversation, THE GroupChatHeader SHALL hide the overflow menu icon and display only the standard group info and group map navigation actions.

### Requirement 16: Member Online Count in Group Header

**User Story:** As a group member, I want to see how many members are currently online, so that I know how active the group is right now.

#### Acceptance Criteria

1. WHILE the ChatScreen displays a group conversation, THE GroupChatHeader SHALL display the online member count (excluding the current user) in the subtitle formatted as "{totalCount} members · {onlineCount} online" where the separator is a middle dot character (e.g., "12 members · 4 online").
2. WHEN a presence update is received via ChatSocketIoClient for a group member, THE ChatViewModel SHALL update the online member count within 1 second of receiving the event.
3. IF no group members are currently online (other than the current user), THEN THE GroupChatHeader SHALL display only the total member count in the format "{totalCount} members" without the online suffix or separator.
4. WHEN the ChatScreen opens a group conversation, THE ChatViewModel SHALL determine the initial online member count from presence state available via ChatSocketIoClient and display it within 2 seconds of screen entry.
5. IF the ChatSocketIoClient connection is lost while a group conversation is displayed, THEN THE GroupChatHeader SHALL continue displaying the last known online count until the connection is re-established and new presence updates are received.

### Requirement 17: Compose Stability and Recomposition Optimization

**User Story:** As a user, I want the chat screens to scroll smoothly without frame drops, so that the experience feels native and responsive.

#### Acceptance Criteria

1. THE MessageUiModel data class SHALL be annotated with @Immutable, all its properties SHALL be val (immutable), and all collection-typed properties (Map, List) SHALL use immutable or persistent collection types or be guaranteed to hold only immutable instances, ensuring Compose skips recomposition of unchanged message bubbles.
2. THE ConversationUiModel data class SHALL be annotated with @Immutable and all its properties SHALL be val (immutable), ensuring Compose skips recomposition of unchanged conversation rows.
3. THE ChatScreen LazyColumn SHALL specify a unique contentType for each item type (message bubble, date separator, pagination indicator, typing indicator) and SHALL provide a stable key for each item, to enable Compose to reuse item compositions across different positions.
4. THE ChatScreen LazyColumn SHALL use stable lambda references (method references or remembered lambdas) for all item click and interaction handlers (including onLocationTap, onRetry, and onNavigate callbacks) to prevent recomposition triggered by lambda recreation on each frame.
5. THE ChatsScreen LazyColumn SHALL specify a unique contentType for conversation rows and dividers, and SHALL provide a stable key for each item, to enable composition reuse.
6. WHILE the ChatScreen message list contains 500 or more messages, THE ChatScreen SHALL maintain a frame render time at or below 16ms (60fps) during a fling scroll gesture at default system fling velocity, as measured by a Macrobenchmark FrameTimingMetric on a Snapdragon 6-series equivalent device (or emulator profile matching that performance tier).
7. THE ChatScreen LazyColumn items block SHALL NOT perform collection grouping, sorting, or date formatting inline; all grouping and formatting SHALL be pre-computed in the ViewModel or mapper layer before being passed to the Composable.

### Requirement 18: Image Caching Strategy

**User Story:** As a user, I want images in chat to load instantly on revisit and not consume excessive memory, so that scrolling through media-heavy conversations is smooth.

#### Acceptance Criteria

1. THE ImageCacheManager SHALL configure Coil with a memory cache limited to 25% of the application's available heap memory and a disk cache limited to 250MB in the app's cache directory.
2. WHEN an image message is scrolled into view, THE ImageCacheManager SHALL serve the image from memory cache (if available), then disk cache, then network, in that priority order.
3. THE ImageCacheManager SHALL use crossfade animation (200ms duration) when loading images from disk or network, and SHALL display images immediately without animation when served from memory cache.
4. WHEN displaying image thumbnails in the message list, THE ImageCacheManager SHALL request images downscaled to the composable's layout dimensions with a maximum of 512px on the longest edge, rather than full resolution, to reduce memory consumption.
5. THE ImageCacheManager SHALL implement a custom fetcher for Firebase Storage URLs that appends the download token and handles token refresh on 403 responses by requesting a new token and retrying the image fetch exactly once; IF the retry also fails with a 403 or the token refresh itself fails, THEN THE ImageCacheManager SHALL treat the request as a permanent failure and display the error placeholder.
6. IF an image fails to load from all cache layers and network (due to network error, HTTP error, or permanent token failure), THEN THE ImageCacheManager SHALL display an error placeholder drawable in place of the image and SHALL not retry automatically until the user scrolls the image out of view and back into view or performs a pull-to-refresh action.

### Requirement 19: LazyColumn Performance Optimization

**User Story:** As a user, I want the message list and conversation list to scroll without jank even with hundreds of items, so that the app feels polished.

#### Acceptance Criteria

1. THE ChatScreen LazyColumn SHALL key every item by a stable unique identifier (message ID for messages, fixed string keys for pagination indicators and date separators) and assign a contentType per item category (message bubble, date separator, pagination indicator) to enable efficient diffing and composable reuse.
2. THE ChatScreen SHALL pre-compute all display-ready values (formatted timestamps, date keys, bubble direction, sender initials) in the ViewModel layer and expose them via MessageUiModel, performing zero formatting or computation inside composable functions.
3. WHILE the message list contains more than 500 messages in memory, THE ChatViewModel SHALL evict the oldest messages beyond the 500-message window when new messages arrive, while preserving the pagination cursor so the user can paginate backward to retrieve evicted messages.
4. THE ChatsScreen LazyColumn SHALL use animateItem modifier with a 250ms tween (matching LIST_ITEM_ANIMATION_DURATION_MS) for item fade-in, fade-out, and placement to provide smooth list transitions on additions, removals, and reordering.
5. THE ChatScreen SHALL debounce typing indicator state updates to emit at most one recomposition-triggering state change per 300ms when multiple typing events arrive in group conversations.
6. WHEN the ChatScreen LazyColumn renders message bubbles, THE ChatScreen SHALL mark each message item composable with Modifier.animateItem and avoid allocating new object instances (lambdas, lists, or maps) inside the item scope to minimize recomposition and garbage collection during scrolling.

### Requirement 20: Startup Time and Lazy Initialization

**User Story:** As a user, I want the app to launch quickly and show content fast, so that I can start chatting without waiting.

#### Acceptance Criteria

1. THE Application class SHALL initialize the ChatSocketIoClient, ConversationRepository Firestore listener, and ImageCacheManager lazily via Hilt lazy providers (dagger.Lazy or javax.inject.Provider) so that none of these objects are instantiated until their first method is invoked by the chat feature.
2. WHEN the ChatsScreen is first composed and the Room cache contains at least one conversation, THE ChatsViewModel SHALL emit the cached conversations to the UI state within 200ms of the ViewModel's init block execution, before any network request completes.
3. IF the ChatsScreen is first composed and the Room cache contains zero conversations, THEN THE ChatsViewModel SHALL emit a loading state within 100ms of the ViewModel's init block execution and SHALL not emit an empty-list content state until the first network response arrives or a timeout of 10 seconds elapses.
4. WHILE the user has not navigated to the ChatsScreen or any screen that depends on chat-related classes (ChatSocketIoClient, ConversationRepository Firestore listener, ImageCacheManager), THE Application SHALL not instantiate any of those chat-related singletons, keeping the startup path limited to authentication and navigation setup.
5. WHEN the app process is started, THE Application SHALL reach the first rendered frame of the initial screen (authentication gate or main navigation) within 500ms on a device meeting minimum supported specifications, excluding any chat-related initialization from the critical startup path.

### Requirement 21: Memory Management and Coroutine Lifecycle

**User Story:** As a user, I want the app to not leak memory or crash due to background operations, so that the app remains stable during extended use.

#### Acceptance Criteria

1. WHEN the ChatScreen is removed from the back stack, THE ChatViewModel SHALL cancel all active coroutines (pagination, typing emission, presence observation) by relying on viewModelScope cancellation, and no coroutine launched within that viewModelScope SHALL continue executing after onCleared() returns.
2. WHEN the user logs out, THE ChatSocketIoClient SHALL cancel its SupervisorJob-based CoroutineScope, disconnect the socket, cancel any active reconnection job, and reset the TypingIndicatorManager, leaving zero active coroutines and zero open socket connections owned by that client instance.
3. WHEN the Application process is destroyed, THE ChatSocketIoClient SHALL cancel its SupervisorJob-based CoroutineScope and release the socket reference, ensuring no background threads or socket connections persist beyond process termination.
4. WHEN the app transitions to background (Activity onStop with isChangingConfigurations == false), THE ChatSocketIoClient SHALL disconnect the socket within 5 seconds and THE ConversationRepository SHALL remove the Firestore snapshot listener, stopping all incoming network callbacks from both the socket and Firestore until the app returns to foreground.
5. WHEN the app returns to foreground (Activity onStart), THE ChatSocketIoClient SHALL reconnect the socket and THE ConversationRepository SHALL re-register the Firestore snapshot listener within 2 seconds of the onStart callback.
6. IF the socket reconnection initiated on foreground return does not reach CONNECTED state within 2 seconds, THEN THE ChatSocketIoClient SHALL continue the reconnection attempt using its existing exponential backoff strategy without crashing or leaving the connection in an indeterminate state.
7. THE ChatViewModel SHALL use SharedFlow with replay = 0 and extraBufferCapacity of at least 1 for one-shot UI events (snackbar messages, navigation commands), ensuring that collectors who attach after emission do not receive previously emitted events on configuration change.

### Requirement 22: Background Sync with WorkManager

**User Story:** As a user, I want to receive message notifications and have conversations up-to-date when I open the app, so that I never miss important messages.

#### Acceptance Criteria

1. WHEN the app is in background for more than 5 minutes, THE WorkManager SHALL schedule a periodic sync job (minimum interval 15 minutes) that fetches unread message counts and updates the Room database.
2. WHEN the periodic sync job executes, THE WorkManager worker SHALL make a single REST API call to fetch all conversation unread counts and update Room, completing within a 30-second execution window.
3. IF the periodic sync fails due to network loss during execution or a server error response, THEN THE WorkManager SHALL retry with exponential backoff (initial 1 minute, max 30 minutes, maximum 5 retry attempts) using the RETRY result, and SHALL return a FAILURE result after all retry attempts are exhausted.
4. WHEN the user opens the app after a background period, THE ChatsViewModel SHALL display the WorkManager-synced unread counts from Room within 500 milliseconds of screen render, while the foreground sync refreshes them from the server.
5. THE WorkManager sync job SHALL require network connectivity as a constraint and SHALL NOT execute when the device has no network connection.
6. WHEN the periodic sync job detects new unread messages that were not previously stored in Room, THE system SHALL display a notification indicating the sender name and conversation with the new unread message count.
7. IF the periodic sync receives an authentication error from the server, THEN THE WorkManager worker SHALL cancel the periodic sync schedule and SHALL NOT retry until the user re-authenticates in the foreground.

### Requirement 23: Message Send and Receive Animations

**User Story:** As a user, I want messages to animate smoothly when sent and received, so that the chat feels alive and responsive like WhatsApp.

#### Acceptance Criteria

1. WHEN a new message is sent by the current user, THE ChatScreen SHALL animate the message bubble sliding up by 48dp from its final position combined with a fade-in from 0% to 100% opacity, completing within 200ms using a decelerate easing curve.
2. WHEN a new message is received from another user, THE ChatScreen SHALL animate the message bubble sliding in 32dp from the left of its final position combined with a fade-in from 0% to 100% opacity, completing within 250ms using a decelerate easing curve.
3. WHEN a new message arrives and the user is scrolled within 150dp of the last visible message, THE ChatScreen SHALL auto-scroll to reveal the new message with a 300ms animation using a decelerate easing curve.
4. IF a new message arrives and the user is scrolled more than 150dp above the last visible message, THEN THE ChatScreen SHALL NOT auto-scroll and SHALL display a "new message" indicator that, when tapped, scrolls to the latest message with a 300ms animation.
5. WHEN a message status indicator transitions between states (pending → sent → delivered → read), THE ChatScreen SHALL animate the transition with a 150ms crossfade from the previous state icon to the new state icon.
6. WHEN the reaction picker is displayed, THE ChatScreen SHALL animate it appearing with a 200ms scale-up from 80% to 100% combined with a fade-in from 0% to 100% opacity.
7. IF the device accessibility setting for reduced motion is enabled, THEN THE ChatScreen SHALL skip all slide and scale animations and apply changes instantly with no transition duration.

### Requirement 24: Conversation List Enhancements

**User Story:** As a user, I want the conversation list to show richer information (message preview types, delivery status, pinned chats), so that I can quickly assess conversation state without opening each one.

#### Acceptance Criteria

1. WHEN the last message in a conversation is an image, THE ChatsScreen SHALL display "📷 Photo" as the preview text; WHEN it is a voice message, THE ChatsScreen SHALL display "🎤 Voice message"; WHEN it is a location, THE ChatsScreen SHALL display "📍 Location"; WHEN it is a video, THE ChatsScreen SHALL display "🎥 Video"; WHEN it is a document, THE ChatsScreen SHALL display "📄 Document". IF the last message type is not one of the recognized media types listed above, THEN THE ChatsScreen SHALL display the raw message text content as the preview.
2. THE ChatsScreen SHALL display a message status indicator before the preview text for conversations where the last message was sent by the current user: a single grey tick indicating the message was sent to the server, a double grey tick indicating the message was delivered to the recipient's device, or a double blue tick indicating the message was read by the recipient. IF the last message sent by the current user failed to send, THEN THE ChatsScreen SHALL display a red error icon before the preview text instead of a tick indicator.
3. WHEN a user long-presses a conversation row, THE ChatsScreen SHALL display a context menu within 200ms containing the actions: Pin (or Unpin if the conversation is already pinned), Mute (or Unmute if the conversation is already muted), Archive, and Delete.
4. WHEN a conversation is pinned, THE ChatsScreen SHALL display pinned conversations at the top of the list (above unpinned conversations) with a pin icon indicator, maintaining timestamp sort order within the pinned group. THE ChatsScreen SHALL allow a maximum of 3 pinned conversations at a time. IF the user attempts to pin a conversation when 3 conversations are already pinned, THEN THE ChatsScreen SHALL display an error message indicating the maximum pin limit has been reached and SHALL NOT pin the conversation.
5. WHEN a conversation is muted, THE ChatsScreen SHALL display a mute icon next to the timestamp and SHALL NOT display the unread count badge for that conversation.

### Requirement 25: Media Gallery Access from Chat

**User Story:** As a user, I want to view all shared media in a conversation from the chat header, so that I can find photos and files without scrolling through messages.

#### Acceptance Criteria

1. WHEN a user taps the media gallery action in the ChatScreen header (available for both 1:1 and group conversations), THE ChatScreen SHALL navigate to a MediaGallery screen displaying two tabs: "Media" (images and videos) and "Files" (shared documents), with the "Media" tab selected by default.
2. THE MediaGallery "Media" tab SHALL display media items in a 3-column grid sorted by timestamp descending (most recent first), loading items in pages of 30, and SHALL display a loading indicator at the bottom of the grid while the next page is being fetched.
3. WHEN a user taps an image item in the gallery, THE MediaGallery SHALL display the image in a full-screen viewer with pinch-to-zoom, swipe-to-dismiss, and left/right swipe to navigate between items.
4. WHEN a user taps a video item in the gallery, THE MediaGallery SHALL display the video in a full-screen viewer with play/pause controls, a seek bar, swipe-to-dismiss, and left/right swipe to navigate between items.
5. THE MediaGallery SHALL load thumbnail versions of images (240px width) in the grid view and full-resolution versions only when the full-screen viewer is opened.
6. IF a media thumbnail or full-resolution image fails to load, THEN THE MediaGallery SHALL display a placeholder error icon in place of the media item and SHALL allow the user to tap to retry loading.
7. IF a conversation has no shared media, THEN THE MediaGallery SHALL display an empty state with the text "No media shared yet" on the Media tab, and "No files shared yet" on the Files tab.

### Requirement 26: Completing Incomplete ViewModel Features

**User Story:** As a developer, I want all ViewModel methods that are currently stubbed to be fully implemented, so that the UI components can function end-to-end.

#### Acceptance Criteria

1. THE ChatViewModel SHALL implement message send retry logic that clears input text and reply state on send, exposes a retry action per FAILED message that re-attempts sending exactly once per tap, tracks consecutive failures (3 consecutive failures triggers a snackbar "Message could not be sent" displayed for 4 seconds then auto-dismissed), resets the consecutive failure counter to 0 on any successful send or after showing the snackbar, and IF the offline queue contains 50 or more pending messages THEN THE ChatViewModel SHALL reject the send and expose an isOfflineQueueFull flag to the UI without clearing the input text.
2. THE ChatViewModel SHALL implement reaction toggle via MessageRepository using optimistic local update with server-side rollback on failure, manage replyingToMessage state (set on reply action, cleared on dismiss or on send), and emit a read event on conversation open covering all unread messages; IF the WebSocket is disconnected at conversation open time, THEN THE ChatViewModel SHALL defer the read event emission until the connection is restored and then emit it immediately.
3. THE ChatViewModel SHALL implement typing indicator observation that displays "{name} is typing…" for 1:1 conversations, displays "{name1}, {name2} are typing…" for exactly 2 typers in group conversations, displays "{name1}, {name2} +N are typing…" for 3 or more typers in group conversations where N is the count beyond the first 2, ignores typing events from the current user, and auto-hides each user's typing state after 5 seconds of no typing event from that user.
4. THE ChatViewModel SHALL implement connection state observation that exposes a reconnecting banner state within 500ms of disconnect, shows a manual retry action after 10 consecutive failed reconnection attempts, hides the banner with a 300ms fade-out animation on successful reconnect, and WHEN reconnected THE ChatViewModel SHALL fetch missed messages via REST using the last cached message timestamp and report fetch failure with an inline retry option.
5. THE CreateGroupViewModel SHALL implement group creation with name validation (3-50 characters, blank names rejected), member count validation (minimum 1 member, maximum 50 members excluding the creator), avatar upload via image URI before the REST API call, REST API call to create the group, invite code display with a share action on success, and IF any step fails (avatar upload or API call) THEN THE CreateGroupViewModel SHALL display an error message indicating the failure reason and preserve all form fields (name, description, selected members, avatar URI) for retry without requiring re-entry.
6. THE GroupInfoViewModel SHALL implement member list display sorted by role (admins first) then alphabetical by display name (case-insensitive), role labels per member row, admin-only actions (remove member requiring confirmation before execution, delete group requiring confirmation before execution), leave group action (disabled and non-invocable if the current user is the sole admin), shared media gallery displaying the 20 most recent IMAGE-type messages, and IF any server operation (remove member, leave group, delete group) fails THEN THE GroupInfoViewModel SHALL display an error indication and preserve the current member list and group state without mutation.

### Requirement 27: Wiring Incomplete UI Components

**User Story:** As a developer, I want all existing UI components (ReactionPicker, ReplyPreviewBar, ReadReceiptIndicator, TypingIndicator, ImageMessageBubble, LocationMessageBubble, ReconnectionBanner) to be fully connected to their ViewModels, so that the features work end-to-end.

#### Acceptance Criteria

1. WHEN the user long-presses (≥ 500ms) a message bubble, THE ChatScreen SHALL display the ReactionPickerOverlay centered on screen with a scrim background, and WHEN the user selects an emoji, THE ChatScreen SHALL call ChatViewModel.toggleReaction with the message ID and selected emoji and dismiss the overlay.
2. WHEN the user taps outside the ReactionPickerOverlay or performs a back gesture, THE ChatScreen SHALL dismiss the overlay without triggering any reaction.
3. IF replyingToMessage state is non-null, THEN THE ChatScreen SHALL display the ReplyPreviewBar above the input bar showing the sender name and message text truncated to 100 characters with ellipsis overflow, and WHEN the user taps the close button, THE ChatScreen SHALL call ChatViewModel.clearReply to set replyingToMessage to null without modifying inputText.
4. THE ChatScreen SHALL display the ReadReceiptIndicator below each sent message whose readBy list contains at least one non-sender user, showing the double-tick icon (DoneAll) tinted with primary color and up to 3 overlapping reader avatars with a "+N" text when readers exceed 3.
5. WHEN typingIndicatorText state is non-null, THE ChatScreen SHALL display the TypingIndicator component below the last message showing the animated 3-dot bouncing indicator and the pre-formatted typing text from ChatUiState ("{name} is typing…" for 1:1, "{name1}, {name2} +N are typing…" for groups with at most 2 names displayed).
6. THE ChatScreen SHALL integrate the ImageMessageBubble component by displaying a determinate linear progress bar (0–100%) overlay during PENDING status with non-null uploadProgress, an error overlay with a circular retry icon on FAILED status, and a solid placeholder matching 4:3 aspect ratio while a received image is loading.
7. THE ChatScreen SHALL integrate the LocationMessageBubble component by displaying the LocationOn icon, the message text, and the locationLabel below it, with tap navigating to the group map via onNavigateToGroupMap when the conversation has a non-null groupId.
8. WHEN showReconnectingBanner state becomes true, THE ChatScreen SHALL display the ReconnectionBanner at the top of the chat area showing "Reconnecting…" with a spinner, and IF showManualRetryAction is true, THEN THE ChatScreen SHALL display the error variant with a "Retry" button that calls ChatViewModel.manualRetry, and WHEN isBannerFadingOut becomes true, THE ChatScreen SHALL animate the banner out with a 300ms fade-out before hiding it.
