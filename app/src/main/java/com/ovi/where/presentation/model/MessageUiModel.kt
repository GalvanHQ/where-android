package com.ovi.where.presentation.model

import androidx.compose.runtime.Immutable
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/** Whether this bubble renders as sent (right-aligned) or received (left-aligned). */
enum class BubbleDirection { SENT, RECEIVED }

/**
 * Presentation-layer representation of a single chat message.
 * Pre-computes all display concerns (time format, bubble direction, location label,
 * sender initials) so the Composable is purely declarative.
 *
 * Annotated with @Immutable to guarantee Compose skips recomposition when the instance
 * is structurally equal. All properties are val and collections use ImmutableList/ImmutableMap.
 */
@Immutable
data class MessageUiModel(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    /** Pre-computed sender initials for avatar fallback (e.g. "JD" for "John Doe"). */
    val senderInitials: String,
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
    val reactions: ImmutableMap<String, ImmutableList<String>> = persistentMapOf(),
    // ─── Read Receipts (Task 11.6) ────────────────────────────────────────────
    /** List of user IDs who have read this message (excluding sender). */
    val readBy: ImmutableList<String> = persistentListOf(),
    /** Photo URLs of readers for avatar display (up to 3). */
    val readByPhotoUrls: ImmutableList<String?> = persistentListOf(),
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
    val uploadProgress: Int? = null,
    // ─── Voice Message (Task 6.2) ─────────────────────────────────────────────
    /** Whether this message is a voice message. */
    val isVoice: Boolean = false,
    /** URL of the voice audio file. */
    val voiceUrl: String? = null,
    /** Duration of the voice message in milliseconds. */
    val voiceDurationMs: Long? = null,
    // ─── Link Preview (Task 7.1) ──────────────────────────────────────────────
    /** Whether this message has a link preview to display. */
    val hasLinkPreview: Boolean = false,
    /** The URL that the link preview is for. */
    val linkPreviewUrl: String? = null,
    /** Title from Open Graph metadata (max 80 chars with ellipsis). */
    val linkPreviewTitle: String? = null,
    /** Description from Open Graph metadata. */
    val linkPreviewDescription: String? = null,
    /** Thumbnail image URL from Open Graph metadata. */
    val linkPreviewImageUrl: String? = null,
    /** Domain name extracted from the URL. */
    val linkPreviewDomain: String? = null,
    // ─── Mentions (Task 9.1) ──────────────────────────────────────────────────
    /** List of mentioned user IDs in this message (Requirement 14.3, 14.4). */
    val mentionedUserIds: ImmutableList<String> = persistentListOf(),
    // ─── Message Grouping Metadata (Requirements 4.6, 4.7, 10.3) ─────────────
    /** Whether this message is the first in a sender group (same sender within 2 min). */
    val isFirstInGroup: Boolean = true,
    /** Whether this message is the last in a sender group (same sender within 2 min). */
    val isLastInGroup: Boolean = true,
    /** Whether to show the timestamp below this message (Messenger-style: only after time gaps). */
    val showTimestamp: Boolean = true,
    /** Whether a date separator should be shown above this message. */
    val showDateSeparator: Boolean = false,
    /** Label for the date separator ("Today", "Yesterday", or formatted date). */
    val dateSeparatorLabel: String? = null,
    // ─── Image Collage Metadata ──────────────────────────────────────────────
    /** For consecutive image messages: IDs of all images in this collage group (only set on first). */
    val imageCollageUrls: List<String> = emptyList(),
    /** Whether this image message should be hidden (it's rendered as part of a collage on a previous item). */
    val isHiddenInCollage: Boolean = false,
    /**
     * Whether this is the furthest-read sent message — the single message where the
     * read receipt avatar should render. Messenger only shows the avatar under the
     * most recent sent message that has been read by the other party.
     */
    val showReadReceipt: Boolean = false,
    /**
     * Whether this is the absolute last sent message in the conversation — the only
     * message that shows the status circle (pending/sent/delivered) when not yet read.
     */
    val showStatusIndicator: Boolean = false
)

/**
 * Computes sender initials from a display name.
 * Takes the first character of the first two words (e.g. "John Doe" → "JD").
 * Falls back to the first character if only one word.
 */
private fun computeSenderInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex())
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        parts.isNotEmpty() && parts[0].isNotEmpty() -> "${parts[0].first().uppercaseChar()}"
        else -> "?"
    }
}

fun Message.toUiModel(
    currentUserId: String,
    timeFormatter: (Long) -> String,
    dateKeyFormatter: (Long) -> String,
    nicknames: Map<String, String> = emptyMap()
): MessageUiModel {
    val direction = if (senderId == currentUserId) BubbleDirection.SENT else BubbleDirection.RECEIVED
    val isLocation = type == MessageType.LOCATION
    val isImage = type == MessageType.IMAGE
    val isVoice = type == MessageType.VOICE
    val locationLabel = if (isLocation && latitude != null && longitude != null) {
        "%.4f, %.4f".format(latitude, longitude)
    } else null

    // Resolve sender name: use nickname if available, otherwise fall back to senderName
    val resolvedSenderName = nicknames[senderId]?.takeIf { it.isNotBlank() } ?: senderName

    // Pre-compute sender initials for avatar fallback
    val initials = computeSenderInitials(resolvedSenderName)

    // Filter readBy to exclude the sender
    val filteredReadBy = readBy.filter { it != senderId }

    // Truncate reply text to 100 characters
    val truncatedReplyText = replyToText?.take(100)

    // Convert reactions map to ImmutableMap<String, ImmutableList<String>>
    val immutableReactions = reactions.mapValues { (_, userIds) ->
        userIds.toImmutableList()
    }.toImmutableMap()

    return MessageUiModel(
        id             = id,
        senderId       = senderId,
        senderName     = resolvedSenderName,
        senderPhotoUrl = senderPhotoUrl,
        senderInitials = initials,
        text           = when {
            isLocation -> "Shared a location"
            isImage -> ""
            isVoice -> ""
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
        reactions      = immutableReactions,
        readBy         = filteredReadBy.toImmutableList(),
        readByPhotoUrls = persistentListOf(), // Populated by ViewModel if needed
        replyToId      = replyToId,
        replyToSenderName = replyToSenderName,
        replyToText    = truncatedReplyText,
        isImage        = isImage,
        imageUrl       = imageUrl,
        thumbnailUrl   = thumbnailUrl,
        uploadProgress = null, // Populated by ViewModel for pending uploads
        isVoice        = isVoice,
        voiceUrl       = voiceUrl,
        voiceDurationMs = voiceDurationMs,
        // Link preview: pre-compute whether preview exists and truncate title to 80 chars
        hasLinkPreview = linkPreviewUrl != null && linkPreviewTitle != null,
        linkPreviewUrl = linkPreviewUrl,
        linkPreviewTitle = linkPreviewTitle?.let {
            if (it.length > 80) it.take(80) + "…" else it
        },
        linkPreviewDescription = linkPreviewDescription,
        linkPreviewImageUrl = linkPreviewImageUrl,
        linkPreviewDomain = linkPreviewDomain,
        // Mentions: pass through for rendering in primary color + bold (Requirement 14.4)
        mentionedUserIds = mentionedUserIds.toImmutableList()
    )
}
