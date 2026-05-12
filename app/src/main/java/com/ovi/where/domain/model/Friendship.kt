package com.ovi.where.domain.model

/**
 * Canonical friendship document between two users.
 *
 * Stored at `friendships/{pairId}` in Firestore where
 * `pairId = min(uidA, uidB) + "_" + max(uidA, uidB)` (lexicographic sort).
 *
 * ### Invariants
 * Every persisted `Friendship` document satisfies all of the following:
 *
 * - `members.size == 2`
 * - `members == members.sorted()` (lexicographic ascending)
 * - `pairId == "${members[0]}_${members[1]}"`
 * - `requesterId in members`
 *
 * Use [FriendshipIds.pairId] and [FriendshipIds.members] to construct
 * consistent values; see `FriendshipIds` for the canonical derivation.
 *
 * ### Persistence
 * All fields have no-arg defaults so Firestore's reflection-based
 * deserialization (`toObject(Friendship::class.java)`) works without a
 * custom converter.
 *
 * Only [FriendshipStatus.PENDING], [FriendshipStatus.ACCEPTED], and
 * [FriendshipStatus.BLOCKED] are serialized to Firestore.
 * [FriendshipStatus.NONE] is an in-memory sentinel meaning "no current
 * relationship" — it is **never** written; the absence of the
 * `friendships/{pairId}` document represents `NONE`.
 */
data class Friendship(
    val pairId: String = "",
    val members: List<String> = emptyList(),
    val requesterId: String = "",
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val acceptedAt: Long? = null
)

/**
 * Status of a friendship between two users.
 *
 * - [PENDING], [ACCEPTED], [BLOCKED] are the only values persisted to
 *   Firestore at `friendships/{pairId}.status`.
 * - [NONE] represents the absence of any current relationship and is
 *   used only in-memory (e.g., as a return value when no document
 *   exists). It is **not** serialized; absence of the document is the
 *   canonical representation of `NONE`.
 */
enum class FriendshipStatus { PENDING, ACCEPTED, BLOCKED, NONE }
