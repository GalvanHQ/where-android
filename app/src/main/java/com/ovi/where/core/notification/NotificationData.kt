package com.ovi.where.core.notification

/**
 * Data class carrying the payload for a notification to be posted.
 *
 * Fields are nullable because not every notification type requires all IDs.
 * For example, a FRIEND_REQUEST only needs the title/body, while a NEW_MESSAGE
 * needs [conversationId].
 */
data class NotificationData(
    val title: String,
    val body: String,
    val targetId: String? = null,
    val conversationId: String? = null,
    val groupId: String? = null,
    val userId: String? = null
)
