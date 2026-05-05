package com.ovi.where.server.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── WebSocket frame types ─────────────────────────────────────────────────────

/** Every frame sent over the WebSocket is one of these. */
@Serializable
sealed class WsFrame {

    @Serializable
    @SerialName("message")
    data class NewMessage(
        val tempId: String = "",          // client-generated ID to correlate ACK
        val text: String = "",
        val messageType: String = "TEXT"  // TEXT | LOCATION
    ) : WsFrame()

    @Serializable
    @SerialName("location_message")
    data class LocationMessage(
        val tempId: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    ) : WsFrame()

    @Serializable
    @SerialName("typing")
    data class Typing(val isTyping: Boolean = true) : WsFrame()

    @Serializable
    @SerialName("read")
    object MarkRead : WsFrame()
}

/** Frames the server broadcasts to all room members. */
@Serializable
sealed class ServerFrame {

    @Serializable
    @SerialName("message")
    data class MessageDelivered(
        val id: String,
        val conversationId: String,
        val senderId: String,
        val senderName: String,
        val senderPhotoUrl: String? = null,
        val text: String,
        val messageType: String,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val timestamp: Long,
        val readBy: List<String> = emptyList()
    ) : ServerFrame()

    @Serializable
    @SerialName("ack")
    data class MessageAck(val tempId: String, val id: String, val timestamp: Long) : ServerFrame()

    @Serializable
    @SerialName("typing")
    data class UserTyping(val userId: String, val userName: String, val isTyping: Boolean) : ServerFrame()

    @Serializable
    @SerialName("error")
    data class Error(val message: String, val code: Int = 400) : ServerFrame()

    @Serializable
    @SerialName("connected")
    data class Connected(val conversationId: String, val userId: String) : ServerFrame()
}

// ── REST API models ───────────────────────────────────────────────────────────

@Serializable
data class ConversationDto(
    val id: String,
    val type: String,           // "direct" | "group"
    val participantIds: List<String>,
    val groupId: String? = null,
    val name: String,
    val photoUrl: String? = null,
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val createdAt: Long
)

@Serializable
data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String? = null,
    val text: String,
    val messageType: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
    val readBy: List<String> = emptyList()
)

@Serializable
data class CreateDirectConversationRequest(val otherUserId: String)

@Serializable
data class CreateGroupConversationRequest(
    val groupId: String,
    val name: String,
    val memberIds: List<String>
)

@Serializable
data class MarkReadRequest(val userId: String)
