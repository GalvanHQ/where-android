package com.ovi.where.domain.model

/**
 * Deterministic identifiers for the canonical friendship pair document at
 * `friendships/{pairId}`.
 *
 * ### Determinism invariant
 * For any two distinct uids `a` and `b`, both [pairId] and [members] return
 * the **same** value regardless of argument order:
 *
 * ```
 * FriendshipIds.pairId(a, b)  == FriendshipIds.pairId(b, a)
 * FriendshipIds.members(a, b) == FriendshipIds.members(b, a)
 * ```
 *
 * This lets either side of a relationship address the shared document with a
 * single, predictable path — eliminating the legacy "query both directions"
 * pattern and cutting the read cost of [Friendship] lookups to a single
 * `get()` against a known path.
 *
 * ### Pair-id format
 * The [pairId] is the two uids joined by an underscore, ordered
 * lexicographically ascending:
 *
 * ```
 * pairId(a, b) == "${min(a, b)}_${max(a, b)}"
 * ```
 *
 * The matching [members] list is the same two uids in the same ascending
 * order, so the document invariant
 *
 * ```
 * pairId == "${members[0]}_${members[1]}"
 * ```
 *
 * always holds. Lexicographic (String natural) ordering is used consistently
 * on client and server so both sides compute byte-identical ids.
 *
 * ### Distinctness precondition
 * Both functions require `a != b`. Self-friendships are not a representable
 * state; callers pass through server-side validation anyway, but rejecting
 * equal uids here catches programming errors at the call site with a clear
 * [IllegalArgumentException] instead of producing a malformed id like
 * `"u_u"`.
 *
 * Implemented as a singleton [object] because the helper is pure and has no
 * state to carry — mirrors design §4.1.
 */
object FriendshipIds {

    /**
     * Returns the canonical `friendships/{pairId}` document id for the
     * friendship between [a] and [b].
     *
     * The result is symmetric: `pairId(a, b) == pairId(b, a)`.
     *
     * @throws IllegalArgumentException if [a] and [b] are equal.
     */
    fun pairId(a: String, b: String): String {
        require(a != b) { "FriendshipIds.pairId requires two distinct uids, got '$a' twice" }
        return if (a < b) "${a}_$b" else "${b}_$a"
    }

    /**
     * Returns the two-element `members` list for the friendship between [a]
     * and [b], sorted lexicographically ascending so that
     * `members[0] < members[1]` always holds.
     *
     * The result is symmetric: `members(a, b) == members(b, a)`.
     *
     * @throws IllegalArgumentException if [a] and [b] are equal.
     */
    fun members(a: String, b: String): List<String> {
        require(a != b) { "FriendshipIds.members requires two distinct uids, got '$a' twice" }
        return if (a < b) listOf(a, b) else listOf(b, a)
    }
}
