package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.ActiveSharingState
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.SharedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    /**
     * Single source of truth for the currently-active sharing session.
     * Both the chat screen and the map screen read from this flow so they
     * always show the same target list and the same remaining time.
     *
     * The flow re-emits on a fixed cadence (~15s) while a session is active
     * so countdown UIs can rebind without each screen running its own ticker.
     */
    val activeSharingState: StateFlow<ActiveSharingState>

    /**
     * Starts a location sharing session for one or more targets (groups + direct friends).
     * If a session already exists, this REPLACES the target set entirely. Each target
     * gets the same expiry computed from [durationMinutes]. Use [addSharingTarget] to
     * add a target with its own duration without affecting existing recipients.
     */
    suspend fun startLocationSharing(targetIds: List<String>, durationMinutes: Long): Resource<Unit>

    /** Stops the active sharing session entirely. */
    suspend fun stopLocationSharing(): Resource<Unit>

    /**
     * Adds one target to the active sharing session with its own duration.
     * No-op if [targetId] is already in the session — use [removeSharingTarget] then add
     * if you want to reset the timer for that target.
     */
    suspend fun addSharingTarget(targetId: String, durationMinutes: Long): Resource<Unit>

    /** Removes one target from the active sharing session. Stops sharing if no targets remain. */
    suspend fun removeSharingTarget(targetId: String): Resource<Unit>
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

    /** Whether there's any active sharing session. */
    fun isSharingLocation(): Boolean

    /** Returns the list of target ids the current user is sharing with (empty if not sharing). */
    fun getSharingTargetIds(): List<String>

    /**
     * Returns the per-target expiry map for the active session.
     * Empty if not sharing. Long.MAX_VALUE means "until stopped".
     */
    fun getTargetExpiries(): Map<String, Long>

    /**
     * Returns the latest expiry across all targets, or null if not sharing.
     * Used by the foreground service to schedule its overall stop time.
     */
    fun getSharingExpiryTime(): Long?

    /** Checks Firestore for an active sharing session and restores in-memory state.
     *  Returns the list of target ids the user is currently sharing with (empty if none). */
    suspend fun checkSharingStatus(): List<String>

    /** Populate visibleTo field for a group share (fetches group member UIDs). */
    suspend fun getGroupMemberIds(groupId: String): List<String>

    // ── Meetup Destination ────────────────────────────────────────────────────

    /**
     * Sets a meetup destination for a group. All members will see this pin on the map
     * along with their distance and ETA to the destination. The creator's
     * UID is captured server-side via the auth token; [memberIds] seeds the
     * participants map so every member starts in `ON_THE_WAY`.
     */
    suspend fun setMeetupDestination(
        groupId: String,
        latitude: Double,
        longitude: Double,
        name: String,
        address: String = "",
        memberIds: List<String>
    ): Resource<Unit>

    /** Clears the active meetup destination for a group. Creator-only at the rule layer. */
    suspend fun clearMeetupDestination(groupId: String): Resource<Unit>

    /**
     * Updates the calling user's participation status on an active meetup
     * destination. Each participant flips their own entry — never anyone
     * else's. Firestore rules enforce that.
     */
    suspend fun updateMeetupParticipantStatus(
        groupId: String,
        status: com.ovi.where.domain.model.MeetupParticipantStatus
    ): Resource<Unit>

    /** Observes the meetup destination for a group (real-time updates). */
    fun observeMeetupDestination(groupId: String): Flow<MeetupDestination?>
}
