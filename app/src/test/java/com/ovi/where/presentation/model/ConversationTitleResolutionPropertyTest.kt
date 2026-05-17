package com.ovi.where.presentation.model

import com.ovi.where.domain.model.ConversationType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for conversation title resolution fallback logic.
 *
 * Verifies that [resolveConversationTitle] always produces a non-blank result and
 * applies the correct fallback ("Unknown User" for DM, "Unnamed Group" for group)
 * when the conversation name is blank and no valid participant name is available.
 *
 * **Validates: Requirements 1.2, 1.4**
 */
class ConversationTitleResolutionPropertyTest : StringSpec({

    // Generator: blank strings (empty, whitespace-only)
    val blankNameArb: Arb<String> = Arb.element(
        "",
        " ",
        "  ",
        "\t",
        "\n",
        "   \t  ",
        "\t\n  "
    )

    // Generator: conversation type
    val conversationTypeArb: Arb<ConversationType> = Arb.element(
        ConversationType.DIRECT,
        ConversationType.GROUP
    )

    // Generator: nullable user ID
    val otherUserIdArb: Arb<String?> = Arb.string(minSize = 1, maxSize = 20).orNull(0.3)

    // Generator: participant names map (may contain blank values)
    val participantNamesArb: Arb<Map<String, String>> = Arb.bind(
        Arb.string(minSize = 1, maxSize = 20),
        Arb.element("", " ", "  ", "\t", "")
    ) { key, value -> mapOf(key to value) }

    "Feature: chat-ui-redesign, Property 1: Conversation title resolution produces correct fallback" {

        // Sub-property 1: Result is NEVER blank for any input combination
        checkAll(
            iterations = 100,
            blankNameArb,
            conversationTypeArb,
            otherUserIdArb,
            participantNamesArb
        ) { name, type, otherUserId, participantNames ->
            val result = resolveConversationTitle(
                name = name,
                type = type,
                otherUserId = otherUserId,
                participantNames = participantNames
            )
            result shouldNot beBlank()
        }
    }

    "Feature: chat-ui-redesign, Property 1: DM with blank name and no valid participant produces Unknown User" {

        // Sub-property 2: When name is blank and type is DIRECT with no valid participant name → "Unknown User"
        val blankParticipantNamesArb: Arb<Map<String, String>> = Arb.bind(
            Arb.string(minSize = 1, maxSize = 20),
            blankNameArb
        ) { key, value -> mapOf(key to value) }

        checkAll(
            iterations = 100,
            blankNameArb,
            otherUserIdArb,
            blankParticipantNamesArb
        ) { name, otherUserId, participantNames ->
            val result = resolveConversationTitle(
                name = name,
                type = ConversationType.DIRECT,
                otherUserId = otherUserId,
                participantNames = participantNames
            )
            result shouldBe "Unknown User"
        }
    }

    "Feature: chat-ui-redesign, Property 1: Group with blank name produces Unnamed Group" {

        // Sub-property 3: When name is blank and type is GROUP → "Unnamed Group"
        checkAll(
            iterations = 100,
            blankNameArb,
            otherUserIdArb,
            participantNamesArb
        ) { name, otherUserId, participantNames ->
            val result = resolveConversationTitle(
                name = name,
                type = ConversationType.GROUP,
                otherUserId = otherUserId,
                participantNames = participantNames
            )
            result shouldBe "Unnamed Group"
        }
    }

    "Feature: chat-ui-redesign, Property 1: Non-blank name is returned as-is" {

        // Sub-property 4: When name is not blank → returns the name as-is
        val nonBlankNameArb: Arb<String> = Arb.string(minSize = 1, maxSize = 50)
            .map { it.ifBlank { "A" } } // ensure non-blank

        checkAll(
            iterations = 100,
            nonBlankNameArb,
            conversationTypeArb,
            otherUserIdArb,
            participantNamesArb
        ) { name, type, otherUserId, participantNames ->
            val result = resolveConversationTitle(
                name = name,
                type = type,
                otherUserId = otherUserId,
                participantNames = participantNames
            )
            result shouldBe name
        }
    }
})
