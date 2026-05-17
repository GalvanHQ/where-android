package com.ovi.where.presentation.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for [resolveSenderDisplayName].
 *
 * **Validates: Requirements 2.4**
 *
 * Feature: chat-ui-redesign, Property 2: Blank sender name displays "Unknown"
 */
class SenderDisplayNamePropertyTest : StringSpec({

    tags(io.kotest.core.Tag("Feature: chat-ui-redesign"), io.kotest.core.Tag("Property 2: Blank sender name displays \"Unknown\""))

    // Generator for blank/whitespace-only strings: empty, spaces, tabs, newlines, mixed whitespace
    val blankStringArb: Arb<String> = arbitrary {
        val whitespaceChars = listOf(' ', '\t', '\n', '\r', '\u000C', '\u000B', '\u00A0')
        val length = Arb.int(0..50).bind()
        buildString {
            repeat(length) {
                append(Arb.element(whitespaceChars).bind())
            }
        }
    }

    // Generator for non-blank strings: at least one non-whitespace character
    val nonBlankStringArb: Arb<String> = arbitrary {
        val base = Arb.string(1..100).bind()
        // Ensure at least one non-whitespace character
        if (base.isBlank()) base + "a" else base
    }

    "Property 2.1: For any blank/whitespace-only string, result is Unknown" {
        checkAll(PropTestConfig(iterations = 100), blankStringArb) { blankName ->
            resolveSenderDisplayName(blankName) shouldBe "Unknown"
        }
    }

    "Property 2.2: For any non-blank string, result is the original string" {
        checkAll(PropTestConfig(iterations = 100), nonBlankStringArb) { nonBlankName ->
            resolveSenderDisplayName(nonBlankName) shouldBe nonBlankName
        }
    }

    "Property 2.3: Result is never blank for any input" {
        checkAll(PropTestConfig(iterations = 100), Arb.string(0..100)) { anyName ->
            resolveSenderDisplayName(anyName) shouldNot beBlank()
        }
    }
})
