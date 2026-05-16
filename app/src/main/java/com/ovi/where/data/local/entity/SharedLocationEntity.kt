package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ovi.where.domain.model.SharedLocation

@Entity(tableName = "shared_location")
data class SharedLocationEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val groupId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long,
    val isSharingActive: Boolean,
    val sharingExpiresAt: Long,
    // Display info for live location bubble rendering
    val displayName: String = "",
    val sharingStartedAt: Long = 0L
)

fun SharedLocationEntity.toDomain(): SharedLocation {
    return SharedLocation(
        id = id,
        userId = userId,
        groupId = groupId,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        speed = speed,
        bearing = bearing,
        timestamp = timestamp,
        isSharingActive = isSharingActive,
        sharingExpiresAt = sharingExpiresAt,
        displayName = displayName,
        sharingStartedAt = sharingStartedAt
    )
}

fun SharedLocation.toEntity(): SharedLocationEntity {
    return SharedLocationEntity(
        id = id,
        userId = userId,
        groupId = groupId,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        speed = speed,
        bearing = bearing,
        timestamp = timestamp,
        isSharingActive = isSharingActive,
        sharingExpiresAt = sharingExpiresAt,
        displayName = displayName,
        sharingStartedAt = sharingStartedAt
    )
}
