package com.ovi.where.domain.model

/**
 * Summary record for a single pending friend request, embedded inside a
 * [RequestInbox] aggregation document.
 *
 * Part of the single-doc aggregation pattern used at
 * `users/{uid}/inbox/friendRequests` and `users/{uid}/outbox/friendRequests`
 * — see [RequestInbox] for the rationale. Because the enclosing doc is the
 * read unit, every [RequestEntry] it contains is delivered for the cost of
 * exactly one Firestore read.
 *
 * ### Semantic meaning of [uid] differs by container
 * The meaning of [uid] depends on which aggregation doc this entry lives in:
 *
 * - **Inbox** (`users/{ownerUid}/inbox/friendRequests`): [uid] is the
 *   **sender** — the other user who initiated the request to the inbox
 *   owner.
 * - **Outbox** (`users/{ownerUid}/outbox/friendRequests`): [uid] is the
 *   **receiver** — the other user that the outbox owner sent a request to.
 *
 * In both containers [uid] always identifies the "other" user relative to
 * the owner of the enclosing doc, and it matches the map key used in
 * [RequestInbox.entries].
 *
 * ### Write lifecycle (server-authoritative)
 * Clients **never** write these entries directly. All mutations happen
 * inside Cloud Functions transactions:
 *
 * - **Added** by `sendFriendRequest` (mirrored into the receiver's inbox
 *   and the sender's outbox in the same transaction).
 * - **Removed** by `cancelFriendRequest`, `acceptFriendRequest`,
 *   `declineFriendRequest`, and `blockUser`.
 * - **Display fields refreshed** by the `onUserProfileUpdated` Firestore
 *   trigger when the referenced user changes [displayName], [username], or
 *   [photoUrl].
 *
 * ### Persistence
 * All fields have no-arg defaults so Firestore's reflection-based
 * deserialization works without a custom converter.
 *
 * Mirrors design §4.3.
 *
 * @property uid Uid of the other user — sender for inbox entries, receiver
 *   for outbox entries. Matches the map key in [RequestInbox.entries].
 * @property displayName Cached display name of the other user, refreshed by
 *   `onUserProfileUpdated`.
 * @property username Cached username of the other user, refreshed by
 *   `onUserProfileUpdated`.
 * @property photoUrl Cached profile photo URL of the other user. Nullable
 *   because users may have no photo.
 * @property sentAt Epoch-millis timestamp of when the request was sent.
 *   Used to order inbox/outbox emissions descending.
 * @property pairId The canonical `friendships/{pairId}` document id for the
 *   relationship — see [FriendshipIds.pairId].
 */
data class RequestEntry(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
    val sentAt: Long = 0L,
    val pairId: String = ""
)
