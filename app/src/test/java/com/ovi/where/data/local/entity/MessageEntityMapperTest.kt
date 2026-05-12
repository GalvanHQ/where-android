package com.ovi.where.data.local.entity

import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageEntityMapperTest {

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = MessageEntity(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "Alice",
            senderPhotoUrl = "https://photo.url",
            text = "Hello",
            type = "TEXT",
            timestamp = 1000L,
            status = "SENT",
            latitude = 40.7128,
            longitude = -74.0060,
            imageUrl = "https://img.url",
            thumbnailUrl = "https://thumb.url",
            replyToId = "reply1",
            replyToText = "Original text",
            replyToSenderName = "Bob",
            reactionsJson = """{"👍":["user2","user3"],"❤️":["user1"]}""",
            readByJson = """["user2","user3"]"""
        )

        val domain = entity.toDomain()

        assertEquals("msg1", domain.id)
        assertEquals("conv1", domain.conversationId)
        assertEquals("user1", domain.senderId)
        assertEquals("Alice", domain.senderName)
        assertEquals("https://photo.url", domain.senderPhotoUrl)
        assertEquals("Hello", domain.text)
        assertEquals(MessageType.TEXT, domain.type)
        assertEquals(1000L, domain.timestamp)
        assertEquals(MessageStatus.SENT, domain.status)
        assertEquals(40.7128, domain.latitude!!, 0.0001)
        assertEquals(-74.0060, domain.longitude!!, 0.0001)
        assertEquals("https://img.url", domain.imageUrl)
        assertEquals("https://thumb.url", domain.thumbnailUrl)
        assertEquals("reply1", domain.replyToId)
        assertEquals("Original text", domain.replyToText)
        assertEquals("Bob", domain.replyToSenderName)
        assertEquals(mapOf("👍" to listOf("user2", "user3"), "❤️" to listOf("user1")), domain.reactions)
        assertEquals(listOf("user2", "user3"), domain.readBy)
    }

    @Test
    fun `toDomain handles empty reactions and readBy`() {
        val entity = MessageEntity(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "Alice",
            senderPhotoUrl = null,
            text = "Hello",
            type = "TEXT",
            timestamp = 1000L,
            status = "PENDING",
            latitude = null,
            longitude = null,
            imageUrl = null,
            thumbnailUrl = null,
            replyToId = null,
            replyToText = null,
            replyToSenderName = null,
            reactionsJson = "{}",
            readByJson = "[]"
        )

        val domain = entity.toDomain()

        assertEquals(emptyMap<String, List<String>>(), domain.reactions)
        assertEquals(emptyList<String>(), domain.readBy)
        assertNull(domain.senderPhotoUrl)
        assertNull(domain.latitude)
        assertNull(domain.imageUrl)
        assertNull(domain.replyToId)
        assertEquals(MessageStatus.PENDING, domain.status)
    }

    @Test
    fun `toDomain defaults to TEXT type for unknown type string`() {
        val entity = MessageEntity(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "Alice",
            senderPhotoUrl = null,
            text = "Hello",
            type = "UNKNOWN_TYPE",
            timestamp = 1000L,
            status = "SENT",
            latitude = null,
            longitude = null,
            imageUrl = null,
            thumbnailUrl = null,
            replyToId = null,
            replyToText = null,
            replyToSenderName = null,
            reactionsJson = "{}",
            readByJson = "[]"
        )

        assertEquals(MessageType.TEXT, entity.toDomain().type)
    }

    @Test
    fun `toDomain defaults to SENT status for unknown status string`() {
        val entity = MessageEntity(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "Alice",
            senderPhotoUrl = null,
            text = "Hello",
            type = "TEXT",
            timestamp = 1000L,
            status = "INVALID_STATUS",
            latitude = null,
            longitude = null,
            imageUrl = null,
            thumbnailUrl = null,
            replyToId = null,
            replyToText = null,
            replyToSenderName = null,
            reactionsJson = "{}",
            readByJson = "[]"
        )

        assertEquals(MessageStatus.SENT, entity.toDomain().status)
    }

    @Test
    fun `toEntity maps all domain fields correctly`() {
        val domain = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "Alice",
            senderPhotoUrl = "https://photo.url",
            text = "Hello",
            type = MessageType.LOCATION,
            timestamp = 2000L,
            status = MessageStatus.FAILED,
            latitude = 51.5074,
            longitude = -0.1278,
            imageUrl = null,
            thumbnailUrl = null,
            replyToId = "r1",
            replyToText = "Reply text",
            replyToSenderName = "Bob",
            reactions = mapOf("😂" to listOf("u1", "u2")),
            readBy = listOf("u3")
        )

        val entity = domain.toEntity()

        assertEquals("msg1", entity.id)
        assertEquals("conv1", entity.conversationId)
        assertEquals("user1", entity.senderId)
        assertEquals("Alice", entity.senderName)
        assertEquals("https://photo.url", entity.senderPhotoUrl)
        assertEquals("Hello", entity.text)
        assertEquals("LOCATION", entity.type)
        assertEquals(2000L, entity.timestamp)
        assertEquals("FAILED", entity.status)
        assertEquals(51.5074, entity.latitude!!, 0.0001)
        assertEquals(-0.1278, entity.longitude!!, 0.0001)
        assertNull(entity.imageUrl)
        assertNull(entity.thumbnailUrl)
        assertEquals("r1", entity.replyToId)
        assertEquals("Reply text", entity.replyToText)
        assertEquals("Bob", entity.replyToSenderName)
    }

    @Test
    fun `round-trip entity to domain and back preserves data`() {
        val original = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "Alice",
            senderPhotoUrl = "https://photo.url",
            text = "Hello world",
            type = MessageType.TEXT,
            timestamp = 5000L,
            status = MessageStatus.DELIVERED,
            latitude = null,
            longitude = null,
            imageUrl = null,
            thumbnailUrl = null,
            replyToId = null,
            replyToText = null,
            replyToSenderName = null,
            reactions = mapOf("👍" to listOf("u1"), "❤️" to listOf("u2", "u3")),
            readBy = listOf("u4", "u5")
        )

        val roundTripped = original.toEntity().toDomain()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.conversationId, roundTripped.conversationId)
        assertEquals(original.senderId, roundTripped.senderId)
        assertEquals(original.senderName, roundTripped.senderName)
        assertEquals(original.senderPhotoUrl, roundTripped.senderPhotoUrl)
        assertEquals(original.text, roundTripped.text)
        assertEquals(original.type, roundTripped.type)
        assertEquals(original.timestamp, roundTripped.timestamp)
        assertEquals(original.status, roundTripped.status)
        assertEquals(original.latitude, roundTripped.latitude)
        assertEquals(original.longitude, roundTripped.longitude)
        assertEquals(original.imageUrl, roundTripped.imageUrl)
        assertEquals(original.thumbnailUrl, roundTripped.thumbnailUrl)
        assertEquals(original.replyToId, roundTripped.replyToId)
        assertEquals(original.replyToText, roundTripped.replyToText)
        assertEquals(original.replyToSenderName, roundTripped.replyToSenderName)
        assertEquals(original.reactions, roundTripped.reactions)
        assertEquals(original.readBy, roundTripped.readBy)
    }

    @Test
    fun `toDomain handles malformed reactions JSON gracefully`() {
        val entity = MessageEntity(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "Alice",
            senderPhotoUrl = null,
            text = "Hello",
            type = "TEXT",
            timestamp = 1000L,
            status = "SENT",
            latitude = null,
            longitude = null,
            imageUrl = null,
            thumbnailUrl = null,
            replyToId = null,
            replyToText = null,
            replyToSenderName = null,
            reactionsJson = "not valid json",
            readByJson = "also not valid"
        )

        val domain = entity.toDomain()
        assertEquals(emptyMap<String, List<String>>(), domain.reactions)
        assertEquals(emptyList<String>(), domain.readBy)
    }
}
