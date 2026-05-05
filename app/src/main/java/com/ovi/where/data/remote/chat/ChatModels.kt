package com.ovi.where.data.remote.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Shared wire models (mirrors server/model/Models.kt) ───────────────────

@Serializable
data class ConversationDto(
    val id: String,
    val type: String,
    val participantIds: List<String>,
    val groupId: String? = null,
    val name: String,
    val photoUrl: String? = null,
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val createdAt: Long = 0L
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

// ─── WebSocket incoming frames (from server) ──────────────────────────────

@Serializable
sealed class ServerFrame {
    @Serializable @SerialName("message")
    data class MessageDelivered(
        val id: String = "",
        val conversationId: String = "",
        val senderId: String = "",
        val senderName: String = "",
        val senderPhotoUrl: String? = null,
        val text: String = "",
        val messageType: String = "TEXT",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val timestamp: Long = 0L,
        val readBy: List<String> = emptyList()
    ) : ServerFrame()

    @Serializable @SerialName("ack")
    data class MessageAck(val tempId: String = "", val id: String = "", val timestamp: Long = 0L) : ServerFrame()

    @Serializable @SerialName("typing")
    data class UserTyping(val userId: String = "", val userName: String = "", val isTyping: Boolean = true) : ServerFrame()

    @Serializable @SerialName("error")
    data class Error(val message: String = "", val code: Int = 400) : ServerFrame()

    @Serializable @SerialName("connected")
    data class Connected(val conversationId: String = "", val userId: String = "") : ServerFrame()
}
