package com.ovi.where.domain.model

import java.util.concurrent.TimeUnit

/**
 * User-selectable durations when muting a conversation.
 *
 * Stored on the conversation doc as `mutedUntil[uid] = expiryEpochMs`. The
 * server (socket.js) honors this map when fanning out chat FCM, dropping
 * recipients whose entry hasn't expired yet (mentions still bypass — see
 * the mention bypass logic in sendFCM).
 *
 * `ALWAYS` is encoded as [Long.MAX_VALUE] so the server-side numeric
 * comparison stays simple ("now < expiry"). The UI surfaces it as
 * "Always" / "Until I unmute".
 */
enum class MuteOption(val durationMs: Long, val label: String) {
    FIFTEEN_MINUTES(TimeUnit.MINUTES.toMillis(15), "15 minutes"),
    ONE_HOUR(TimeUnit.HOURS.toMillis(1), "1 hour"),
    EIGHT_HOURS(TimeUnit.HOURS.toMillis(8), "8 hours"),
    ONE_DAY(TimeUnit.DAYS.toMillis(1), "24 hours"),
    ONE_WEEK(TimeUnit.DAYS.toMillis(7), "1 week"),
    ALWAYS(Long.MAX_VALUE, "Until I unmute");

    /** Computes the absolute expiry timestamp from "now". */
    fun expiryFromNow(now: Long = System.currentTimeMillis()): Long {
        return if (durationMs == Long.MAX_VALUE) Long.MAX_VALUE
        else now + durationMs
    }
}
