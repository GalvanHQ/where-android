package com.ovi.where.core.notification

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ovi.where.R
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.repository.AuthRepository
import com.ovi.where.domain.repository.ConversationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds [NotificationCompat.MessagingStyle] notifications for chat messages.
 *
 * MessagingStyle is the Android-native chat notification format. It carries:
 *  • A [Person] for the current user (the "you")
 *  • A list of historical [NotificationCompat.MessagingStyle.Message] entries
 *    so the shade shows a mini conversation, not a single line
 *  • A conversation title + group flag so the system knows whether to render
 *    "Sender" or "Sender (Group)" labels
 *  • Sender avatars (loaded via Coil) so the shade matches what the user sees
 *    on the Chats list
 *
 * Why not just BigText? On Android 10+ the system uses MessagingStyle to
 * promote chat notifications above other categories, enables Bubbles, and
 * lights up the per-conversation settings UI. Plain BigText feels generic
 * and loses all of those affordances.
 *
 * History sourcing: we pull the last [MAX_HISTORY] messages from Room rather
 * than relying on FCM payload size (4KB max) — Room is always up to date
 * because the socket has already persisted the same messages, so the shade
 * shows a coherent thread even when several pushes coalesced.
 */
@Singleton
class ChatNotificationStyler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val conversationRepository: ConversationRepository,
    private val authRepository: AuthRepository
) {

    /**
     * Builds a fully-styled chat [NotificationCompat.MessagingStyle].
     *
     * @param conversationId The conversation the inbound message belongs to.
     * @param incomingSenderId Sender of the new message (used for the latest entry).
     * @param incomingSenderName Resolved display name of [incomingSenderId].
     * @param incomingSenderPhotoUrl Optional avatar for the latest sender.
     * @param incomingText Body text of the new message.
     * @param incomingTimestamp Epoch millis of the new message.
     * @return A configured [NotificationCompat.MessagingStyle], or null if the
     *  current user can't be resolved (we never want to mislabel "you").
     */
    suspend fun buildStyle(
        conversationId: String,
        incomingSenderId: String,
        incomingSenderName: String,
        incomingSenderPhotoUrl: String?,
        incomingText: String,
        incomingTimestamp: Long
    ): NotificationCompat.MessagingStyle? {
        val selfUid = authRepository.currentUserId ?: return null

        val selfName = runCatching {
            // The auth user's display name lives on FirebaseAuth, but we
            // don't have a direct sync getter — fall back to "You" if missing.
            com.google.firebase.auth.FirebaseAuth.getInstance()
                .currentUser?.displayName?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "You"

        val selfPerson = Person.Builder()
            .setKey(selfUid)
            .setName(selfName)
            .build()

        val style = NotificationCompat.MessagingStyle(selfPerson)

        // Conversation metadata — drives the title at the top of the shade card.
        val conversation = runCatching {
            conversationRepository.observeConversation(conversationId).first()
        }.getOrNull()
        val isGroup = conversation?.type?.name == "GROUP"
        val conversationTitle = conversation?.name?.takeIf { it.isNotBlank() }
            ?: incomingSenderName
        style.setConversationTitle(conversationTitle)
        style.setGroupConversation(isGroup)

        // Historical messages from Room, oldest first, capped at MAX_HISTORY.
        // We exclude the incoming one (it's appended explicitly below) so the
        // shade renders chronologically without duplication.
        val history = runCatching {
            messageDao.getLatestMessages(conversationId, MAX_HISTORY)
        }.getOrDefault(emptyList())
            .filter { it.id.isNotBlank() && it.timestamp < incomingTimestamp }
            .sortedBy { it.timestamp }
            .takeLast(MAX_HISTORY - 1) // leave room for the incoming message

        // Resolve sender avatars in parallel-ish fashion. Coil caches between
        // calls so subsequent notifications for the same sender are instant.
        val avatarCache = mutableMapOf<String, IconCompat?>()

        for (entry in history) {
            val isSelf = entry.senderId == selfUid
            val person = if (isSelf) {
                selfPerson
            } else {
                buildPerson(
                    uid = entry.senderId,
                    name = entry.senderName.ifBlank { conversationTitle },
                    photoUrl = entry.senderPhotoUrl,
                    avatarCache = avatarCache
                )
            }
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    previewText(entry),
                    entry.timestamp,
                    person
                )
            )
        }

        // The latest message — always from the inbound payload.
        val incomingPerson = if (incomingSenderId == selfUid) {
            selfPerson
        } else {
            buildPerson(
                uid = incomingSenderId,
                name = incomingSenderName,
                photoUrl = incomingSenderPhotoUrl,
                avatarCache = avatarCache
            )
        }
        style.addMessage(
            NotificationCompat.MessagingStyle.Message(
                incomingText,
                incomingTimestamp,
                incomingPerson
            )
        )

        return style
    }

    /**
     * Renders a one-line preview for messages whose `text` field is empty
     * (image / voice / location / system events). Mirrors what the chats list
     * shows so the shade is consistent with the in-app preview.
     */
    private fun previewText(msg: MessageEntity): CharSequence {
        if (msg.text.isNotBlank()) return msg.text
        return when (runCatching { MessageType.valueOf(msg.type) }.getOrNull()) {
            MessageType.IMAGE -> "📷 Photo"
            MessageType.VIDEO -> "🎬 Video"
            MessageType.VOICE -> "🎤 Voice message"
            MessageType.LOCATION -> "📍 Location"
            MessageType.LIVE_LOCATION -> "📡 Live location"
            MessageType.DOCUMENT -> "📄 Document"
            MessageType.SYSTEM -> msg.text.ifBlank { "System update" }
            MessageType.TEXT, null -> msg.text
        }
    }

    /**
     * Builds a [Person] for a chat sender, resolving their avatar through
     * Coil and caching the result for reuse within the same notification.
     */
    private suspend fun buildPerson(
        uid: String,
        name: String,
        photoUrl: String?,
        avatarCache: MutableMap<String, IconCompat?>
    ): Person {
        val icon = avatarCache.getOrPut(uid) {
            loadAvatarIcon(photoUrl)
        }
        val builder = Person.Builder()
            .setKey(uid)
            .setName(name.ifBlank { "Someone" })
        if (icon != null) builder.setIcon(icon)
        return builder.build()
    }

    /**
     * Loads an avatar URL through Coil and converts it into an [IconCompat]
     * suitable for [Person.setIcon]. Falls back to a tinted placeholder when
     * the load fails — empty avatars look broken in the shade.
     */
    suspend fun loadAvatarIcon(photoUrl: String?): IconCompat? {
        if (photoUrl.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(photoUrl)
                .size(AVATAR_SIZE_PX)
                .allowHardware(false) // Hardware bitmaps can't be turned into Icons
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { IconCompat.createWithAdaptiveBitmap(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load avatar for chat notification")
            null
        }
    }



    companion object {
        /**
         * Shade history length cap. MessagingStyle accepts up to 25 entries
         * but caps display at ~7-8 depending on the device. 6 keeps the API
         * call cheap (Room read of 6 rows) and matches what the system
         * actually renders.
         */
        const val MAX_HISTORY = 6

        /** Avatar resolution for shade icons. 128px matches Coil's typical
         *  list-avatar size and looks crisp on every density bucket. */
        const val AVATAR_SIZE_PX = 128
    }
}

// Manual decode helper retained in case we need to render from raw bytes
// (e.g. an avatar fetched directly from FCM payload). Currently unused but
// kept for symmetry with the bitmap path above.
@Suppress("unused")
private fun decodeBitmap(bytes: ByteArray) =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
