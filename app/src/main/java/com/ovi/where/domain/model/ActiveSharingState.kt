package com.ovi.where.domain.model

/**
 * Single source-of-truth snapshot of the user's currently-active live location
 * sharing session.
 *
 * Exposed as a [kotlinx.coroutines.flow.StateFlow] from `LocationRepository` so
 * the chat screen and the map screen consume the exact same state and tick on
 * the same cadence — eliminating drift between their countdown labels.
 */
data class ActiveSharingState(
    /** Per-target expiry map. Empty when no session is active. */
    val targetExpiries: Map<String, Long> = emptyMap(),
    /** Wall-clock timestamp the snapshot was produced at, used by countdown UIs. */
    val nowMs: Long = System.currentTimeMillis()
) {
    val isSharing: Boolean get() = targetExpiries.isNotEmpty()
    val targetIds: List<String> get() = targetExpiries.keys.toList()
    /** Latest expiry across all targets, or null if continuous / no session. */
    val overallExpiry: Long?
        get() = targetExpiries.values.maxOrNull()?.takeIf { it != Long.MAX_VALUE }
}
