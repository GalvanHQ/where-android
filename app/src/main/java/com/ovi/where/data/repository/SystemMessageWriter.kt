package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.data.remote.chat.ChatApiClient
import com.ovi.where.data.remote.chat.SystemMessageRequest
import com.ovi.where.domain.model.SystemEventType
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a [SystemEventType] message into the chat timeline. Used by repos
 * and view models that mutate state worth surfacing as a Messenger-style
 * "info line" — group renames, member changes, theme tweaks, etc.
 *
 * Authoring goes through `POST /api/conversations/:id/system-message` rather
 * than directly to Firestore. Two reasons:
 *
 *   1. The `messages` collection is Admin-SDK-only by Firestore rules. Keeping
 *      it that way means we don't have to encode payload validation, sender
 *      identity, and `recentMessages` integrity in declarative rules — the
 *      server route validates everything in one well-tested code path.
 *   2. The server handles the same transactional `recentMessages` /
 *      `lastMessage*` update pattern the WS message handler uses, so embedded
 *      pagination, chat list previews, and the archive stay consistent.
 *
 * Idempotency: the message id is derived deterministically from
 * `(eventType, conversationId, timestamp_bucket, actorId, targetId?)` so a
 * retried request on the same logical event collapses to a single document
 * (server uses `set` on the same id).
 *
 * Notification suppression: the server route does not call `sendFCM()`, so
 * system events never produce push notifications by construction
 * (Requirement 8.1).
 *
 * See `.kiro/specs/group-system-messages/`.
 */
@Singleton
class SystemMessageWriter @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

    /**
     * Authors the system message via the server endpoint. Failures log and
     * swallow — the underlying state change (which the caller already
     * committed) matters more than the cosmetic timeline entry.
     */
    suspend fun writeSystemMessage(
        conversationId: String,
        eventType: SystemEventType,
        targetUserId: String? = null,
        payload: Map<String, String> = emptyMap(),
        fallbackText: String
    ) {
        val actor = firebaseAuth.currentUser ?: run {
            Timber.tag(TAG).w("Skipping $eventType in $conversationId — no current user")
            return
        }
        val token = try {
            actor.getIdToken(false).await()?.token
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to fetch ID token for $eventType")
            return
        }
        if (token.isNullOrBlank()) {
            Timber.tag(TAG).w("Empty ID token; skipping $eventType")
            return
        }

        val timestamp = System.currentTimeMillis()
        val messageId = buildDeterministicId(
            eventType, conversationId, timestamp, actor.uid, targetUserId
        )

        try {
            ChatApiClient.apiService.postSystemMessage(
                token = "Bearer $token",
                conversationId = conversationId,
                request = SystemMessageRequest(
                    messageId = messageId,
                    systemEventType = eventType.name,
                    systemEventPayload = payload.takeIf { it.isNotEmpty() },
                    targetUserId = targetUserId,
                    fallbackText = fallbackText,
                    timestamp = timestamp
                )
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(
                e,
                "Failed to write system message $eventType for $conversationId; underlying change still committed"
            )
        }
    }

    /**
     * Deterministic id keyed on a 1-second timestamp bucket so retries within
     * the same second collapse to one document. Adding `eventType` to the
     * key prefix lets two distinct field changes in the same update (e.g.
     * group name + description in one call) coexist with different ids.
     */
    private fun buildDeterministicId(
        eventType: SystemEventType,
        conversationId: String,
        timestampMs: Long,
        actorId: String,
        targetUserId: String?
    ): String {
        val bucket = timestampMs / 1000
        val target = targetUserId ?: ""
        return "sys_${eventType.name}_${conversationId}_${bucket}_${actorId}_$target"
    }

    companion object {
        private const val TAG = "SystemMessageWriter"
    }
}
