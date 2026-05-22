package com.ovi.where.data.remote.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Shared wire models (mirrors server/model/Models.kt) ───────────────────

@Serializable
data class ConversationDto(
    val id: String = "",
    val type: String = "direct",
    val participantIds: List<String> = emptyList(),
    val groupId: String? = null,
    val name: String = "",
    val photoUrl: String? = null,
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val createdAt: Long = 0L
)

@Serializable
data class MessageDto(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val messageType: String = "TEXT",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val voiceUrl: String? = null,
    val voiceDurationMs: Long? = null,
    val timestamp: Long = 0L,
    val readBy: List<String> = emptyList(),
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    val reactions: Map<String, List<String>> = emptyMap()
)

@Serializable
data class MessagePageDto(
    val messages: List<MessageDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

@Serializable
data class UnreadCountDto(
    val conversationId: String = "",
    val unreadCount: Int = 0
)

@Serializable
data class CreateDirectConversationRequest(val otherUserId: String)

@Serializable
data class CreateGroupConversationRequest(
    val groupId: String,
    val name: String,
    val memberIds: List<String>
)

/**
 * Body for `POST /api/conversations/{id}/system-message`. Mirrors the fields
 * the server route validates. See `.kiro/specs/group-system-messages/`.
 */
@Serializable
data class SystemMessageRequest(
    val messageId: String,
    val systemEventType: String,
    val systemEventPayload: Map<String, String>? = null,
    val targetUserId: String? = null,
    val fallbackText: String,
    val timestamp: Long
)

@Serializable
data class SystemMessageResponse(
    val success: Boolean = false,
    val id: String = ""
)

// ─── WebSocket incoming frames (from server) ──────────────────────────────

// ─── Link Preview DTO ─────────────────────────────────────────────────────

/**
 * Response DTO from the server-side link preview API.
 * Contains Open Graph metadata for a URL.
 *
 * Requirement 12.2: title, description, image URL from Open Graph metadata.
 */
@Serializable
data class LinkPreviewDto(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val url: String = ""
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
        val imageUrl: String? = null,
        val thumbnailUrl: String? = null,
        val voiceUrl: String? = null,
        val voiceDurationMs: Long? = null,
        val timestamp: Long = 0L,
        val readBy: List<String> = emptyList()
    ) : ServerFrame()

    @Serializable @SerialName("ack")
    data class MessageAck(val tempId: String = "", val id: String = "", val timestamp: Long = 0L) : ServerFrame()

    @Serializable @SerialName("typing")
    data class UserTyping(val userId: String = "", val userName: String = "", val isTyping: Boolean = true) : ServerFrame()

    @Serializable @SerialName("reaction_update")
    data class ReactionUpdate(
        val messageId: String = "",
        val userId: String = "",
        val emoji: String = "",
        val action: String = "" // "add" or "remove"
    ) : ServerFrame()

    @Serializable @SerialName("read_receipt")
    data class ReadReceipt(
        val messageId: String = "",
        val messageIds: List<String> = emptyList(),
        val userId: String = "",
        val timestamp: Long = 0L
    ) : ServerFrame()

    @Serializable @SerialName("presence")
    data class Presence(
        val userId: String = "",
        val status: String = "" // "online" or "offline"
    ) : ServerFrame()

    @Serializable @SerialName("error")
    data class Error(val message: String = "", val code: Int = 400) : ServerFrame()

    @Serializable @SerialName("connected")
    data class Connected(val conversationId: String = "", val userId: String = "") : ServerFrame()

    @Serializable @SerialName("location_update")
    data class LocationUpdate(
        val userId: String = "",
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val accuracy: Float = 0f,
        val timestamp: Long = 0L
    ) : ServerFrame()
}
