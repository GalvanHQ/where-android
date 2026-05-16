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
    val isLastMessageFromCurrentUser: Boolean = false
)

fun Conversation.toUiModel(currentUserId: String, timeFormatter: (Long) -> String): ConversationUiModel {
    val unread = (unreadCounts[currentUserId] as? Int)
        ?: (unreadCounts[currentUserId] as? Long)?.toInt()
        ?: 0
    val otherUid = if (type == ConversationType.DIRECT) {
        participantIds.firstOrNull { it != currentUserId }
    } else null
    val otherOnline = if (type == ConversationType.DIRECT && otherUid != null) {
        otherUid in onlineMembers
    } else false
    val isOwnLastMessage = lastMessageSenderId == currentUserId
    return ConversationUiModel(
        id               = id,
        title            = name.ifBlank { "Chat" },
        lastMessageText  = formatLastMessagePreview(lastMessageType, lastMessageText),
        lastMessageTime  = if (lastMessageTimestamp > 0) timeFormatter(lastMessageTimestamp) else "",
        unreadCount      = unread,
        photoUrl         = photoUrl,
        isGroup          = type == ConversationType.GROUP,
        groupId          = groupId,
        currentUserId    = currentUserId,
        memberCount      = participantIds.size,
        isOtherUserOnline = otherOnline,
        otherUserId      = otherUid,
        isPinned         = currentUserId in pinnedBy,
        isMuted          = currentUserId in mutedBy,
        lastMessageType  = lastMessageType,
        lastMessageStatus = if (isOwnLastMessage) lastMessageStatus else null,
        isLastMessageFromCurrentUser = isOwnLastMessage
    )
}

/**
 * Formats the last message preview text based on message type (Req 24.1).
 * Media types get emoji-prefixed descriptors; text messages show raw content.
 */
private fun formatLastMessagePreview(type: MessageType, text: String): String {
    return when (type) {
        MessageType.IMAGE -> "📷 Photo"
        MessageType.VOICE -> "🎤 Voice message"
        MessageType.LOCATION -> "📍 Location"
        MessageType.LIVE_LOCATION -> "📍 Location"
        MessageType.VIDEO -> "🎥 Video"
        MessageType.DOCUMENT -> "📄 Document"
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
    activeLocations: List<SharedLocation>
): ConversationUiModel {
    val base = toUiModel(currentUserId, timeFormatter)

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
