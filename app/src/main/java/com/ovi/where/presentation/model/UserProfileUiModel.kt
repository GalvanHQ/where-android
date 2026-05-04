package com.ovi.where.presentation.model

import com.ovi.where.domain.model.User

data class UserProfileUiModel(
    val displayName: String,
    val email: String,
    val photoUrl: String?
)

fun User.toProfileUiModel(): UserProfileUiModel {
    return UserProfileUiModel(
        displayName = displayName,
        email = email,
        photoUrl = photoUrl
    )
}
