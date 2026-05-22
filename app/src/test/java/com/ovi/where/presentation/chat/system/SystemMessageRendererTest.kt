package com.ovi.where.presentation.chat.system

import com.ovi.where.domain.model.SystemEventType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Feature: group-system-messages
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
class SystemMessageRendererTest {

    private val me = "me"
    private val them = "them"

    private fun render(
        eventType: SystemEventType,
        payload: Map<String, String> = emptyMap(),
        actorId: String = them,
        targetId: String? = null,
        actorName: String = "Sara",
        targetName: String? = "Alex"
    ): String = SystemMessageRenderer.render(
        eventType = eventType,
        payload = payload,
        actorName = actorName,
        targetName = targetName,
        currentUserId = me,
        actorId = actorId,
        targetId = targetId,
        fallback = "fallback"
    )

    // ─── GROUP_RENAMED ────────────────────────────────────────────────────────
    @Test fun `GROUP_RENAMED with newName uses You for self actor`() {
        val s = render(
            SystemEventType.GROUP_RENAMED,
            payload = mapOf("newName" to "Trip"),
            actorId = me
        )
        assertEquals("You renamed the group to \"Trip\"", s)
    }

    @Test fun `GROUP_RENAMED with newName uses actor name for other`() {
        val s = render(
            SystemEventType.GROUP_RENAMED,
            payload = mapOf("newName" to "Trip"),
            actorId = them
        )
        assertEquals("Sara renamed the group to \"Trip\"", s)
    }

    @Test fun `GROUP_RENAMED without newName falls back gracefully`() {
        val s = render(SystemEventType.GROUP_RENAMED, actorId = me)
        assertEquals("You renamed the group", s)
    }

    // ─── GROUP_DESCRIPTION_CHANGED ────────────────────────────────────────────
    @Test fun `GROUP_DESCRIPTION_CHANGED self`() {
        val s = render(SystemEventType.GROUP_DESCRIPTION_CHANGED, actorId = me)
        assertEquals("You updated the group description", s)
    }

    @Test fun `GROUP_DESCRIPTION_CHANGED other`() {
        val s = render(SystemEventType.GROUP_DESCRIPTION_CHANGED, actorId = them)
        assertEquals("Sara updated the group description", s)
    }

    // ─── GROUP_PHOTO_CHANGED ──────────────────────────────────────────────────
    @Test fun `GROUP_PHOTO_CHANGED self`() {
        val s = render(SystemEventType.GROUP_PHOTO_CHANGED, actorId = me)
        assertEquals("You changed the group photo", s)
    }

    @Test fun `GROUP_PHOTO_CHANGED other`() {
        val s = render(SystemEventType.GROUP_PHOTO_CHANGED, actorId = them)
        assertEquals("Sara changed the group photo", s)
    }

    // ─── MEMBER_ADDED ─────────────────────────────────────────────────────────
    @Test fun `MEMBER_ADDED other adds me`() {
        val s = render(SystemEventType.MEMBER_ADDED, actorId = them, targetId = me)
        assertEquals("Sara added you", s)
    }

    @Test fun `MEMBER_ADDED I add other`() {
        val s = render(SystemEventType.MEMBER_ADDED, actorId = me, targetId = them, targetName = "Alex")
        assertEquals("You added Alex", s)
    }

    @Test fun `MEMBER_ADDED self-reference falls back to joined`() {
        val s = render(SystemEventType.MEMBER_ADDED, actorId = me, targetId = me)
        assertEquals("You joined the group", s)
    }

    // ─── MEMBER_REMOVED ───────────────────────────────────────────────────────
    @Test fun `MEMBER_REMOVED other removes me`() {
        val s = render(SystemEventType.MEMBER_REMOVED, actorId = them, targetId = me)
        assertEquals("Sara removed you", s)
    }

    @Test fun `MEMBER_REMOVED self-reference falls back to left`() {
        val s = render(SystemEventType.MEMBER_REMOVED, actorId = me, targetId = me)
        assertEquals("You left the group", s)
    }

    // ─── MEMBER_LEFT / MEMBER_JOINED ──────────────────────────────────────────
    @Test fun `MEMBER_LEFT other`() {
        assertEquals("Sara left the group", render(SystemEventType.MEMBER_LEFT, actorId = them))
    }

    @Test fun `MEMBER_LEFT self`() {
        assertEquals("You left the group", render(SystemEventType.MEMBER_LEFT, actorId = me))
    }

    @Test fun `MEMBER_JOINED other`() {
        assertEquals("Sara joined the group", render(SystemEventType.MEMBER_JOINED, actorId = them))
    }

    @Test fun `MEMBER_JOINED self`() {
        assertEquals("You joined the group", render(SystemEventType.MEMBER_JOINED, actorId = me))
    }

