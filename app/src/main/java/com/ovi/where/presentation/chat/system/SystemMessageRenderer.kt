package com.ovi.where.presentation.chat.system

import com.ovi.where.domain.model.SystemEventType

/**
 * Pure-function renderer that turns a system message ([SystemEventType] +
 * payload + actor / target ids and names) into a single localised line for
 * the chat timeline.
 *
 * Has no Android dependencies, performs no I/O. The renderer is intentionally
 * stateless so it's trivially testable and shareable between the chat
 * timeline and the chat list preview row.
 *
 * See `.kiro/specs/group-system-messages/` for the full event matrix and
 * pronoun substitution rules (Requirement 6).
 */
object SystemMessageRenderer {

    /**
     * @param eventType the typed event variant authored by the Cloud Function
     * @param payload event-specific extras (e.g. `oldName`/`newName` for renames)
     * @param actorName resolved display name of the user who performed the action
     * @param targetName resolved display name of the user the action was performed on, or null
     * @param currentUserId the local user's uid — used for "You"/"you" substitution
     * @param actorId the id of the actor for pronoun substitution
     * @param targetId the id of the target (if any) for pronoun substitution
     * @param fallback used when the event type is unknown or required payload missing
     */
    fun render(
        eventType: SystemEventType?,
        payload: Map<String, String>,
        actorName: String,
        targetName: String?,
        currentUserId: String,
        actorId: String,
        targetId: String?,
        fallback: String
    ): String {
        if (eventType == null) return fallback

        val isSelfActor = actorId == currentUserId
        val isSelfTarget = targetId != null && targetId == currentUserId

        // Sentence-start "You" capitalised; mid-sentence "you" lowercase.
        val actor = if (isSelfActor) "You" else actorName.ifBlank { "Someone" }
        val target = when {
            isSelfTarget -> "you"
            !targetName.isNullOrBlank() -> targetName
            else -> "someone"
        }

        return when (eventType) {
            SystemEventType.GROUP_RENAMED -> {
                val newName = payload["newName"].orEmpty()
                if (newName.isBlank()) "$actor renamed the group"
                else "$actor renamed the group to \"$newName\""
            }

            SystemEventType.GROUP_DESCRIPTION_CHANGED ->
                "$actor updated the group description"

            SystemEventType.GROUP_PHOTO_CHANGED ->
                "$actor changed the group photo"

            SystemEventType.MEMBER_ADDED -> {
                if (isSelfActor && isSelfTarget) "You joined the group"
                else "$actor added $target"
            }

            SystemEventType.MEMBER_REMOVED -> {
                if (isSelfActor && isSelfTarget) "You left the group"
                else "$actor removed $target"
            }

            SystemEventType.MEMBER_LEFT ->
                "$actor left the group"

            SystemEventType.MEMBER_JOINED ->
                "$actor joined the group"

            SystemEventType.MEMBER_PROMOTED -> {
                if (isSelfActor && isSelfTarget) "You became an admin"
                else "$actor made $target an admin"
            }

            SystemEventType.MEMBER_DEMOTED -> {
                if (isSelfActor && isSelfTarget) "You stepped down as admin"
                else "$actor removed $target as admin"
            }

            SystemEventType.NICKNAME_CHANGED -> {
                val newNick = payload["newNickname"].orEmpty()
                val targetSuffix = if (isSelfTarget) "your" else "$target's"
                if (newNick.isNotBlank()) {
                    "$actor set $targetSuffix nickname to \"$newNick\""
                } else {
                    "$actor cleared $targetSuffix nickname"
                }
            }

            SystemEventType.THEME_COLOR_CHANGED ->
                "$actor changed the chat color"

            SystemEventType.EMOJI_SHORTCUT_CHANGED -> {
                val newEmoji = payload["newEmoji"].orEmpty()
                if (newEmoji.isBlank()) "$actor changed the emoji shortcut"
                else "$actor set the emoji shortcut to $newEmoji"
            }

            SystemEventType.LIVE_LOCATION_STARTED -> {
                if (isSelfActor) "You started sharing your live location"
                else "$actor started sharing their live location"
            }

            SystemEventType.USER_BLOCKED -> {
                // Renderer reaches this branch only on the blocked user's side
                // (filter happens upstream in MessageRepositoryImpl).
                "$actor blocked you"
            }
        }
    }
}
