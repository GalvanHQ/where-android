package com.ovi.where.domain.usecase.friend

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.BlockEntry
import com.ovi.where.domain.model.FriendEntry
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.RequestEntry
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.SocialSummary
import com.ovi.where.domain.repository.FriendshipRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use cases around the new friendship data model. Signatures match
 * `FriendshipRepository` (design §5.1).
 */

class SendFriendRequestUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(receiverId: String): Resource<Unit> =
        friendshipRepository.sendFriendRequest(receiverId)
}

class AcceptFriendRequestUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    /** @param requesterId uid of the user who sent the pending request. */
    suspend operator fun invoke(requesterId: String): Resource<Unit> =
        friendshipRepository.acceptFriendRequest(requesterId)
}

class DeclineFriendRequestUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    /** @param requesterId uid of the user who sent the pending request. */
    suspend operator fun invoke(requesterId: String): Resource<Unit> =
        friendshipRepository.declineFriendRequest(requesterId)
}

class RemoveFriendUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(friendId: String): Resource<Unit> =
        friendshipRepository.removeFriend(friendId)
}

class ObserveFriendsUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    operator fun invoke(): Flow<List<FriendEntry>> = friendshipRepository.observeFriends()
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

class CancelFriendRequestUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(receiverId: String): Resource<Unit> =
        friendshipRepository.cancelFriendRequest(receiverId)
}

class BlockUserUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(userId: String): Resource<Unit> =
        friendshipRepository.blockUser(userId)
}

class UnblockUserUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    suspend operator fun invoke(userId: String): Resource<Unit> =
        friendshipRepository.unblockUser(userId)
}

class ObserveIncomingRequestsUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    operator fun invoke(): Flow<List<RequestEntry>> =
        friendshipRepository.observeIncomingRequests()
}

class ObserveOutgoingRequestsUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    operator fun invoke(): Flow<List<RequestEntry>> =
        friendshipRepository.observeOutgoingRequests()
}

class ObserveSocialSummaryUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    operator fun invoke(): Flow<SocialSummary> =
        friendshipRepository.observeSocialSummary()
}

class ObserveBlockedUsersUseCase @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) {
    operator fun invoke(): Flow<List<BlockEntry>> =
        friendshipRepository.observeBlockedUsers()
}
