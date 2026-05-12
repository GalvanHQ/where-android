# Requirements Document

## Introduction

The **People UX & Friendship Data Redesign** reshapes the four People screens (`PeopleScreen`, `SearchUsersScreen`, `UserProfileScreen`, `FriendRequestsScreen`) to match the "Glassy · Dynamic · Warm" language in `DESIGN.md`, and overhauls the Firestore friend-request data model so that the typical user journey issues **one** snapshot listener for friends, **one** for incoming requests, and **one** for the social-summary badge. All friend-related mutations are routed through Cloud Function callables so that server-side transactions can enforce symmetry, block precedence, and rate limits that client rules cannot express. The redesign targets a ≥ 60% reduction in Firestore document reads across the People flow, eliminates the existing ">10 friends truncation" bug, and adds first-class support for outgoing-request management and blocking.

This document derives its acceptance criteria from `design.md`. Each requirement focuses on observable behaviour so that the system can be validated with property-based tests, emulator-backed integration tests, and Compose UI tests without dictating implementation internals.

## Glossary

- **Caller**: The authenticated user currently operating the client (`uid == request.auth.uid`).
- **Other_User**: A user identified by a uid distinct from the Caller, referenced in a friend/block/request operation.
- **Pair_Id**: The deterministic identifier `min(uidA, uidB) + "_" + max(uidA, uidB)` for the canonical friendship document between two uids.
- **Friendship_Repository**: The Android data-layer component (`FriendshipRepository` / `FriendshipRepositoryImpl`) responsible for friendship reads and write routing.
- **Friendship_Callable**: Any of the Cloud Function HTTPS callables that mediate a friendship mutation (`sendFriendRequest`, `cancelFriendRequest`, `acceptFriendRequest`, `declineFriendRequest`, `removeFriend`, `blockUser`, `unblockUser`).
- **Profile_Fanout_Function**: The Firestore-triggered Cloud Function `onUserProfileUpdated` that propagates user profile display-field changes into denormalized `friends` / `inbox` / `outbox` entries.
- **People_Screen**: The `PeopleScreen` composable — root of the Friends tab.
- **Friend_Requests_Screen**: The `FriendRequestsScreen` composable with Incoming and Sent tabs.
- **Search_Users_Screen**: The `SearchUsersScreen` composable for finding other users.
- **User_Profile_Screen**: The `UserProfileScreen` composable for viewing another user's profile.
- **Friend_Entry**: The denormalized document at `users/{uid}/friends/{friendUid}` carrying display fields (`displayName`, `username`, `photoUrl`, `isOnline`, `since`, `pairId`).
- **Request_Entry**: The summary record stored inside a `RequestInbox`, carrying `uid`, `displayName`, `username`, `photoUrl`, `sentAt`, `pairId`.
- **Request_Inbox**: The single aggregation document at `users/{uid}/inbox/friendRequests` or `users/{uid}/outbox/friendRequests` with `entries: Map<String, RequestEntry>`.
- **Social_Summary**: The document at `users/{uid}/summary/social` with counts for `friendsCount`, `pendingIncomingCount`, `pendingOutgoingCount`, `blockedCount`.
- **Block_Entry**: The document at `users/{uid}/blocks/{blockedUid}` private to the blocker.
- **Friendship_Doc**: The canonical document at `friendships/{pairId}` with `members`, `requesterId`, `status`, `createdAt`, `updatedAt`, `acceptedAt`.
- **Friendship_Status**: One of `PENDING`, `ACCEPTED`, `BLOCKED`. `NONE` is the absence of a `Friendship_Doc`.
- **Firestore_Rules**: The `firestore.rules` security-rules file deployed for the project.
- **Remote_Config_Flag**: The Firebase Remote Config boolean `useNewFriendshipModel`.
- **Migration_Backfill_Function**: The Cloud Function `backfillFriendships` that reconciles legacy `friendships/{uuid}` docs into the new model.
- **PBT**: Property-based testing suite (Kotest property / jqwik).
- **A11y_Scanner**: The Android Accessibility Scanner or Compose `AccessibilityChecks` running in instrumented tests.

## Requirements

### Requirement 1: Canonical Friendship Pair Document

**User Story:** As a platform engineer, I want every friendship between two users to be represented by exactly one canonical document addressed by a deterministic pair id, so that duplicate pending relationships cannot exist and either-direction lookups cost a single read.

#### Acceptance Criteria

