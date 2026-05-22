package com.ovi.where.presentation.model

import androidx.compose.runtime.Immutable
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.model.SharedLocation

/**
 * Presentation-layer representation of a chat conversation.
 * All display formatting is pre-computed here so screens contain zero business logic.
 *
 * Annotated with @Immutable to guarantee Compose skips recomposition when the instance
 * is structurally equal. All properties are val with stable types.
 */
@Immutable
data class ConversationUiModel(
    val id: String,
    val title: String,           // resolved display name (partner name for DM, group name for group)
    val lastMessageText: String,
    val lastMessageTime: String, // formatted: "14:32" / "Yesterday" / "12 Jan"
    val unreadCount: Int,
    val photoUrl: String?,
    val isGroup: Boolean,
    val groupId: String?,        // non-null only for group conversations
    val currentUserId: String,   // kept for unread count lookup in mapper
    /** Number of members in the conversation (relevant for group chats). */
    val memberCount: Int = 0,
    /** Whether the other user in a 1:1 conversation is currently online. */
    val isOtherUserOnline: Boolean = false,
    /**
     * Last-seen timestamp (epoch millis) for the other user in a 1:1 conversation.
     * Used to render "Active 5m ago" / "Active 2h ago" subtitles when offline.
     * `0L` means unknown — show plain "Offline" instead.
     */
    val otherUserLastSeen: Long = 0L,
    /** The other user's ID in a 1:1 conversation (null for groups). */
    val otherUserId: String? = null,
    /** Whether any member in this conversation has active location sharing (Req 3.1). */
    val hasActiveLocationSharing: Boolean = false,
    /** Location sharing preview text to override lastMessageText (Req 3.2-3.4). */
    val locationSharingPreview: String? = null,
    /** Whether this conversation is pinned by the current user (Req 24.4). */
    val isPinned: Boolean = false,
    /** Whether this conversation is muted by the current user (Req 24.5). */
    val isMuted: Boolean = false,
    /** The type of the last message for media preview formatting (Req 24.1). */
    val lastMessageType: MessageType = MessageType.TEXT,
    /** The status of the last message sent by the current user (Req 24.2). */
    val lastMessageStatus: MessageStatus? = null,
    /** Whether the last message was sent by the current user (Req 24.2). */
    val isLastMessageFromCurrentUser: Boolean = false,
    /** Theme color hex string for this conversation (e.g., "#5170FF"). */
    val themeColor: String? = null,
    /** Emoji shortcut for quick reactions in this conversation. */
    val emojiShortcut: String? = null,
    /** Nicknames map: userId -> nickname. */
    val nicknames: Map<String, String> = emptyMap()
)

fun Conversation.toUiModel(
    currentUserId: String,
    timeFormatter: (Long) -> String,
    participantNames: Map<String, String> = emptyMap(),
    participantPhotos: Map<String, String?> = emptyMap(),
    onlineUserIds: Set<String> = emptySet(),
    lastSeenByUser: Map<String, Long> = emptyMap()
): ConversationUiModel {
    val unread = (unreadCounts[currentUserId] as? Int)
        ?: (unreadCounts[currentUserId] as? Long)?.toInt()
        ?: 0
    val otherUid = if (type == ConversationType.DIRECT) {
        participantIds.firstOrNull { it != currentUserId }
    } else null
    val otherOnline = if (type == ConversationType.DIRECT && otherUid != null) {
        otherUid in onlineUserIds || otherUid in onlineMembers
    } else false
    val otherLastSeen = if (type == ConversationType.DIRECT && otherUid != null) {
        lastSeenByUser[otherUid] ?: 0L
    } else 0L
    val isOwnLastMessage = lastMessageSenderId == currentUserId

    val resolvedTitle = resolveConversationTitle(
        name = name,
        type = type,
        otherUserId = otherUid,
        participantNames = participantNames,
        nicknames = nicknames
    )

    // Resolve photo URL: for DMs with no photo, use the other participant's profile photo
    val resolvedPhotoUrl = if (type == ConversationType.DIRECT && photoUrl.isNullOrBlank() && otherUid != null) {
        participantPhotos[otherUid] ?: photoUrl
    } else {
        photoUrl
    }

    return ConversationUiModel(
        id               = id,
        title            = resolvedTitle,
        lastMessageText  = formatLastMessagePreview(lastMessageType, lastMessageText),
        lastMessageTime  = if (lastMessageTimestamp > 0) timeFormatter(lastMessageTimestamp) else "",
        unreadCount      = unread,
        photoUrl         = resolvedPhotoUrl,
        isGroup          = type == ConversationType.GROUP,
        groupId          = groupId,
        currentUserId    = currentUserId,
        memberCount      = participantIds.size,
        isOtherUserOnline = otherOnline,
        otherUserLastSeen = otherLastSeen,
        otherUserId      = otherUid,
        isPinned         = currentUserId in pinnedBy,
        isMuted          = currentUserId in mutedBy,
        lastMessageType  = lastMessageType,
        lastMessageStatus = if (isOwnLastMessage) lastMessageStatus else null,
        isLastMessageFromCurrentUser = isOwnLastMessage,
        themeColor       = themeColor,
        emojiShortcut    = emojiShortcut,
        nicknames        = nicknames
    )
}

