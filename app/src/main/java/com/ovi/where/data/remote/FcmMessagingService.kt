package com.ovi.where.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.core.notification.NotificationData
import com.ovi.where.core.notification.NotificationHelper
import com.ovi.where.core.notification.NotificationType
import com.ovi.where.data.repository.NotificationRepository
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

/**
 * Receives FCM pushes and routes them through the central [NotificationHelper].
 *
 * Why a single dispatcher? Each push has an FCM `data.type` field that the
 * server sets (e.g. `new_message`, `meetup_destination_set`). We map that
 * string to a [NotificationType], let [NotificationHelper] decide whether
 * to actually post (channel preferences, foreground suppression), and
 * always mirror it into the in-app inbox via [NotificationRepository] —
 * even when suppressed — so the notification list stays accurate.
 *
 * FCM payload contract (data-only messages):
 *  • `type` — one of [NotificationHelper.Companion] TYPE_* strings.
 *  • `title`, `body` — display text. Server falls back to `notification`
 *    block when these are missing.
 *  • Type-specific extras: `conversationId`, `groupId`, `userId`,
 *    `senderName`, `destinationName`, `mentionedUserIds` (CSV), etc.
 *
 * Cloud Functions and the Node socket server are the only producers of
 * these messages — clients never send FCM directly.
 */
@AndroidEntryPoint
class FcmMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var notificationRepository: NotificationRepository

    // ── Token refresh ─────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.i("FCM token refreshed")
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to save FCM token")
            }
        }
    }

    // ── Message dispatch ──────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val rawType = data["type"]
        val type = NotificationType.fromFcmType(rawType)
        Timber.i("FCM received: rawType=$rawType -> $type")

        val payload = parsePayload(type, data, message.notification)

        serviceScope.launch {
            // Always persist to the inbox first — even when the system push is
            // suppressed (active chat / mute / disabled channel). The bell
            // badge and the inbox screen are derived from this table, so a
            // missing entry here would silently lose the user a notification.
            val deepLinkRoute = notificationHelper.resolveDeepLinkRoute(type, payload)
            runCatching {
                notificationRepository.add(type, payload, deepLinkRoute)
            }.onFailure { Timber.w(it, "Failed to persist notification to inbox") }

            // Now post the system tray notification through the helper.
            // The helper short-circuits when the relevant screen is foregrounded,
            // when channel prefs are off, or when POST_NOTIFICATIONS is denied.
            runCatching {
                notificationHelper.postForType(type, payload)
            }.onFailure { Timber.e(it, "Failed to post notification") }
        }
    }

    /**
     * Extracts a [NotificationData] from the FCM payload, applying type-specific
     * fallbacks for the title/body so we always have something to display.
     */
    private fun parsePayload(
        type: NotificationType,
        data: Map<String, String>,
        notif: RemoteMessage.Notification?
    ): NotificationData {
        val appName = getString(R.string.app_name)
        val rawTitle = data["title"] ?: notif?.title
        val rawBody = data["body"] ?: notif?.body.orEmpty()

        // Per-type cosmetic fallbacks — the server is best-effort but we want
        // every shade entry to read coherently regardless of producer.
        val title = when (type) {
            NotificationType.NEW_MESSAGE,
            NotificationType.MENTION ->
                rawTitle ?: data["senderName"] ?: appName

            NotificationType.FRIEND_REQUEST -> rawTitle
                ?: data["requesterName"]?.let { "$it wants to be your friend" }
                ?: "New friend request"

            NotificationType.FRIEND_ACCEPTED -> rawTitle
                ?: data["userName"]?.let { "$it accepted your friend request" }
                ?: "Friend request accepted"

            NotificationType.LIVE_LOCATION_STARTED -> rawTitle
                ?: data["userName"]?.let { "$it started sharing location" }
                ?: "Location sharing started"

            NotificationType.LIVE_LOCATION_STOPPED -> rawTitle
                ?: data["userName"]?.let { "$it stopped sharing location" }
                ?: "Location sharing stopped"

            NotificationType.MEETUP_DESTINATION_SET -> rawTitle
                ?: data["destinationName"]?.let { "Meetup set: $it" }
                ?: "Meetup destination set"

            NotificationType.MEETUP_DESTINATION_CLEARED -> rawTitle
                ?: "Meetup destination cleared"

            NotificationType.MEETUP_MEMBER_ARRIVED -> rawTitle
                ?: data["userName"]?.let { "$it arrived at the meetup" }
                ?: "Member arrived"

            NotificationType.MEMBER_JOINED -> rawTitle
                ?: data["userName"]?.let { "$it joined the group" }
                ?: "New group member"

            NotificationType.MEMBER_LEFT -> rawTitle
                ?: data["userName"]?.let { "$it left the group" }
                ?: "Member left"

            NotificationType.LOCATION_UPDATE,
            NotificationType.GENERAL -> rawTitle ?: appName
        }

        val body = rawBody.ifBlank {
            when (type) {
                NotificationType.NEW_MESSAGE -> data["text"].orEmpty()
                NotificationType.MENTION -> data["text"]?.let { "Mentioned you: $it" } ?: "Mentioned you"
                NotificationType.FRIEND_REQUEST -> "Tap to review the request"
                NotificationType.FRIEND_ACCEPTED -> "You're now friends on Where"
                NotificationType.LIVE_LOCATION_STARTED -> "Tap to view on the map"
                NotificationType.LIVE_LOCATION_STOPPED -> "They are no longer sharing"
                NotificationType.MEETUP_DESTINATION_SET ->
                    data["destinationName"]?.let { "Meeting at $it" } ?: "Tap to view the destination"

                NotificationType.MEETUP_DESTINATION_CLEARED -> "Tap to view the group"
                NotificationType.MEETUP_MEMBER_ARRIVED -> "They reached the meetup point"
                NotificationType.MEMBER_JOINED -> "Open the group to say hi"
                NotificationType.MEMBER_LEFT -> "Tap to view the group"
                NotificationType.LOCATION_UPDATE,
                NotificationType.GENERAL -> ""
            }
        }

        return NotificationData(
            title = title,
            body = body,
            targetId = data["targetId"],
            conversationId = data["conversationId"],
            groupId = data["groupId"],
            userId = data["userId"]
                ?: data["requesterId"]
                ?: data["senderId"]
                ?: data["actorId"],
            destinationName = data["destinationName"],
            mentionedUserIds = data["mentionedUserIds"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
