package com.ovi.where.core.notification

/**
 * Payload for a notification ready to be posted or persisted to the inbox.
 *
 * Fields are nullable because not every notification type uses every id —
 * see [NotificationType] for the per-type field expectations:
 *  • [NotificationType.NEW_MESSAGE] / [NotificationType.MENTION] →
 *    [conversationId], optional [groupId].
 *  • [NotificationType.FRIEND_REQUEST] / [NotificationType.FRIEND_ACCEPTED] →
 *    [userId].
 *  • [NotificationType.MEMBER_JOINED] / [NotificationType.MEMBER_LEFT] →
 *    [groupId], optional [userId].
 *  • [NotificationType.LIVE_LOCATION_STARTED] /
 *    [NotificationType.LIVE_LOCATION_STOPPED] → [userId] of the sharer +
 *    optional [groupId] / [conversationId] (direct share).
 *  • [NotificationType.MEETUP_DESTINATION_SET] /
 *    [NotificationType.MEETUP_DESTINATION_CLEARED] /
 *    [NotificationType.MEETUP_MEMBER_ARRIVED] → [groupId] +
 *    [userId] (the actor) + [destinationName].
 */
data class NotificationData(
    val title: String,
    val body: String,
    val targetId: String? = null,
    val conversationId: String? = null,
    val groupId: String? = null,
    val userId: String? = null,
    val destinationName: String? = null,
    val mentionedUserIds: List<String> = emptyList()
)
