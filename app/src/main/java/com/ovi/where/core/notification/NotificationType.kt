package com.ovi.where.core.notification

/**
 * Enumeration of every notification type the app handles.
 *
 * Each type maps to:
 *  • a target screen via [NotificationHelper.resolveDeepLinkRoute],
 *  • a notification channel via [NotificationHelper.resolveChannelForType],
 *  • an icon + accent in the in-app inbox via [NotificationTypeUiSpec].
 *
 * **Adding a new type?** Update all three resolvers plus the Cloud Function
 * trigger that emits the FCM payload. The string constants in
 * [NotificationHelper] companion (TYPE_*) must mirror the lowercase value
 * used in the FCM `data.type` field — that value is what
 * [FcmMessagingService] dispatches on.
 */
enum class NotificationType {
    // Chat
    NEW_MESSAGE,
    MENTION,

    // Friends / social
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,

    // Group membership
    MEMBER_JOINED,
    MEMBER_LEFT,

    // Live location sharing
    LOCATION_UPDATE,
    LIVE_LOCATION_STARTED,
    LIVE_LOCATION_STOPPED,

    // Meetup destination
    MEETUP_DESTINATION_SET,
    MEETUP_DESTINATION_CLEARED,
    MEETUP_MEMBER_ARRIVED,

    // Fallback
    GENERAL;

    companion object {
        /** Maps an FCM `type` string to a [NotificationType]; defaults to [GENERAL]. */
        fun fromFcmType(type: String?): NotificationType = when (type) {
            "new_message" -> NEW_MESSAGE
            "mention" -> MENTION
            "friend_request" -> FRIEND_REQUEST
            "friend_accepted" -> FRIEND_ACCEPTED
            "member_joined" -> MEMBER_JOINED
            "member_left" -> MEMBER_LEFT
            "location_update" -> LOCATION_UPDATE
            "live_location_started" -> LIVE_LOCATION_STARTED
            "live_location_stopped" -> LIVE_LOCATION_STOPPED
            "meetup_destination_set" -> MEETUP_DESTINATION_SET
            "meetup_destination_cleared" -> MEETUP_DESTINATION_CLEARED
            "meetup_member_arrived" -> MEETUP_MEMBER_ARRIVED
            else -> GENERAL
        }
    }
}
