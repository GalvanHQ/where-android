package com.ovi.where.domain.model

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = 0L,
    val status: MessageStatus = MessageStatus.SENT,
    // Location
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Media
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    // Reply
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    // Reactions: emoji -> list of userIds who reacted with that emoji
    val reactions: Map<String, List<String>> = emptyMap(),
    // Read receipts
    val readBy: List<String> = emptyList()
) {
    /**
     * Validates this message based on its type.
     * Returns true if the message is valid, false otherwise.
     */
    fun isValid(): Boolean = when (type) {
        MessageType.TEXT -> text.isNotEmpty()
        MessageType.LOCATION -> latitude != null && longitude != null
        MessageType.IMAGE -> imageUrl != null
        MessageType.SYSTEM -> true
    }
}

enum class MessageType { TEXT, LOCATION, IMAGE, SYSTEM }

enum class MessageStatus { PENDING, SENT, DELIVERED, READ, FAILED }
