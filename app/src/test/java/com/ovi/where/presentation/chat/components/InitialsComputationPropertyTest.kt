package com.ovi.where.presentation.chat.components

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for initials computation in ConversationAvatar.
 *
 * Verifies that [computeInitials] produces 1-2 uppercase characters from the
 * first characters of the first two words of a name, or "?" for blank/empty names.
 *
 * **Validates: Requirements 3.3**
 */
// Feature: chat-ui-redesign, Property 3: Initials computation produces valid uppercase characters
class InitialsComputationPropertyTest : StringSpec({

    // ─────────────────────────────────────────────────────────────────────────
    // Feature: chat-ui-redesign, Property 3: Initials computation produces valid uppercase characters
    // ─────────────────────────────────────────────────────────────────────────

    // Generator: blank/empty strings (empty, spaces, tabs, mixed whitespace)
    val arbBlankString: Arb<String> = arbitrary {
        val whitespaceChars = listOf(' ', '\t', '\n', '\r')
        val length = Arb.int(0..20).bind()
        buildString {
            repeat(length) {
                append(Arb.element(whitespaceChars).bind())
            }
        }
    }

    // Letter characters for generating words (no digits - digits don't have uppercase)
    val letterChars = ('a'..'z').toList() + ('A'..'Z').toList()

    // Generator: single alphabetic word (no whitespace, at least 1 char)
    val arbSingleWord: Arb<String> = arbitrary {
        val length = Arb.int(1..12).bind()
        buildString {
            repeat(length) {
                append(Arb.element(letterChars).bind())
            }
        }
    }

    // Generator: multi-word name (two words separated by space)
    val arbTwoWordName: Arb<Pair<String, String>> = arbitrary {
        val len1 = Arb.int(1..10).bind()
        val len2 = Arb.int(1..10).bind()
        val word1 = buildString { repeat(len1) { append(Arb.element(letterChars).bind()) } }
        val word2 = buildString { repeat(len2) { append(Arb.element(letterChars).bind()) } }
        word1 to word2
    }

    "Property 3a: blank or empty names produce question mark fallback" {
        checkAll(PropTestConfig(iterations = 100), arbBlankString) { name ->
            computeInitials(name) shouldBe "?"
        }
    }

    "Property 3b: single-word non-blank names produce exactly 1 uppercase character" {
        checkAll(PropTestConfig(iterations = 150), arbSingleWord) { word ->
            val result = computeInitials(word)
            result shouldHaveLength 1
            result[0].isUpperCase() shouldBe true
            result[0] shouldBe word.first().uppercaseChar()
        }
    }

    "Property 3c: multi-word names produce exactly 2 uppercase characters from first chars of first two words" {
        checkAll(PropTestConfig(iterations = 150), arbTwoWordName) { (word1, word2) ->
            val name = "$word1 $word2"
            val result = computeInitials(name)

            result shouldHaveLength 2
            result[0].isUpperCase() shouldBe true
            result[1].isUpperCase() shouldBe true
            result[0] shouldBe word1.first().uppercaseChar()
            result[1] shouldBe word2.first().uppercaseChar()
        }
    }

    "Property 3d: initials length is 1-2 or question mark for any string input" {
        checkAll(PropTestConfig(iterations = 200), Arb.string(0..30)) { name ->
            val result = computeInitials(name)

            if (name.trim().isBlank()) {
                result shouldBe "?"
            } else {
                result.length shouldBeInRange 1..2
                // Each character should be the uppercased form of the corresponding word's first char
                val words = name.trim().split("\\s+".toRegex())
                result[0] shouldBe words[0].first().uppercaseChar()
                if (result.length == 2) {
                    result[1] shouldBe words[1].first().uppercaseChar()
                }
            }
        }
    }

    "Property 3e: first initial always matches first character of first word uppercased" {
        checkAll(PropTestConfig(iterations = 150), arbSingleWord) { word ->
            val result = computeInitials(word)
            result[0] shouldBe word.first().uppercaseChar()
        }
    }

})
