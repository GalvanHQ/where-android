package com.ovi.where.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.MainActivity
import com.ovi.where.R
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.remote.chat.ChatApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import timber.log.Timber

/**
 * WorkManager periodic worker that syncs unread message counts in the background.
 *
 * Behavior:
 * - Scheduled when app is in background for > 5 minutes (15-minute periodic interval)
 * - Makes a single REST API call to fetch unread counts and updates Room
 * - Must complete within 30 seconds
 * - On network/server failure: retries with exponential backoff (1min initial, 30min max, 5 attempts max)
 * - On auth error (401/403): cancels periodic schedule, no retry until re-auth
 * - On new unread messages detected: displays notification with sender name, conversation, count
 *
 * Requirements: 22.1, 22.2, 22.3, 22.4, 22.5, 22.6, 22.7
 */
@HiltWorker
class BackgroundSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val conversationDao: ConversationDao,
    private val firebaseAuth: FirebaseAuth
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("BackgroundSyncWorker: starting sync (attempt ${runAttemptCount + 1})")

        // Check authentication — cancel schedule on auth error (Req 22.7)
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Timber.w("BackgroundSyncWorker: user not authenticated, cancelling schedule")
            BackgroundSyncScheduler.cancel(appContext)
            return Result.failure()
        }

        return try {
            // Get auth token
            val token = currentUser.getIdToken(false).await()?.token
            if (token.isNullOrEmpty()) {
                Timber.w("BackgroundSyncWorker: failed to get auth token, cancelling schedule")
                BackgroundSyncScheduler.cancel(appContext)
                return Result.failure()
            }

            // Fetch unread counts within 30-second execution window (Req 22.2)
            val unreadCounts = withTimeout(EXECUTION_TIMEOUT_MS) {
                ChatApiClient.apiService.getUnreadCounts("Bearer $token")
            }

            // Get current counts from Room to detect new unread messages (Req 22.6)
            val currentConversations = conversationDao.getAll()
            val currentCountMap = currentConversations.associate { it.id to it.unreadCount }

            // Update Room with new unread counts
            for (dto in unreadCounts) {
                conversationDao.updateUnreadCount(dto.conversationId, dto.unreadCount)
            }

            // Detect new unread messages and show notification (Req 22.6)
            val newUnreadConversations = unreadCounts.filter { dto ->
                val previousCount = currentCountMap[dto.conversationId] ?: 0
                dto.unreadCount > previousCount
            }

            if (newUnreadConversations.isNotEmpty()) {
                showUnreadNotification(newUnreadConversations.size, newUnreadConversations)
            }

            Timber.d("BackgroundSyncWorker: sync completed successfully, updated ${unreadCounts.size} conversations")
            Result.success()
        } catch (e: HttpException) {
            val code = e.code()
            if (code == 401 || code == 403) {
                // Auth error — cancel periodic schedule, no retry (Req 22.7)
                Timber.w("BackgroundSyncWorker: auth error ($code), cancelling schedule")
                BackgroundSyncScheduler.cancel(appContext)
                return Result.failure()
            }
            // Server error — retry with backoff (Req 22.3)
            handleRetry(e)
        } catch (e: Exception) {
            // Network or other failure — retry with backoff (Req 22.3)
            handleRetry(e)
        }
    }

    /**
     * Handles retry logic with exponential backoff.
     * Maximum 5 retry attempts (Req 22.3).
     */
    private fun handleRetry(e: Exception): Result {
        Timber.w(e, "BackgroundSyncWorker: sync failed (attempt ${runAttemptCount + 1})")
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Timber.w("BackgroundSyncWorker: all retry attempts exhausted")
            Result.failure()
        }
    }

    /**
     * Displays a notification when new unread messages are detected (Req 22.6).
     * Shows sender name, conversation, and unread count.
     */
    private fun showUnreadNotification(
        totalNewConversations: Int,
        newUnreadConversations: List<com.ovi.where.data.remote.chat.UnreadCountDto>
    ) {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BACKGROUND_SYNC,
                "Background Sync Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new messages detected during background sync"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build notification content
        val totalUnread = newUnreadConversations.sumOf { it.unreadCount }
        val title = if (totalNewConversations == 1) {
            "New message"
        } else {
            "$totalNewConversations conversations with new messages"
        }
        val body = "$totalUnread unread message${if (totalUnread > 1) "s" else ""}"

        // Deep link to chats screen
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, "chats")
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_BACKGROUND_SYNC)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "background_sync_periodic"

        /** Maximum execution time for the sync operation (Req 22.2) */
        private const val EXECUTION_TIMEOUT_MS = 30_000L

        /** Maximum retry attempts before giving up (Req 22.3) */
        private const val MAX_RETRY_ATTEMPTS = 5

        /** Notification channel for background sync messages */
        private const val CHANNEL_BACKGROUND_SYNC = "background_sync"

        private const val NOTIFICATION_ID = 8888
        private const val NOTIFICATION_REQUEST_CODE = 8889
    }
}
