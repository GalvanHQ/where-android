package com.ovi.where.domain.model

/**
 * Single-doc aggregation of pending friend requests for one user and one
 * direction.
 *
 * ### Storage
 * Stored as a **single Firestore document** at one of two paths:
 *
 * - `users/{uid}/inbox/friendRequests` — incoming pending requests; map
 *   keys are the **sender** uids (`fromUid`).
 * - `users/{uid}/outbox/friendRequests` — outgoing pending requests; map
 *   keys are the **receiver** uids (`toUid`).
 *
 * In both cases the key of [entries] equals the "other" user's uid and
 * matches [RequestEntry.uid] of the corresponding value, so lookups by the
 * other uid are O(1) and the mirror invariant between a sender's outbox
 * and a receiver's inbox is trivially expressible.
 *
 * ### Why a single doc, not a subcollection per request?
 * Firestore bills per document read. A subcollection listener returns N
 * reads initially (one per pending request); a listener on one aggregation
 * doc returns exactly **1 read** regardless of how many entries are
 * packed inside. For the People/FriendRequests screens — which are opened
 * frequently and typically hold only a handful of pending requests — the
 * aggregation pattern collapses the per-open cost to a single read. The
 * 1 MB Firestore document limit comfortably holds thousands of summarized
 * entries (each ≈ 150 B).
 *
 * ### Bounds and spill
 * When [entries] would exceed **500** records, the Cloud Function spills
 * additional entries into a secondary document at
 * `users/{uid}/inbox/friendRequests_2` (or `outbox/friendRequests_2`); the
 * client merges both documents when observing. Practical cap before
 * running out of spill capacity is roughly **5 000** entries per
 * direction.
 *
 * ### Write lifecycle (server-authoritative)
 * This document is **never** written by clients. All mutations happen
 * inside Cloud Functions transactions (`sendFriendRequest`,
 * `cancelFriendRequest`, `acceptFriendRequest`, `declineFriendRequest`,
 * `blockUser`, and the `onUserProfileUpdated` fan-out trigger). Firestore
 * security rules enforce the server-only invariant at the Phase 3 cutover.
 *
 * ### Persistence
 * The [entries] field defaults to an empty map so Firestore's
 * reflection-based deserialization (`toObject(RequestInbox::class.java)`)
 * produces an empty inbox/outbox when the doc does not yet exist.
 *
 * Mirrors design §4.3.
 *
 * @property entries Map from the other user's uid (sender uid for inbox;
 *   receiver uid for outbox) to the [RequestEntry] summarizing that
 *   request.
 */
data class RequestInbox(
    val entries: Map<String, RequestEntry> = emptyMap()
)
