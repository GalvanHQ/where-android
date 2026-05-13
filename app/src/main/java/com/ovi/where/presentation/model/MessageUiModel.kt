package com.ovi.where.presentation.model

import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType

/** Whether this bubble renders as sent (right-aligned) or received (left-aligned). */
enum class BubbleDirection { SENT, RECEIVED }

/**
 * Presentation-layer representation of a single chat message.
 * Pre-computes all display concerns (time format, bubble direction, location label)
 * so the Composable is purely declarative.
 */
data class MessageUiModel(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val text: String,
    /** HH:mm formatted time shown under the bubble. */
    val formattedTime: String,
    /** yyyy-MM-dd key used to group messages under date separators. */
    val dateKey: String,
    val direction: BubbleDirection,
    val isLocation: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    /** Human-readable coordinates label for location bubbles. */
    val locationLabel: String?,
    /** Message delivery status for showing retry affordance on FAILED messages. */
    val status: MessageStatus = MessageStatus.SENT,
    // ─── Reactions (Task 11.4) ────────────────────────────────────────────────
    /** Aggregated reactions: emoji → list of user IDs who reacted. */
    val reactions: Map<String, List<String>> = emptyMap(),
    // ─── Read Receipts (Task 11.6) ────────────────────────────────────────────
    /** List of user IDs who have read this message (excluding sender). */
    val readBy: List<String> = emptyList(),
    /** Photo URLs of readers for avatar display (up to 3). */
    val readByPhotoUrls: List<String?> = emptyList(),
    // ─── Reply / Quote (Task 11.5) ────────────────────────────────────────────
    /** ID of the message this is replying to (null if not a reply). */
    val replyToId: String? = null,
    /** Sender name of the quoted message. */
    val replyToSenderName: String? = null,
    /** Text of the quoted message (truncated to 100 chars). */
    val replyToText: String? = null,
    // ─── Image / Media (Task 11.8) ────────────────────────────────────────────
    /** Whether this message is an image message. */
    val isImage: Boolean = false,
    /** URL of the full-size image. */
    val imageUrl: String? = null,
    /** URL of the thumbnail image. */
    val thumbnailUrl: String? = null,
    /** Upload progress 0-100 for pending image messages, null when not uploading. */
    val uploadProgress: Int? = null
)

fun Message.toUiModel(
    currentUserId: String,
    timeFormatter: (Long) -> String,
    dateKeyFormatter: (Long) -> String
): MessageUiModel {
    val direction = if (senderId == currentUserId) BubbleDirection.SENT else BubbleDirection.RECEIVED
    val isLocation = type == MessageType.LOCATION
    val isImage = type == MessageType.IMAGE
    val locationLabel = if (isLocation && latitude != null && longitude != null) {
        "%.4f, %.4f".format(latitude, longitude)
    } else null

    // Filter readBy to exclude the sender
    val filteredReadBy = readBy.filter { it != senderId }

    // Truncate reply text to 100 characters
    val truncatedReplyText = replyToText?.take(100)

    return MessageUiModel(
        id             = id,
        senderId       = senderId,
        senderName     = senderName,
        senderPhotoUrl = senderPhotoUrl,
        text           = when {
            isLocation -> "Shared a location"
            isImage -> ""
            else -> text
        },
        formattedTime  = timeFormatter(timestamp),
        dateKey        = dateKeyFormatter(timestamp),
        direction      = direction,
        isLocation     = isLocation,
        latitude       = latitude,
        longitude      = longitude,
        locationLabel  = locationLabel,
        status         = status,
        reactions      = reactions,
        readBy         = filteredReadBy,
        readByPhotoUrls = emptyList(), // Populated by ViewModel if needed
        replyToId      = replyToId,
        replyToSenderName = replyToSenderName,
        replyToText    = truncatedReplyText,
        isImage        = isImage,
        imageUrl       = imageUrl,
        thumbnailUrl   = thumbnailUrl,
        uploadProgress = null // Populated by ViewModel for pending uploads
    )
}
