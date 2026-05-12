package com.ovi.where.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for [FriendshipIds].
 *
 * Tag:
 *   Feature: people-ux-and-friendship-data-redesign,
 *   Property 1: FriendshipIds are symmetric and sorted
 *
 * Properties exercised (design §8 Property 1):
 *   1. Symmetry of pairId:
 *      ∀ a, b ∈ Uid. a != b ⟹ pairId(a, b) == pairId(b, a)
 *   2. Symmetry of members:
 *      ∀ a, b ∈ Uid. a != b ⟹ members(a, b) == members(b, a)
 *   3. Members sorted ascending & size 2:
 *      ∀ a, b. a != b ⟹ members.size == 2 && members == members.sorted()
 *   4. Cross-consistency:
 *      ∀ a, b. a != b ⟹ pairId(a, b) == "${members(a, b)[0]}_${members(a, b)[1]}"
 *
 * Negative (not a property, but documented alongside):
 *   pairId(a, a) and members(a, a) throw IllegalArgumentException.
 *
 * Requirements: 1.2, 1.3, 1.5
 */
class FriendshipIdsPropertyTest : StringSpec({

    // UID arbitrary: non-empty alphanumeric strings up to 28 chars.
    // Constrains to the same input space Firebase Auth uids live in so
    // shrunken counter-examples stay in-domain.
    val uid: Arb<String> = Arb.string(
        minSize = 1,
        maxSize = 28,
        codepoints = Codepoint.alphanumeric(),
    )

    // Distinct pair of uids — filter enforces the `a != b` precondition once
    // at the generator level rather than skipping iterations in each property.
    val distinctUids: Arb<Pair<String, String>> =
        Arb.pair(uid, uid).filter { (a, b) -> a != b }

    "Property 1: pairId is symmetric (pairId(a,b) == pairId(b,a))" {
        checkAll(iterations = 200, distinctUids) { (a, b) ->
            FriendshipIds.pairId(a, b) shouldBe FriendshipIds.pairId(b, a)
        }
    }

    "Property 2: members is symmetric (members(a,b) == members(b,a))" {
        checkAll(iterations = 200, distinctUids) { (a, b) ->
            FriendshipIds.members(a, b) shouldBe FriendshipIds.members(b, a)
        }
    }

    "Property 3: members is size 2 and sorted ascending" {
        checkAll(iterations = 200, distinctUids) { (a, b) ->
            val members = FriendshipIds.members(a, b)
            members.size shouldBe 2
            members shouldBe members.sorted()
        }
    }

    "Property 4: pairId is consistent with members (pairId == members[0] + '_' + members[1])" {
        checkAll(iterations = 200, distinctUids) { (a, b) ->
            val members = FriendshipIds.members(a, b)
            FriendshipIds.pairId(a, b) shouldBe "${members[0]}_${members[1]}"
        }
    }

    // Example-based negative checks — not property tests because the invariant
    // is "always throws" for a single input class (self-uid), not a universal
    // statement over the input space.
    "Negative: pairId(a, a) throws IllegalArgumentException" {
        shouldThrow<IllegalArgumentException> { FriendshipIds.pairId("u1", "u1") }
    }

    "Negative: members(a, a) throws IllegalArgumentException" {
        shouldThrow<IllegalArgumentException> { FriendshipIds.members("u1", "u1") }
    }
}) {
    companion object {
        init {
            // Lock the seed so counter-examples are reproducible across runs.
            // Any failure will include this seed in the Kotest report.
            PropertyTesting.defaultSeed = 0xC0FFEE_L
        }
    }
}
