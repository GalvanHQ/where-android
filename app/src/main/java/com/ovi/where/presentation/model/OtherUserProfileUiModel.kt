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
    /** The caller has blocked this user. */
    object Blocked : ProfileFriendshipAction()
    /** This user has blocked the caller. */
    object BlockedByThem : ProfileFriendshipAction()
}

/**
 * Maps a [FriendshipStatus] to the appropriate [ProfileFriendshipAction].
 *
 * For [FriendshipStatus.PENDING], the direction is determined by comparing
 * [requesterId] with [callerUid]: if the caller initiated the request it's
 * [ProfileFriendshipAction.RequestSent], otherwise [ProfileFriendshipAction.RequestReceived].
 *
 * For [FriendshipStatus.BLOCKED], the blocker is identified by [requesterId]
 * (the `blockUser` callable sets `requesterId = callerUid` on the pair doc).
 * If the caller is the blocker → [ProfileFriendshipAction.Blocked],
 * otherwise → [ProfileFriendshipAction.BlockedByThem].
 *
 * @param callerUid The authenticated user's uid.
 * @param requesterId The `requesterId` field from the canonical Friendship document,
 *   or null when no document exists.
 */
fun FriendshipStatus?.toProfileAction(
    callerUid: String,
    requesterId: String?
): ProfileFriendshipAction = when (this) {
    FriendshipStatus.ACCEPTED -> ProfileFriendshipAction.AlreadyFriends
    FriendshipStatus.PENDING -> {
        if (requesterId == callerUid) ProfileFriendshipAction.RequestSent
        else ProfileFriendshipAction.RequestReceived
    }
    FriendshipStatus.BLOCKED -> {
        if (requesterId == callerUid) ProfileFriendshipAction.Blocked
        else ProfileFriendshipAction.BlockedByThem
    }
    else -> ProfileFriendshipAction.AddFriend
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
    val isOnline: Boolean,
    val lastSeen: Long,
    val createdAt: Long,
    val friendshipAction: ProfileFriendshipAction
)

fun User.toOtherProfileUiModel(
    status: FriendshipStatus?,
    callerUid: String,
    requesterId: String?
): OtherUserProfileUiModel =
    OtherUserProfileUiModel(
        userId           = id,
        displayName      = displayName,
        username         = username,
        bio              = bio,
        photoUrl         = photoUrl,
        avatarInitial    = displayName.take(1).uppercase().ifEmpty { "?" },
        isOnline         = isOnline,
        lastSeen         = lastSeen,
        createdAt        = createdAt,
        friendshipAction = status.toProfileAction(callerUid, requesterId)
    )
