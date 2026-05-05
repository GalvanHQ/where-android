package com.ovi.where.presentation.model

import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType

/**
 * Presentation-layer representation of a chat conversation.
 * All display formatting is pre-computed here so screens contain zero business logic.
 */
data class ConversationUiModel(
    val id: String,
    val title: String,           // resolved display name (partner name for DM, group name for group)
    val lastMessageText: String,
    val lastMessageTime: String, // formatted: "14:32" / "Yesterday" / "12 Jan"
    val unreadCount: Int,
    val photoUrl: String?,
    val isGroup: Boolean,
    val groupId: String?,        // non-null only for group conversations
    val currentUserId: String    // kept for unread count lookup in mapper
)

fun Conversation.toUiModel(currentUserId: String, timeFormatter: (Long) -> String): ConversationUiModel {
    val unread = (unreadCounts[currentUserId] as? Int)
        ?: (unreadCounts[currentUserId] as? Long)?.toInt()
        ?: 0
    return ConversationUiModel(
        id               = id,
        title            = name.ifBlank { "Chat" },
        lastMessageText  = lastMessageText.ifEmpty { "No messages yet" },
        lastMessageTime  = if (lastMessageTimestamp > 0) timeFormatter(lastMessageTimestamp) else "",
        unreadCount      = unread,
        photoUrl         = photoUrl,
        isGroup          = type == ConversationType.GROUP,
        groupId          = groupId,
        currentUserId    = currentUserId
    )
}
