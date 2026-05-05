# Where App — Complete Redesign Plan

## Vision

A production-level real-time location sharing social app (Zenly + WhatsApp hybrid).
Users see friends on a live map, chat (DMs + group chat), and manage social connections.
Messenger/iMessage-style chat UI with brand pink theme.

---

## Locked-In Decisions

| Decision | Answer |
|---|---|
| Social model | Friends + Groups + DMs |
| Chat types | Direct messages + Group chat |
| Navigation | 4 tabs, icons only (Instagram style) |
| Location sharing scope | Per-group only — global map aggregates all your groups |
| Friend system | Mutual friend requests (like Facebook) |
| @username | Required on signup — unique, searchable |
| Chat message types | Text + location pin |
| UI style | Clean light theme, iMessage/WhatsApp bubbles, brand pink (#B5006F) |

---

## Current Tech Stack (unchanged)

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Clean Architecture (MVVM + Use Cases), Hilt DI
- **Backend:** Firebase (Auth, Firestore, FCM, Storage)
- **Map:** MapLibre + OpenFreeMap tiles (no API key)
- **Async:** Coroutines + Flow
- **Image loading:** Coil
- **Local storage:** Room (unused currently), DataStore
- **Font:** Nunito (Google Fonts)
- **Theme:** Pink primary (#B5006F), Green secondary (#006E2C), Purple tertiary (#6950A1)

---

## New Firestore Schema

Since location is per-group, the global map aggregates all `groups/{id}/locations/`
collections the user belongs to. No new top-level locations collection needed.

```
users/{uid}
  ├── id, displayName, email, photoUrl
  ├── bio: String              ← NEW
  ├── username: String         ← NEW (unique, required)
  ├── phoneNumber, isOnline, lastSeen, createdAt, fcmToken

friendships/{friendshipId}    ← NEW collection
  ├── id
  ├── requesterId: String
  ├── receiverId: String
  ├── status: "pending" | "accepted" | "blocked"
  ├── createdAt: Long
  └── updatedAt: Long

conversations/{conversationId}    ← NEW collection
  ├── id
  ├── type: "direct" | "group"
  ├── participantIds: [uid1, uid2, ...]
  ├── groupId: String?              ← null for DMs, groupId for group chats
  ├── name: String                  ← other user's name for DM, group name for group
  ├── photoUrl: String?
  ├── lastMessageText: String
  ├── lastMessageSenderId: String
  ├── lastMessageTimestamp: Long
  ├── unreadCounts: { uid: count }  ← per-user unread count map
  └── createdAt: Long

conversations/{conversationId}/messages/{messageId}    ← NEW subcollection
  ├── id, conversationId
  ├── senderId, senderName, senderPhotoUrl
  ├── text: String
  ├── type: "text" | "location"
  ├── latitude: Double?     ← for location type
  ├── longitude: Double?    ← for location type
  ├── timestamp: Long
  └── readBy: [uid]

groups/{groupId}            ← unchanged structure
  ├── id, name, description, createdBy, createdAt
  ├── memberCount, inviteCode, memberIds[]
  ├── conversationId: String    ← NEW field — links group to its conversation
  └── members/{uid}
        └── (existing GroupMember fields)
  └── locations/{uid}
        └── (existing SharedLocation fields)
```

---

## New Domain Models

```kotlin
// Message.kt
data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val type: MessageType = MessageType.TEXT,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = 0L,
    val readBy: List<String> = emptyList()
)

enum class MessageType { TEXT, LOCATION }

// Conversation.kt
data class Conversation(
    val id: String = "",
    val type: ConversationType = ConversationType.DIRECT,
    val participantIds: List<String> = emptyList(),
    val groupId: String? = null,
    val name: String = "",
    val photoUrl: String? = null,
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val createdAt: Long = 0L
)

enum class ConversationType { DIRECT, GROUP }

// Friendship.kt
data class Friendship(
    val id: String = "",
    val requesterId: String = "",
    val receiverId: String = "",
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class FriendshipStatus { PENDING, ACCEPTED, BLOCKED }

// Updated User.kt — add these fields:
// val bio: String = ""
// val username: String = ""
```

---

## New Repository Interfaces

### ConversationRepository
```kotlin
interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(conversationId: String): Flow<Conversation?>
    suspend fun getOrCreateDirectConversation(otherUserId: String): Resource<Conversation>
    suspend fun createGroupConversation(groupId: String, name: String, memberIds: List<String>): Resource<Conversation>
    suspend fun markAsRead(conversationId: String, userId: String): Resource<Unit>
    suspend fun updateLastMessage(conversationId: String, text: String, senderId: String): Resource<Unit>
}
```

### MessageRepository
```kotlin
interface MessageRepository {
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, text: String): Resource<Message>
    suspend fun sendLocationMessage(conversationId: String, latitude: Double, longitude: Double): Resource<Message>
}
```

### FriendshipRepository
```kotlin
interface FriendshipRepository {
    suspend fun sendFriendRequest(receiverId: String): Resource<Unit>
    suspend fun acceptFriendRequest(friendshipId: String): Resource<Unit>
    suspend fun declineFriendRequest(friendshipId: String): Resource<Unit>
    suspend fun removeFriend(userId: String): Resource<Unit>
    fun observeFriends(): Flow<List<User>>
    fun observeFriendRequests(): Flow<List<Friendship>>
    suspend fun getFriendshipStatus(otherUserId: String): FriendshipStatus?
    fun observeAllFriendLocations(): Flow<List<SharedLocation>>
}
```

---

## New Use Cases (18 new)

### Chat Use Cases
| Class | Method |
|---|---|
| `SendMessageUseCase` | `invoke(conversationId, text): Resource<Message>` |
| `SendLocationMessageUseCase` | `invoke(conversationId, lat, lng): Resource<Message>` |
| `ObserveMessagesUseCase` | `invoke(conversationId): Flow<List<Message>>` |
| `ObserveConversationsUseCase` | `invoke(): Flow<List<Conversation>>` |
| `GetOrCreateDirectConversationUseCase` | `invoke(otherUserId): Resource<Conversation>` |
| `MarkConversationReadUseCase` | `invoke(conversationId): Resource<Unit>` |

### Friend Use Cases
| Class | Method |
|---|---|
| `SendFriendRequestUseCase` | `invoke(userId): Resource<Unit>` |
| `AcceptFriendRequestUseCase` | `invoke(friendshipId): Resource<Unit>` |
| `DeclineFriendRequestUseCase` | `invoke(friendshipId): Resource<Unit>` |
| `RemoveFriendUseCase` | `invoke(userId): Resource<Unit>` |
| `ObserveFriendsUseCase` | `invoke(): Flow<List<User>>` |
| `ObserveFriendRequestsUseCase` | `invoke(): Flow<List<Friendship>>` |
| `GetFriendshipStatusUseCase` | `invoke(userId): FriendshipStatus?` |
| `ObserveAllFriendLocationsUseCase` | `invoke(): Flow<List<SharedLocation>>` |

---

## Updated Navigation

### Bottom Tabs (icons only — no labels)

```
Tab 1: Map      (MapPin icon)         → GlobalMapScreen   ← starting destination
Tab 2: Chats    (ChatBubble icon)     → ChatsScreen       + unread badge
Tab 3: People   (PeopleAlt icon)      → PeopleScreen      + request badge
Tab 4: Profile  (Person icon)         → ProfileScreen
```

### Full-Screen Stack Routes

```
chat/{conversationId}         → ChatScreen (DM or group chat)
user_profile/{userId}         → UserProfileScreen
friend_requests               → FriendRequestsScreen
search_people                 → SearchUsersScreen
group_details/{groupId}       → GroupDetailsScreen (updated)
group_map/{groupId}           → GroupMapScreen (renamed MapScreen)
create_group                  → CreateGroupScreen (updated)
join_group                    → JoinGroupScreen (unchanged)
edit_group/{groupId}          → EditGroupScreen (unchanged)
```

### Auth Routes (unchanged)
```
splash → onboarding → login → register → forgot_password → main
```

---

## Complete Screen Inventory

### New Screens

#### 1. GlobalMapScreen *(starting destination, Tab 1)*
**ViewModel:** `GlobalMapViewModel`

**What it does:**
- Full-screen MapLibre map
- Aggregates locations from ALL groups the user belongs to
- Friend avatar markers: circular photo or initial letter, `colorScheme.surface` border
- Active sharers: pulsing green ring animation (`colorScheme.tertiary`)
- Tap a marker → bottom sheet slides up:
  - Friend avatar (64dp), display name, "@username"
  - "Sharing since X / Last seen X ago" text
  - **Message** button (primary, opens ChatScreen)
  - **View Profile** button (secondary, opens UserProfileScreen)
- Top floating pill: "All Friends" dropdown → switch to group filter
  - Group filter sheet: list of user's groups, tap to filter map to that group
- FABs: My Location (SmallFAB), Fit All Friends (SmallFAB)
- Top right: own sharing toggle (start/stop sharing in a group)

**State:**
```kotlin
data class GlobalMapUiState(
    val friendLocations: List<FriendLocationUiModel> = emptyList(),
    val myLatitude: Double = 0.0,
    val myLongitude: Double = 0.0,
    val hasMyLocation: Boolean = false,
    val selectedFriend: FriendLocationUiModel? = null,
    val showFriendSheet: Boolean = false,
    val activeGroupFilter: String? = null,
    val isLoading: Boolean = false
)
```

---

#### 2. ChatsScreen *(Tab 2)*
**ViewModel:** `ChatsViewModel`

**What it does:**
- "Chats" large headline (Instagram style, `headlineLarge`)
- Search bar below title (tapping opens SearchUsersScreen)
- Active Location row: horizontal scroll of friends currently sharing (avatar rings)
- Conversation list (`LazyColumn`):
  - Each row: circular avatar (or group collage of 4 avatars for group chat)
  - Bold name if unread, normal if read
  - Grey last message preview (truncated 1 line)
  - Relative time (right aligned): "2m", "1h", "Yesterday"
  - Pink unread badge (circle with count) if unread > 0
  - Green dot on avatar if user is online (DM only)
  - Long press → options: Delete, Mute, Archive (sheet)
- Empty state: icon + "No chats yet. Find friends to start chatting."
- FAB: compose new message (opens SearchUsersScreen)

**State:**
```kotlin
data class ChatsUiState(
    val conversations: List<ConversationUiModel> = emptyList(),
    val activeFriends: List<UserUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

---

#### 3. ChatScreen *(DM and Group Chat)*
**ViewModel:** `ChatViewModel`

**What it does:**
- **TopAppBar:** back button + circular avatar (64dp) + name + online dot (for DM) or
  member count (for group) + info icon
- **Messages list** (`LazyColumn`, reversed):
  - Sent: right-aligned, `colorScheme.primary` bubble, `onPrimary` text
  - Received: left-aligned, `colorScheme.surfaceVariant` bubble, `onSurfaceVariant` text
  - Corners: `16.dp` all, except `4.dp` on the "tail" corner
  - For group chat: small avatar left of received bubble, sender name above first consecutive message
  - Date separator chips between different days
  - Timestamp (`labelSmall`, grey) below last consecutive message in a group
  - Location message: mini Card with MapPin icon + "Shared a location" text, tappable opens map
  - Read receipts: small avatar row below sent messages
- **Input bar** (pinned above keyboard, `imePadding`):
  - `OutlinedTextField` with `extraLarge` shape (pill), `surfaceVariant` fill
  - Send button: pink FAB (shown when text non-empty)
  - Location pin icon button (left of text field)
- Marks messages as read when screen is visible

**State:**
```kotlin
data class ChatUiState(
    val conversation: ConversationUiModel? = null,
    val messages: List<MessageUiModel> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val otherUserOnline: Boolean = false
)
```

---

#### 4. PeopleScreen *(Tab 3)*
**ViewModel:** `PeopleViewModel`

**What it does:**
- "People" headline
- Search bar (tapping → opens SearchUsersScreen)
- **Friend Requests card** (shown only if pending count > 0):
  - Pink accent, "{N} Friend Request{s}"
  - Tap → opens FriendRequestsScreen
- **My Friends section:**
  - Count badge: "X friends"
  - `LazyColumn` of friends, alphabetical order
  - Each row: avatar (48dp) + display name + @username + green online dot + "Message" button
  - Swipe-to-dismiss → "Unfriend" confirmation dialog
- Empty state: icon + "Find friends by searching above"

**State:**
```kotlin
data class PeopleUiState(
    val friends: List<UserUiModel> = emptyList(),
    val pendingRequestCount: Int = 0,
    val isLoading: Boolean = false
)
```

---

#### 5. FriendRequestsScreen
**ViewModel:** `FriendRequestsViewModel`

**What it does:**
- `TabRow`: "Received" | "Sent" tabs
- **Received tab:** pending received requests
  - Row: avatar + name + "@username" + **Accept** button + **Decline** button
  - Empty: "No pending requests"
- **Sent tab:** sent pending requests
  - Row: avatar + name + "Pending" chip + **Cancel** button
  - Empty: "No sent requests"

---

#### 6. SearchUsersScreen
**ViewModel:** `SearchUsersViewModel`

**What it does:**
- `SearchBar` composable (auto-focused on open)
- Results list: avatar + name + @username + email domain
- Per-result action: **Add Friend** / **Pending** / **Friends** / **Message**
- Empty query: "Search by name, @username, or email"
- No results: "No users found for '{query}'"

---

#### 7. UserProfileScreen
**ViewModel:** `UserProfileViewModel`

**What it does:**
- Large avatar (96dp), display name, "@username", bio
- Stats row: "X mutual friends" | "X groups in common"
- Action buttons:
  - Not friends: **Add Friend** (primary) + **Message** (outlined)
  - Request sent: **Pending** (tonal) + **Message**
  - Request received: **Accept** (primary) + **Decline** (outlined)
  - Friends: **Message** (primary) + **Friends ▾** (outlined → Unfriend option)
- Location indicator if friend is currently sharing in a shared group
- Groups in common list (if friends)

---

### Revamped Screens

#### 8. ProfileScreen *(Tab 4 — completely revamped)*

**What it does:**
- **Hero:** avatar (120dp) with camera edit badge, tap → photo picker → Firebase Storage upload
- **Display name** (tap pencil → inline edit)
- **@username** (tap → edit, validates uniqueness)
- **Bio** (tap → multi-line edit, 150 char limit with counter)
- **Stats row:** `{N} Friends` | `{N} Groups` (both tappable)
- **Location Sharing section** (Card):
  - Per-group toggles: each group with name + sharing toggle
  - Start sharing → duration picker dialog
- **Settings section** (Card):
  - Notifications, Location, Battery Optimization, Privacy
- **Sign Out** button (`errorContainer` background)
- App version at bottom

---

#### 9. GlobalMapScreen *(see full description above)*

---

### Updated Existing Screens

#### GroupDetailsScreen
- Add **"Group Chat"** button below invite code card → opens `ChatScreen` with group's `conversationId`

#### CreateGroupScreen
- After group created → automatically creates a group `Conversation` doc
- Navigates to the new group chat on success

#### RegisterScreen
- Add **@username field** (after display name, before email)
- Validation: letters/numbers/underscores, 3–20 chars, unique check on blur

### Removed Screens
- `HomeScreen` + `HomeViewModel` — deleted
- `SettingsScreen` + `SettingsViewModel` — deleted (dead code)

---

## UI Design Specifications

### Chat Bubbles
```
Sent message:
  Background:    colorScheme.primary (#B5006F)
  Text color:    colorScheme.onPrimary (white)
  Alignment:     end (right)
  Corner radius: shape.large all, shape.extraSmall bottom-right ("tail")
  Max width:     75% of screen width
  Padding:       12dp horizontal, 8dp vertical

Received message:
  Background:    colorScheme.surfaceVariant
  Text color:    colorScheme.onSurfaceVariant
  Alignment:     start (left)
  Corner radius: shape.large all, shape.extraSmall bottom-left ("tail")
  Max width:     75% of screen width
  Padding:       12dp horizontal, 8dp vertical

Date separator:
  Centered chip, colorScheme.surfaceVariant, bodySmall, grey text

Timestamp:
  labelSmall, colorScheme.onSurfaceVariant, below message group
```

### Bottom Navigation
```
Style:     Icons only (no labels) — Instagram style
Icon size: 26dp
Selected:  filled icon, colorScheme.primary tint
Unselected: outlined icon, colorScheme.onSurfaceVariant tint
Unread badge: colorScheme.primary circle, white count, top-right of icon
Background: colorScheme.surface
```

### Map Markers
```
Friend marker:
  Size:          44dp diameter
  Content:       AsyncImage (photo) or initial letter on colorScheme.primary
  Border:        3dp colorScheme.surface
  Active pulse:  animated ring colorScheme.tertiary, expands + fades

Own marker:
  Same + "You" label below
  Extra: colorScheme.primary shadow glow
```

### Chat Input Bar
```
Container:      colorScheme.surface, elevation 4dp, above keyboard
TextField:      OutlinedTextField, shape.extraLarge (pill), surfaceVariant fill
Send FAB:       40dp, colorScheme.primary, animates in/out with text
Location btn:   40dp icon, colorScheme.primary tint
```

---

## FCM Notification Types (updated)

| Type | Payload | Behavior on tap |
|---|---|---|
| `"new_message"` | `conversationId`, `senderName`, `text` | Deep link → `chat/{conversationId}` |
| `"friend_request"` | `requesterId`, `requesterName` | Deep link → `friend_requests` |
| `"friend_accepted"` | `userId`, `userName` | Deep link → `user_profile/{userId}` |
| `"location_update"` | `groupId` (existing) | Opens app root |
| `"member_joined"` | `groupId` (existing) | Opens app root |
| `"member_left"` | `groupId` (existing) | Opens app root |

---

## Bugs to Fix During Implementation

| Bug | Location | Fix |
|---|---|---|
| Photo upload uses `displayName` instead of UID for storage path | `ProfileViewModel.onPhotoSelected` | Use `firebaseAuth.currentUser?.uid` |
| `whereIn` query has no chunking (Firestore max 10) | `UserRepositoryImpl.getUsers` | Chunk into batches of ≤10, merge results |
| `activeSharingSessions` in-memory — lost on process kill | `LocationRepositoryImpl` | Check Firestore `isSharingActive` on ViewModel init |
| `SettingsScreen` unreachable (mapped to `ProfileScreen` in nav) | `AppNavGraph` | Remove `SettingsScreen` entirely |
| `Screen.Home` and `Screen.Profile` defined but no nav routes exist | `Screen.kt` | Remove unused route constants |

---

## New Files to Create (26 files)

### Domain Models (3)
```
domain/model/Message.kt
domain/model/Conversation.kt
domain/model/Friendship.kt
```

### Repository Interfaces (3)
```
domain/repository/ConversationRepository.kt
domain/repository/MessageRepository.kt
domain/repository/FriendshipRepository.kt
```

### Repository Implementations (3)
```
data/repository/ConversationRepositoryImpl.kt
data/repository/MessageRepositoryImpl.kt
data/repository/FriendshipRepositoryImpl.kt
```

### Use Cases (14)
```
domain/usecase/chat/SendMessageUseCase.kt
domain/usecase/chat/SendLocationMessageUseCase.kt
domain/usecase/chat/ObserveMessagesUseCase.kt
domain/usecase/chat/ObserveConversationsUseCase.kt
domain/usecase/chat/GetOrCreateDirectConversationUseCase.kt
domain/usecase/chat/MarkConversationReadUseCase.kt
domain/usecase/friend/SendFriendRequestUseCase.kt
domain/usecase/friend/AcceptFriendRequestUseCase.kt
domain/usecase/friend/DeclineFriendRequestUseCase.kt
domain/usecase/friend/RemoveFriendUseCase.kt
domain/usecase/friend/ObserveFriendsUseCase.kt
domain/usecase/friend/ObserveFriendRequestsUseCase.kt
domain/usecase/friend/GetFriendshipStatusUseCase.kt
domain/usecase/friend/ObserveAllFriendLocationsUseCase.kt
```

### Screens + ViewModels (14)
```
presentation/map/GlobalMapScreen.kt
presentation/map/GlobalMapViewModel.kt
presentation/chat/ChatsScreen.kt
presentation/chat/ChatsViewModel.kt
presentation/chat/ChatScreen.kt
presentation/chat/ChatViewModel.kt
presentation/people/PeopleScreen.kt
presentation/people/PeopleViewModel.kt
presentation/people/FriendRequestsScreen.kt
presentation/people/FriendRequestsViewModel.kt
presentation/people/SearchUsersScreen.kt
presentation/people/SearchUsersViewModel.kt
presentation/people/UserProfileScreen.kt
presentation/people/UserProfileViewModel.kt
```

### UI Models (3)
```
presentation/model/MessageUiModel.kt
presentation/model/ConversationUiModel.kt
presentation/model/FriendLocationUiModel.kt
```

---

## Files to Delete

```
presentation/home/HomeScreen.kt
presentation/home/HomeViewModel.kt
presentation/settings/SettingsScreen.kt
presentation/settings/SettingsViewModel.kt
```

---

## Files to Significantly Modify

| File | What Changes |
|---|---|
| `Screen.kt` | Add new routes, remove unused ones |
| `AppNavGraph.kt` | Wire all new screens, update logout flow |
| `MainScaffold.kt` | 4-tab bottom nav (icons only), Map as start tab |
| `RegisterScreen.kt` | Add @username field + uniqueness validation |
| `ProfileScreen.kt` | Full revamp (bio, username, stats, sharing toggles) |
| `ProfileViewModel.kt` | Fix photo upload bug, add bio/username editing |
| `GroupDetailsScreen.kt` | Add "Group Chat" button |
| `CreateGroupScreen.kt` | Auto-create group conversation on group creation |
| `MapScreen.kt` | Rename to `GroupMapScreen.kt` |
| `MapViewModel.kt` | Rename to `GroupMapViewModel.kt` |
| `FcmMessagingService.kt` | Handle new notification types, add deep links |
| `LocationRepositoryImpl.kt` | Fix sharing state persistence |
| `GroupRepositoryImpl.kt` | On `createGroup`, also create conversation |
| `AppConstants.kt` | Add chat/friendship collection name constants |
| `RepositoryModule.kt` | Bind 3 new repository implementations |
| `UseCaseModule.kt` | Provide 14 new use cases |
| `User.kt` | Add `bio`, `username` fields |
| `strings.xml` | Add all new strings for chat, friends, profile |

---

## Implementation Phases

### Phase 1 — Foundation & Navigation (~2 hours)
1. Update `User.kt` — add `bio`, `username`
2. Create `Message.kt`, `Conversation.kt`, `Friendship.kt`
3. Create all 3 repository interfaces
4. Update `Screen.kt` (new routes, remove old)
5. Update `AppNavGraph.kt` (stub new screens)
6. Update `MainScaffold.kt` (4-tab, icons only, Map tab first)
7. Update `AppConstants.kt` (new collection names)
8. Create placeholder screens for new routes (so build passes)

### Phase 2 — Friends System (~3 hours)
1. `FriendshipRepositoryImpl` (Firestore: friendships collection)
2. All 4 friend use cases + DI wiring
3. `PeopleScreen` + `PeopleViewModel`
4. `FriendRequestsScreen` + ViewModel
5. `SearchUsersScreen` + ViewModel
6. `UserProfileScreen` + ViewModel
7. Update `RegisterScreen` — add username field
8. Update `ProfileScreen` — add bio, username, friends count

### Phase 3 — Chat System (~4 hours)
1. `ConversationRepositoryImpl`
2. `MessageRepositoryImpl`
3. All 6 chat use cases + DI wiring
4. `ChatsScreen` + `ChatsViewModel`
5. `ChatScreen` + `ChatViewModel` (full bubble UI)
6. Update `CreateGroupScreen` — auto-create conversation
7. Update `GroupDetailsScreen` — "Group Chat" button
8. Update `GroupRepositoryImpl.createGroup` — also write conversation

### Phase 4 — Map Revamp (~2 hours)
1. `ObserveAllFriendLocationsUseCase` (aggregates group locations)
2. `GlobalMapScreen` + `GlobalMapViewModel`
3. Rename `MapScreen` → `GroupMapScreen`, `MapViewModel` → `GroupMapViewModel`
4. Fix location persistence (check Firestore `isSharingActive` on init)
5. Update nav so Map tab shows `GlobalMapScreen`

### Phase 5 — Notifications & Deep Linking (~1 hour)
1. Update `FcmMessagingService` — handle `new_message`, `friend_request`, `friend_accepted`
2. Add deep link handling in `MainActivity`
3. Wire notification taps to correct screens

### Phase 6 — Polish & Bug Fixes (~2 hours)
1. Fix `ProfileViewModel` photo upload bug (use UID not displayName)
2. Fix `UserRepositoryImpl.getUsers` chunking for > 10 users
3. Fix `activeSharingSessions` persistence
4. Remove `HomeScreen`, `SettingsScreen` dead code
5. Update all `strings.xml` for new screens
6. Add empty states, error states, skeleton loaders for all new screens
7. Final build + compile error sweep

**Total estimated time: ~14 hours**

---

## Firestore Security Rules (to add)

```javascript
// friendships
match /friendships/{friendshipId} {
  allow read: if request.auth.uid == resource.data.requesterId
                || request.auth.uid == resource.data.receiverId;
  allow create: if request.auth.uid == request.resource.data.requesterId;
  allow update: if request.auth.uid == resource.data.receiverId
                  && request.resource.data.status == 'accepted';
  allow delete: if request.auth.uid == resource.data.requesterId
                  || request.auth.uid == resource.data.receiverId;
}

// conversations
match /conversations/{convId} {
  allow read, write: if request.auth.uid in resource.data.participantIds;

  match /messages/{msgId} {
    allow read: if request.auth.uid in
      get(/databases/$(database)/documents/conversations/$(convId)).data.participantIds;
    allow create: if request.auth.uid == request.resource.data.senderId
                    && request.auth.uid in
      get(/databases/$(database)/documents/conversations/$(convId)).data.participantIds;
  }
}
```

---

## Firestore Indexes to Create

| Collection | Fields | Query |
|---|---|---|
| `friendships` | `requesterId` ASC, `status` ASC | Sent requests by user |
| `friendships` | `receiverId` ASC, `status` ASC | Received requests by user |
| `conversations` | `participantIds` ARRAY, `lastMessageTimestamp` DESC | User's conversations sorted |
| `conversations/*/messages` | `timestamp` ASC | Messages in order |
| `users` | `username` ASC | Username lookup |
