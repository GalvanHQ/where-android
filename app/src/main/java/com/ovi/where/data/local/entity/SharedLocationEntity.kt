package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ovi.where.domain.model.SharedLocation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

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
    val sharingStartedAt: Long = 0L,
    // Denormalized profile and targeting fields
    val photoUrl: String? = null,
    val targetType: String = "",
    val targetId: String = "",
    /** Denormalized "at home" flag — see [SharedLocation.isAtHome]. */
    val isAtHome: Boolean = false,
    /** JSON-serialized list of target ids for multi-target shares.
     *  When the sharer fans out to several groups + direct friends at once,
     *  the legacy [targetId] is empty and only [targetIdsJson] carries the
     *  full membership list. The chat header's "who's actively sharing"
     *  filter relies on this — without it, multi-target shares were
     *  invisible because Room dropped the field on its way to UI. */
    val targetIdsJson: String? = null,
    val visibleTo: String? = null  // JSON-serialized list of user IDs
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
        photoUrl = photoUrl,
        sharingStartedAt = sharingStartedAt,
        targetType = targetType,
        targetId = targetId,
        isAtHome = isAtHome,
        targetIds = targetIdsJson?.let {
            try {
                Json.parseToJsonElement(it).jsonArray.map { el -> el.jsonPrimitive.content }
            } catch (_: Exception) { emptyList() }
        } ?: emptyList(),
        visibleTo = visibleTo?.let {
            try {
                Json.parseToJsonElement(it).jsonArray.map { el -> el.jsonPrimitive.content }
            } catch (_: Exception) { emptyList() }
        } ?: emptyList()
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
        photoUrl = photoUrl,
        sharingStartedAt = sharingStartedAt,
        targetType = targetType,
        targetId = targetId,
        isAtHome = isAtHome,
        targetIdsJson = if (targetIds.isNotEmpty()) {
            JsonArray(targetIds.map { JsonPrimitive(it) }).toString()
        } else null,
        visibleTo = if (visibleTo.isNotEmpty()) {
            JsonArray(visibleTo.map { JsonPrimitive(it) }).toString()
        } else null
    )
}
