package com.ovi.where.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ovi.where.MainActivity
import com.ovi.where.R
import com.ovi.where.core.notification.NotificationHelper.Companion.CHANNEL_DEFINITIONS
import com.ovi.where.core.notification.NotificationHelper.Companion.SUMMARY_THRESHOLD
import com.ovi.where.data.repository.ChannelSoundConfig
import com.ovi.where.data.repository.NotificationPreferencesRepository
import com.ovi.where.data.repository.NotificationSoundPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
    private val soundPreferencesRepository: NotificationSoundPreferencesRepository,
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

    /**
     * Cached map of base channel id → currently-active versioned channel id.
     * Refreshed on every [createChannels] call. The send path uses this so
     * a single channel-version bump from the settings UI propagates to the
     * very next notification without us re-reading DataStore on the hot path.
     */
    @Volatile
    private var activeChannelMap: Map<String, String> = BASE_CHANNEL_IDS.associateWith { it }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Channel creation must read DataStore for sound configs. We do
        // it on a background coroutine to avoid blocking process start,
        // and re-create on every preference change so the user's chosen
        // ringtone takes effect on the next notification.
        ioScope.launch { createChannels() }
    }

    // ── Channel creation ──────────────────────────────────────────────────

    /**
     * Creates every channel the app uses, applying the user's saved
     * sound preferences via channel versioning.
     *
     * Channel versioning trick:
     * On Android O+, `NotificationChannel.setSound(...)` is **immutable
     * after the first time the channel is created** — the system locks
     * the field so apps can't fight per-channel user customizations. To
     * honour a sound change we delete the old channel and create a new
     * one under a versioned id (`messages_v3`, `meetup_v1`, ...). The
     * versioned id is what we pass to `NotificationCompat.Builder`; the
     * base id (`messages`, `meetup`, ...) is what we display in the UI
     * and use for preference keys.
     *
     * Safe to call multiple times. Pre-O devices skip channel creation
     * entirely (no-op).
     */
    suspend fun createChannels() {
        val configs = soundPreferencesRepository.snapshot()
        val configByBase = configs.associateBy { it.baseChannelId }

        // Build the new active map first so the send path sees a
        // consistent view at all times.
        activeChannelMap = BASE_CHANNEL_IDS.associateWith { base ->
            configByBase[base]?.versionedChannelId ?: base
        }

        val systemManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Delete stale versioned channels (any channel that starts with
        // a base id and isn't the current active one). This keeps the
        // system-settings list clean as users hop between sounds.
        val activeIds = activeChannelMap.values.toSet()
        systemManager.notificationChannels
            .filter { ch ->
                BASE_CHANNEL_IDS.any { base ->
                    ch.id == base || ch.id.startsWith("${base}_v")
                } && ch.id !in activeIds
            }
            .forEach { stale ->
                runCatching { systemManager.deleteNotificationChannel(stale.id) }
            }

        val channels = BASE_CHANNEL_IDS.map { baseId ->
            val config = configByBase[baseId] ?: ChannelSoundConfig(
                baseChannelId = baseId,
                sound = NotificationSound.defaultFor(baseId),
                version = 0
            )
            buildChannel(baseId, config)
        }
        systemManager.createNotificationChannels(channels)
    }

    /**
     * Builds a single [NotificationChannel] for a base channel id, using
     * the importance / vibration profile baked into [CHANNEL_DEFINITIONS].
     */
    private fun buildChannel(baseId: String, config: ChannelSoundConfig): NotificationChannel {
        val def = CHANNEL_DEFINITIONS.firstOrNull { it.id == baseId }
            ?: ChannelDefinition(
                id = baseId,
                nameRes = R.string.notification_channel_general,
                descRes = R.string.notification_channel_general_desc,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                vibrate = false,
                lights = false
            )
        return NotificationChannel(
            config.versionedChannelId,
            context.getString(def.nameRes),
            def.importance
        ).apply {
            description = context.getString(def.descRes)
            enableVibration(def.vibrate)
            enableLights(def.lights)

            val soundUri = config.sound.resolveUri(context)
            if (soundUri != null) {
                val attrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, attrs)
            } else {
                // Silent: must be explicitly null + null attrs, otherwise
                // Android falls back to the default ringtone.
                setSound(null, null)
            }
        }
    }

    /**
     * Triggers a channel rebuild after a sound preference change. Returns
     * a cold suspension so the caller can `await` if it cares (the
     * settings VM doesn't — fire-and-forget).
     */
    suspend fun rebuildChannels() {
        createChannels()
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
        val baseChannelId = resolveChannelForType(type)
        // Versioned channel id — what the system actually knows about.
        // Falls back to the base id if the active map doesn't have an
        // entry yet (channel creation hasn't completed on a cold start).
        val channelId = activeChannelMap[baseChannelId] ?: baseChannelId

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
        if (!preferencesRepository.isChannelEnabledSync(baseChannelId)) {
            Timber.i("Channel '$baseChannelId' disabled - suppressing $type")
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
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
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
        val messagesChannelId = activeChannelMap[CHANNEL_MESSAGES] ?: CHANNEL_MESSAGES
        val summary = NotificationCompat.Builder(context, messagesChannelId)
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

        /**
         * Stable list of every base channel the app owns. Iteration order
         * is the order channels appear in the system Settings → Notifications
         * UI on most launchers, so it doubles as a UI ordering hint.
         */
        val BASE_CHANNEL_IDS = listOf(
            CHANNEL_MESSAGES,
            CHANNEL_SOCIAL,
            CHANNEL_LOCATION_UPDATES,
            CHANNEL_GROUP_ACTIVITY,
            CHANNEL_MEETUP,
            CHANNEL_GENERAL,
        )

        /**
         * Per-channel metadata used by [buildChannel]. Keeping these in a
         * single table avoids duplicating string-resource refs and makes
         * adding a new channel a one-line change.
         */
        internal val CHANNEL_DEFINITIONS = listOf(
            ChannelDefinition(
                id = CHANNEL_MESSAGES,
                nameRes = R.string.notification_channel_messages,
                descRes = R.string.notification_channel_messages_desc,
                importance = NotificationManager.IMPORTANCE_HIGH,
                vibrate = true,
                lights = true
            ),
            ChannelDefinition(
                id = CHANNEL_SOCIAL,
                nameRes = R.string.notification_channel_social,
                descRes = R.string.notification_channel_social_desc,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                vibrate = false,
                lights = false
            ),
            ChannelDefinition(
                id = CHANNEL_LOCATION_UPDATES,
                nameRes = R.string.notification_channel_location_updates,
                descRes = R.string.notification_channel_location_updates_desc,
                importance = NotificationManager.IMPORTANCE_HIGH,
                vibrate = false,
                lights = false
            ),
            ChannelDefinition(
                id = CHANNEL_GROUP_ACTIVITY,
                nameRes = R.string.notification_channel_group_activity,
                descRes = R.string.notification_channel_group_activity_desc,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                vibrate = false,
                lights = false
            ),
            ChannelDefinition(
                id = CHANNEL_MEETUP,
                nameRes = R.string.notification_channel_meetup,
                descRes = R.string.notification_channel_meetup_desc,
                importance = NotificationManager.IMPORTANCE_HIGH,
                vibrate = true,
                lights = true
            ),
            ChannelDefinition(
                id = CHANNEL_GENERAL,
                nameRes = R.string.notification_channel_general,
                descRes = R.string.notification_channel_general_desc,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                vibrate = false,
                lights = false
            ),
        )
    }
}

/**
 * Static per-channel metadata. Only used inside [NotificationHelper] to
 * build the `NotificationChannel` objects on Android O+.
 */
internal data class ChannelDefinition(
    val id: String,
    @androidx.annotation.StringRes val nameRes: Int,
    @androidx.annotation.StringRes val descRes: Int,
    val importance: Int,
    val vibrate: Boolean,
    val lights: Boolean,
)
