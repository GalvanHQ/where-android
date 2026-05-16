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
    val readBy: List<String> = emptyList(),
    // Voice message
    val voiceUrl: String? = null,
    val voiceDurationMs: Long? = null,
    // Link preview
    val linkPreviewTitle: String? = null,
    val linkPreviewDescription: String? = null,
    val linkPreviewImageUrl: String? = null,
    val linkPreviewDomain: String? = null,
    val linkPreviewUrl: String? = null,
    // Mentions
    val mentionedUserIds: List<String> = emptyList(),
    // Live location sharing
    val locationSharingSessionId: String? = null,
    val locationSharingDurationMinutes: Long? = null,
    // Forward
    val forwardedFrom: String? = null
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
        MessageType.VOICE -> voiceUrl != null
        MessageType.LIVE_LOCATION -> locationSharingSessionId != null
        MessageType.VIDEO -> imageUrl != null // video uses imageUrl for the video URL
        MessageType.DOCUMENT -> text.isNotEmpty() // document uses text for filename
    }
}

enum class MessageType { TEXT, LOCATION, IMAGE, SYSTEM, VOICE, LIVE_LOCATION, VIDEO, DOCUMENT }

enum class MessageStatus { PENDING, SENT, DELIVERED, READ, FAILED }
