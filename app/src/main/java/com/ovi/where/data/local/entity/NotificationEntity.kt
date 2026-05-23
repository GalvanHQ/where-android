package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of notifications shown to the user.
 *
 * Why Room? FCM notifications are ephemeral — once the user dismisses the
 * system tray entry, the payload is gone. The in-app inbox needs durable
 * history so users can browse what they missed even after a clear-all.
 *
 * One row per notification. The unread badge on the bell chip in the map
 * scaffold is `count(*)` where `isRead = 0`. Old entries are pruned by
 * [com.ovi.where.data.repository.NotificationRepository.prune].
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    /** Stable id — typically `${type}_${targetId}_${timestamp}` from the FCM payload. */
    @PrimaryKey
    val id: String,
    /** Matches [com.ovi.where.core.notification.NotificationType] name(). */
    val type: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    /** Resolved deep-link route, populated on receive so taps don't recompute. */
    val deepLinkRoute: String? = null,
    /** Optional ids that the deep-link / tap handler may need. */
    val conversationId: String? = null,
    val groupId: String? = null,
    val userId: String? = null,
    /** Display name of the destination for meetup notifications. */
    val destinationName: String? = null
)
