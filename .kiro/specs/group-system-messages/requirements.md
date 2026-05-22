# Requirements Document

## Introduction

This feature adds **system messages** (Messenger / WhatsApp style "info messages") to chat conversations. When events happen inside a group or DM — someone joins, the group is renamed, the photo changes, a member is promoted, etc. — a small centered grey line appears chronologically in the chat timeline summarising the event. System messages are persisted alongside regular messages so they survive reload, sync across devices, and respect normal message ordering.

The feature covers twelve event types spanning group identity, membership, roles, customization, location sharing, and friendship state changes.

## Glossary

- **System_Message**: A message of `MessageType.SYSTEM` rendered as a centered grey caption inside the chat timeline. Has no avatar, no bubble, no reactions, no read receipts, and is not editable or deletable by users.
- **Event_Type**: A discriminator describing what action a System_Message represents (e.g. `GROUP_RENAMED`, `MEMBER_ADDED`).
- **Actor**: The user who performed the action that triggered the System_Message.
- **Target**: The user the action was performed on (e.g. the user who was added, removed, or promoted). May be null for events that have no target (e.g. group rename).
- **Event_Payload**: A typed map carried with each System_Message containing event-specific data (e.g. `oldName`, `newName`, `oldPhotoUrl`).
- **Cloud_Function**: A Firestore-triggered serverless function that authors System_Messages in response to data changes. Authoritative source so two clients can't race or duplicate.
- **Locale_Template**: A localised string template with named placeholders (e.g. `"{actor} renamed the group to {newName}"`) resolved at render time on the client.
- **Live_Location_Session**: An active location-sharing session as already modeled by the app (`locationSharingSessionId`).
- **Friendship_Block**: An entry in the friendship system marking that one user has blocked another, surfaced into a DM as a System_Message visible only inside that DM.
- **Chat_Timeline**: The lazily-loaded list of messages displayed by `ChatScreen`, ordered by `timestamp` ascending.

## Requirements

### Requirement 1: System Message Domain Model

**User Story:** As a developer, I want a typed representation of system events stored on the existing `Message` entity, so that the chat timeline can render and the data layer can persist them with no parallel pipeline.

#### Acceptance Criteria

1. THE Message domain model SHALL retain its existing `MessageType.SYSTEM` value and add a non-nullable `systemEventType: SystemEventType?` field plus a `systemEventPayload: Map<String, String>` field, both nullable for non-system messages.
2. THE SystemEventType SHALL be an enum covering: `GROUP_RENAMED`, `GROUP_DESCRIPTION_CHANGED`, `GROUP_PHOTO_CHANGED`, `MEMBER_ADDED`, `MEMBER_REMOVED`, `MEMBER_LEFT`, `MEMBER_JOINED`, `MEMBER_PROMOTED`, `MEMBER_DEMOTED`, `NICKNAME_CHANGED`, `THEME_COLOR_CHANGED`, `EMOJI_SHORTCUT_CHANGED`, `LIVE_LOCATION_STARTED`, `USER_BLOCKED`.
3. THE Message.senderId field SHALL hold the Actor user id for system messages.
4. THE Message SHALL carry a `targetUserId: String?` field nullable for events that have no Target.
5. THE Message.text field SHALL contain a fallback English rendering of the event ("Ovi changed the group photo") so legacy clients without the typed fields still display something readable.
6. WHEN a system message is created without a `systemEventType`, THE data layer SHALL reject the write at the repository boundary.
7. THE existing message types (TEXT, LOCATION, IMAGE, VOICE, LIVE_LOCATION, VIDEO, DOCUMENT) SHALL be unaffected by these schema additions.

### Requirement 2: Local Persistence (Room)

**User Story:** As a user, I want system messages to persist offline and be paginated alongside regular messages, so that scrolling through history shows events in the right place even with no connection.

#### Acceptance Criteria