/**
 * Resolves the display title for a conversation.
 *
 * Resolution order:
 * 1. For DMs: use nickname for the other user if set.
 * 2. Use the conversation [name] if it is not blank.
 * 3. For direct messages, resolve from [participantNames] using [otherUserId].
 * 4. Fall back to "Unknown User" for direct messages or "Unnamed Group" for group conversations.
 */
internal fun resolveConversationTitle(
    name: String,
    type: ConversationType,
    otherUserId: String?,
    participantNames: Map<String, String> = emptyMap(),
    nicknames: Map<String, String> = emptyMap()
): String {
    // For DMs, check nickname first
    if (type == ConversationType.DIRECT && otherUserId != null) {
        val nickname = nicknames[otherUserId]
        if (!nickname.isNullOrBlank()) return nickname
    }

    // Use the conversation name if it's not blank
    if (name.isNotBlank()) return name

    // For DMs, try to resolve from participant metadata
    if (type == ConversationType.DIRECT && otherUserId != null) {
        val participantName = participantNames[otherUserId]
        if (!participantName.isNullOrBlank()) return participantName
    }

    // Final fallback based on conversation type
    return when (type) {
        ConversationType.DIRECT -> "Unknown User"
        ConversationType.GROUP -> "Unnamed Group"
    }
}

/**
 * Formats the last message preview text based on message type (Req 24.1).
 * Media types get emoji-prefixed descriptors; text messages show raw content.
 */
private fun formatLastMessagePreview(type: MessageType, text: String): String {
    return when (type) {
        MessageType.IMAGE -> "📷 Photo"
        MessageType.VOICE -> "🎤 Voice message"
        // Both location types now render as system info lines in the timeline,
        // so reuse the actual fallback text written into lastMessageText
        // instead of a generic icon. Falls back to a friendly default if blank.
        MessageType.LOCATION -> text.ifEmpty { "📍 Location" }
        MessageType.LIVE_LOCATION -> text.ifEmpty { "📍 Live location" }
        MessageType.VIDEO -> "🎥 Video"
        MessageType.DOCUMENT -> "📄 Document"
        MessageType.SYSTEM -> text.ifEmpty { "Group activity" }
        else -> text.ifEmpty { "No messages yet" }
    }
}

/**
 * Maps a Conversation to ConversationUiModel with location sharing status.
 * Computes hasActiveLocationSharing and locationSharingPreview based on active locations
 * associated with this conversation's groupId (Req 3.1-3.6).
 */
fun Conversation.toUiModel(
    currentUserId: String,
    timeFormatter: (Long) -> String,
    activeLocations: List<SharedLocation>,
    participantNames: Map<String, String> = emptyMap(),
    participantPhotos: Map<String, String?> = emptyMap(),
    onlineUserIds: Set<String> = emptySet(),
    lastSeenByUser: Map<String, Long> = emptyMap()
): ConversationUiModel {
    val base = toUiModel(currentUserId, timeFormatter, participantNames, participantPhotos, onlineUserIds, lastSeenByUser)

    // Match active locations to this conversation by groupId
    val conversationLocations = if (groupId != null) {
        activeLocations.filter { it.groupId == groupId && it.isSharingActive }
    } else {
        emptyList()
    }

    if (conversationLocations.isEmpty()) return base

    // Determine location sharing preview text
    val currentUserSharing = conversationLocations.any { it.userId == currentUserId }
    val othersSharing = conversationLocations.filter { it.userId != currentUserId }

    val preview = when {
        // Req 3.2: Current user sharing takes precedence
        currentUserSharing -> "📍 Sharing live location"
        // Req 3.3: Single other user sharing
        othersSharing.size == 1 -> "📍 ${othersSharing.first().displayName} is sharing location"
        // Req 3.4: Multiple others sharing — most recently started first
        othersSharing.size > 1 -> {
            val mostRecent = othersSharing.maxByOrNull { it.sharingStartedAt } ?: othersSharing.first()
            val count = othersSharing.size - 1
            "📍 ${mostRecent.displayName} and $count others sharing location"
        }
        else -> null
    }

    return base.copy(
        hasActiveLocationSharing = true,
        locationSharingPreview = preview
    )
}
