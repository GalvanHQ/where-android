package com.ovi.where.data.local.entity

import com.ovi.where.domain.model.ConversationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationEntityMapperTest {

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = ConversationEntity(
            id = "conv1",
            name = "Test Chat",
            type = "GROUP",
            photoUrl = "https://photo.url",
            groupId = "group1",
            lastMessageText = "Hello",
            lastMessageTimestamp = 5000L,
            lastMessageSenderId = "user1",
            unreadCount = 3,
            memberIdsJson = """["user1","user2","user3"]""",
            lastSyncTimestamp = 6000L
        )

        val domain = entity.toDomain()

        assertEquals("conv1", domain.id)
        assertEquals("Test Chat", domain.name)
        assertEquals(ConversationType.GROUP, domain.type)
        assertEquals("https://photo.url", domain.photoUrl)
        assertEquals("group1", domain.groupId)
        assertEquals("Hello", domain.lastMessageText)
        assertEquals(5000L, domain.lastMessageTimestamp)
        assertEquals("user1", domain.lastMessageSenderId)
        assertEquals(listOf("user1", "user2", "user3"), domain.participantIds)
        assertEquals(3, domain.unreadCounts["conv1"])
    }

    @Test
    fun `toDomain handles empty memberIds`() {
        val entity = ConversationEntity(
            id = "conv1",
            name = "Chat",
            type = "DIRECT",
            photoUrl = null,
            groupId = null,
            lastMessageText = "",
            lastMessageTimestamp = 0L,
            lastMessageSenderId = "",
            unreadCount = 0,
            memberIdsJson = "[]",
            lastSyncTimestamp = 0L
        )

        val domain = entity.toDomain()

        assertEquals(ConversationType.DIRECT, domain.type)
        assertEquals(emptyList<String>(), domain.participantIds)
        assertNull(domain.photoUrl)
        assertNull(domain.groupId)
    }

    @Test
    fun `toDomain defaults to DIRECT type for unknown type string`() {
        val entity = ConversationEntity(
            id = "conv1",
            name = "Chat",
            type = "UNKNOWN",
            photoUrl = null,
            groupId = null,
            lastMessageText = "",
            lastMessageTimestamp = 0L,
            lastMessageSenderId = "",
            unreadCount = 0,
            memberIdsJson = "[]",
            lastSyncTimestamp = 0L
        )

        assertEquals(ConversationType.DIRECT, entity.toDomain().type)
    }

    @Test
    fun `toEntity maps domain fields correctly`() {
        val domain = com.ovi.where.domain.model.Conversation(
            id = "conv1",
            name = "Group Chat",
            type = ConversationType.GROUP,
            participantIds = listOf("u1", "u2"),
            groupId = "g1",
            photoUrl = "https://photo.url",
            lastMessageText = "Last msg",
            lastMessageSenderId = "u1",
            lastMessageTimestamp = 9000L,
            unreadCounts = mapOf("conv1" to 5)
        )

        val entity = domain.toEntity()

        assertEquals("conv1", entity.id)
        assertEquals("Group Chat", entity.name)
        assertEquals("GROUP", entity.type)
        assertEquals("https://photo.url", entity.photoUrl)
        assertEquals("g1", entity.groupId)
        assertEquals("Last msg", entity.lastMessageText)
        assertEquals(9000L, entity.lastMessageTimestamp)
        assertEquals("u1", entity.lastMessageSenderId)
        assertEquals(5, entity.unreadCount)
    }

    @Test
    fun `toEntity uses 0 unreadCount when unreadCounts map is empty`() {
        val domain = com.ovi.where.domain.model.Conversation(
            id = "conv1",
            name = "Chat",
            type = ConversationType.DIRECT,
            participantIds = listOf("u1", "u2"),
            unreadCounts = emptyMap()
        )

        assertEquals(0, domain.toEntity().unreadCount)
    }

    @Test
    fun `round-trip entity to domain and back preserves core data`() {
        val entity = ConversationEntity(
            id = "conv1",
            name = "Test",
            type = "GROUP",
            photoUrl = "https://photo.url",
            groupId = "g1",
            lastMessageText = "Hi",
            lastMessageTimestamp = 3000L,
            lastMessageSenderId = "u1",
            unreadCount = 2,
            memberIdsJson = """["u1","u2"]""",
            lastSyncTimestamp = 4000L
        )

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.name, roundTripped.name)
        assertEquals(entity.type, roundTripped.type)
        assertEquals(entity.photoUrl, roundTripped.photoUrl)
        assertEquals(entity.groupId, roundTripped.groupId)
        assertEquals(entity.lastMessageText, roundTripped.lastMessageText)
        assertEquals(entity.lastMessageTimestamp, roundTripped.lastMessageTimestamp)
        assertEquals(entity.lastMessageSenderId, roundTripped.lastMessageSenderId)
    }

    @Test
    fun `toDomain handles malformed memberIds JSON gracefully`() {
        val entity = ConversationEntity(
            id = "conv1",
            name = "Chat",
            type = "DIRECT",
            photoUrl = null,
            groupId = null,
            lastMessageText = "",
            lastMessageTimestamp = 0L,
            lastMessageSenderId = "",
            unreadCount = 0,
            memberIdsJson = "not valid json",
            lastSyncTimestamp = 0L
        )

        assertEquals(emptyList<String>(), entity.toDomain().participantIds)
    }
}
