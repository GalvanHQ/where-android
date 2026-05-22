package com.ovi.where.domain.model

/**
 * Represents a meetup destination point set by a group member.
 *
 * When a destination is active, the map shows:
 * - A destination pin marker
 * - Distance and ETA from each member to the destination
 * - Each member's participation state (on the way / arrived / can't make it)
 *
 * The destination is stored on the group Firestore document under the
 * `meetupDestination` field. All members listen to changes in real time.
 *
 * Lifecycle:
 *  • The setter (creator) initializes [participants] with every group
 *    member set to [MeetupParticipantStatus.ON_THE_WAY]. The setter is the
 *    only one who can clear the destination manually, but the destination
 *    auto-clears once every participant is in a terminal status (ARRIVED
 *    or CANT_MAKE_IT).
 *  • Each member's device flips its own participant entry: ARRIVED on
 *    geo-detection, CANT_MAKE_IT when the user opts out.
 */
data class MeetupDestination(
    /** Latitude of the destination point. */
    val latitude: Double = 0.0,
    /** Longitude of the destination point. */
    val longitude: Double = 0.0,
    /** Human-readable name/label for the destination (e.g., "Starbucks Downtown"). */
    val name: String = "",
    /** Address string (optional, from reverse geocoding). */
    val address: String = "",
    /** UID of the user who set this destination — only they can manually clear it. */
    val setBy: String = "",
    /** Timestamp when the destination was set. */
    val setAt: Long = 0L,
    /** Whether the destination is currently active. */
    val isActive: Boolean = false,
    /**
     * Per-member participation status. Keyed by user uid; missing entries
     * default to [MeetupParticipantStatus.ON_THE_WAY] for forward-compat.
     * The creator seeds this with every group member at set time.
     */
    val participants: Map<String, MeetupParticipant> = emptyMap()
) {
    val hasValidLocation: Boolean
        get() = latitude != 0.0 && longitude != 0.0
}

/**
 * Per-member meetup participation entry. Stored as a nested map on the
 * destination so we keep "1 destination = 1 Firestore doc field" simple.
 */
data class MeetupParticipant(
    val status: MeetupParticipantStatus = MeetupParticipantStatus.ON_THE_WAY,
    /** Epoch millis of the last status update. */
    val updatedAt: Long = 0L
)

/**
 * Per-member meetup status.
 *
 *  • [ON_THE_WAY]    — default. App auto-shares this user's live location.
 *  • [ARRIVED]       — terminal. Set automatically when the user enters the
 *                      100m radius. Live share auto-stops.
 *  • [CANT_MAKE_IT]  — terminal. User opted out via the place card. Live
 *                      share auto-stops.
 *
 * "Inactive" is **not** a persistent status. It's derived per-render from
 * the last [SharedLocation.timestamp] (>5min stale = inactive) so we don't
 * pay write cost for a state that's already implicit in the existing
 * heartbeat stream.
 */
enum class MeetupParticipantStatus {
    ON_THE_WAY,
    ARRIVED,
    CANT_MAKE_IT;

    val isTerminal: Boolean
        get() = this == ARRIVED || this == CANT_MAKE_IT

    companion object {
        fun fromString(value: String?): MeetupParticipantStatus =
            value?.let {
                runCatching { valueOf(it) }.getOrNull()
            } ?: ON_THE_WAY
    }
}
