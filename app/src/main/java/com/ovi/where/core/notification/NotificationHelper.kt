package com.ovi.where.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ovi.where.MainActivity
import com.ovi.where.R
import com.ovi.where.data.repository.NotificationPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized singleton responsible for notification channel creation and posting.
 *
 * Channels are created on initialization (when the app process starts and Hilt
 * constructs this singleton). Posting checks POST_NOTIFICATIONS permission on
 * Android 13+ and silently discards the notification if permission is not granted.
 *
 * Additionally, posting checks per-channel user preferences from DataStore and
 * suppresses the notification if the target channel is disabled by the user.
 *
 * Requirements: 12.1, 12.2, 12.3, 12.6, 12.7
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: NotificationPreferencesRepository
) {

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    /** Tracks distinct conversation IDs with active notifications for grouping logic. */
    private val activeConversationIds: MutableSet<String> = mutableSetOf()

    init {
        createChannels()
    }

    // ── Channel Creation ──────────────────────────────────────────────────────

    /**
     * Creates all notification channels required by the app.
     * Safe to call multiple times — the system ignores re-creation of existing channels.
     *
     * Channels:
     * - messages: high importance (chat messages)
     * - social: default importance (friend requests, social activity)
     * - location_updates: high importance (location sharing notifications)
     * - group_activity: default importance (group membership changes)
     * - general: default importance (fallback for unrecognized types)
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New chat messages from friends and groups"
                enableVibration(true)
                enableLights(true)
            },
            NotificationChannel(
                CHANNEL_SOCIAL,
                "Friends & Social",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Friend requests and social activity"
            },
            NotificationChannel(
                CHANNEL_LOCATION_UPDATES,
                "Location Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when group members share their location"
            },
            NotificationChannel(
                CHANNEL_GROUP_ACTIVITY,
                "Group Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about group membership changes"
            },
            NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General notifications"
            }
        )

        val systemManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemManager.createNotificationChannels(channels)
    }

    // ── Posting ───────────────────────────────────────────────────────────────

    /**
     * Posts a notification after verifying POST_NOTIFICATIONS permission on Android 13+
     * and checking per-channel user preferences.
     *
     * If the permission has not been granted on Android 13+, the notification is
     * silently discarded without crashing (Requirement 12.3).
     *
     * If the target channel is disabled in user preferences, the notification is
     * suppressed without displaying it (Requirement 12.6).
     *
     * @param notificationId Unique ID for this notification (used for updates/cancellation).
     * @param channelId The notification channel ID to check preferences for.
     * @param notification The built [NotificationCompat.Builder] ready to post.
     */
    suspend fun postNotification(
        notificationId: Int,
        channelId: String,
        notification: NotificationCompat.Builder
    ) {
        if (!canPostNotifications()) {
            Timber.w("POST_NOTIFICATIONS permission not granted — discarding notification id=$notificationId")
            return
        }
        if (!preferencesRepository.isChannelEnabledSync(channelId)) {
            Timber.i("Channel '$channelId' disabled by user — suppressing notification id=$notificationId")
            return
        }
        notificationManager.notify(notificationId, notification.build())
    }

    /**
     * Posts a pre-built [android.app.Notification] after verifying permission and preferences.
     *
     * @param notificationId Unique ID for this notification.
     * @param channelId The notification channel ID to check preferences for.
     * @param notification The already-built notification object.
     */
    suspend fun postNotification(
        notificationId: Int,
        channelId: String,
        notification: android.app.Notification
    ) {
        if (!canPostNotifications()) {
            Timber.w("POST_NOTIFICATIONS permission not granted — discarding notification id=$notificationId")
            return
        }
        if (!preferencesRepository.isChannelEnabledSync(channelId)) {
            Timber.i("Channel '$channelId' disabled by user — suppressing notification id=$notificationId")
            return
        }
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Posts a notification without preference checking (fire-and-forget, non-suspend).
     * Only checks POST_NOTIFICATIONS permission.
     *
     * Use this for cases where preference checking is handled externally or not applicable.
     */
    fun postNotificationDirect(notificationId: Int, notification: NotificationCompat.Builder) {
        if (!canPostNotifications()) {
            Timber.w("POST_NOTIFICATIONS permission not granted — discarding notification id=$notificationId")
            return
        }
        notificationManager.notify(notificationId, notification.build())
    }

    /**
     * Posts a pre-built notification without preference checking (fire-and-forget, non-suspend).
     */
    fun postNotificationDirect(notificationId: Int, notification: android.app.Notification) {
        if (!canPostNotifications()) {
            Timber.w("POST_NOTIFICATIONS permission not granted — discarding notification id=$notificationId")
            return
        }
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Cancels an active notification by its ID.
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    // ── Deep-Link PendingIntents (Requirement 12.4) ───────────────────────────

    /**
     * Builds a [PendingIntent] that opens [MainActivity] and navigates to the
     * screen corresponding to the given [NotificationType].
     *
     * Mapping:
     * - NEW_MESSAGE      → Chat screen for [NotificationData.conversationId]
     * - FRIEND_REQUEST   → FriendRequests screen
     * - FRIEND_ACCEPTED  → UserProfile screen for [NotificationData.userId]
     * - MEMBER_JOINED    → GroupDetails screen for [NotificationData.groupId]
     * - MEMBER_LEFT      → GroupDetails screen for [NotificationData.groupId]
     * - LOCATION_UPDATE  → GroupMap screen for [NotificationData.groupId]
     * - GENERAL          → App default (no deep link)
     *
     * The route is delivered via [MainActivity.EXTRA_DEEP_LINK_ROUTE] string extra.
     *
     * @param type The notification type determining the target screen.
     * @param data The notification payload containing relevant IDs.
     * @param requestCode Unique code for the PendingIntent (defaults to type + targetId hash).
     * @return A [PendingIntent] that launches the correct screen on tap.
     */
    fun buildDeepLinkPendingIntent(
        type: NotificationType,
        data: NotificationData,
        requestCode: Int = buildRequestCode(type, data)
    ): PendingIntent {
        val route = resolveDeepLinkRoute(type, data)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) {
                putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, route)
            }
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Resolves the deep-link route string for a given notification type and data.
     *
     * @return The route string (e.g., "chat/abc123") or null for GENERAL type.
     */
    fun resolveDeepLinkRoute(type: NotificationType, data: NotificationData): String? {
        return when (type) {
            NotificationType.NEW_MESSAGE -> data.conversationId?.let { "chat/$it" }
            NotificationType.FRIEND_REQUEST -> "friend_requests"
            NotificationType.FRIEND_ACCEPTED -> data.userId?.let { "user_profile/$it" }
            NotificationType.MEMBER_JOINED -> data.groupId?.let { "group_details/$it" }
            NotificationType.MEMBER_LEFT -> data.groupId?.let { "group_details/$it" }
            NotificationType.LOCATION_UPDATE -> data.groupId?.let { "group_map/$it" }
            NotificationType.GENERAL -> null
        }
    }

    // ── Notification Grouping (Requirement 12.5) ──────────────────────────────

    /**
     * Posts a message notification grouped by [conversationId] using
     * [NotificationCompat.Builder.setGroup]. When 2 or more distinct conversations
     * have active notifications, a summary notification is also posted.
     *
     * @param conversationId The conversation this message belongs to.
     * @param data The notification payload (title, body, etc.).
     * @param notificationId Unique ID for this individual notification.
     */
    fun postGroupedMessageNotification(
        conversationId: String,
        data: NotificationData,
        notificationId: Int = conversationId.hashCode() xor data.body.hashCode()
    ) {
        if (!canPostNotifications()) {
            Timber.w("POST_NOTIFICATIONS permission not granted — discarding grouped notification")
            return
        }

        // Track active conversation IDs for summary logic
        activeConversationIds.add(conversationId)

        val pendingIntent = buildDeepLinkPendingIntent(
            type = NotificationType.NEW_MESSAGE,
            data = data.copy(conversationId = conversationId),
            requestCode = notificationId
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY_MESSAGES)
            .build()

        notificationManager.notify(notificationId, notification)

        // Post summary notification when 2+ distinct conversations are active
        if (activeConversationIds.size >= SUMMARY_THRESHOLD) {
            postSummaryNotification()
        }
    }

    /**
     * Removes a conversation from the active tracking set.
     * Call this when a conversation's notifications are dismissed or cleared.
     */
    fun removeConversationFromGroup(conversationId: String) {
        activeConversationIds.remove(conversationId)
    }

    /**
     * Clears all tracked active conversations.
     */
    fun clearActiveConversations() {
        activeConversationIds.clear()
    }

    /**
     * Returns the current number of distinct active conversations with notifications.
     */
    fun getActiveConversationCount(): Int = activeConversationIds.size

    /**
     * Posts a summary notification that groups all active message notifications.
     * Displayed when 2+ distinct conversations have active notifications.
     */
    private fun postSummaryNotification() {
        val summaryText = "${activeConversationIds.size} conversations"

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText(summaryText)
            )
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Generates a stable request code for PendingIntent uniqueness.
     */
    private fun buildRequestCode(type: NotificationType, data: NotificationData): Int {
        val targetId = when (type) {
            NotificationType.NEW_MESSAGE -> data.conversationId
            NotificationType.FRIEND_REQUEST -> "friend_requests"
            NotificationType.FRIEND_ACCEPTED -> data.userId
            NotificationType.MEMBER_JOINED -> data.groupId
            NotificationType.MEMBER_LEFT -> data.groupId
            NotificationType.LOCATION_UPDATE -> data.groupId
            NotificationType.GENERAL -> null
        }
        return (type.name + (targetId ?: "")).hashCode()
    }

    // ── Preference Check ──────────────────────────────────────────────────────

    /**
     * Checks whether the given channel is enabled in user preferences.
     * Returns true if enabled or if no preference has been set (default enabled).
     */
    suspend fun isChannelEnabled(channelId: String): Boolean {
        return preferencesRepository.isChannelEnabledSync(channelId)
    }

    // ── Permission Check ──────────────────────────────────────────────────────

    /**
     * Returns true if the app is allowed to post notifications.
     *
     * On Android 13+ (API 33), this checks the POST_NOTIFICATIONS runtime permission.
     * On earlier versions, notifications are always allowed (channels control visibility).
     */
    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ── Channel Resolution ────────────────────────────────────────────────────

    /**
     * Resolves the appropriate notification channel for a given FCM message type.
     *
     * If the type is unrecognized, returns [CHANNEL_GENERAL] (Requirement 12.7).
     */
    fun resolveChannelForType(type: String?): String {
        return when (type) {
            TYPE_NEW_MESSAGE -> CHANNEL_MESSAGES
            TYPE_FRIEND_REQUEST, TYPE_FRIEND_ACCEPTED -> CHANNEL_SOCIAL
            TYPE_LOCATION_UPDATE -> CHANNEL_LOCATION_UPDATES
            TYPE_MEMBER_JOINED, TYPE_MEMBER_LEFT -> CHANNEL_GROUP_ACTIVITY
            else -> CHANNEL_GENERAL
        }
    }

    companion object {
        // Notification channel IDs
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_SOCIAL = "social"
        const val CHANNEL_LOCATION_UPDATES = "location_updates"
        const val CHANNEL_GROUP_ACTIVITY = "group_activity"
        const val CHANNEL_GENERAL = "general"

        // FCM message type values (mirrored from FcmMessagingService for resolution)
        const val TYPE_NEW_MESSAGE = "new_message"
        const val TYPE_FRIEND_REQUEST = "friend_request"
        const val TYPE_FRIEND_ACCEPTED = "friend_accepted"
        const val TYPE_LOCATION_UPDATE = "location_update"
        const val TYPE_MEMBER_JOINED = "member_joined"
        const val TYPE_MEMBER_LEFT = "member_left"

        // Notification grouping
        const val GROUP_KEY_MESSAGES = "com.ovi.where.GROUP_MESSAGES"
        const val SUMMARY_NOTIFICATION_ID = 9999
        const val SUMMARY_THRESHOLD = 2
    }
}
