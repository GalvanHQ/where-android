package com.ovi.where.domain.model

/**
 * Represents a meetup destination point set by a group admin or member.
 *
 * When a destination is active, the map shows:
 * - A destination pin marker
 * - Distance from each member to the destination
 * - ETA for each member to reach the destination
 *
 * The destination is stored in the group document in Firestore under
 * the `meetupDestination` field and is visible to all group members.
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
    /** UID of the user who set this destination. */
    val setBy: String = "",
    /** Timestamp when the destination was set. */
    val setAt: Long = 0L,
    /** Whether the destination is currently active. */
    val isActive: Boolean = false
) {
    val hasValidLocation: Boolean
        get() = latitude != 0.0 && longitude != 0.0
}
