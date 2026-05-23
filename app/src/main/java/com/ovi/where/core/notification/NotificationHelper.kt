package com.ovi.where.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
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
import androidx.core.net.toUri

/**
 * Centralized notification helper.
 *
 * Responsibilities:
 *  • Create the system notification channels at process start.
 *  • Resolve `(NotificationType, NotificationData)` pairs into channel ids,
 *    deep-link routes, and ready-to-fire [PendingIntent]s.
 *  • Post notifications, gating on POST_NOTIFICATIONS (Android 13+),
 *    per-channel user preferences, and active-screen suppression
 *    (open chat / visible map).
 *  • Manage message-grouping with summary support.
 *
 * Channels (one per logical category):
 *
 *  | id                | name              | importance | UX intent              |
 *  |-------------------|-------------------|------------|------------------------|
 *  | messages          | Messages          | HIGH       | chat + @mentions       |
 *  | social            | Friends & Social  | DEFAULT    | friend requests        |
 *  | location_updates  | Location Updates  | HIGH       | live location sharing  |
 *  | group_activity    | Group Activity    | DEFAULT    | member join / leave    |
 *  | meetup            | Meetup            | HIGH       | destination + arrivals |
 *  | general           | General           | DEFAULT    | fallback               |
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: NotificationPreferencesRepository,
    private val activeConversationTracker: ActiveConversationTracker,
    private val activeMapTracker: ActiveMapTracker,
    private val chatNotificationStyler: ChatNotificationStyler,
    private val conversationShortcutManager: ConversationShortcutManager,
    private val quietHoursRepository: com.ovi.where.data.repository.QuietHoursRepository,
    private val closeFriendsRepository: com.ovi.where.data.repository.CloseFriendsRepository
) {

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    /** Active conversations with on-screen pushes — drives summary creation. */
    private val activeConversationIds: MutableSet<String> = mutableSetOf()

    init {
        createChannels()
    }

    // ── Channel creation ──────────────────────────────────────────────────

    /**
     * Creates every channel the app uses. Safe to call multiple times — the
     * system de-duplicates by channel id.
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_messages_desc)
                enableVibration(true)
                enableLights(true)
            },
            NotificationChannel(
                CHANNEL_SOCIAL,
                context.getString(R.string.notification_channel_social),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_social_desc)
            },
            NotificationChannel(
                CHANNEL_LOCATION_UPDATES,
                context.getString(R.string.notification_channel_location_updates),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_location_updates_desc)
            },
            NotificationChannel(
                CHANNEL_GROUP_ACTIVITY,
                context.getString(R.string.notification_channel_group_activity),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_group_activity_desc)
            },
            NotificationChannel(
                CHANNEL_MEETUP,
                context.getString(R.string.notification_channel_meetup),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_meetup_desc)
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_GENERAL,
                context.getString(R.string.notification_channel_general),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_general_desc)
            }
        )

        val systemManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemManager.createNotificationChannels(channels)
    }

    // ── High-level entry point ─────────────────────────────────────────────

    /**
     * Builds and posts a notification for the given [type] / [data] pair.
     *
     * Honors:
     *  • POST_NOTIFICATIONS permission on Android 13+
     *  • Per-channel user preferences (DataStore)
     *  • Foreground-suppression rules (active chat / visible map)
     *
     * Returns true if a system notification was actually posted.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    suspend fun postForType(type: NotificationType, data: NotificationData): Boolean {
        val channelId = resolveChannelForType(type)

        if (!canPostNotifications()) {
            Timber.w("POST_NOTIFICATIONS permission not granted - dropping $type")
            return false
        }
        if (!notificationManager.areNotificationsEnabled()) {
            Timber.i("Notifications disabled at the system level - dropping $type")
            return false
        }
        if (!isChannelEnabledOnSystem(channelId)) {
            Timber.i("Channel '$channelId' disabled at the system level - dropping $type")
            return false
        }
        if (!preferencesRepository.isChannelEnabledSync(channelId)) {
            Timber.i("Channel '$channelId' disabled - suppressing $type")
            return false
        }
        if (shouldSuppressInForeground(type, data)) {
            Timber.i("Suppressing $type because the relevant screen is foregrounded")
            return false
        }

        // Quiet-hours: bypass for close friends + meetup arrivals (the
        // app's most time-critical events). Otherwise either drop entirely
        // or post silently depending on the user's full-block preference.
        val quietApplies = quietHoursRepository.shouldMuteNow()
            && !shouldBypassQuietHours(type, data)
        val downgradeToSilent = quietApplies && !quietHoursRepository.isFullBlock()
        if (quietApplies && quietHoursRepository.isFullBlock()) {
            Timber.i("Quiet hours full-block - dropping $type")
            return false
        }

        // Chat notifications take a richer path — MessagingStyle, conversation
        // shortcut, inline reply, mark-as-read. Keeping this branch separate
        // keeps the generic path lean for the simpler notification types.
        val notification = if (type == NotificationType.NEW_MESSAGE || type == NotificationType.MENTION) {
            buildChatNotification(type, data, channelId) ?: return false
        } else {
            buildNotification(type, data, channelId)
        }

        val notificationId = buildNotificationId(type, data)

        // Apply quiet-hours silent downgrade by suppressing default sound /
        // vibration on the posted notification. The shade entry still
        // appears so the user can see the message at-a-glance, but their
        // peace doesn't get interrupted.
        val finalNotification = if (downgradeToSilent) {
            notification.also {
                it.defaults = 0
                it.sound = null
                it.vibrate = null
                it.flags = it.flags or Notification.FLAG_ONLY_ALERT_ONCE
            }
        } else notification

        notificationManager.notify(notificationId, finalNotification)

        if (type == NotificationType.NEW_MESSAGE || type == NotificationType.MENTION) {
            data.conversationId?.let { rememberActiveConversation(it) }
        }
        return true
    }

    /**
     * Returns true when this notification should bypass quiet hours. Two
     * categories qualify:
     *  • Sender is in the user's "close friends" list — they always come
     *    through (matches WhatsApp's "important contacts" semantics).
     *  • Type is a time-critical safety event (currently meetup arrivals;
     *    extend if more domain events get added later).
     */
    private suspend fun shouldBypassQuietHours(
        type: NotificationType,
        data: NotificationData
    ): Boolean {
        if (type == NotificationType.MEETUP_MEMBER_ARRIVED) return true
        val senderUid = data.userId ?: return false
        return runCatching {
            closeFriendsRepository.isCloseFriend(senderUid)
        }.getOrDefault(false)
    }

    /**
     * Suppresses notifications for the screen the user is already looking at.
     * Returns true when the system tray push should be dropped.
     */
    private fun shouldSuppressInForeground(type: NotificationType, data: NotificationData): Boolean {
        return when (type) {
            NotificationType.NEW_MESSAGE,
            NotificationType.MENTION -> activeConversationTracker.isActive(data.conversationId)

            NotificationType.LOCATION_UPDATE,
            NotificationType.LIVE_LOCATION_STARTED,
            NotificationType.LIVE_LOCATION_STOPPED,
            NotificationType.MEETUP_DESTINATION_SET,
            NotificationType.MEETUP_DESTINATION_CLEARED,
            NotificationType.MEETUP_MEMBER_ARRIVED -> activeMapTracker.isMapVisible()

            else -> false
        }
    }

    /**
     * Builds a chat-specific notification using [NotificationCompat.MessagingStyle].
     *
     * Adds inline Reply (RemoteInput) and Mark-as-Read action buttons,
     * attaches the conversation Shortcut + LocusId so the system promotes
     * the chat as a "conversation" (Bubbles, priority, per-thread settings),
     * and renders the last few messages from Room as the shade history.
     *
     * Returns null when the conversation id is missing (which would mean an
     * upstream payload bug — refuse to show a misleading "?" notification).
     */
    private suspend fun buildChatNotification(
        type: NotificationType,
        data: NotificationData,
        channelId: String
    ): Notification? {
        val conversationId = data.conversationId ?: return null
        val pendingIntent = buildDeepLinkPendingIntent(type, data)
        val notificationId = buildNotificationId(type, data)

        // Refresh the dynamic shortcut so the OS associates this notification
        // with a "conversation" — required for Priority + Bubbles on R+.
        val shortcutId = runCatching {
            conversationShortcutManager.pushShortcut(conversationId)
        }.getOrNull()

        // The MessagingStyle pulls the recent message list from Room so the
        // shade renders a coherent thread, not a single line.
        val style = chatNotificationStyler.buildStyle(
            conversationId = conversationId,
            incomingSenderId = data.userId.orEmpty(),
            incomingSenderName = data.title.takeIf { it.isNotBlank() } ?: "Someone",
            incomingSenderPhotoUrl = null,
            incomingText = data.body,
            incomingTimestamp = System.currentTimeMillis()
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY_MESSAGES)
            .setOnlyAlertOnce(false)

        // MessagingStyle is the canonical chat presentation. Falls back to
        // BigText when the style fails to build (e.g. unauthenticated state).
        if (style != null) {
            builder.setStyle(style)
        } else {
            builder.setContentTitle(data.title)
                .setContentText(data.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(data.body))
        }

        // Conversation shortcut linkage — the system uses this to enable
        // Bubbles + the "Priority" / "Silent" / per-conversation settings.
        if (shortcutId != null) {
            builder.setShortcutId(shortcutId)
            builder.setLocusId(androidx.core.content.LocusIdCompat(shortcutId))
        }

        // Inline Reply — RemoteInput driven, handled by ChatActionReceiver.
        builder.addAction(buildReplyAction(conversationId, notificationId))
        // Mark as Read — clears the unread counter without opening the chat.
        builder.addAction(buildMarkReadAction(conversationId, notificationId))

        return builder.build()
    }

    /**
     * Builds the inline-reply action. The user types in the shade →
     * [com.ovi.where.data.remote.ChatActionReceiver] receives the broadcast,
     * pushes the message through [com.ovi.where.data.repository.MessageRepositoryImpl.sendMessage],
     * and dismisses the notification.
     */
    private fun buildReplyAction(
        conversationId: String,
        notificationId: Int
    ): NotificationCompat.Action {
        val remoteInput = androidx.core.app.RemoteInput.Builder(
            com.ovi.where.data.remote.ChatActionReceiver.KEY_TEXT_REPLY
        ).setLabel("Reply").build()

        val replyIntent = Intent(
            context,
            com.ovi.where.data.remote.ChatActionReceiver::class.java
        ).apply {
            action = com.ovi.where.data.remote.ChatActionReceiver.ACTION_REPLY
            putExtra(
                com.ovi.where.data.remote.ChatActionReceiver.EXTRA_CONVERSATION_ID,
                conversationId
            )
            putExtra(
                com.ovi.where.data.remote.ChatActionReceiver.EXTRA_NOTIFICATION_ID,
                notificationId
            )
            // Unique URI so the PendingIntent isn't deduped across conversations.
            data = "where://reply/$conversationId/$notificationId".toUri()
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            "reply_$conversationId".hashCode(),
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification, "Reply", replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    /** Builds the mark-as-read action button. */
    private fun buildMarkReadAction(
        conversationId: String,
        notificationId: Int
    ): NotificationCompat.Action {
        val markReadIntent = Intent(
            context,
            com.ovi.where.data.remote.ChatActionReceiver::class.java
        ).apply {
            action = com.ovi.where.data.remote.ChatActionReceiver.ACTION_MARK_READ
            putExtra(
                com.ovi.where.data.remote.ChatActionReceiver.EXTRA_CONVERSATION_ID,
                conversationId
            )
            putExtra(
                com.ovi.where.data.remote.ChatActionReceiver.EXTRA_NOTIFICATION_ID,
                notificationId
            )
            data = "where://markread/$conversationId/$notificationId".toUri()
        }

        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            "markread_$conversationId".hashCode(),
            markReadIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification, "Mark as read", markReadPendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    /**
     * Builds a [Notification] for the given type. Style + actions vary per
     * type but every notification gets the same auto-cancel + deep-link
     * boilerplate.
     */
    private fun buildNotification(
        type: NotificationType,
        data: NotificationData,
        channelId: String
    ): Notification {
        val pendingIntent = buildDeepLinkPendingIntent(type, data)

        val priority = when (channelId) {
            CHANNEL_MESSAGES, CHANNEL_LOCATION_UPDATES, CHANNEL_MEETUP ->
                NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val category = when (type) {
            NotificationType.NEW_MESSAGE,
            NotificationType.MENTION -> NotificationCompat.CATEGORY_MESSAGE
            NotificationType.FRIEND_REQUEST,
            NotificationType.FRIEND_ACCEPTED -> NotificationCompat.CATEGORY_SOCIAL
            NotificationType.LOCATION_UPDATE,
            NotificationType.LIVE_LOCATION_STARTED,
            NotificationType.LIVE_LOCATION_STOPPED -> NotificationCompat.CATEGORY_STATUS
            NotificationType.MEETUP_DESTINATION_SET,
            NotificationType.MEETUP_DESTINATION_CLEARED,
            NotificationType.MEETUP_MEMBER_ARRIVED -> NotificationCompat.CATEGORY_EVENT
            NotificationType.MEMBER_JOINED,
            NotificationType.MEMBER_LEFT -> NotificationCompat.CATEGORY_SOCIAL
            NotificationType.GENERAL -> NotificationCompat.CATEGORY_RECOMMENDATION
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(priority)
            .setCategory(category)

        // Group message + mention notifications so the shade stays tidy on chatty days
        if (type == NotificationType.NEW_MESSAGE || type == NotificationType.MENTION) {
            builder.setGroup(GROUP_KEY_MESSAGES)
        }

        return builder.build()
    }

    /**
     * Tracks the conversation as having an active push. When the count of
     * distinct conversations crosses the [SUMMARY_THRESHOLD], a summary
     * notification is posted on the messages channel.
     */
    private fun rememberActiveConversation(conversationId: String) {
        activeConversationIds.add(conversationId)
        if (activeConversationIds.size >= SUMMARY_THRESHOLD) {
            postSummaryNotification()
        }
    }

    /**
     * Posts a summary "X conversations" notification grouping individual
     * message bubbles in the shade.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun postSummaryNotification() {
        if (!canPostNotifications()) return
        val summaryText = "${activeConversationIds.size} conversations"
        val summary = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(summaryText)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(summaryText))
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    /** Removes a conversation from grouping bookkeeping on dismissal. */
    fun removeConversationFromGroup(conversationId: String) {
        activeConversationIds.remove(conversationId)
    }

    /**
     * Cancels any active system-tray notifications associated with the
     * given conversation. Called from the chat screen on resume so the
     * user doesn't see stale shade entries for messages they're now
     * actively reading.
     *
     * Also drops the conversation from the grouping bookkeeping so the
     * "X conversations" summary shrinks when one of the rows clears.
     */
    fun cancelForConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        // Cancel the message + mention notifications for this conversation.
        // The id derivation must match buildNotificationId().
        val data = NotificationData(title = "", body = "", conversationId = conversationId)
        val ids = listOf(NotificationType.NEW_MESSAGE, NotificationType.MENTION)
            .map { type -> buildNotificationId(type, data) }
        ids.forEach { notificationManager.cancel(it) }

        activeConversationIds.remove(conversationId)
        // If the summary's group dropped below the threshold, hide it too.
        if (activeConversationIds.size < SUMMARY_THRESHOLD) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
        }
    }

    // ── Deep-link PendingIntents ───────────────────────────────────────────

    /** Builds a [PendingIntent] that opens [MainActivity] with the resolved deep-link. */
    fun buildDeepLinkPendingIntent(
        type: NotificationType,
        data: NotificationData,
        requestCode: Int = buildNotificationId(type, data)
    ): PendingIntent {
        val route = resolveDeepLinkRoute(type, data)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Resolves the in-app navigation route for the notification, or null when
     * the notification has no associated screen.
     */
    fun resolveDeepLinkRoute(type: NotificationType, data: NotificationData): String? = when (type) {
        NotificationType.NEW_MESSAGE,
        NotificationType.MENTION -> data.conversationId?.let { "chat/$it" }

        NotificationType.FRIEND_REQUEST -> "friend_requests"
        NotificationType.FRIEND_ACCEPTED -> data.userId?.let { "user_profile/$it" }

        NotificationType.MEMBER_JOINED,
        NotificationType.MEMBER_LEFT -> data.groupId?.let { "group_info/$it" }

        NotificationType.LOCATION_UPDATE,
        NotificationType.LIVE_LOCATION_STARTED,
        NotificationType.LIVE_LOCATION_STOPPED ->
            data.groupId?.let { "group_map/$it" }
                ?: data.conversationId?.let { "chat/$it" }
                ?: "tab_map"

        NotificationType.MEETUP_DESTINATION_SET,
        NotificationType.MEETUP_DESTINATION_CLEARED,
        NotificationType.MEETUP_MEMBER_ARRIVED -> data.groupId?.let { "group_map/$it" }

        NotificationType.GENERAL -> null
    }

    /** Stable id for de-duping notifications across redeliveries. */
    private fun buildNotificationId(type: NotificationType, data: NotificationData): Int {
        val anchor = data.conversationId
            ?: data.groupId
            ?: data.userId
            ?: data.targetId
            ?: ""
        return (type.name + anchor).hashCode()
    }

    // ── Preference / permission helpers ────────────────────────────────────

    /** True when the OS allows the app to post notifications. */
    fun canPostNotifications(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    /**
     * True when the OS reports the given channel as importance ≥ MIN.
     *
     * Users can disable individual channels in the Android settings even
     * when the global toggle is on. We treat IMPORTANCE_NONE as "off" and
     * skip posting so we don't waste cycles on a ghost notification.
     *
     * Pre-O devices don't have channels; always returns true.
     */
    private fun isChannelEnabledOnSystem(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val systemManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = systemManager.getNotificationChannel(channelId) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Channel id for a given [NotificationType]. */
    fun resolveChannelForType(type: NotificationType): String = when (type) {
        NotificationType.NEW_MESSAGE,
        NotificationType.MENTION -> CHANNEL_MESSAGES

        NotificationType.FRIEND_REQUEST,
        NotificationType.FRIEND_ACCEPTED -> CHANNEL_SOCIAL

        NotificationType.LOCATION_UPDATE,
        NotificationType.LIVE_LOCATION_STARTED,
        NotificationType.LIVE_LOCATION_STOPPED -> CHANNEL_LOCATION_UPDATES

        NotificationType.MEMBER_JOINED,
        NotificationType.MEMBER_LEFT -> CHANNEL_GROUP_ACTIVITY

        NotificationType.MEETUP_DESTINATION_SET,
        NotificationType.MEETUP_DESTINATION_CLEARED,
        NotificationType.MEETUP_MEMBER_ARRIVED -> CHANNEL_MEETUP

        NotificationType.GENERAL -> CHANNEL_GENERAL
    }

    /** Legacy entry-point retained for callers that hand us the FCM string directly. */
    fun resolveChannelForType(type: String?): String =
        resolveChannelForType(NotificationType.fromFcmType(type))

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_SOCIAL = "social"
        const val CHANNEL_LOCATION_UPDATES = "location_updates"
        const val CHANNEL_GROUP_ACTIVITY = "group_activity"
        const val CHANNEL_MEETUP = "meetup"
        const val CHANNEL_GENERAL = "general"

        const val GROUP_KEY_MESSAGES = "com.ovi.where.GROUP_MESSAGES"
        const val SUMMARY_NOTIFICATION_ID = 9999
        const val SUMMARY_THRESHOLD = 2
    }
}
