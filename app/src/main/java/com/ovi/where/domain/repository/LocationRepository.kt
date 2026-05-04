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

    fun observeGroupLocations(groupId: String): Flow<List<SharedLocation>>
    fun observeUserLocation(userId: String, groupId: String): Flow<SharedLocation?>
    fun isSharingLocation(groupId: String): Boolean
    fun getSharingExpiryTime(groupId: String): Long?
}