1. THE Friendship_Repository SHALL address every `Friendship_Doc` at the path `friendships/{pairId}` where `pairId == min(uidA, uidB) + "_" + max(uidA, uidB)`.
2. WHEN `FriendshipIds.pairId(a, b)` is invoked with any two distinct uids, THE Friendship_Repository SHALL return the same value regardless of argument order.
3. WHEN `FriendshipIds.members(a, b)` is invoked with any two distinct uids, THE Friendship_Repository SHALL return a two-element list sorted lexicographically ascending.
4. THE Friendship_Doc SHALL satisfy the invariant `members.size == 2 AND members == members.sorted() AND pairId == "${members[0]}_${members[1]}" AND requesterId IN members`.
5. IF a write would create a `Friendship_Doc` where `members[0] == members[1]`, THEN THE Friendship_Callable SHALL reject the write with error code `invalid-argument`.
6. WHEN `Friendship_Repository.getFriendshipStatus(otherUserId)` is called for any authenticated Caller, THE Friendship_Repository SHALL issue exactly one Firestore document read against `friendships/{pairId}`.

### Requirement 2: Friend Request Send Flow

**User Story:** As a user, I want to send a friend request to another user through a server-validated call, so that duplicates, self-requests, and blocked-user spam are impossible.

#### Acceptance Criteria

1. WHEN the Caller invokes `Friendship_Repository.sendFriendRequest(receiverId)`, THE Friendship_Repository SHALL call the `sendFriendRequest` Friendship_Callable with payload `{ receiverId }` and return a `Resource<Unit>` reflecting the callable result.
2. WHEN the `sendFriendRequest` Friendship_Callable succeeds, THE `sendFriendRequest` Friendship_Callable SHALL within a single Firestore transaction: set `friendships/{pairId}.status = PENDING`, add a mirrored Request_Entry to `users/{receiverId}/inbox/friendRequests.entries[callerUid]`, add a mirrored Request_Entry to `users/{callerUid}/outbox/friendRequests.entries[receiverId]`, increment `users/{receiverId}/summary/social.pendingIncomingCount` by one, and increment `users/{callerUid}/summary/social.pendingOutgoingCount` by one.
3. IF `receiverId == request.auth.uid`, THEN THE `sendFriendRequest` Friendship_Callable SHALL reject the call with error code `invalid-argument`.
4. IF either `users/{callerUid}/blocks/{receiverId}` or `users/{receiverId}/blocks/{callerUid}` exists, THEN THE `sendFriendRequest` Friendship_Callable SHALL reject the call with error code `permission-denied`.
5. IF a `Friendship_Doc` already exists at `friendships/{pairId}` with status `PENDING`, `ACCEPTED`, or `BLOCKED`, THEN THE `sendFriendRequest` Friendship_Callable SHALL return success without mutating state (idempotent).
6. WHEN the Caller has issued more than 20 successful `sendFriendRequest` calls within the preceding rolling hour, THE `sendFriendRequest` Friendship_Callable SHALL reject subsequent calls with error code `resource-exhausted`.
7. WHEN the `sendFriendRequest` Friendship_Callable fails with any terminal error, THE Friendship_Repository SHALL return `Resource.Error` with a non-empty message and SHALL NOT mutate local Firestore state.

### Requirement 3: Cancel Outgoing Friend Request

**User Story:** As a user, I want to cancel a friend request that I previously sent, so that I can withdraw accidental or obsolete requests.

#### Acceptance Criteria

1. WHEN the Caller invokes `Friendship_Repository.cancelFriendRequest(receiverId)`, THE Friendship_Repository SHALL call the `cancelFriendRequest` Friendship_Callable with payload `{ receiverId }` and return a `Resource<Unit>`.
2. WHEN the `cancelFriendRequest` Friendship_Callable succeeds, THE `cancelFriendRequest` Friendship_Callable SHALL within a single Firestore transaction: delete `friendships/{pairId}`, remove `users/{receiverId}/inbox/friendRequests.entries[callerUid]`, remove `users/{callerUid}/outbox/friendRequests.entries[receiverId]`, decrement `users/{receiverId}/summary/social.pendingIncomingCount` by one (floored at zero), and decrement `users/{callerUid}/summary/social.pendingOutgoingCount` by one (floored at zero).
3. IF the current `Friendship_Doc` status is not `PENDING` or `requesterId != callerUid`, THEN THE `cancelFriendRequest` Friendship_Callable SHALL reject the call with error code `failed-precondition`.
4. IF no `Friendship_Doc` exists at `friendships/{pairId}`, THEN THE `cancelFriendRequest` Friendship_Callable SHALL return success without mutating state (idempotent).

### Requirement 4: Accept Incoming Friend Request

**User Story:** As a user, I want to accept a pending incoming friend request so that both users become friends and both denormalized friend summaries appear atomically.

#### Acceptance Criteria

