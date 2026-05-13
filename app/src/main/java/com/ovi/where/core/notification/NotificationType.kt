package com.ovi.where.core.notification

/**
 * Enumeration of all notification types handled by the app.
 *
 * Each type maps to a specific deep-link route and notification channel.
 */
enum class NotificationType {
    NEW_MESSAGE,
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    MEMBER_JOINED,
    MEMBER_LEFT,
    LOCATION_UPDATE,
    GENERAL
}
