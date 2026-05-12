package com.ovi.where.domain.model

/**
 * Denormalized per-user summary of a confirmed friendship.
 *
 * Stored at `users/{uid}/friends/{friendUid}` — one document per friend, per
 * side of the relationship. Each side owns its own copy so that a single
 * subcollection listener on `users/{uid}/friends` loads the entire friends
 * list with display fields already embedded. This eliminates the legacy
 * "list friendships, then issue a `whereIn` fetch against `users/{uid}`"
 * round trip: one listener loads everything.
 *
 * ### Write lifecycle (server-authoritative)
 * These documents are **never** written by the client. The Android app only
 * reads from them. Lifecycle is managed by Cloud Functions:
 *
 * - **Created** by the `acceptFriendRequest` callable, which writes both
 *   mirror copies (`users/{a}/friends/{b}` and `users/{b}/friends/{a}`) in
 *   the same transaction that flips `friendships/{pairId}.status` to
 *   `ACCEPTED`.
 * - **Deleted** by the `removeFriend` and `blockUser` callables, each of
 *   which deletes both mirrors in the same transaction that transitions the
 *   pair document away from `ACCEPTED`.
 * - **Refreshed** opportunistically by the `onUserProfileUpdated` Firestore
 *   trigger, which fans out changes to [displayName], [username], and
 *   [photoUrl] to every mirror doc referencing the updated user. Propagation
 *   is eventually consistent with a p95 latency budget of ≤ 5 s — acceptable
 *   staleness for a friends list.
 *
 * The [isOnline] flag is likewise eventual: it reflects the last presence
 * snapshot fanned out to this doc. Screens that need authoritative
 * up-to-the-second presence overlay a dedicated presence listener on top of
 * this data rather than relying on [isOnline] alone.
 *
 * ### Persistence
 * All fields have no-arg defaults so Firestore's reflection-based
 * deserialization (`toObject(FriendEntry::class.java)`) works without a
 * custom converter.
 *
 * Mirrors design §4.2.
 *
 * @property friendUid The uid of the friend this entry describes — matches
 *   the containing document id.
 * @property displayName Cached display name of the friend, refreshed by
 *   `onUserProfileUpdated`.
 * @property username Cached username of the friend, refreshed by
 *   `onUserProfileUpdated`.
 * @property photoUrl Cached profile photo URL of the friend, refreshed by
 *   `onUserProfileUpdated`. Nullable because users may have no photo.
 * @property isOnline Eventual presence flag; a dedicated presence listener
 *   may override this for live indicators.
 * @property since Epoch-millis timestamp of when the friendship was
 *   accepted.
 * @property pairId The canonical `friendships/{pairId}` document id for the
 *   relationship — see [FriendshipIds.pairId].
 */
data class FriendEntry(
    val friendUid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
    val isOnline: Boolean = false,
    val since: Long = 0L,
    val pairId: String = ""
)
