package com.ovi.where.presentation.model

import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.User

/**
 * Sealed class representing the possible friendship actions available
 * when viewing another user's profile page.
 */
sealed class ProfileFriendshipAction {
    /** User can be added as a friend. */
    object AddFriend : ProfileFriendshipAction()
    /** A request has been sent and is waiting for acceptance. */
    object RequestSent : ProfileFriendshipAction()
    /** A request has been received — user can accept or decline. */
    object RequestReceived : ProfileFriendshipAction()
    /** Already friends — can message or unfriend. */
    object AlreadyFriends : ProfileFriendshipAction()
}

fun FriendshipStatus?.toProfileAction(): ProfileFriendshipAction = when (this) {
    FriendshipStatus.ACCEPTED -> ProfileFriendshipAction.AlreadyFriends
    FriendshipStatus.PENDING  -> ProfileFriendshipAction.RequestSent
    else                      -> ProfileFriendshipAction.AddFriend
}

/**
 * Presentation model for viewing another user's profile.
 * Distinct from [UserProfileUiModel] (the signed-in user's own profile).
 * Only exposes the fields the UI renders.
 */
data class OtherUserProfileUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val bio: String,
    val photoUrl: String?,
    val avatarInitial: String,
    val friendshipAction: ProfileFriendshipAction
)

fun User.toOtherProfileUiModel(status: FriendshipStatus?): OtherUserProfileUiModel =
    OtherUserProfileUiModel(
        userId           = id,
        displayName      = displayName,
        username         = username,
        bio              = bio,
        photoUrl         = photoUrl,
        avatarInitial    = displayName.take(1).uppercase().ifEmpty { "?" },
        friendshipAction = status.toProfileAction()
    )