1. THE existing `MessageEntity` Room table SHALL gain three columns: `systemEventType` (nullable text), `systemEventPayload` (nullable text, JSON-encoded), and `targetUserId` (nullable text).
2. THE Room migration SHALL add the columns with default `NULL` values without dropping or recreating the table.
3. THE MessageDao SHALL preserve the existing query order semantics — system messages are interleaved by `timestamp ASC` with regular messages.
4. WHEN a system message row is mapped to the domain `Message`, THE mapper SHALL parse `systemEventPayload` JSON into a `Map<String, String>`; on parse failure it SHALL log via Timber and treat the payload as empty rather than dropping the row.
5. THE local cache SHALL not require any special invalidation for system messages — they follow the same read/write/sync paths as `MessageType.TEXT`.

### Requirement 3: Cloud Functions Triggered Authoring

**User Story:** As a user, I want the system messages I see to match exactly what happened on the server, so that two clients editing the same group can't produce duplicate or contradictory event entries.

#### Acceptance Criteria

1. WHEN a Cloud_Function detects a Firestore change matching a tracked event, IT SHALL write a single message document into the corresponding conversation's `messages` subcollection within the same logical operation as the data change (idempotent on retry).
2. THE Cloud_Function SHALL set `senderId` to the Actor's uid (resolved from `request.auth.uid` for HTTPS-callable triggers, or from the Firestore document's `updatedBy` field for write triggers, or `"system"` if neither is available), `senderName` to the Actor's resolved displayName at trigger time, `senderPhotoUrl` to null, `type = "SYSTEM"`, `text` to the localised English fallback, and the typed `systemEventType` / `systemEventPayload` fields per Requirement 1.
3. THE Cloud_Function SHALL also update the parent conversation's `lastMessageText`, `lastMessageType = "SYSTEM"`, `lastMessageTimestamp`, and `lastMessageSenderId` fields atomically with the message write so the chat list preview reflects the event.
4. THE Cloud_Function SHALL not increment per-user `unreadCounts` for system messages — system messages are informational and do not generate notifications.
5. WHEN a Cloud_Function fails to write the system message, IT SHALL emit a structured error log but SHALL NOT roll back the underlying data change.
6. THE following data changes SHALL each have a corresponding Cloud_Function trigger writing exactly one system message:
   - `groups/{id}.name` change → `GROUP_RENAMED` (payload: `oldName`, `newName`)
   - `groups/{id}.description` change → `GROUP_DESCRIPTION_CHANGED` (payload: `oldDescription`, `newDescription`)
   - `groups/{id}.avatarUrl` change → `GROUP_PHOTO_CHANGED` (payload: `oldPhotoUrl`, `newPhotoUrl`)
   - `groups/{id}/members/{uid}` create → `MEMBER_JOINED` if user joined themselves (e.g. via invite link), or `MEMBER_ADDED` if `actorId != targetId`
   - `groups/{id}/members/{uid}` delete → `MEMBER_LEFT` if `actorId == targetId`, otherwise `MEMBER_REMOVED`
   - `groups/{id}/members/{uid}.role` change → `MEMBER_PROMOTED` (member→admin) or `MEMBER_DEMOTED` (admin→member)
   - `conversations/{id}.nicknames.{uid}` change → `NICKNAME_CHANGED` (payload: `targetUserId`, `oldNickname`, `newNickname`)
   - `conversations/{id}.themeColor` change → `THEME_COLOR_CHANGED` (payload: `oldColor`, `newColor`)
   - `conversations/{id}.emojiShortcut` change → `EMOJI_SHORTCUT_CHANGED` (payload: `oldEmoji`, `newEmoji`)
   - `activeLocations/{uid}` create with `targetIds` containing a conversationId → `LIVE_LOCATION_STARTED` (payload: `durationMinutes`)
   - `friendships/{id}.status` change to `BLOCKED` → `USER_BLOCKED` written into the DM conversation (payload empty)
7. WHEN multiple fields change in the same Firestore update (e.g. name + description in one `updateGroup` call), THE Cloud_Functions SHALL emit one system message per changed field, all sharing the same `timestamp` (or differing by 1ms to preserve ordering).

### Requirement 4: Server-Side Idempotency

**User Story:** As a user, I want Cloud Functions retries during transient errors not to leave duplicate "Ovi renamed the group" lines in my chat, so that the timeline stays clean.

#### Acceptance Criteria

