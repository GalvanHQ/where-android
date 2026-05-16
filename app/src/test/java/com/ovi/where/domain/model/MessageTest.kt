package com.ovi.where.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {

    @Test
    fun `TEXT message is valid when text is non-empty`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "Hello",
            type = MessageType.TEXT,
            timestamp = 1000L
        )
        assertTrue(message.isValid())
    }

    @Test
    fun `TEXT message is invalid when text is empty`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "",
            type = MessageType.TEXT,
            timestamp = 1000L
        )
        assertFalse(message.isValid())
    }

    @Test
    fun `LOCATION message is valid when lat and lng are non-null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "Location",
            type = MessageType.LOCATION,
            timestamp = 1000L,
            latitude = 40.7128,
            longitude = -74.0060
        )
        assertTrue(message.isValid())
    }

    @Test
    fun `LOCATION message is invalid when latitude is null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "Location",
            type = MessageType.LOCATION,
            timestamp = 1000L,
            latitude = null,
            longitude = -74.0060
        )
        assertFalse(message.isValid())
    }

    @Test
    fun `LOCATION message is invalid when longitude is null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "Location",
            type = MessageType.LOCATION,
            timestamp = 1000L,
            latitude = 40.7128,
            longitude = null
        )
        assertFalse(message.isValid())
    }

    @Test
    fun `IMAGE message is valid when imageUrl is non-null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "",
            type = MessageType.IMAGE,
            timestamp = 1000L,
            imageUrl = "https://example.com/image.jpg"
        )
        assertTrue(message.isValid())
    }

    @Test
    fun `IMAGE message is invalid when imageUrl is null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "",
            type = MessageType.IMAGE,
            timestamp = 1000L,
            imageUrl = null
        )
        assertFalse(message.isValid())
    }

    @Test
    fun `SYSTEM message is always valid`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "system",
            senderName = "System",
            text = "",
            type = MessageType.SYSTEM,
            timestamp = 1000L
        )
        assertTrue(message.isValid())
    }

    @Test
    fun `VOICE message is valid when voiceUrl is non-null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "",
            type = MessageType.VOICE,
            timestamp = 1000L,
            voiceUrl = "https://example.com/voice.aac"
        )
        assertTrue(message.isValid())
    }

    @Test
    fun `VOICE message is invalid when voiceUrl is null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "",
            type = MessageType.VOICE,
            timestamp = 1000L,
            voiceUrl = null
        )
        assertFalse(message.isValid())
    }

    @Test
    fun `LIVE_LOCATION message is valid when locationSharingSessionId is non-null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "",
            type = MessageType.LIVE_LOCATION,
            timestamp = 1000L,
            locationSharingSessionId = "session-123"
        )
        assertTrue(message.isValid())
    }

    @Test
    fun `LIVE_LOCATION message is invalid when locationSharingSessionId is null`() {
        val message = Message(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderName = "User",
            text = "",
            type = MessageType.LIVE_LOCATION,
            timestamp = 1000L,
            locationSharingSessionId = null
        )
        assertFalse(message.isValid())
    }
}
