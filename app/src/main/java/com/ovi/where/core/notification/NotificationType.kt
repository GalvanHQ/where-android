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
     * Most apps in this category (WhatsApp, Telegram, Messenger) keep the
     * in-app notifications surface for *high-signal, action-required, or
     * one-shot* events and let the system tray + the relevant primary tab
     * carry the noisy ephemeral stuff.
     *
     * Concretely:
     *  • **Friend requests / accepted** — action-required, one-shot.
     *  • **Meetup destination set** — group coordination signal, one per session.
     *  • **Meetup member arrived** — high-signal moment.
     *
     * Everything else (chat messages, member join/leave, live location
     * start/stop/update, meetup cleared, GENERAL) is intentionally excluded:
     *  • *Chat messages and mentions* live on the Chats tab.
     *  • *Live location* events are visible on the map in real time.
     *  • *Member join/leave* is a low-action, high-frequency social event.
     *  • *Meetup cleared* is paired with *set*, so seeing both is redundant.
     *  • *Location update* fires per GPS tick — would dominate the inbox.
     *
     * The system tray push is **independent** from this flag — those still
     * fire normally (subject to user channel preferences). Only the
     * persisted inbox row is suppressed for non-important types, which
     * also saves Firestore writes server-side (see `notify.ts`).
     */
    val isInboxImportant: Boolean
        get() = when (this) {
            FRIEND_REQUEST,
            FRIEND_ACCEPTED,
            MEETUP_DESTINATION_SET,
            MEETUP_MEMBER_ARRIVED -> true

            NEW_MESSAGE,
            MENTION,
            MEMBER_JOINED,
            MEMBER_LEFT,
            LOCATION_UPDATE,
            LIVE_LOCATION_STARTED,
            LIVE_LOCATION_STOPPED,
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
