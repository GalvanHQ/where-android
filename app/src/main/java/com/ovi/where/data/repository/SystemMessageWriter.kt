package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.ovi.where.domain.model.SystemEventType
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a [SystemEventType] message into the chat timeline. Used by repos
 * that mutate state worth surfacing as a Messenger-style "info line" — group
 * renames, member changes, theme tweaks, etc.
 *
 * Idempotency: the message id is derived deterministically from
 * `(eventType, conversationId, timestamp_bucket, actorId, targetId?)` so a
 * retried write on the same logical event collapses into a single document
 * (Firestore `set` on the same id = overwrite, not duplicate).
 *
 * Notification suppression: writes go directly to Firestore, bypassing the
 * Node WebSocket server's FCM push pipeline. System events therefore never
 * generate phone notifications (Requirement 8.1) — this is by construction.
 *
 * See `.kiro/specs/group-system-messages/`.
 */
@Singleton
class SystemMessageWriter @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {

    /**
     * Writes the system message and updates the conversation's `lastMessage*`
     * preview fields. Failures log and swallow — the underlying state change
     * (which the caller has already committed) is more important than the
     * cosmetic timeline entry. Caller MUST have permission to read the
     * conversation; the Firestore rules already require this.
     *
     * @param conversationId target chat conversation id
     * @param eventType the typed event variant
     * @param targetUserId optional — the user the action was performed on
     * @param payload event-specific extras (e.g. "newName", "oldEmoji")
     * @param fallbackText English line written into `Message.text` for legacy clients
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
        val actorId = actor.uid
        val actorName = actor.displayName ?: ""

        val timestamp = System.currentTimeMillis()
        val messageId = buildDeterministicId(
            eventType, conversationId, timestamp, actorId, targetUserId
        )

        val payloadDoc = if (payload.isEmpty()) null else payload
        val docData = mapOf(
            "id" to messageId,
            "conversationId" to conversationId,
            "senderId" to actorId,
            "senderName" to actorName,
            "senderPhotoUrl" to actor.photoUrl?.toString(),
            "text" to fallbackText,
            "messageType" to "SYSTEM",
            "timestamp" to timestamp,
            "readBy" to emptyList<String>(),
            "reactions" to emptyMap<String, List<String>>(),
            "systemEventType" to eventType.name,
            "systemEventPayload" to payloadDoc,
            "targetUserId" to targetUserId
        )

        try {
            val convRef = firestore
                .collection("conversations")
                .document(conversationId)
            val msgRef = firestore.collection("messages").document(messageId)

            // Atomic batch: write the message archive + update the conversation
            // preview fields. unreadCounts intentionally NOT incremented —
            // system events are informational and shouldn't bump the badge.
            val batch = firestore.batch()
            batch.set(msgRef, docData)
            batch.update(
                convRef,
                mapOf(
                    "lastMessageText" to fallbackText,
                    "lastMessageType" to "SYSTEM",
                    "lastMessageSenderId" to actorId,
                    "lastMessageTimestamp" to timestamp,
                    "recentMessages" to FieldValue.arrayUnion(
                        // Strip conversationId from embedded copy (matches Node persistMessage)
                        docData - "conversationId"
                    )
                )
            )
            batch.commit().await()
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
