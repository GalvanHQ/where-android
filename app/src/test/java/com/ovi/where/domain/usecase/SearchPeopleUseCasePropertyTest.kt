package com.ovi.where.domain.usecase

import com.ovi.where.domain.model.FriendEntry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for [SearchPeopleUseCase].
 *
 * Tag:
 *   Feature: messenger-style-search,
 *   Property 3: People search returns only matching users
 *
 * **Validates: Requirements 5.3, 9.1**
 *
 * Properties exercised:
 *   1. Soundness: All returned results contain the query as a case-insensitive
 *      substring of their displayName or username.
 *   2. Completeness: No matching user is excluded from the result.
 *   3. Empty/blank queries always return an empty list.
 */
class SearchPeopleUseCasePropertyTest : StringSpec({

    val useCase = SearchPeopleUseCase()

    // Generator for a FriendEntry with random displayName and username
    val friendEntryArb: Arb<FriendEntry> = arbitrary {
        FriendEntry(
            friendUid = Arb.string(minSize = 1, maxSize = 10, codepoints = Codepoint.alphanumeric()).bind(),
            displayName = Arb.string(minSize = 0, maxSize = 30).bind(),
            username = Arb.string(minSize = 0, maxSize = 20).bind(),
            photoUrl = Arb.string(minSize = 5, maxSize = 30, codepoints = Codepoint.alphanumeric()).orNull().bind(),
            isOnline = Arb.boolean().bind(),
            since = Arb.long(0L..System.currentTimeMillis()).bind(),
            pairId = Arb.string(minSize = 1, maxSize = 15, codepoints = Codepoint.alphanumeric()).bind(),
        )
    }

    val friendListArb: Arb<List<FriendEntry>> = Arb.list(friendEntryArb, range = 0..20)

    // Non-empty, non-blank query generator (at least 1 printable char after trim)
    val queryArb: Arb<String> = Arb.string(minSize = 1, maxSize = 10, codepoints = Codepoint.alphanumeric())

    "Property 3 - Soundness: all returned users match the query in displayName or username" {
        checkAll(iterations = 150, friendListArb, queryArb) { friends, query ->
            val results = useCase(query, friends)
            val lowerQuery = query.trim().lowercase()

            results.forEach { friend ->
                val matchesDisplayName = friend.displayName.lowercase().contains(lowerQuery)
                val matchesUsername = friend.username.lowercase().contains(lowerQuery)
                (matchesDisplayName || matchesUsername) shouldBe true
            }
        }
    }

    "Property 3 - Completeness: no matching user is excluded from the result" {
        checkAll(iterations = 150, friendListArb, queryArb) { friends, query ->
            val results = useCase(query, friends)
            val lowerQuery = query.trim().lowercase()

            // Compute expected matches independently
            val expected = friends.filter { friend ->
                friend.displayName.lowercase().contains(lowerQuery) ||
                    friend.username.lowercase().contains(lowerQuery)
            }

            results shouldContainExactlyInAnyOrder expected
        }
    }

    "Property 3 - Empty/blank queries return empty list" {
        val blankQueryArb = Arb.string(minSize = 0, maxSize = 5).map { " ".repeat(it.length) }

        checkAll(iterations = 100, friendListArb, blankQueryArb) { friends, blankQuery ->
            useCase(blankQuery, friends) shouldBe emptyList()
        }
    }
}) {
    companion object {
        init {
            PropertyTesting.defaultSeed = 0xCAFEL
        }
    }
}
