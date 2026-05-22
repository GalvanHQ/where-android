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
    val forwardedFrom: String? = null,
    // System messages (group renamed, member added, etc.) — only set when type == SYSTEM.
    // See spec/group-system-messages.
    val systemEventType: SystemEventType? = null,
    val systemEventPayload: Map<String, String> = emptyMap(),
    val targetUserId: String? = null
) {
    /**
     * Validates this message based on its type.
     * Returns true if the message is valid, false otherwise.
     */
    fun isValid(): Boolean = when (type) {
        MessageType.TEXT -> text.isNotEmpty()
        MessageType.LOCATION -> latitude != null && longitude != null
        MessageType.IMAGE -> imageUrl != null
        MessageType.SYSTEM -> systemEventType != null
        MessageType.VOICE -> voiceUrl != null
        MessageType.LIVE_LOCATION -> locationSharingSessionId != null
        MessageType.VIDEO -> imageUrl != null // video uses imageUrl for the video URL
        MessageType.DOCUMENT -> text.isNotEmpty() // document uses text for filename
    }
}

enum class MessageType { TEXT, LOCATION, IMAGE, SYSTEM, VOICE, LIVE_LOCATION, VIDEO, DOCUMENT }

enum class MessageStatus { PENDING, SENT, DELIVERED, READ, FAILED }

/**
 * Discriminator for [MessageType.SYSTEM] messages. Each value corresponds to
 * one Cloud Functions trigger that authors the system message in response to
 * a Firestore data change. Stored as a string in Room and Firestore.
 *
 * See `.kiro/specs/group-system-messages/` for the full event matrix.
 */
enum class SystemEventType {
    GROUP_RENAMED,
    GROUP_DESCRIPTION_CHANGED,
    GROUP_PHOTO_CHANGED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    MEMBER_LEFT,
    MEMBER_JOINED,
    MEMBER_PROMOTED,
    MEMBER_DEMOTED,
    NICKNAME_CHANGED,
    THEME_COLOR_CHANGED,
    EMOJI_SHORTCUT_CHANGED,
    LIVE_LOCATION_STARTED,
    LOCATION_SHARED,
    USER_BLOCKED;

    companion object {
        /** Safe parse from a string; returns null on unknown values (forward-compat). */
        fun fromStringOrNull(value: String?): SystemEventType? = value?.let {
            try {
                valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
