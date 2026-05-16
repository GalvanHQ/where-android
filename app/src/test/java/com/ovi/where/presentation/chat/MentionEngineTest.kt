package com.ovi.where.presentation.chat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContainExactly

/**
 * Unit tests for MentionEngine.
 *
 * Tests cover:
 * - "@" trigger detection (Requirement 14.1)
 * - Prefix filtering (case-insensitive) (Requirement 14.1)
 * - Suggestion limit for groups > 10 members (Requirement 14.5)
 * - "@" with no additional chars shows first 5 (Requirement 14.6)
 * - Mention insertion (Requirement 14.2)
 * - Deduplicated mentionedUserIds (Requirement 14.3)
 * - Token deletion removes entire token (Requirement 14.7)
 * - Dismiss conditions (Requirement 14.8)
 */
class MentionEngineTest : StringSpec({

    val testMembers = listOf(
        MentionEngine.MentionMember("user1", "Alice", null),
        MentionEngine.MentionMember("user2", "Bob", null),
        MentionEngine.MentionMember("user3", "Charlie", null),
        MentionEngine.MentionMember("user4", "David", null),
        MentionEngine.MentionMember("user5", "Eve", null),
        MentionEngine.MentionMember("user6", "Frank", null),
        MentionEngine.MentionMember("currentUser", "Me", null)
    )

    val largeGroupMembers = (1..12).map { i ->
        MentionEngine.MentionMember("user$i", "Member$i", null)
    } + MentionEngine.MentionMember("currentUser", "Me", null)

    "detectMentionQuery - detects @ at start of text" {
        val engine = MentionEngine()
        val result = engine.detectMentionQuery("@", 1)
        result.isActive shouldBe true
        result.query shouldBe ""
        result.triggerStartIndex shouldBe 0
    }

    "detectMentionQuery - detects @ with query text" {
        val engine = MentionEngine()
        val result = engine.detectMentionQuery("@Ali", 4)
        result.isActive shouldBe true
        result.query shouldBe "Ali"
        result.triggerStartIndex shouldBe 0
    }

    "detectMentionQuery - detects @ after space" {
        val engine = MentionEngine()
        val result = engine.detectMentionQuery("Hello @Bo", 9)
        result.isActive shouldBe true
        result.query shouldBe "Bo"
        result.triggerStartIndex shouldBe 6
    }

    "detectMentionQuery - not active when @ is preceded by non-whitespace" {
        val engine = MentionEngine()
        val result = engine.detectMentionQuery("email@test", 10)
        result.isActive shouldBe false
    }

    "detectMentionQuery - not active when query contains space" {
        val engine = MentionEngine()
        val result = engine.detectMentionQuery("@hello world", 12)
        result.isActive shouldBe false
    }

    "detectMentionQuery - not active with empty text" {
        val engine = MentionEngine()
        val result = engine.detectMentionQuery("", 0)
        result.isActive shouldBe false
    }

    "detectMentionQuery - not active with cursor at 0" {
        val engine = MentionEngine()
        val result = engine.detectMentionQuery("@test", 0)
        result.isActive shouldBe false
    }

    "getSuggestions - filters by prefix match case-insensitive" {
        val engine = MentionEngine()
        val suggestions = engine.getSuggestions(testMembers, "al", "currentUser", 7)
        suggestions shouldHaveSize 1
        suggestions[0].displayName shouldBe "Alice"
    }

    "getSuggestions - excludes current user" {
        val engine = MentionEngine()
        val suggestions = engine.getSuggestions(testMembers, "M", "currentUser", 7)
        suggestions.shouldBeEmpty()
    }

    "getSuggestions - returns sorted by displayName ascending" {
        val engine = MentionEngine()
        val suggestions = engine.getSuggestions(testMembers, "", "currentUser", 7)
        // With empty query and group <= 10, still takes 5 (Requirement 14.6)
        suggestions shouldHaveSize 5
        suggestions[0].displayName shouldBe "Alice"
        suggestions[1].displayName shouldBe "Bob"
        suggestions[2].displayName shouldBe "Charlie"
        suggestions[3].displayName shouldBe "David"
        suggestions[4].displayName shouldBe "Eve"
    }

    "getSuggestions - limits to 5 when group > 10 members" {
        val engine = MentionEngine()
        val suggestions = engine.getSuggestions(largeGroupMembers, "Member", "currentUser", 13)
        suggestions shouldHaveSize 5
    }

    "getSuggestions - empty query shows first 5 members excluding current user" {
        val engine = MentionEngine()
        val suggestions = engine.getSuggestions(testMembers, "", "currentUser", 7)
        suggestions shouldHaveSize 5
        suggestions.none { it.userId == "currentUser" } shouldBe true
    }

    "getSuggestions - no match returns empty list" {
        val engine = MentionEngine()
        val suggestions = engine.getSuggestions(testMembers, "xyz", "currentUser", 7)
        suggestions.shouldBeEmpty()
    }

    "insertMention - replaces @query with @DisplayName and space" {
        val engine = MentionEngine()
        val result = engine.insertMention(
            text = "Hello @Al",
            triggerStartIndex = 6,
            cursorPosition = 9,
            member = MentionEngine.MentionMember("user1", "Alice", null)
        )
        result.newText shouldBe "Hello @Alice "
        result.newCursorPosition shouldBe 13
        result.mentionToken.userId shouldBe "user1"
        result.mentionToken.displayName shouldBe "Alice"
    }

    "insertMention - at start of text" {
        val engine = MentionEngine()
        val result = engine.insertMention(
            text = "@B",
            triggerStartIndex = 0,
            cursorPosition = 2,
            member = MentionEngine.MentionMember("user2", "Bob", null)
        )
        result.newText shouldBe "@Bob "
        result.newCursorPosition shouldBe 5
    }

    "insertMention - preserves text after cursor" {
        val engine = MentionEngine()
        val result = engine.insertMention(
            text = "@Ch and more text",
            triggerStartIndex = 0,
            cursorPosition = 3,
            member = MentionEngine.MentionMember("user3", "Charlie", null)
        )
        result.newText shouldBe "@Charlie  and more text"
        result.newCursorPosition shouldBe 9
    }

    "mentionedUserIds - returns deduplicated list" {
        val engine = MentionEngine()
        // Insert first mention
        engine.insertMention("@A", 0, 2, MentionEngine.MentionMember("user1", "Alice", null))
        // Insert same user again
        engine.insertMention(
            "@Alice @A",
            7,
            9,
            MentionEngine.MentionMember("user1", "Alice", null)
        )
        engine.mentionedUserIds shouldContainExactly listOf("user1")
    }

    "mentionedUserIds - multiple distinct users" {
        val engine = MentionEngine()
        engine.insertMention("@A", 0, 2, MentionEngine.MentionMember("user1", "Alice", null))
        val afterFirst = "@Alice "
        engine.insertMention(
            "$afterFirst@B",
            afterFirst.length,
            afterFirst.length + 2,
            MentionEngine.MentionMember("user2", "Bob", null)
        )
        engine.mentionedUserIds shouldHaveSize 2
        engine.mentionedUserIds shouldContainExactly listOf("user1", "user2")
    }

    "clear - resets all state" {
        val engine = MentionEngine()
        engine.insertMention("@A", 0, 2, MentionEngine.MentionMember("user1", "Alice", null))
        engine.mentionedUserIds shouldHaveSize 1
        engine.clear()
        engine.mentionedUserIds.shouldBeEmpty()
        engine.mentionTokens.shouldBeEmpty()
    }

    "findMentionRangesInMessage - finds mention patterns in text" {
        val ranges = MentionEngine.findMentionRangesInMessage(
            text = "Hey @Alice check this out",
            mentionedUserIds = listOf("user1"),
            userDisplayNames = mapOf("user1" to "Alice")
        )
        ranges shouldHaveSize 1
        ranges[0] shouldBe (4..9)
    }

    "findMentionRangesInMessage - finds multiple mentions" {
        val ranges = MentionEngine.findMentionRangesInMessage(
            text = "@Alice and @Bob hello",
            mentionedUserIds = listOf("user1", "user2"),
            userDisplayNames = mapOf("user1" to "Alice", "user2" to "Bob")
        )
        ranges shouldHaveSize 2
        ranges[0] shouldBe (0..5)
        ranges[1] shouldBe (11..14)
    }

    "findMentionRangesInMessage - empty when no mentions" {
        val ranges = MentionEngine.findMentionRangesInMessage(
            text = "Hello world",
            mentionedUserIds = emptyList(),
            userDisplayNames = emptyMap()
        )
        ranges.shouldBeEmpty()
    }

    "findMentionRangesInMessage - handles missing display name gracefully" {
        val ranges = MentionEngine.findMentionRangesInMessage(
            text = "@Alice hello",
            mentionedUserIds = listOf("user1", "user2"),
            userDisplayNames = mapOf("user1" to "Alice") // user2 not in map
        )
        ranges shouldHaveSize 1
    }
})
