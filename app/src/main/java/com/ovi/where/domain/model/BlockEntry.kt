package com.ovi.where.domain.model

/**
 * Private record representing a block the owning user has placed on another
 * user.
 *
 * ### Storage
 * Stored at `users/{uid}/blocks/{blockedUid}` — one document per block,
 * living under the **blocking** user's own subtree. [blockedUid] matches
 * the containing document id. Firestore security rules restrict read and
 * write access to `request.auth.uid == uid`, so each block document is
 * strictly private to the blocker: the blocked user cannot list, read, or
 * otherwise detect the existence of the block.
 *
 * ### Semantics — existence means "blocked"
 * The document's mere existence is the signal. Cloud Functions treat
 * `users/{A}/blocks/{B}` and `users/{B}/blocks/{A}` symmetrically when
 * gating new relationships: the `sendFriendRequest` callable rejects with
 * `permission-denied` whenever **either** direction's block doc exists,
 * so a block in one direction is sufficient to prevent any future
 * friend-request flow between the pair.
 *
 * ### Write lifecycle (server-authoritative)
 * Clients **never** write these documents directly. All mutations happen
 * inside Cloud Functions transactions so that the block state and its
 * derived counters stay consistent:
 *
 * - **Created** by the `blockUser` callable, which in a single Firestore
 *   transaction writes this doc, sets `friendships/{pairId}.status =
 *   BLOCKED` with `requesterId = callerUid`, removes both
 *   [FriendEntry] mirrors for the pair, strips any mirrored
 *   [RequestEntry] rows from both users' inbox/outbox aggregation docs,
 *   increments the blocker's [SocialSummary.blockedCount], and adjusts
 *   friend / pending counters on both sides to reflect the removals.
 * - **Deleted** by the `unblockUser` callable, which in the same
 *   transaction deletes `friendships/{pairId}` when its status is
 *   `BLOCKED` and `requesterId == callerUid`, and decrements the
 *   blocker's [SocialSummary.blockedCount] floored at zero.
 *
 * ### Privacy
 * Because reads are gated to the owner uid, the blocked user cannot
 * observe that they are blocked. From their perspective the
 * relationship simply appears as "no friendship" and any attempt to
 * send a fresh request surfaces as a generic `permission-denied` error
 * without disclosing the block.
 *
 * ### Persistence
 * All fields have no-arg defaults so Firestore's reflection-based
 * deserialization (`toObject(BlockEntry::class.java)`) works without a
 * custom converter.
 *
 * Mirrors design §4.5.
 *
 * @property blockedUid Uid of the user being blocked — matches the
 *   containing document id.
 * @property blockedAt Epoch-millis timestamp of when the block was
 *   created.
 * @property reason Optional free-form reason captured at block time.
 *   Reserved for a future moderation UI; left `null` by the current
 *   `blockUser` callable.
 */
data class BlockEntry(
    val blockedUid: String = "",
    val blockedAt: Long = 0L,
    val reason: String? = null
)
