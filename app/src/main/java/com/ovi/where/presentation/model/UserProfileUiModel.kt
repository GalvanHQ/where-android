package com.ovi.where.presentation.model

import com.ovi.where.domain.model.User

data class UserProfileUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val email: String,
    val bio: String,
    val photoUrl: String?
)

fun User.toProfileUiModel(): UserProfileUiModel {
    return UserProfileUiModel(
        userId      = id,
        displayName = displayName,
        username    = username,
        email       = email,
        bio         = bio,
        photoUrl    = photoUrl
    )
}
