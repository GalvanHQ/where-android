package com.ovi.where.presentation.model

import com.ovi.where.domain.model.SharedLocation

data class MemberLocationUiModel(
    val id: String,
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val timeAgo: String,
    val hasValidLocation: Boolean,
    val isActive: Boolean,
    /** Distance from the current user in meters (null if user location unavailable). */
    val distanceMeters: Double? = null,
    /** Formatted ETA string (e.g., "5 min", "12 min") based on speed or driving estimate. */
    val etaText: String? = null,
    /** Speed in m/s for directional/movement indicators. */
    val speed: Float = 0f,
    /** Bearing in degrees for directional arrow display. */
    val bearing: Float = 0f
)

fun SharedLocation.toUiModel(
    displayName: String = userId,
    timeAgoText: String,
    photoUrl: String? = null,
    distanceMeters: Double? = null,
    etaText: String? = null
): MemberLocationUiModel {
    return MemberLocationUiModel(
        id = id,
        userId = userId,
        displayName = displayName.take(20),
        photoUrl = photoUrl,
        latitude = latitude,
        longitude = longitude,
        timeAgo = timeAgoText,
        hasValidLocation = latitude != 0.0 && longitude != 0.0,
        isActive = isSharingActive,
        distanceMeters = distanceMeters,
        etaText = etaText,
        speed = speed,
        bearing = bearing
    )
}