    // ─── MEMBER_PROMOTED / DEMOTED ────────────────────────────────────────────
    @Test fun `MEMBER_PROMOTED self promotes other`() {
        val s = render(SystemEventType.MEMBER_PROMOTED, actorId = me, targetId = them, targetName = "Alex")
        assertEquals("You made Alex an admin", s)
    }

    @Test fun `MEMBER_PROMOTED other promotes me`() {
        val s = render(SystemEventType.MEMBER_PROMOTED, actorId = them, targetId = me)
        assertEquals("Sara made you an admin", s)
    }

    @Test fun `MEMBER_PROMOTED self-reference falls back`() {
        val s = render(SystemEventType.MEMBER_PROMOTED, actorId = me, targetId = me)
        assertEquals("You became an admin", s)
    }

    @Test fun `MEMBER_DEMOTED self demotes other`() {
        val s = render(SystemEventType.MEMBER_DEMOTED, actorId = me, targetId = them, targetName = "Alex")
        assertEquals("You removed Alex as admin", s)
    }

    @Test fun `MEMBER_DEMOTED self-reference falls back`() {
        val s = render(SystemEventType.MEMBER_DEMOTED, actorId = me, targetId = me)
        assertEquals("You stepped down as admin", s)
    }

    // ─── NICKNAME_CHANGED ─────────────────────────────────────────────────────
    @Test fun `NICKNAME_CHANGED self sets other's nickname`() {
        val s = render(
            SystemEventType.NICKNAME_CHANGED,
            payload = mapOf("newNickname" to "Lex"),
            actorId = me, targetId = them, targetName = "Alex"
        )
        assertEquals("You set Alex's nickname to \"Lex\"", s)
    }

    @Test fun `NICKNAME_CHANGED other sets my nickname`() {
        val s = render(
            SystemEventType.NICKNAME_CHANGED,
            payload = mapOf("newNickname" to "Bossman"),
            actorId = them, targetId = me
        )
        assertEquals("Sara set your nickname to \"Bossman\"", s)
    }

    @Test fun `NICKNAME_CHANGED cleared`() {
        val s = render(
            SystemEventType.NICKNAME_CHANGED,
            payload = mapOf("newNickname" to ""),
            actorId = me, targetId = them, targetName = "Alex"
        )
        assertEquals("You cleared Alex's nickname", s)
    }

    // ─── THEME_COLOR / EMOJI_SHORTCUT ─────────────────────────────────────────
    @Test fun `THEME_COLOR_CHANGED self`() {
        assertEquals("You changed the chat color", render(SystemEventType.THEME_COLOR_CHANGED, actorId = me))
    }

    @Test fun `EMOJI_SHORTCUT_CHANGED with newEmoji`() {
        val s = render(
            SystemEventType.EMOJI_SHORTCUT_CHANGED,
            payload = mapOf("newEmoji" to "🚀"),
            actorId = me
        )
        assertEquals("You set the emoji shortcut to 🚀", s)
    }

    @Test fun `EMOJI_SHORTCUT_CHANGED without payload`() {
        val s = render(SystemEventType.EMOJI_SHORTCUT_CHANGED, actorId = them)
        assertEquals("Sara changed the emoji shortcut", s)
    }

    // ─── LIVE_LOCATION_STARTED ────────────────────────────────────────────────
    @Test fun `LIVE_LOCATION_STARTED self`() {
        assertEquals(
            "You started sharing your live location",
            render(SystemEventType.LIVE_LOCATION_STARTED, actorId = me)
        )
    }

    @Test fun `LIVE_LOCATION_STARTED other`() {
        assertEquals(
            "Sara started sharing their live location",
            render(SystemEventType.LIVE_LOCATION_STARTED, actorId = them)
        )
    }

    @Test fun `LOCATION_SHARED self`() {
        assertEquals(
            "You shared your location",
            render(SystemEventType.LOCATION_SHARED, actorId = me)
        )
    }

    @Test fun `LOCATION_SHARED other`() {
        assertEquals(
            "Sara shared their location",
            render(SystemEventType.LOCATION_SHARED, actorId = them)
        )
    }

    // ─── USER_BLOCKED ─────────────────────────────────────────────────────────
    @Test fun `USER_BLOCKED renders to current user only`() {
        val s = render(SystemEventType.USER_BLOCKED, actorId = them, targetId = me)
        assertEquals("Sara blocked you", s)
    }

    // ─── Fallback ─────────────────────────────────────────────────────────────
    @Test fun `null eventType returns fallback`() {
        val s = SystemMessageRenderer.render(
            eventType = null,
            payload = emptyMap(),
            actorName = "Sara",
            targetName = null,
            currentUserId = me,
            actorId = them,
            targetId = null,
            fallback = "Sara did something"
        )
        assertEquals("Sara did something", s)
    }

    @Test fun `blank actor name falls back to Someone`() {
        val s = render(SystemEventType.GROUP_PHOTO_CHANGED, actorId = them, actorName = "")
        assertEquals("Someone changed the group photo", s)
    }
}
