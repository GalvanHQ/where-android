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
    /** Generic identifier — used by deep-links if no more specific id is set. */
    val targetId: String? = null,
    val conversationId: String? = null,
    val groupId: String? = null,
    val userId: String? = null,
    /** Display name of the destination for meetup notifications. */
    val destinationName: String? = null,
    /** Mention metadata: ids of users explicitly @mentioned in the source message. */
    val mentionedUserIds: List<String> = emptyList()
)
