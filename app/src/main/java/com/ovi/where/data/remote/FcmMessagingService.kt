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

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM Token: $token")
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        serviceScope.launch {
            try {
                val userId = authRepository.currentUserId
                if (userId != null) {
                    FirebaseFirestore.getInstance()
                        .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                        .document(userId)
                        .update("fcmToken", token)
                        .await()
                    Timber.d("FCM token saved to Firestore")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save FCM token")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("FCM Message: ${message.data}")
        
        val title = message.data["title"] ?: message.notification?.title ?: getString(R.string.app_name)
        val body = message.data["body"] ?: message.notification?.body ?: ""
        val groupId = message.data["groupId"]
        val type = message.data["type"]
        
        when (type) {
            "location_update" -> showLocationUpdateNotification(title, body, groupId)
            "member_joined" -> showMemberJoinedNotification(title, body, groupId)
            "member_left" -> showMemberLeftNotification(title, body, groupId)
            else -> showDefaultNotification(title, body)
        }
    }

    private fun showLocationUpdateNotification(title: String, body: String, groupId: String?) {
        showNotification(
            notificationId = (groupId?.hashCode() ?: System.currentTimeMillis().toInt()),
            title = title,
            body = body,
            channelId = CHANNEL_LOCATION_UPDATES
        )
    }

    private fun showMemberJoinedNotification(title: String, body: String, groupId: String?) {
        showNotification(
            notificationId = (groupId?.hashCode() ?: System.currentTimeMillis().toInt()),
            title = title,
            body = body,
            channelId = CHANNEL_GROUP_ACTIVITY
        )
    }

    private fun showMemberLeftNotification(title: String, body: String, groupId: String?) {
        showNotification(
            notificationId = (groupId?.hashCode() ?: System.currentTimeMillis().toInt()),
            title = title,
            body = body,
            channelId = CHANNEL_GROUP_ACTIVITY
        )
    }

    private fun showDefaultNotification(title: String, body: String) {
        showNotification(
            notificationId = System.currentTimeMillis().toInt(),
            title = title,
            body = body,
            channelId = CHANNEL_DEFAULT
        )
    }

    private fun showNotification(
        notificationId: Int,
        title: String,
        body: String,
        channelId: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannels(notificationManager)
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
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
            
            notificationManager.createNotificationChannels(channels)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_LOCATION_UPDATES = "location_updates"
        const val CHANNEL_GROUP_ACTIVITY = "group_activity"
        const val CHANNEL_DEFAULT = "general"
    }
}
