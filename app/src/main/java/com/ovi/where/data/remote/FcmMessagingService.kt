package com.ovi.where.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ovi.where.MainActivity
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FcmMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var notificationPreferencesRepository: com.ovi.where.data.repository.NotificationPreferencesRepository

    // ── Token refresh ─────────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.i("FCM Token refreshed")
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        serviceScope.launch {
            try {
                val userId = authRepository.currentUserId ?: return@launch
                FirebaseFirestore.getInstance()
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId)
                    .update("fcmToken", token)
                    .await()
                Timber.i("FCM token saved for user $userId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save FCM token")
            }
        }
    }

    // ── Message received ──────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.i("FCM message received: type=${message.data["type"]}")

        val data  = message.data
        val type  = data["type"] ?: return
        val title = data["title"] ?: message.notification?.title ?: getString(R.string.app_name)
        val body  = data["body"]  ?: message.notification?.body  ?: ""

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createAllChannels(notificationManager)

        // Resolve the target channel for this message type.
        // Unrecognized types are routed to the general channel (Requirement 12.7).
        val channelId = resolveChannelForType(type)

        // Check user preference for the target channel before posting (Requirement 12.6).
        serviceScope.launch {
            val isEnabled = notificationPreferencesRepository.isChannelEnabledSync(channelId)
            if (!isEnabled) {
                Timber.i("Channel '$channelId' disabled by user — suppressing FCM notification type=$type")
                return@launch
            }

            when (type) {
                TYPE_NEW_MESSAGE      -> handleNewMessage(data, notificationManager)
                TYPE_FRIEND_REQUEST   -> handleFriendRequest(data, title, body, notificationManager)
                TYPE_FRIEND_ACCEPTED  -> handleFriendAccepted(data, title, body, notificationManager)
                TYPE_LOCATION_UPDATE  -> handleLocationUpdate(data, title, body, notificationManager)
                TYPE_MEMBER_JOINED    -> handleGroupActivity(data, title, body, notificationManager)
                TYPE_MEMBER_LEFT      -> handleGroupActivity(data, title, body, notificationManager)
                else                  -> showSimpleNotification(title, body, null, CHANNEL_DEFAULT, notificationManager)
            }
        }
    }

    /**
     * Resolves the appropriate notification channel for a given FCM message type.
     * Unrecognized types are routed to the general channel (Requirement 12.7).
     */
    private fun resolveChannelForType(type: String?): String {
        return when (type) {
            TYPE_NEW_MESSAGE -> CHANNEL_MESSAGES
            TYPE_FRIEND_REQUEST, TYPE_FRIEND_ACCEPTED -> CHANNEL_SOCIAL
            TYPE_LOCATION_UPDATE -> CHANNEL_LOCATION_UPDATES
            TYPE_MEMBER_JOINED, TYPE_MEMBER_LEFT -> CHANNEL_GROUP_ACTIVITY
            else -> CHANNEL_DEFAULT
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    /**
     * New chat message.
     * Payload: conversationId, senderName, senderPhotoUrl?, text, conversationName
     * Deep link: where://chat/{conversationId}
     */
    private fun handleNewMessage(
        data: Map<String, String>,
        nm: NotificationManager
    ) {
        val conversationId   = data["conversationId"]   ?: return
        val senderName       = data["senderName"]       ?: getString(R.string.app_name)
        val text             = data["text"]             ?: ""
        val conversationName = data["conversationName"] ?: senderName

        val deepLinkRoute = "chat/$conversationId"
        val pendingIntent = buildDeepLinkPendingIntent(deepLinkRoute, conversationId.hashCode())

        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(text)
            .setSubText(conversationName)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(text)
                    .setBigContentTitle(senderName)
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_MESSAGES)
            .build()

        nm.notify(conversationId.hashCode(), notification)

        // Summary notification so messages are grouped in the notification shade
        val summary = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("New messages")
            .setGroup(GROUP_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        nm.notify(SUMMARY_MESSAGES_ID, summary)
    }

    /**
     * Someone sent a friend request.
     * Payload: requesterId, requesterName
     * Deep link: where://friend_requests
     */
    private fun handleFriendRequest(
        data: Map<String, String>,
        title: String,
        body: String,
        nm: NotificationManager
    ) {
        val requesterId   = data["requesterId"]   ?: ""
        val requesterName = data["requesterName"] ?: title

        val notifTitle = "$requesterName wants to be your friend"
        val notifBody  = body.ifEmpty { "Tap to review the request" }

        showSimpleNotification(
            title          = notifTitle,
            body           = notifBody,
            deepLinkRoute  = "friend_requests",
            channelId      = CHANNEL_SOCIAL,
            nm             = nm,
            notificationId = requesterId.hashCode()
        )
    }

    /**
     * A friend request was accepted.
     * Payload: userId, userName
     * Deep link: where://user_profile/{userId}
     */
    private fun handleFriendAccepted(
        data: Map<String, String>,
        title: String,
        body: String,
        nm: NotificationManager
    ) {
        val userId   = data["userId"]   ?: ""
        val userName = data["userName"] ?: title

        val notifTitle = "$userName accepted your friend request"
        val notifBody  = body.ifEmpty { "You are now friends on Where!" }

        showSimpleNotification(
            title          = notifTitle,
            body           = notifBody,
            deepLinkRoute  = if (userId.isNotEmpty()) "user_profile/$userId" else null,
            channelId      = CHANNEL_SOCIAL,
            nm             = nm,
            notificationId = userId.hashCode() + 1_000
        )
    }

    /**
     * Group location update (existing behaviour, now with group map deep link).
     * Deep link: where://group_map/{groupId}
     */
    private fun handleLocationUpdate(
        data: Map<String, String>,
        title: String,
        body: String,
        nm: NotificationManager
    ) {
        val groupId = data["groupId"]
        showSimpleNotification(
            title          = title,
            body           = body,
            deepLinkRoute  = if (groupId != null) "group_map/$groupId" else null,
            channelId      = CHANNEL_LOCATION_UPDATES,
            nm             = nm,
            notificationId = groupId?.hashCode() ?: System.currentTimeMillis().toInt()
        )
    }

    /** Member joined / left (existing behaviour). */
    private fun handleGroupActivity(
        data: Map<String, String>,
        title: String,
        body: String,
        nm: NotificationManager
    ) {
        val groupId = data["groupId"]
        showSimpleNotification(
            title          = title,
            body           = body,
            deepLinkRoute  = if (groupId != null) "group_details/$groupId" else null,
            channelId      = CHANNEL_GROUP_ACTIVITY,
            nm             = nm,
            notificationId = (groupId ?: System.currentTimeMillis().toString()).hashCode()
        )
    }

    // ── Notification builder helpers ──────────────────────────────────────────

    private fun showSimpleNotification(
        title: String,
        body: String,
        deepLinkRoute: String?,
        channelId: String,
        nm: NotificationManager,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val pendingIntent = buildDeepLinkPendingIntent(deepLinkRoute, notificationId)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(notificationId, notification)
    }

    /**
     * Builds a [PendingIntent] that opens [MainActivity] and delivers the deep-link
     * route via [MainActivity.EXTRA_DEEP_LINK_ROUTE]. If [deepLinkRoute] is null the
     * intent simply opens the app at its default destination.
     */
    private fun buildDeepLinkPendingIntent(deepLinkRoute: String?, requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags  = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deepLinkRoute != null) {
                putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, deepLinkRoute)
            }
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ── Notification channels ─────────────────────────────────────────────────

    private fun createAllChannels(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_MESSAGES,
                getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description      = getString(R.string.notification_channel_messages_desc)
                enableVibration(true)
                enableLights(true)
            },
            NotificationChannel(
                CHANNEL_SOCIAL,
                getString(R.string.notification_channel_social),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_social_desc)
            },
            NotificationChannel(
                CHANNEL_LOCATION_UPDATES,
                getString(R.string.notification_channel_location_updates),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_location_updates_desc)
            },
            NotificationChannel(
                CHANNEL_GROUP_ACTIVITY,
                getString(R.string.notification_channel_group_activity),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_group_activity_desc)
            },
            NotificationChannel(
                CHANNEL_DEFAULT,
                getString(R.string.notification_channel_general),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_general_desc)
            }
        )
        nm.createNotificationChannels(channels)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        // FCM message type values
        const val TYPE_NEW_MESSAGE     = "new_message"
        const val TYPE_FRIEND_REQUEST  = "friend_request"
        const val TYPE_FRIEND_ACCEPTED = "friend_accepted"
        const val TYPE_LOCATION_UPDATE = "location_update"
        const val TYPE_MEMBER_JOINED   = "member_joined"
        const val TYPE_MEMBER_LEFT     = "member_left"

        // Notification channel IDs
        const val CHANNEL_MESSAGES         = "messages"
        const val CHANNEL_SOCIAL           = "social"
        const val CHANNEL_LOCATION_UPDATES = "location_updates"
        const val CHANNEL_GROUP_ACTIVITY   = "group_activity"
        const val CHANNEL_DEFAULT          = "general"

        // Notification group keys
        const val GROUP_MESSAGES       = "com.ovi.where.MESSAGES"
        const val SUMMARY_MESSAGES_ID  = 9999
    }
}