1. WHEN the Caller invokes `Friendship_Repository.acceptFriendRequest(requesterId)`, THE Friendship_Repository SHALL call the `acceptFriendRequest` Friendship_Callable with payload `{ requesterId }` and return a `Resource<Unit>`.
2. WHEN the `acceptFriendRequest` Friendship_Callable succeeds, THE `acceptFriendRequest` Friendship_Callable SHALL within a single Firestore transaction: set `friendships/{pairId}.status = ACCEPTED` with `acceptedAt = now`, write Friend_Entry at `users/{callerUid}/friends/{requesterId}`, write Friend_Entry at `users/{requesterId}/friends/{callerUid}`, remove `users/{callerUid}/inbox/friendRequests.entries[requesterId]`, remove `users/{requesterId}/outbox/friendRequests.entries[callerUid]`, increment both Social_Summary `friendsCount`, and decrement `pendingIncomingCount` on the Caller and `pendingOutgoingCount` on the requester by one (floored at zero).
3. IF the current `Friendship_Doc` status is not `PENDING` or `requesterId == callerUid`, THEN THE `acceptFriendRequest` Friendship_Callable SHALL reject the call with error code `failed-precondition`.
4. IF no `Friendship_Doc` exists at `friendships/{pairId}`, THEN THE `acceptFriendRequest` Friendship_Callable SHALL reject the call with error code `not-found`.
5. WHILE a `Friendship_Doc` has status `ACCEPTED`, THE `acceptFriendRequest` Friendship_Callable SHALL reject any further `acceptFriendRequest` calls for the same pair with error code `failed-precondition`.

### Requirement 5: Decline Incoming Friend Request

**User Story:** As a user, I want to decline a pending incoming friend request silently, so that the requester is not notified but the request is removed from my inbox.

#### Acceptance Criteria

1. WHEN the Caller invokes `Friendship_Repository.declineFriendRequest(requesterId)`, THE Friendship_Repository SHALL call the `declineFriendRequest` Friendship_Callable with payload `{ requesterId }` and return a `Resource<Unit>`.
2. WHEN the `declineFriendRequest` Friendship_Callable succeeds, THE `declineFriendRequest` Friendship_Callable SHALL within a single Firestore transaction: delete `friendships/{pairId}`, remove `users/{callerUid}/inbox/friendRequests.entries[requesterId]`, remove `users/{requesterId}/outbox/friendRequests.entries[callerUid]`, and decrement both Social_Summary pending counters by one (floored at zero).
3. IF the current `Friendship_Doc` status is not `PENDING` or `requesterId == callerUid`, THEN THE `declineFriendRequest` Friendship_Callable SHALL reject the call with error code `failed-precondition`.
4. IF no `Friendship_Doc` exists at `friendships/{pairId}`, THEN THE `declineFriendRequest` Friendship_Callable SHALL return success without mutating state (idempotent).

### Requirement 6: Unfriend (Remove Friend)

**User Story:** As a user, I want to unfriend another user so that both sides of the friendship are removed atomically and counters stay consistent.

#### Acceptance Criteria

1. WHEN the Caller invokes `Friendship_Repository.removeFriend(friendId)`, THE Friendship_Repository SHALL call the `removeFriend` Friendship_Callable with payload `{ friendId }` and return a `Resource<Unit>`.
2. WHEN the `removeFriend` Friendship_Callable succeeds, THE `removeFriend` Friendship_Callable SHALL within a single Firestore transaction: delete `friendships/{pairId}`, delete `users/{callerUid}/friends/{friendId}`, delete `users/{friendId}/friends/{callerUid}`, and decrement both Social_Summary `friendsCount` by one (floored at zero).
3. IF the current `Friendship_Doc` status is not `ACCEPTED`, THEN THE `removeFriend` Friendship_Callable SHALL return success without mutating friend subcollections (idempotent no-op).
4. WHEN `removeFriend` completes successfully, THE Friendship_Repository SHALL emit a new `observeFriends` snapshot with the removed friend absent.

### Requirement 7: Block and Unblock

**User Story:** As a user, I want to block another user so that no friendship or request can exist between us, and I can later unblock the same user.

#### Acceptance Criteria

