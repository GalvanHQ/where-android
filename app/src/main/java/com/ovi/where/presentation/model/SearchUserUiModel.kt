package com.ovi.where.presentation.model

import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.User

/**
 * Represents the friendship action available for a user in the search results.
 * Translates the domain FriendshipStatus enum into concrete UI affordances.
 */
enum class FriendshipActionUiModel {
    ADD,      // Can send a friend request
    PENDING,  // Request already sent, waiting for acceptance
    FRIENDS   // Already friends
}

fun FriendshipStatus?.toActionUiModel(): FriendshipActionUiModel = when (this) {
    FriendshipStatus.ACCEPTED -> FriendshipActionUiModel.FRIENDS
    FriendshipStatus.PENDING  -> FriendshipActionUiModel.PENDING
    else                      -> FriendshipActionUiModel.ADD
}

/**
 * Presentation model for a user result in SearchUsersScreen.
 * Pre-merges User + FriendshipStatus — no parallel map lookups in the composable.
 */
data class SearchUserUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val avatarInitial: String,
    val friendshipAction: FriendshipActionUiModel
)

fun User.toSearchUiModel(status: FriendshipStatus?): SearchUserUiModel = SearchUserUiModel(
    userId           = id,
    displayName      = displayName,
    username         = username,
    photoUrl         = photoUrl,
    avatarInitial    = displayName.take(1).uppercase().ifEmpty { "?" },
    friendshipAction = status.toActionUiModel()
)
