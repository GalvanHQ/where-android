package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.SharedLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    suspend fun startLocationSharing(groupId: String, durationMinutes: Long): Resource<Unit>
    suspend fun stopLocationSharing(groupId: String): Resource<Unit>
    suspend fun updateLocation(
        groupId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float,
        bearing: Float
    ): Resource<Unit>

    /** Single consolidated listener — returns all locations visible to current user. */
    fun observeActiveLocations(): Flow<List<SharedLocation>>

    /**
     * Observes cached locations from Room database.
     * Serves cached positions within 100ms of screen open (Requirement 7.2).
     * Room is the single source of truth for location display.
     */
    fun observeCachedLocations(): Flow<List<SharedLocation>>

    /**
     * Observes locations using Socket.IO as primary source while connected.
     * Falls back to Firestore snapshot listener after 30s of disconnection.
     * On reconnect: removes Firestore listener and resumes Socket.IO.
     * (Requirements 6.4, 6.5)
     */
    fun observeLocationsWithSocketFallback(): Flow<List<SharedLocation>>

    /**
     * Observes locations with Socket.IO as primary source and Firestore as fallback.
     * If Socket.IO fails to deliver a location update within [fallbackTimeoutMs],
     * falls back to Firestore listener. All updates are persisted to Room cache.
     *
     * Requirement 7.3: Display cached locations within 100ms, subscribe to Socket.IO,
     * fall back to Firestore after 10s timeout.
     */
    fun observeLocationsWithCacheFallback(fallbackTimeoutMs: Long = 10_000L): Flow<List<SharedLocation>>

    // Legacy methods kept for backward compat
    fun observeGroupLocations(groupId: String): Flow<List<SharedLocation>>
    fun observeDirectLocationShares(friendIds: List<String>): Flow<List<SharedLocation>>
    fun observeUserLocation(userId: String, groupId: String): Flow<SharedLocation?>
    fun isSharingLocation(groupId: String): Boolean
    fun getSharingExpiryTime(groupId: String): Long?

    /** Checks Firestore for an active sharing session and restores in-memory state.
     *  Returns true if the user is currently sharing in this group. */
    suspend fun checkSharingStatus(groupId: String): Boolean

    /** Populate visibleTo field for a group share (fetches group member UIDs). */
    suspend fun getGroupMemberIds(groupId: String): List<String>

    // ── Meetup Destination ────────────────────────────────────────────────────

    /**
     * Sets a meetup destination for a group. All members will see this pin on the map
     * along with their distance and ETA to the destination.
     */
    suspend fun setMeetupDestination(
        groupId: String,
        latitude: Double,
        longitude: Double,
        name: String,
        address: String = ""
    ): Resource<Unit>

    /** Clears the active meetup destination for a group. */
    suspend fun clearMeetupDestination(groupId: String): Resource<Unit>

    /** Observes the meetup destination for a group (real-time updates). */
    fun observeMeetupDestination(groupId: String): Flow<MeetupDestination?>
}