1. WHEN the Caller invokes `Friendship_Repository.blockUser(userId)`, THE Friendship_Repository SHALL call the `blockUser` Friendship_Callable with payload `{ userId }` and return a `Resource<Unit>`.
2. WHEN the `blockUser` Friendship_Callable succeeds, THE `blockUser` Friendship_Callable SHALL within a single Firestore transaction: write `users/{callerUid}/blocks/{userId}` with `blockedAt = now`, set `friendships/{pairId}.status = BLOCKED` with `requesterId = callerUid`, remove any Friend_Entry docs for the pair in both users' `friends` subcollections, remove any mirrored Request_Entries from both users' `inbox` and `outbox` aggregation docs, increment `users/{callerUid}/summary/social.blockedCount`, and adjust both Social_Summary pending and friend counters to reflect the removals.
3. WHEN the Caller invokes `Friendship_Repository.unblockUser(userId)`, THE Friendship_Repository SHALL call the `unblockUser` Friendship_Callable with payload `{ userId }` and return a `Resource<Unit>`.
4. WHEN the `unblockUser` Friendship_Callable succeeds, THE `unblockUser` Friendship_Callable SHALL within a single Firestore transaction: delete `users/{callerUid}/blocks/{userId}`, delete `friendships/{pairId}` if its current status is `BLOCKED` and `requesterId == callerUid`, and decrement `users/{callerUid}/summary/social.blockedCount` by one (floored at zero).
5. WHILE `users/{A}/blocks/{B}` or `users/{B}/blocks/{A}` exists, THE `sendFriendRequest` Friendship_Callable SHALL reject `sendFriendRequest` calls between A and B with error code `permission-denied`.
6. THE `users/{uid}/blocks/{blockedUid}` document SHALL be readable only by the uid whose subcollection it belongs to.

### Requirement 8: Per-User Denormalized Friend List

**User Story:** As a user, I want to see my friend list with a single listener that carries display fields, so that opening the People tab costs one read plus one per friend.

#### Acceptance Criteria

1. WHEN the Caller subscribes via `Friendship_Repository.observeFriends()`, THE Friendship_Repository SHALL open exactly one Firestore snapshot listener on `users/{callerUid}/friends` ordered by `displayName` ascending.
2. WHEN the listener emits, THE Friendship_Repository SHALL deliver a `List<FriendEntry>` containing every current Friend_Entry document in that subcollection.
3. WHEN `observeFriends()` is collected while the Caller has no authenticated session, THE Friendship_Repository SHALL emit `emptyList()` and SHALL NOT open a listener.
4. WHEN a Profile_Fanout_Function completes for Other_User whose uid is X, THE Profile_Fanout_Function SHALL update `users/{friend}/friends/{X}.displayName`, `username`, and `photoUrl` for every friend `friend` of X within p95 ≤ 5 seconds of the source profile write.
5. WHEN any number of friends greater than ten exists, THE Friendship_Repository SHALL include every friend in the emission (no truncation).

### Requirement 9: Per-User Inbox and Outbox Aggregation Docs

**User Story:** As a user, I want my incoming and outgoing friend requests each delivered as a single aggregation document so that the Friend Requests screen costs one read per list regardless of pending count.

#### Acceptance Criteria

1. WHEN the Caller subscribes via `Friendship_Repository.observeIncomingRequests()`, THE Friendship_Repository SHALL open exactly one snapshot listener on `users/{callerUid}/inbox/friendRequests`.
2. WHEN the inbox listener emits, THE Friendship_Repository SHALL deliver `RequestInbox.entries.values` sorted by `sentAt` descending.
3. WHEN the Caller subscribes via `Friendship_Repository.observeOutgoingRequests()`, THE Friendship_Repository SHALL open exactly one snapshot listener on `users/{callerUid}/outbox/friendRequests` and deliver its entries sorted by `sentAt` descending.
4. IF the aggregation document does not exist, THEN THE Friendship_Repository SHALL emit `emptyList()` rather than an error.
5. THE `RequestInbox.entries` map for any single aggregation doc SHALL contain at most 500 entries; WHEN a Cloud Function mutation would exceed 500 entries, THE Friendship_Callable SHALL spill the new entry to a secondary document named `friendRequests_2` under the same subcollection.
6. FOR every pair of uids `(A, B)` with a pending request from A to B, THE `Friendship_Callable` suite SHALL maintain the invariant that `users/{B}/inbox/friendRequests.entries[A]` and `users/{A}/outbox/friendRequests.entries[B]` both exist and mirror each other (same `sentAt`, `pairId`).

### Requirement 10: Social Summary Badge Document

**User Story:** As a user, I want the pending-requests badge on the People tab to update cheaply without listening to the full inbox, so that the People screen stays responsive as request volume grows.

#### Acceptance Criteria

1. WHEN the Caller subscribes via `Friendship_Repository.observeSocialSummary()`, THE Friendship_Repository SHALL open exactly one snapshot listener on `users/{callerUid}/summary/social`.
2. WHEN the listener emits, THE Friendship_Repository SHALL deliver a `SocialSummary` with non-negative `friendsCount`, `pendingIncomingCount`, `pendingOutgoingCount`, and `blockedCount`.
3. WHEN the Social_Summary document does not exist, THE Friendship_Repository SHALL emit `SocialSummary()` with all counters set to zero.
4. WHEN any Friendship_Callable completes a state transition, THE Friendship_Callable SHALL update the affected Social_Summary documents within the same Firestore transaction so that `friendsCount == |users/{uid}/friends|`, `pendingIncomingCount == |inbox.entries|`, `pendingOutgoingCount == |outbox.entries|`, and `blockedCount == |users/{uid}/blocks|` all hold eventually.