1. THE Cloud_Function SHALL compute a deterministic `messageId` as `"sys_{eventType}_{conversationId}_{timestampBucket}_{actorId}_{targetId?}"` where `timestampBucket = floor(timestamp / 1000)` so retries within the same second share an id.
2. THE Cloud_Function SHALL use Firestore's `set` with `merge = false` on the deterministic id; duplicate retries SHALL overwrite identical content rather than create new docs.
3. WHEN two distinct field changes occur within the same `timestampBucket`, THE Cloud_Function SHALL include the field name in the id (e.g. `"sys_GROUP_RENAMED_..."`) so they get different ids.

### Requirement 5: Chat Timeline Rendering

**User Story:** As a user, I want system messages to look like Messenger's grey informational lines so I can immediately distinguish them from real conversation, without losing scroll position.

#### Acceptance Criteria

1. WHEN the `ChatScreen` LazyColumn encounters a message with `type == MessageType.SYSTEM`, THE ChatScreen SHALL render a `ChatSystemMessage` composable instead of a `ChatBubble`.
2. THE ChatSystemMessage SHALL be horizontally centered, rendered with `MaterialTheme.typography.labelMedium`, color `MaterialTheme.colorScheme.onSurfaceVariant`, with `12.dp` vertical padding above and below.
3. THE ChatSystemMessage SHALL render a single line where possible, wrapping to two lines maximum with `TextOverflow.Ellipsis` if longer.
4. THE ChatSystemMessage SHALL not render an avatar, sender name, timestamp footer, status indicator, or reaction bar.
5. THE ChatSystemMessage SHALL be tappable to reveal a small floating timestamp (e.g. "Today at 14:32") that fades out after 2 seconds; identical to the existing day-divider timestamp pattern used by `ChatBubble`.
6. THE ChatSystemMessage SHALL never appear in long-press selection mode and SHALL not surface a context menu (no copy, no react, no reply, no delete).
7. THE LazyColumn SHALL key system messages by `message.id` exactly like regular messages so item identity stays stable across recompositions.
8. THE existing date dividers SHALL still render between system messages and regular messages, ordered by `timestamp`.

### Requirement 6: Localised Event Templates

**User Story:** As a user, I want the system message wording to read naturally — using the actor's display name, the target's display name, and event-specific values — so the timeline feels human, not technical.

#### Acceptance Criteria

1. THE client SHALL maintain a `SystemMessageRenderer` that maps `(systemEventType, payload, actorName, targetName, currentUserId, actorId, targetId)` to a final localised string.
2. THE SystemMessageRenderer SHALL substitute `"You"` for the Actor's name when `actorId == currentUserId`, and `"you"` mid-sentence (lowercase) when the Target is the current user.
3. THE SystemMessageRenderer SHALL produce these templates (English, default `Locale`):
   - `GROUP_RENAMED` → `"{actor} renamed the group to "{newName}""`
   - `GROUP_DESCRIPTION_CHANGED` → `"{actor} updated the group description"`
   - `GROUP_PHOTO_CHANGED` → `"{actor} changed the group photo"`
   - `MEMBER_ADDED` → `"{actor} added {target}"`
   - `MEMBER_REMOVED` → `"{actor} removed {target}"`
   - `MEMBER_LEFT` → `"{actor} left the group"`
   - `MEMBER_JOINED` → `"{actor} joined the group"`
   - `MEMBER_PROMOTED` → `"{actor} made {target} an admin"`
   - `MEMBER_DEMOTED` → `"{actor} removed {target} as admin"`
   - `NICKNAME_CHANGED` (with `newNickname` non-empty) → `"{actor} set {target}'s nickname to "{newNickname}""`
   - `NICKNAME_CHANGED` (with `newNickname` empty) → `"{actor} cleared {target}'s nickname"`
   - `THEME_COLOR_CHANGED` → `"{actor} changed the chat color"`
   - `EMOJI_SHORTCUT_CHANGED` → `"{actor} set the emoji shortcut to {newEmoji}"`
   - `LIVE_LOCATION_STARTED` → `"{actor} started sharing their live location"`
   - `USER_BLOCKED` → `"{actor} blocked you"` (only ever rendered to the blocked user; otherwise the message is filtered out by the renderer)
