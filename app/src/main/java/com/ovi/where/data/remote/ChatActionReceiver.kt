package com.ovi.where.data.remote

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.ovi.where.core.notification.NotificationHelper
import com.ovi.where.data.remote.ChatActionReceiver.Companion.ACTION_MARK_READ
import com.ovi.where.data.remote.ChatActionReceiver.Companion.ACTION_REPLY
import com.ovi.where.data.repository.MessageRepositoryImpl
import com.ovi.where.domain.repository.ConversationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles in-shade chat actions:
 *  • [ACTION_REPLY]      — sends an inline reply via the existing
 *                          [MessageRepositoryImpl] (queued offline / socket
 *                          when online — the user gets the same reliability
 *                          as a reply typed in the chat screen).
 *  • [ACTION_MARK_READ]  — clears the conversation's unread counter through
 *                          [ConversationRepository.markAsRead] and dismisses
 *                          the system tray entry.
 *
 * Why a BroadcastReceiver instead of a Service? RemoteInput-driven replies
 * are short-lived; a BroadcastReceiver runs on the main thread for ~10s
 * which is plenty of time to enqueue the message into our offline-aware
 * repository. We delegate the actual emission to the repo's existing
 * coroutine path so the receiver can return immediately.
 *
 * Both actions cancel the system-tray notification on completion so the
 * user gets a clean shade after acting.
 */
@AndroidEntryPoint
class ChatActionReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject
    lateinit var messageRepository: MessageRepositoryImpl

    @Inject
    lateinit var conversationRepository: ConversationRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (conversationId.isBlank()) {
            Timber.w("ChatActionReceiver: missing conversationId")
            return
        }

        when (intent.action) {
            ACTION_REPLY -> handleReply(context, conversationId, notificationId, intent)
            ACTION_MARK_READ -> handleMarkRead(context, conversationId, notificationId)
        }
    }

    private fun handleReply(
        context: Context,
        conversationId: String,
        notificationId: Int,
        intent: Intent
    ) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput
            .getCharSequence(KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (replyText.isEmpty()) return

        val pendingResult: PendingResult = goAsync()
        receiverScope.launch {
            try {
                // Push through the same code path as a typed reply so
                // optimistic insert + offline queue + ack handling all
                // behave identically to the in-app send.
                messageRepository.sendMessage(conversationId, replyText)

                // Clear the system-tray notification — the message is on its
                // way; the user will see the round-trip when they next open
                // the conversation. Leaving the notification up would be
                // confusing because they already "interacted" with it.
                if (notificationId >= 0) {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm?.cancel(notificationId)
                }
                notificationHelper.removeConversationFromGroup(conversationId)
            } catch (e: Exception) {
                Timber.e(e, "Inline reply failed for conversation=$conversationId")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkRead(
        context: Context,
        conversationId: String,
        notificationId: Int
    ) {
        val pendingResult: PendingResult = goAsync()
        receiverScope.launch {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .currentUser?.uid
                if (uid != null) {
                    conversationRepository.markAsRead(conversationId, uid)
                }
                if (notificationId >= 0) {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm?.cancel(notificationId)
                }
                notificationHelper.cancelForConversation(conversationId)
            } catch (e: Exception) {
                Timber.e(e, "Mark-read action failed for conversation=$conversationId")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.ovi.where.action.REPLY"
        const val ACTION_MARK_READ = "com.ovi.where.action.MARK_READ"
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        /** Key for the RemoteInput field. */
        const val KEY_TEXT_REPLY = "key_text_reply"
    }
}
