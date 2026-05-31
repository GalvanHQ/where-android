package com.ovi.where.presentation.model

import com.ovi.where.domain.model.User

data class UserProfileUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val email: String,
    val bio: String,
    val photoUrl: String?,
    val isEmailVerified: Boolean,
    // ── Home + social ──────────────────────────────────────────────────────
    val homeLatitude: Double,
    val homeLongitude: Double,
    val homeLabel: String,
    val facebookUrl: String,
    val instagramUrl: String,
    val linkedinUrl: String
) {
    /** True when a home location has been set. */
    val hasHome: Boolean get() = homeLatitude != 0.0 || homeLongitude != 0.0

    /** True when at least one social link is present. */
    val hasAnySocial: Boolean
        get() = facebookUrl.isNotBlank() || instagramUrl.isNotBlank() || linkedinUrl.isNotBlank()
}

fun User.toProfileUiModel(): UserProfileUiModel {
    return UserProfileUiModel(
        userId          = id,
        displayName     = displayName,
        username        = username,
        email           = email,
        bio             = bio,
        photoUrl        = photoUrl,
        isEmailVerified = isEmailVerified,
        homeLatitude    = homeLatitude,
        homeLongitude   = homeLongitude,
        homeLabel       = homeLabel,
        facebookUrl     = facebookUrl,
        instagramUrl    = instagramUrl,
        linkedinUrl     = linkedinUrl
    )
}