4. WHEN the `Actor` is the current user, THE rendered string SHALL begin with `"You"` capitalised at the sentence start.
5. WHEN the `Target` is the current user, THE rendered string SHALL substitute `"you"` for the target's name.
6. WHEN the rendered string would self-reference (e.g. "You added you"), THE renderer SHALL fall back to a context-appropriate variant (e.g. drop the second `"you"`) — defined per template.
7. THE renderer SHALL be a pure function with no Android `Context` dependency, accepting a `StringResources` interface so unit tests can supply fakes.

### Requirement 7: Conversation Preview Behaviour

**User Story:** As a user, I want the chat list preview to reflect the latest system event when nothing else has been said, so the chat list never looks stale.

#### Acceptance Criteria

1. WHEN the latest message in a conversation is a system message, THE chat list preview row SHALL display the rendered system text (Requirement 6) instead of "..." or empty.
2. WHEN the latest message is a system message, THE chat list preview row SHALL not show a sender avatar prefix (e.g. "You: ...") — system events stand alone.
3. WHEN the latest system message is `USER_BLOCKED`, IT SHALL only appear in the preview for the blocked user; other participants see the previous non-system message as the preview.
4. WHEN no non-system messages exist yet (a freshly-created group with only a "Ovi created the group" event), THE preview SHALL still render the system message line so the chat list isn't blank.

### Requirement 8: Notification Suppression

**User Story:** As a user, I don't want my phone to buzz every time someone changes the chat color, so the system events stay informational and don't pollute notifications.

#### Acceptance Criteria

1. WHEN a system message is created server-side, THE FCM notification pipeline SHALL not deliver a push notification for it.
2. THE conversation `unreadCounts.{uid}` field SHALL not be incremented for system messages (already covered by Requirement 3.4).
3. THE in-app message-delivered animation/badge SHALL not animate for newly received system messages.

### Requirement 9: Permissions and Visibility

**User Story:** As a user, I only want to see system messages relevant to me, so I'm not confused by events I had no part in seeing.

#### Acceptance Criteria

1. THE system message read access SHALL follow the existing per-conversation Firestore rule — any participant of the conversation can read all messages including system ones.
2. THE `USER_BLOCKED` system message SHALL be filtered out client-side in the `MessageRepository` so it appears only in the blocked user's view of the DM.
3. THE Cloud_Function for `USER_BLOCKED` SHALL author exactly one message into the DM and rely on client filtering rather than writing two different docs.
4. WHEN a user is removed from a group, THE existing system messages remain in the historical chat (visible if the user is later re-added); no retroactive cleanup is performed.

### Requirement 10: Backwards Compatibility

**User Story:** As a user running an older app version, I want my chat to keep working when servers start writing system messages I can't fully render, so the upgrade is smooth.

#### Acceptance Criteria

1. WHEN an older client receives a `MessageType.SYSTEM` message it doesn't fully understand (unknown `systemEventType`), THE older client SHALL fall back to displaying the `Message.text` field as plain text in a centered grey style.
2. THE Room migration SHALL be additive (Requirement 2.2) so a force-downgrade still reads existing messages, just without the new typed fields.
3. THE Cloud_Function payloads SHALL never omit `Message.text` — the legacy fallback rendering must always produce a readable English line.

### Requirement 11: Testing

**User Story:** As a developer, I want each event template, payload mapping, and edge case proven by tests, so that future changes don't silently break a single event type.

#### Acceptance Criteria

1. THE `SystemMessageRenderer` SHALL have unit tests covering every `SystemEventType` in Requirement 1.2, including the You/you substitution rules.
2. THE `MessageEntity ↔ Message` mapper SHALL have property-based tests (Kotest Property Testing, ≥100 iterations) verifying that arbitrary `Map<String, String>` payloads round-trip without loss.
3. THE `ChatSystemMessage` composable SHALL have at least one preview screenshot test in `app/src/screenshotTest/...` covering rendered output for `MEMBER_ADDED`, `GROUP_RENAMED`, and `USER_BLOCKED`.
4. THE Cloud_Function idempotency rule (Requirement 4) SHALL have a test executing the same trigger twice and asserting only one message exists.
5. THE conversation preview override (Requirement 7) SHALL have a unit test verifying preview text equals the renderer output when latest message type is SYSTEM.
