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
    val isActive: Boolean
)

fun SharedLocation.toUiModel(
    displayName: String = userId,
    timeAgoText: String,
    photoUrl: String? = null
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
        isActive = isSharingActive
    )
}
