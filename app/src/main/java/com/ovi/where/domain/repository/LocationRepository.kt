package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
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
}
