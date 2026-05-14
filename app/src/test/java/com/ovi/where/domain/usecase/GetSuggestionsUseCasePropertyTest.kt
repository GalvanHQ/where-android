package com.ovi.where.domain.usecase

import com.ovi.where.domain.model.Interaction
import com.ovi.where.domain.model.InteractionType
import com.ovi.where.domain.repository.InteractionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Property-based tests for [GetSuggestionsUseCase].
 *
 * Uses a fake [InteractionRepository] that returns a configurable list of
 * interactions as a Flow. Each property test runs at least 100 iterations.
 *
 * Tag:
 *   Feature: messenger-style-search,
 *   Property 10: Suggestions include recently interacted users
 *   Property 11: Suggestions ordered by recency
 *   Property 12: Suggestions max 15 invariant
 *
 * **Validates: Requirements 8.2, 8.3, 8.4, 8.5**
 */
class GetSuggestionsUseCasePropertyTest : StringSpec({

    // Generator for a single Interaction with random fields and distinct userId
    val interactionArb: Arb<Interaction> = arbitrary {
        Interaction(
            userId = Arb.string(minSize = 1, maxSize = 15, codepoints = Codepoint.alphanumeric()).bind(),
            displayName = Arb.string(minSize = 1, maxSize = 25).bind(),
            photoUrl = Arb.string(minSize = 5, maxSize = 30, codepoints = Codepoint.alphanumeric()).orNull().bind(),
            type = Arb.enum<InteractionType>().bind(),
            timestamp = Arb.long(1_000_000L..9_999_999_999L).bind(),
            isOnline = Arb.boolean().bind()
        )
    }

    // Generator: list of interactions with distinct userIds (0..15 items)
    // This ensures all interactions appear in suggestions (within the 15 limit)
    val interactionListArb: Arb<List<Interaction>> = Arb.list(interactionArb, range = 1..15)
        .let { arb ->
            arbitrary {
                val list = arb.bind()
                // Ensure distinct userIds by deduplicating
                list.distinctBy { it.userId }
            }
        }

    // Generator: list of interactions with distinct userIds (0..30 items, may exceed 15)
    val largeInteractionListArb: Arb<List<Interaction>> = Arb.list(interactionArb, range = 16..30)
        .let { arb ->
            arbitrary {
                val list = arb.bind()
                list.distinctBy { it.userId }
            }
        }

    // Generator: list of interactions with distinct userIds and distinct timestamps (for ordering test)
    val orderedInteractionListArb: Arb<List<Interaction>> = Arb.list(interactionArb, range = 2..15)
        .let { arb ->
            arbitrary {
                val list = arb.bind()
                // Ensure distinct userIds and distinct timestamps
                list.distinctBy { it.userId }
                    .mapIndexed { index, interaction ->
                        interaction.copy(timestamp = 1_000_000L + index * 1000L)
                    }
                    .sortedByDescending { it.timestamp }
            }
        }

    "Feature: messenger-style-search, Property 10: Suggestions include recently interacted users" {
        checkAll(iterations = 100, interactionListArb) { interactions ->
            val fakeRepo = FakeInteractionRepository(interactions)
            val useCase = GetSuggestionsUseCase(fakeRepo)

            val suggestions = useCase(limit = 15).first()

            // All interactions with recorded events appear in suggestions (up to limit 15)
            val expectedUserIds = interactions.take(15).map { it.userId }
            suggestions.map { it.userId } shouldContainExactly expectedUserIds
        }
    }

    "Feature: messenger-style-search, Property 11: Suggestions ordered by recency" {
        checkAll(iterations = 100, orderedInteractionListArb) { interactions ->
            // interactions are already sorted by timestamp DESC (most recent first)
            val fakeRepo = FakeInteractionRepository(interactions)
            val useCase = GetSuggestionsUseCase(fakeRepo)

            val suggestions = useCase(limit = 15).first()

            // Verify the use case preserves the repository's descending timestamp order
            val expectedUserIds = interactions.take(15).map { it.userId }
            suggestions.map { it.userId } shouldContainExactly expectedUserIds
        }
    }

    "Feature: messenger-style-search, Property 12: Suggestions max 15 invariant" {
        checkAll(iterations = 100, largeInteractionListArb) { interactions ->
            val fakeRepo = FakeInteractionRepository(interactions)
            val useCase = GetSuggestionsUseCase(fakeRepo)

            val suggestions = useCase(limit = 15).first()

            suggestions.size shouldBeLessThanOrEqual 15
        }
    }
}) {
    companion object {
        init {
            PropertyTesting.defaultSeed = 0xCAFEL
        }
    }
}

/**
 * Fake [InteractionRepository] that returns a configurable list of interactions.
 * The list is returned as-is (simulating the repository already sorting by timestamp DESC).
 */
private class FakeInteractionRepository(
    private val interactions: List<Interaction>
) : InteractionRepository {

    override fun getRecentInteractions(limit: Int): Flow<List<Interaction>> {
        return flowOf(interactions.take(limit))
    }

    override suspend fun recordInteraction(
        userId: String,
        displayName: String,
        photoUrl: String?,
        type: InteractionType
    ) {
        // No-op for testing
    }
}
