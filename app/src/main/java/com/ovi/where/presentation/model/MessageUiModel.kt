package com.ovi.where.presentation.model

import com.ovi.where.domain.model.Message
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
    val locationLabel: String?
)

fun Message.toUiModel(
    currentUserId: String,
    timeFormatter: (Long) -> String,
    dateKeyFormatter: (Long) -> String
): MessageUiModel {
    val direction = if (senderId == currentUserId) BubbleDirection.SENT else BubbleDirection.RECEIVED
    val isLocation = type == MessageType.LOCATION
    val locationLabel = if (isLocation && latitude != null && longitude != null) {
        "%.4f, %.4f".format(latitude, longitude)
    } else null

    return MessageUiModel(
        id             = id,
        senderId       = senderId,
        senderName     = senderName,
        senderPhotoUrl = senderPhotoUrl,
        text           = if (isLocation) "📍 Shared a location" else text,
        formattedTime  = timeFormatter(timestamp),
        dateKey        = dateKeyFormatter(timestamp),
        direction      = direction,
        isLocation     = isLocation,
        latitude       = latitude,
        longitude      = longitude,
        locationLabel  = locationLabel
    )
}
