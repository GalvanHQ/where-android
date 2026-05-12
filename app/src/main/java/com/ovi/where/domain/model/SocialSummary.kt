package com.ovi.where.domain.model

/**
 * Aggregated counter doc summarizing a user's social graph at a glance.
 *
 * ### Storage
 * Stored as a **single Firestore document** at
 * `users/{uid}/summary/social`. One document per user — never a
 * subcollection — so the entire summary is loaded with a single read.
 *
 * ### Why it exists
 * Powers the "pending requests" badge on `PeopleScreen` (and similar
 * at-a-glance counters) with exactly **one** document listener. Reading
 * [pendingIncomingCount] off this doc is cheaper than subscribing to the
 * `users/{uid}/inbox/friendRequests` aggregation doc just to compute its
 * size — the badge only needs the count, not the entries themselves, and
 * this doc is also smaller and changes less frequently than the inbox
 * under heavy traffic. The same single listener simultaneously feeds the
 * friend count, outgoing-request count, and blocked count, so every
 * at-a-glance surface in the People area can be fed from one read.
 *
 * ### Invariants
 * All counters are non-negative. Cloud Functions increment and decrement
 * them inside the **same transaction** as the state transition that
 * produced the change (friend request sent / cancelled / accepted /
 * declined, friend removed, user blocked / unblocked). Consequently, for
 * every user `uid`, eventually:
 *
 * - `friendsCount == |users/{uid}/friends|`
 * - `pendingIncomingCount == |users/{uid}/inbox/friendRequests.entries|`
 * - `pendingOutgoingCount == |users/{uid}/outbox/friendRequests.entries|`
 * - `blockedCount == |users/{uid}/blocks|`
 *
 * Because the counter updates happen inside the same transaction as the
 * underlying mutation, these equalities hold at every consistent
 * post-commit snapshot — the "eventually" qualifier only covers the
 * brief window between the transaction commit and the listener
 * delivering the new snapshot to the client.
 *
 * ### Write lifecycle (server-authoritative)
 * This document is **never** written by clients. All mutations happen
 * inside Cloud Functions transactions. Firestore security rules enforce
 * the server-only write invariant at the Phase 3 cutover.
 *
 * ### Reading when the doc is absent
 * When the document does not yet exist (e.g., a freshly-created user who
 * has never had any friendship activity), reading code MUST treat the
 * missing doc as the zero-valued `SocialSummary()` rather than an error.
 * All fields default to `0` / `0L` precisely so this substitution is a
 * no-op.
 *
 * ### Persistence
 * All fields have no-arg defaults so Firestore's reflection-based
 * deserialization (`toObject(SocialSummary::class.java)`) works without
 * a custom converter.
 *
 * Mirrors design §4.4.
 *
 * @property friendsCount Number of accepted friends — equals the
 *   cardinality of `users/{uid}/friends`.
 * @property pendingIncomingCount Number of pending incoming friend
 *   requests — equals `inbox.entries.size` (plus any spill doc).
 * @property pendingOutgoingCount Number of pending outgoing friend
 *   requests — equals `outbox.entries.size` (plus any spill doc).
 * @property blockedCount Number of users this user has blocked — equals
 *   the cardinality of `users/{uid}/blocks`.
 * @property updatedAt Epoch-millis timestamp of the last server-side
 *   update to this document.
 */
data class SocialSummary(
    val friendsCount: Int = 0,
    val pendingIncomingCount: Int = 0,
    val pendingOutgoingCount: Int = 0,
    val blockedCount: Int = 0,
    val updatedAt: Long = 0L
)
