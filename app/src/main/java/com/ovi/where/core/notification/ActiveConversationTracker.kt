package com.ovi.where.core.notification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the conversation the user is currently viewing on screen.
 *
 * The chat screen sets and clears this from its lifecycle observer. When
 * an FCM message notification arrives, [FcmMessagingService] checks this
 * tracker — if the active conversation matches the inbound notification,
 * we drop the system tray push (the user is already looking at the
 * messages) but still persist it to the in-app inbox so the unread badge
 * is correct on first background.
 *
 * Implemented as a process-singleton because Android Activities and the
 * FCM service share the same process and the gating is purely a runtime
 * UI cue — no persistence needed.
 */
@Singleton
class ActiveConversationTracker @Inject constructor() {

    @Volatile
    private var activeConversationId: String? = null

    /** Sets the currently-foregrounded conversation. Pass null to clear. */
    fun setActive(conversationId: String?) {
        activeConversationId = conversationId
    }

    /** Returns true if [conversationId] is the one the user is viewing. */
    fun isActive(conversationId: String?): Boolean {
        if (conversationId.isNullOrBlank()) return false
        return activeConversationId == conversationId
    }
}
