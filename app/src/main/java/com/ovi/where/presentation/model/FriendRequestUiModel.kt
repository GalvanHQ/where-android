package com.ovi.where.presentation.model

import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.User

/**
 * Presentation model for a received friend request shown in FriendRequestsScreen.
 * Pre-merges Friendship + User so the composable does not need a map lookup on every recomposition.
 */
data class FriendRequestUiModel(
    val friendshipId: String,
    val requesterId: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val avatarInitial: String
)

fun Friendship.toUiModel(requester: User?): FriendRequestUiModel = FriendRequestUiModel(
    friendshipId  = id,
    requesterId   = requesterId,
    displayName   = requester?.displayName ?: "Unknown",
    username      = requester?.username ?: "",
    photoUrl      = requester?.photoUrl,
    avatarInitial = (requester?.displayName?.take(1)?.uppercase() ?: "?")
)
