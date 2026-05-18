package com.ovi.where.presentation.model

import java.util.concurrent.TimeUnit

/**
 * Formats Messenger-style "active" subtitles for chat headers and rows.
 *
 * Behaviour mirrors Messenger / Instagram:
 *   - online                              → "Active now"
 *   - offline, lastSeen < 1m              → "Active now"
 *   - offline, lastSeen < 60m             → "Active 12m ago"
 *   - offline, lastSeen < 24h             → "Active 3h ago"
 *   - offline, lastSeen < 7d              → "Active 2d ago"
 *   - offline, lastSeen unknown / older   → "Offline"
 *
 * The `now` parameter is injectable so callers can render deterministically in tests.
 */
object LastActiveFormatter {

    fun format(
        isOnline: Boolean,
        lastSeen: Long,
        now: Long = System.currentTimeMillis()
    ): String {
        if (isOnline) return "Active now"
        if (lastSeen <= 0L) return "Offline"

        val diffMs = (now - lastSeen).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)

        return when {
            minutes < 1 -> "Active now"
            minutes < 60 -> "Active ${minutes}m ago"
            hours < 24 -> "Active ${hours}h ago"
            days < 7 -> "Active ${days}d ago"
            else -> "Offline"
        }
    }
}
