package com.ovi.where.core.notification

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ovi.where.MainActivity
import com.ovi.where.R
import com.ovi.where.core.notification.ConversationShortcutManager.Companion.MAX_DYNAMIC_SHORTCUTS
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.repository.AuthRepository
import com.ovi.where.domain.repository.ConversationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages dynamic [ShortcutInfoCompat] entries — one per recently-active
 * conversation.
 *
 * Why shortcuts matter for chat notifications:
 *  • On Android 11+, attaching a [shortcutId] to a [androidx.core.app.NotificationCompat.Builder]
 *    makes the system treat the notification as a "conversation". Conversations
 *    are sorted ABOVE all other notifications, get larger avatars, expose
 *    "Priority", "Bubble", and "Silent" controls in the long-press menu, and
 *    can be promoted to the home screen as a Bubble.
 *  • The shortcut also doubles as a long-press launcher hand-off — users
 *    can pin a conversation to their home screen.
 *
 * We publish at most [MAX_DYNAMIC_SHORTCUTS] shortcuts (the system caps it
 * around 4-5 anyway). Each call refreshes the shortcut for the active
 * conversation; older shortcuts stay around but lose their [LocusIdCompat]
 * promotion when they fall outside the cap.
 *
 * The shortcut intent opens [MainActivity] with the same extra the FCM
 * service uses, so the deep-link path is identical regardless of how the
 * conversation was launched.
 */
@Singleton
class ConversationShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationRepository: ConversationRepository,
    private val authRepository: AuthRepository
) {

    /**
     * Publishes / refreshes the dynamic shortcut for [conversationId].
     * Returns the shortcut id (always equals [conversationId]) so the caller
     * can pass it to `NotificationCompat.Builder.setShortcutId`.
     */
    suspend fun pushShortcut(conversationId: String): String {
        val conversation = runCatching {
            conversationRepository.observeConversation(conversationId).first()
        }.getOrNull()

        val shortLabel = conversation?.name?.takeIf { it.isNotBlank() }
            ?: "Conversation"
        val longLabel = "$shortLabel — Where"

        val photoUrl = conversation?.photoUrl
            ?: resolveOtherParticipantPhoto(conversation)
        val icon = loadShortcutIcon(photoUrl)
            ?: IconCompat.createWithResource(context, R.drawable.ic_notification)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, "chat/$conversationId")
        }

        val person = buildPerson(conversation, conversationId, shortLabel, icon)

        val shortcut = ShortcutInfoCompat.Builder(context, conversationId)
            .setLocusId(LocusIdCompat(conversationId))
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(icon)
            .setIntent(intent)
            // setLongLived = true tells the system this shortcut is the
            // canonical handle for the conversation — the OS uses it for
            // bubble grouping, conversation widgets, etc.
            .setLongLived(true)
            .setPerson(person)
            .setCategories(setOf(CATEGORY_TEXT_SHARE_TARGET))
            .build()

        try {
            // Trim the dynamic shortcut list before adding so we stay under
            // the per-app cap. ShortcutManagerCompat.pushDynamicShortcut
            // already does LRU eviction, but calling it explicitly keeps the
            // intent obvious in the call site.
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (e: Exception) {
            Timber.w(e, "Failed to push conversation shortcut")
        }

        // Report the shortcut as used so the system promotes it. This is
        // what makes the OS treat the chat notification as "Priority"-eligible.
        runCatching {
            ShortcutManagerCompat.reportShortcutUsed(context, conversationId)
        }

        return conversationId
    }

    /** Wipes every conversation shortcut (e.g. on sign-out). */
    fun clearAll() {
        runCatching {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }
    }

    /**
     * Picks a sensible avatar for direct conversations: when the conversation
     * doc itself has no photo, fall back to the *other* participant's photo
     * (we don't want to show the current user's avatar on their own chat).
     */
    private fun resolveOtherParticipantPhoto(conversation: Conversation?): String? {
        if (conversation == null) return null
        val selfUid = authRepository.currentUserId ?: return null
        val otherUid = conversation.participantIds.firstOrNull { it != selfUid } ?: return null
        return conversation.participantPhotos[otherUid]
    }

    /**
     * Builds a [Person] for the conversation — for direct chats this is the
     * other participant; for group chats it's a synthetic Person describing
     * the group itself. Either way the system uses it as the icon when the
     * shortcut is rendered as a Bubble.
     */
    private fun buildPerson(
        conversation: Conversation?,
        conversationId: String,
        label: String,
        icon: IconCompat
    ): Person {
        val isGroup = conversation?.type?.name == "GROUP"
        val key = if (isGroup) "group:$conversationId"
            else conversation?.participantIds
                ?.firstOrNull { it != authRepository.currentUserId }
                ?: conversationId
        return Person.Builder()
            .setKey(key)
            .setName(label)
            .setIcon(icon)
            .setBot(false)
            .setImportant(true)
            .build()
    }

    private suspend fun loadShortcutIcon(photoUrl: String?): IconCompat? {
        if (photoUrl.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(photoUrl)
                .size(SHORTCUT_ICON_SIZE_PX)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { IconCompat.createWithAdaptiveBitmap(it) }
            } else null
        } catch (e: Exception) {
            Timber.w(e, "Failed to load shortcut icon")
            null
        }
    }

    companion object {
        /** Soft cap — actual cap is set per-OEM but typically 4-5. */
        const val MAX_DYNAMIC_SHORTCUTS = 4

        const val SHORTCUT_ICON_SIZE_PX = 192

        /**
         * Required category for the shortcut to participate in Direct Share /
         * Bubbles. The framework uses this constant verbatim; mirroring it
         * here avoids a dependency on the slice-builders artifact.
         */
        const val CATEGORY_TEXT_SHARE_TARGET =
            "android.shortcut.conversation"
    }
}
