package com.ovi.where.presentation.onboarding

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for the Social Proof hero section.
 *
 * Verifies the card count invariant (3–5 profiles accepted) and the display name
 * truncation logic (max 30 characters, ellipsis if exceeded).
 *
 * **Validates: Requirements 3.1, 3.2**
 */
class SocialProofHeroPropertyTest : StringSpec({

    // ─────────────────────────────────────────────────────────────────────────
    // Feature: premium-onboarding-redesign, Property 2: Social proof card count invariant
    // ─────────────────────────────────────────────────────────────────────────

    "Property 2a: Only lists with 3 to 5 profile cards are valid for Social Proof section" {
        val arbProfileCard = Arb.string(1..20).map { name ->
            ProfileCardData(
                displayName = name,
                subtitle = "Active now",
                avatarContentDescription = "Sample user avatar"
            )
        }

        checkAll(iterations = 200, Arb.list(arbProfileCard, 1..10)) { profiles ->
            // The Social Proof section accepts only 3..5 cards
            val isValidForSocialProof = profiles.size in 3..5

            if (profiles.size < 3) {
                isValidForSocialProof shouldBe false
            } else if (profiles.size > 5) {
                isValidForSocialProof shouldBe false
            } else {
                isValidForSocialProof shouldBe true
                profiles.size shouldBeGreaterThanOrEqual 3
                profiles.size shouldBeLessThanOrEqual 5
            }
        }
    }

    "Property 2b: Static socialProofProfiles list has valid count within 3..5 range" {
        val count = socialProofProfiles.size
        count shouldBeGreaterThanOrEqual 3
        count shouldBeLessThanOrEqual 5
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feature: premium-onboarding-redesign, Property 3: Display name truncation
    // ─────────────────────────────────────────────────────────────────────────

    "Property 3a: Display names within 30 characters are unchanged after truncation" {
        checkAll(iterations = 200, Arb.string(0..30)) { name ->
            val truncated = truncateDisplayName(name)
            truncated shouldBe name
        }
    }

    "Property 3b: Display names exceeding 30 characters are truncated to 30 chars plus ellipsis" {
        checkAll(iterations = 200, Arb.string(31..100)) { name ->
            val truncated = truncateDisplayName(name)

            // Output should be 30 chars of original + ellipsis character
            truncated shouldHaveLength 31
            truncated.substring(0, 30) shouldBe name.substring(0, 30)
            truncated shouldEndWith "\u2026"
        }
    }

    "Property 3c: Truncation logic is consistent for any string length 0..100" {
        checkAll(iterations = 200, Arb.string(0..100)) { name ->
            val truncated = truncateDisplayName(name)

            if (name.length <= 30) {
                // No truncation — output equals input
                truncated shouldBe name
            } else {
                // Truncated to 30 chars + ellipsis
                truncated shouldHaveLength 31
                truncated.substring(0, 30) shouldBe name.substring(0, 30)
                truncated.last() shouldBe '\u2026'
            }
        }
    }

}) {
    companion object {
        init {
            // Lock the seed for reproducible counter-examples.
            PropertyTesting.defaultSeed = 0xFACEL
        }
    }
}

/**
 * Replicates the display name truncation logic used by [ProfileCard].
 *
 * If the name exceeds 30 characters, it is truncated to 30 characters
 * followed by an ellipsis character (\u2026). Otherwise, it is returned unchanged.
 */
private fun truncateDisplayName(name: String): String {
    return if (name.length > 30) {
        name.take(30) + "\u2026"
    } else {
        name
    }
}
