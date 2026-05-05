package com.ovi.where.presentation.model

import com.ovi.where.domain.model.User

/**
 * Presentation model for a friend shown in the People tab friends list.
 * Only exposes fields the UI actually needs — hides fcmToken, lastSeen, createdAt, etc.
 */
data class FriendUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val isOnline: Boolean,
    /** First letter of displayName, uppercased — used for avatar fallback. */
    val avatarInitial: String
)

fun User.toFriendUiModel(): FriendUiModel = FriendUiModel(
    userId        = id,
    displayName   = displayName,
    username      = username,
    photoUrl      = photoUrl,
    isOnline      = isOnline,
    avatarInitial = displayName.take(1).uppercase().ifEmpty { "?" }
)
