package com.ovi.where.presentation.model

import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank

class ConversationUiModelMapperTest : StringSpec({

    val timeFormatter: (Long) -> String = { "12:00" }

    "toUiModel uses conversation name when it is not blank" {
        val conversation = Conversation(
            id = "conv1",
            name = "Alice",
            type = ConversationType.DIRECT,
            participantIds = listOf("me", "alice123")
        )
        val result = conversation.toUiModel("me", timeFormatter)
        result.title shouldBe "Alice"
    }

    "toUiModel resolves DM title from participantNames when name is blank" {
        val conversation = Conversation(
            id = "conv1",
            name = "",
            type = ConversationType.DIRECT,
            participantIds = listOf("me", "alice123")
        )
        val participantNames = mapOf("alice123" to "Alice Smith")
        val result = conversation.toUiModel("me", timeFormatter, participantNames)
        result.title shouldBe "Alice Smith"
    }

    "toUiModel falls back to Unknown User for DM when name is blank and no participant metadata" {
        val conversation = Conversation(
            id = "conv1",
            name = "",
            type = ConversationType.DIRECT,
            participantIds = listOf("me", "alice123")
        )
        val result = conversation.toUiModel("me", timeFormatter)
        result.title shouldBe "Unknown User"
    }

    "toUiModel falls back to Unknown User for DM when name is blank and participant name is also blank" {
        val conversation = Conversation(
            id = "conv1",
            name = "   ",
            type = ConversationType.DIRECT,
            participantIds = listOf("me", "alice123")
        )
        val participantNames = mapOf("alice123" to "  ")
        val result = conversation.toUiModel("me", timeFormatter, participantNames)
        result.title shouldBe "Unknown User"
    }

    "toUiModel falls back to Unnamed Group for group conversations with blank name" {
        val conversation = Conversation(
            id = "conv1",
            name = "",
            type = ConversationType.GROUP,
            participantIds = listOf("me", "alice123", "bob456"),
            groupId = "group1"
        )
        val result = conversation.toUiModel("me", timeFormatter)
        result.title shouldBe "Unnamed Group"
    }

    "toUiModel title is never blank for DM with empty name" {
        val conversation = Conversation(
            id = "conv1",
            name = "",
            type = ConversationType.DIRECT,
            participantIds = listOf("me", "other")
        )
        val result = conversation.toUiModel("me", timeFormatter)
        result.title shouldNot beBlank()
    }

    "toUiModel title is never blank for group with empty name" {
        val conversation = Conversation(
            id = "conv1",
            name = "",
            type = ConversationType.GROUP,
            participantIds = listOf("me", "other", "third")
        )
        val result = conversation.toUiModel("me", timeFormatter)
        result.title shouldNot beBlank()
    }

    "resolveConversationTitle returns name when not blank" {
        resolveConversationTitle(
            name = "My Chat",
            type = ConversationType.DIRECT,
            otherUserId = "user1"
        ) shouldBe "My Chat"
    }

    "resolveConversationTitle resolves from participantNames for DM" {
        resolveConversationTitle(
            name = "",
            type = ConversationType.DIRECT,
            otherUserId = "user1",
            participantNames = mapOf("user1" to "John Doe")
        ) shouldBe "John Doe"
    }

    "resolveConversationTitle returns Unknown User for DM with no metadata" {
        resolveConversationTitle(
            name = "",
            type = ConversationType.DIRECT,
            otherUserId = "user1",
            participantNames = emptyMap()
        ) shouldBe "Unknown User"
    }

    "resolveConversationTitle returns Unnamed Group for group with blank name" {
        resolveConversationTitle(
            name = "",
            type = ConversationType.GROUP,
            otherUserId = null
        ) shouldBe "Unnamed Group"
    }

    "resolveConversationTitle ignores participantNames for group conversations" {
        resolveConversationTitle(
            name = "",
            type = ConversationType.GROUP,
            otherUserId = null,
            participantNames = mapOf("user1" to "John")
        ) shouldBe "Unnamed Group"
    }

    "resolveConversationTitle skips blank participant name and falls back" {
        resolveConversationTitle(
            name = "",
            type = ConversationType.DIRECT,
            otherUserId = "user1",
            participantNames = mapOf("user1" to "")
        ) shouldBe "Unknown User"
    }

    "resolveConversationTitle skips whitespace-only participant name and falls back" {
        resolveConversationTitle(
            name = "   ",
            type = ConversationType.DIRECT,
            otherUserId = "user1",
            participantNames = mapOf("user1" to "   ")
        ) shouldBe "Unknown User"
    }
})
