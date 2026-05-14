package com.ovi.where.domain.usecase

import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for [SearchChatsUseCase].
 *
 * Tag:
 *   Feature: messenger-style-search,
 *   Property 4: Chats search returns only matching conversations
 *
 * **Validates: Requirements 5.4, 9.2**
 *
 * Properties exercised:
 *   1. Soundness: All returned conversations contain the query as a case-insensitive
 *      substring of their name or lastMessageText.
 *   2. Completeness: No matching conversation is excluded from the result.
 *   3. Empty/blank queries always return an empty list.
 */
class SearchChatsUseCasePropertyTest : StringSpec({

    val useCase = SearchChatsUseCase()

    // Generator for a Conversation with random name and lastMessageText
    val conversationArb: Arb<Conversation> = arbitrary {
        Conversation(
            id = Arb.string(minSize = 1, maxSize = 10, codepoints = Codepoint.alphanumeric()).bind(),
            type = Arb.enum<ConversationType>().bind(),
            name = Arb.string(minSize = 0, maxSize = 30).bind(),
            lastMessageText = Arb.string(minSize = 0, maxSize = 50).bind(),
            lastMessageTimestamp = Arb.long(0L..System.currentTimeMillis()).bind(),
        )
    }

    val conversationListArb: Arb<List<Conversation>> = Arb.list(conversationArb, range = 0..20)

    // Non-empty, non-blank query generator (at least 1 printable char after trim)
    val queryArb: Arb<String> = Arb.string(minSize = 1, maxSize = 10, codepoints = Codepoint.alphanumeric())

    "Property 4 - Soundness: all returned conversations match the query in name or lastMessageText" {
        checkAll(iterations = 150, conversationListArb, queryArb) { conversations, query ->
            val results = useCase(query, conversations)
            val lowerQuery = query.trim().lowercase()

            results.forEach { conversation ->
                val matchesName = conversation.name.lowercase().contains(lowerQuery)
                val matchesLastMessage = conversation.lastMessageText.lowercase().contains(lowerQuery)
                (matchesName || matchesLastMessage) shouldBe true
            }
        }
    }

    "Property 4 - Completeness: no matching conversation is excluded from the result" {
        checkAll(iterations = 150, conversationListArb, queryArb) { conversations, query ->
            val results = useCase(query, conversations)
            val lowerQuery = query.trim().lowercase()

            // Compute expected matches independently
            val expected = conversations.filter { conversation ->
                conversation.name.lowercase().contains(lowerQuery) ||
                    conversation.lastMessageText.lowercase().contains(lowerQuery)
            }

            results shouldContainExactlyInAnyOrder expected
        }
    }

    "Property 4 - Empty/blank queries return empty list" {
        val blankQueryArb = Arb.string(minSize = 0, maxSize = 5).map { " ".repeat(it.length) }

        checkAll(iterations = 100, conversationListArb, blankQueryArb) { conversations, blankQuery ->
            useCase(blankQuery, conversations) shouldBe emptyList()
        }
    }
}) {
    companion object {
        init {
            PropertyTesting.defaultSeed = 0xCAFEL
        }
    }
}