### Requirement 11: Friendship State Machine Correctness

**User Story:** As a platform engineer, I want friendship transitions to follow a well-defined state machine, so that impossible states (duplicate pending, resurrected accepted, block-bypass) cannot occur.

#### Acceptance Criteria

1. THE Friendship_Status enum SHALL have exactly the values `PENDING`, `ACCEPTED`, `BLOCKED` for document storage and SHALL use absence of the document (`NONE`) to represent no relationship.
2. FOR any pair of uids, THE Friendship_Callable suite SHALL permit only the transitions: `NONE → PENDING` via send, `PENDING → NONE` via cancel or decline, `PENDING → ACCEPTED` via accept, `ACCEPTED → NONE` via unfriend, `{NONE, PENDING, ACCEPTED} → BLOCKED` via block, `BLOCKED → NONE` via unblock.
3. IF a Friendship_Callable is invoked that would effect a transition not in the list in criterion 11.2, THEN THE Friendship_Callable SHALL reject the call with error code `failed-precondition`.
4. FOR any sequence of `[send, cancel, accept, decline, unfriend, block, unblock]` callable invocations starting from `NONE`, the derived final Friendship_Status SHALL equal the state produced by a reference implementation of the state machine in criterion 11.2.
5. WHILE a `Friendship_Doc` has status `ACCEPTED`, THE Friendship_Callable suite SHALL NOT transition it back to `PENDING`.

### Requirement 12: Firestore Security Rules

**User Story:** As a platform engineer, I want the Firestore security rules to deny client-side writes to friendship-related documents, so that every mutation is forced through a validated Cloud Function.

#### Acceptance Criteria

1. THE Firestore_Rules SHALL permit read on `users/{uid}/friends/{friendUid}` only when `request.auth.uid == uid` and SHALL permit write on the same path only when the request carries the admin auth token.
2. THE Firestore_Rules SHALL permit read on `users/{uid}/inbox/{docId}` and `users/{uid}/outbox/{docId}` only when `request.auth.uid == uid` and SHALL permit write on the same paths only when the request carries the admin auth token.
3. THE Firestore_Rules SHALL permit read on `users/{uid}/summary/{docId}` only when `request.auth.uid == uid` and SHALL permit write on the same path only when the request carries the admin auth token.
4. THE Firestore_Rules SHALL permit read and write on `users/{uid}/blocks/{blockedUid}` only when `request.auth.uid == uid`.
5. THE Firestore_Rules SHALL permit read on `friendships/{pairId}` only when the request is authenticated and `request.auth.uid in resource.data.members`.
6. THE Firestore_Rules SHALL deny all client-origin `create`, `update`, and `delete` on `friendships/{pairId}` that do not carry the admin auth token.
7. THE Firestore_Rules SHALL leave existing rule blocks for `groups`, `conversations`, `directLocationShares`, and `activeLocations` unchanged in deployed behaviour.

### Requirement 13: Profile Fan-Out on User Update

**User Story:** As a user, I want my friends to see my updated display name and photo across the app shortly after I change them, so that the denormalized friend list does not go stale.

#### Acceptance Criteria

1. WHEN the `users/{uid}` document is updated and the fields `displayName`, `username`, or `photoUrl` change, THE Profile_Fanout_Function SHALL enqueue updates to every `users/{friend}/friends/{uid}` doc where `friend` is an accepted friend of `uid`.
2. WHEN the `users/{uid}` document is updated and the same fields change, THE Profile_Fanout_Function SHALL update every mirrored Request_Entry for `uid` present in any other user's inbox or outbox aggregation doc.
3. WHEN the Profile_Fanout_Function runs, THE Profile_Fanout_Function SHALL complete all fan-out writes within p95 ≤ 5 seconds measured from the source profile write.
4. IF the Profile_Fanout_Function partially fails, THEN THE Profile_Fanout_Function SHALL retry the failed writes idempotently without duplicating entries or counters.

### Requirement 14: People Screen UX

**User Story:** As a user opening the Friends tab, I want a premium-feeling screen with clear sections, live context, and accessible interactions, so that I can quickly see who is active and reach what I need.

#### Acceptance Criteria

