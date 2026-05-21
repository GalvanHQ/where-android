package com.ovi.where.presentation.common

/**
 * Single formatter for live-location sharing countdowns.
 *
 * Both `ChatScreen` (header pill + meetup sheet) and `GlobalMapScreen` render
 * the same active session, so they call this helper to make sure their labels
 * always match — no per-screen drift, no off-by-one rounding.
 *
 * Two flavours:
 *  - [formatRemaining]   → "1h 30m" / "45m" — raw value, used in compact pills.
 *  - [formatRemainingWithSuffix] → "1h 30m left" / "Until you stop" / "Ending"
 *    — for places that show the value standalone.
 */
object SharingTimeFormatter {

    /**
     * Computes the remaining minutes from an expiry timestamp. Uses ceiling so
     * a freshly started 60-minute share displays as "60" not "59" the moment
     * the user taps "Share".
     *
     * @return remaining minutes, clamped to 0, or null for no/continuous expiry.
     */
    fun remainingMinutes(expiresAt: Long?, nowMs: Long = System.currentTimeMillis()): Long? {
        if (expiresAt == null || expiresAt == Long.MAX_VALUE) return null
        val remainingMs = expiresAt - nowMs
        if (remainingMs <= 0) return 0L
        return (remainingMs + 59_999L) / 60_000L
    }

    /** Compact form: "1h 30m" / "45m". Returns null if expiry is missing/continuous. */
    fun formatRemaining(expiresAt: Long?, nowMs: Long = System.currentTimeMillis()): String? {
        val minutes = remainingMinutes(expiresAt, nowMs) ?: return null
        return formatMinutes(minutes)
    }

    /** Same as [formatRemaining] but takes a precomputed minute count. */
    fun formatMinutes(minutes: Long): String {
        if (minutes < 1) return "<1m"
        if (minutes < 60) return "${minutes}m"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0L) "${h}h" else "${h}h ${m}m"
    }

    /** Standalone form: "1h 30m left" / "Until you stop" / "Ending". */
    fun formatRemainingWithSuffix(expiresAt: Long?, nowMs: Long = System.currentTimeMillis()): String {
        if (expiresAt == null || expiresAt == Long.MAX_VALUE) return "Until you stop"
        val remainingMs = expiresAt - nowMs
        if (remainingMs <= 0) return "Ending"
        val minutes = (remainingMs + 59_999L) / 60_000L
        return when {
            minutes < 1 -> "<1m left"
            minutes < 60 -> "${minutes}m left"
            else -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) "${h}h left" else "${h}h ${m}m left"
            }
        }
    }
}
