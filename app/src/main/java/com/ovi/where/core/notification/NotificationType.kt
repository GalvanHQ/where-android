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

    /**
     * Whether this type is worth persisting into the in-app inbox.
     *
     * Curated tight to the **Facebook model** plus one meetup signal:
     * only friend-shaped events and the moment-of-truth meetup arrival
     * earn an inbox row. Everything else is either visible elsewhere
     * in the product (Chats tab, the map itself) or ephemeral enough
     * that persisting it would just burn Firestore writes.
     *
     * Concrete keep-list:
     *  • [FRIEND_REQUEST]        — action-required, one-shot, social.
     *  • [FRIEND_ACCEPTED]       — confirmation, social signal.
     *  • [MEETUP_MEMBER_ARRIVED] — high-signal "made it" moment that the
     *    user often misses on the live map (phone in pocket, app
     *    backgrounded). Persisting it lets people scroll back later.
     *
     * Everything else returns false:
     *  • Chat / mention → Chats tab + system tray.
     *  • Member join/left → low-action group churn.
     *  • Live location start/stop/update → visible on the map in real time.
     *  • Meetup set/cleared → the meetup card on the map and the chat
     *    sheet are the canonical surface; an inbox row about a meetup
     *    someone already saw two seconds earlier is just noise.
     *  • GENERAL → catch-all, almost always low-signal.
     *
     * The system tray push is **independent** from this flag — those still
     * fire normally (subject to user channel preferences). Only the
     * persisted inbox row is suppressed for non-important types, which
     * directly cuts our Firestore write cost (the inbox doc is the
     * highest-frequency write per recipient — see `notify.ts`).
     */
    val isInboxImportant: Boolean
        get() = when (this) {
            FRIEND_REQUEST,
            FRIEND_ACCEPTED,
            MEETUP_MEMBER_ARRIVED -> true

            NEW_MESSAGE,
            MENTION,
            MEMBER_JOINED,
            MEMBER_LEFT,
            LOCATION_UPDATE,
            LIVE_LOCATION_STARTED,
            LIVE_LOCATION_STOPPED,
            MEETUP_DESTINATION_SET,
            MEETUP_DESTINATION_CLEARED,
            GENERAL -> false
        }

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