1. WHILE the People_Screen is in loading state, THE People_Screen SHALL render a shimmer skeleton composed of one request-card placeholder followed by six friend-row placeholders.
2. WHILE the People_Screen has zero friends and zero pending incoming requests, THE People_Screen SHALL render an empty state containing an illustration, the headline "Find your people", a supporting body text, and a primary CTA that navigates to Search_Users_Screen.
3. WHILE the People_Screen has at least one pending incoming request, THE People_Screen SHALL render a Requests Inbox card at the top of the list showing the pending-incoming count and navigating to Friend_Requests_Screen on tap.
4. WHILE the People_Screen has at least one friend currently sharing a live location, THE People_Screen SHALL render an "Active now" section header with the live count and SHALL decorate those rows with a Live Ring avatar treatment.
5. WHEN the Caller long-presses a friend row, THE People_Screen SHALL open a bottom sheet offering `Unfriend`, `Block`, and `Share my location with …` actions.
6. WHEN the Caller taps the message icon on a friend row, THE People_Screen SHALL invoke the existing open-or-create DM action and navigate to the chat screen.
7. WHEN the Friendship_Repository reports an error, THE People_Screen SHALL render an inline error info card at the top of the list with a retry action that re-subscribes the listeners.
8. THE People_Screen SHALL key each `LazyColumn` row by `userId` or `pairId`.

### Requirement 15: Friend Requests Screen UX

**User Story:** As a user, I want to manage both incoming and outgoing friend requests from one screen, so that I can accept, decline, or cancel with a single clear affordance and undo any accidental action.

#### Acceptance Criteria

1. THE Friend_Requests_Screen SHALL render a segmented tab control with `Incoming` and `Sent` tabs showing the respective pending counts.
2. WHEN the Caller taps `Accept` on an incoming request row, THE Friend_Requests_Screen SHALL optimistically remove the row, call `acceptFriendRequest`, and show a snackbar "Now friends with {name}. Undo" for 3 seconds.
3. WHEN the Caller taps `Decline` on an incoming request row, THE Friend_Requests_Screen SHALL optimistically remove the row, call `declineFriendRequest`, and show a snackbar "Declined. Undo" for 3 seconds.
4. WHEN the Caller taps `Cancel` on an outgoing request row, THE Friend_Requests_Screen SHALL optimistically remove the row, call `cancelFriendRequest`, and show a snackbar "Request cancelled. Undo" for 3 seconds.
5. WHEN the Caller taps `Undo` on any of the above snackbars before it dismisses, THE Friend_Requests_Screen SHALL call the inverse Friendship_Repository action and restore the row.
6. IF a Friendship_Callable invocation fails, THEN THE Friend_Requests_Screen SHALL revert the optimistic removal and display an error snackbar.
7. WHILE the Friend_Requests_Screen selected tab has zero rows, THE Friend_Requests_Screen SHALL render a tab-specific empty state with illustration, headline, body, and (for the Sent tab) a primary CTA to Search_Users_Screen.
8. THE selected tab of the Friend_Requests_Screen SHALL be persisted across back-navigation via `SavedStateHandle`.

### Requirement 16: Search Users Screen UX

**User Story:** As a user, I want a debounced search for other users with clear status pills and pre-search guidance, so that I can find and act on people without spamming the backend.

#### Acceptance Criteria

1. WHEN the Caller types into the Search_Users_Screen query field, THE Search_Users_Screen SHALL debounce `onQueryChange` by 300 milliseconds before invoking `userRepository.searchUsers`.
2. WHEN the query length is less than 2 characters, THE Search_Users_Screen SHALL NOT invoke `userRepository.searchUsers` and SHALL render the pre-search empty state.
3. WHEN the Caller presses the IME `Search` action, THE Search_Users_Screen SHALL bypass the debounce and invoke `userRepository.searchUsers` immediately with the current query.
4. WHEN search results are available, THE Search_Users_Screen SHALL render each result as a row showing avatar, display name, username, and a friendship-action pill derived from the intersection of already-subscribed `observeFriends` and `observeOutgoingRequests` results without issuing additional Firestore reads.
5. WHERE a search result row has friendship-action state `ADD`, THE row SHALL render a filled primary pill that invokes `sendFriendRequest` on tap.
6. WHERE a search result row has friendship-action state `PENDING`, THE row SHALL render an outlined warning-color pill that opens a cancel-confirmation bottom sheet invoking `cancelFriendRequest` on confirm.
7. WHERE a search result row has friendship-action state `FRIENDS`, THE row SHALL render a tonal tertiary pill and SHALL NOT perform any action on tap of the pill itself.
8. WHILE a search request is in flight, THE Search_Users_Screen SHALL render a shimmer placeholder of at least four rows.
9. IF `userRepository.searchUsers` returns an error, THEN THE Search_Users_Screen SHALL render an inline error info card with a retry action.
10. WHILE the query is non-empty and returned zero results, THE Search_Users_Screen SHALL render a "No users found for '{query}'" empty state with an "Invite via link" fallback CTA.
11. THE Search_Users_Screen search field SHALL use the project `WhereTextField` component with leading search icon and trailing clear-button affordance.

