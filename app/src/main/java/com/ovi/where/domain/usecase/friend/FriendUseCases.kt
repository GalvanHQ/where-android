package com.ovi.where.domain.usecase.friend

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.FriendshipRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SendFriendRequestUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(receiverId: String): Resource<Unit> =
        friendshipRepository.sendFriendRequest(receiverId)
}

class AcceptFriendRequestUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(friendshipId: String): Resource<Unit> =
        friendshipRepository.acceptFriendRequest(friendshipId)
}

class DeclineFriendRequestUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(friendshipId: String): Resource<Unit> =
        friendshipRepository.declineFriendRequest(friendshipId)
}

class RemoveFriendUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(userId: String): Resource<Unit> =
        friendshipRepository.removeFriend(userId)
}

class ObserveFriendsUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    operator fun invoke(): Flow<List<User>> = friendshipRepository.observeFriends()
}

class ObserveFriendRequestsUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    operator fun invoke(): Flow<List<Friendship>> = friendshipRepository.observeFriendRequests()
}

class GetFriendshipStatusUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(otherUserId: String): FriendshipStatus? =
        friendshipRepository.getFriendshipStatus(otherUserId)
}

class ObserveAllFriendLocationsUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    operator fun invoke(): Flow<List<SharedLocation>> =
        friendshipRepository.observeAllFriendLocations()
}