### Requirement 17: User Profile Screen UX

**User Story:** As a user, I want a richer profile screen with state-driven actions for every possible relationship state, so that I can send, cancel, accept, decline, unfriend, block, or message from one place.

#### Acceptance Criteria

1. WHILE the User_Profile_Screen is loading, THE User_Profile_Screen SHALL render a skeleton containing a ghost avatar and two shimmer text bars.
2. WHEN the `getUser` or `getFriendshipStatus` call fails, THE User_Profile_Screen SHALL render an error state with an error message and a retry action that re-invokes both loads.
3. WHEN the target user does not exist, THE User_Profile_Screen SHALL render a "User no longer exists" state with a back-navigation action.
4. WHERE the relationship state is `AddFriend`, THE User_Profile_Screen SHALL render a primary "Send friend request" button that calls `sendFriendRequest`.
5. WHERE the relationship state is `RequestSent`, THE User_Profile_Screen SHALL render a "Request Sent · Cancel" pill that, on tap, opens a confirmation bottom sheet invoking `cancelFriendRequest`.
6. WHERE the relationship state is `RequestReceived`, THE User_Profile_Screen SHALL render Accept and Decline actions that call `acceptFriendRequest` and `declineFriendRequest` respectively.
7. WHERE the relationship state is `AlreadyFriends`, THE User_Profile_Screen SHALL render a primary `Message` action and a secondary `Unfriend` overflow action that, on confirm, calls `removeFriend`.
8. WHERE the relationship state is `Blocked` (the Caller has blocked Other_User), THE User_Profile_Screen SHALL render a banner "You blocked @{username}" with an `Unblock` action that calls `unblockUser`.
9. WHERE the relationship state is `BlockedByThem`, THE User_Profile_Screen SHALL render only the display name, avatar, and body text "This user is unavailable." with no action affordances.
10. WHILE Other_User is currently sharing a live location visible to the Caller, THE User_Profile_Screen SHALL render the avatar with the Live Ring treatment and include a "Sharing now" stats indicator.
11. THE User_Profile_Screen top bar SHALL display the target user's `@username` rather than a flashing "Profile" placeholder.

### Requirement 18: Friendship Repository Observation Read Budgets

**User Story:** As a platform engineer, I want enforceable read-count targets for each People action, so that we can verify the ≥ 60% reduction in Firestore reads in CI.

#### Acceptance Criteria

1. WHEN the Caller opens the People_Screen with `F` friends and non-zero pending incoming count, THE Friendship_Repository SHALL incur at most `F + 1` initial document reads (`F` for friends + 1 for Social_Summary).
2. WHEN the Caller opens the Friend_Requests_Screen, THE Friendship_Repository SHALL incur at most 2 initial document reads (1 inbox + 1 outbox).
3. WHEN the Caller opens the User_Profile_Screen for another user, THE Friendship_Repository SHALL incur at most 2 document reads (1 user doc + 1 Friendship_Doc).
4. WHEN the Caller types a query on the Search_Users_Screen, THE Search_Users_Screen SHALL incur at most 40 document reads per debounced search, and zero friendship-collection reads, by deriving friendship state from already-subscribed flows.
5. WHEN the Caller performs any friendship mutation (send, cancel, accept, decline, unfriend, block, unblock), THE Friendship_Repository SHALL incur zero client-side Firestore document reads for that mutation.

### Requirement 19: Accessibility

**User Story:** As a user relying on assistive technology, I want every People-flow interactive element labelled, sized, and announced correctly, so that I can use the feature with TalkBack and large touch targets.

#### Acceptance Criteria

1. THE People_Screen, Friend_Requests_Screen, Search_Users_Screen, and User_Profile_Screen SHALL assign a non-null `contentDescription` to every avatar image equal to the associated user's display name.
2. WHEN the Caller interacts with any friend row or request row, THE row SHALL expose a single merged semantics node with `Role.Button` and a `contentDescription` that includes the display name and online-or-offline state.
3. THE People_Screen, Friend_Requests_Screen, Search_Users_Screen, and User_Profile_Screen SHALL ensure every interactive element has a touch target of at least 48 dp by 48 dp.
4. WHEN online state is indicated visually, THE associated composable SHALL also expose a text label ("Online" or "Offline") in its semantics so that color alone is not the only indicator.
5. THE Friend_Requests_Screen segmented tabs SHALL expose `Role.Tab` semantics and SHALL announce tab changes via a live region.
6. THE User_Profile_Screen `Block` action SHALL present a confirmation dialog that describes what blocking does before invoking `blockUser`.
7. WHEN the A11y_Scanner runs against any of the four People screens in a Compose instrumented test, THE A11y_Scanner SHALL report zero violations for content-label, touch-target-size, and contrast-ratio categories.

### Requirement 20: Motion and Visual Conformance

**User Story:** As a user, I want People screens that feel dynamic and match the app's design language, so that transitions, shadows, and typography align with `DESIGN.md`.

#### Acceptance Criteria

1. WHEN the People_Screen list first renders, THE People_Screen SHALL fade-in items with a 50 millisecond stagger consistent with DESIGN.md §8.2.
2. WHILE the People_Screen has at least one pending incoming request, THE Requests Inbox card SHALL render with the DESIGN.md Level-3 glassy shadow treatment.
3. WHEN the Caller navigates from People_Screen, Search_Users_Screen, or Friend_Requests_Screen to User_Profile_Screen via an avatar tap, THE navigation SHALL use a shared-element transition on the avatar matching DESIGN.md §8.3.
4. WHERE a friend or profile subject is actively sharing a live location, THE avatar SHALL render the existing Live Ring pulse animation at a 1.5 second interval.
5. THE online status dot SHALL render at 10 dp diameter anchored to the avatar, not the 8 dp tertiary-colored dot used before the redesign.

### Requirement 21: Offline and Error Behaviour

**User Story:** As a user on an unreliable network, I want the People flow to behave predictably when reads are cached and writes fail, so that I am not stranded with stale UI or lost actions.

#### Acceptance Criteria

1. WHILE the device is offline, THE Friendship_Repository SHALL emit cached snapshot data from Firestore's local persistence for `observeFriends`, `observeIncomingRequests`, `observeOutgoingRequests`, and `observeSocialSummary`.
2. IF a Friendship_Callable fails with `UNAVAILABLE` because the device is offline, THEN THE invoking ViewModel SHALL surface a one-shot event mapped to a snackbar "You're offline" with a retry action.
3. IF a Friendship_Callable fails with `DEADLINE_EXCEEDED`, THEN THE invoking ViewModel SHALL surface a one-shot event mapped to a snackbar "Couldn't complete action, try again".
4. IF the `sendFriendRequest` Friendship_Callable returns `failed-precondition` because the relationship already exists, THEN THE invoking ViewModel SHALL silently refresh by re-reading `friendships/{pairId}` once and correcting the local UI state.
5. WHEN the accept Friendship_Callable returns `not-found` because the request was cancelled concurrently, THE Friend_Requests_Screen SHALL show a snackbar "Request is no longer available" and re-subscribe the inbox listener.
6. WHEN any snapshot listener reports `onError` with `PERMISSION_DENIED`, THE owning ViewModel SHALL expose an error state with a retry action that re-creates the listener rather than crashing the screen.

### Requirement 22: Migration Safety

**User Story:** As a platform operator, I want the rollout to preserve existing friendships and allow rollback, so that no user loses friends during the switchover.

#### Acceptance Criteria

1. WHILE the Remote_Config_Flag `useNewFriendshipModel` is `false`, THE Friendship_Repository SHALL read from the legacy `friendships/{uuid}` collection and continue to operate as before the redesign.
2. WHEN the Remote_Config_Flag transitions from `false` to `true`, THE Friendship_Repository SHALL switch to reading from the new `users/{uid}/friends`, `inbox`, `outbox`, and `summary` documents at the next observe subscription.
3. WHILE the rollout is in the dual-write phase, THE Friendship_Repository SHALL write to the legacy `friendships/{uuid}` collection and also call the new Friendship_Callable for every mutation.
4. THE Migration_Backfill_Function SHALL be idempotent: repeated invocations over the same legacy data SHALL NOT create duplicate Friend_Entry, Request_Entry, or Friendship_Doc records.
5. WHEN the Migration_Backfill_Function completes a user's data, THE Migration_Backfill_Function SHALL ensure `users/{uid}/summary/social.friendsCount` equals the count of accepted legacy friendships involving `uid`.
6. IF migration detects a parity mismatch (legacy-count ≠ new-count) for any user, THEN THE Migration_Backfill_Function SHALL log the mismatch and SHALL NOT auto-delete the legacy doc until the mismatch is resolved.

### Requirement 23: Dependency Injection for Cloud Functions

**User Story:** As a platform engineer, I want a single Hilt-provided `FirebaseFunctions` instance, so that Friendship_Repository and any future callables share one configured client.

#### Acceptance Criteria

1. THE Hilt dependency-injection graph SHALL provide a singleton `FirebaseFunctions` bound to the correct region used by the deployed Cloud Functions.
2. THE `FriendshipRepositoryImpl` SHALL receive the `FirebaseFunctions` instance via constructor injection.
3. WHEN unit tests are executed, THE Hilt test module SHALL allow replacement of `FirebaseFunctions` with a test double without modifying production code.
