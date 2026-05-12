package com.ovi.where.data.repository.fake

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.BlockEntry
import com.ovi.where.domain.model.FriendEntry
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.RequestEntry
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.SocialSummary
import com.ovi.where.domain.repository.FriendshipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Test double for [FriendshipRepository] that stores state in [MutableStateFlow]s
 * and records callable invocations in [callLog].
 *
 * Usage:
 * - Call `setFriends(...)`, `setIncomingRequests(...)`, etc. to seed observable state.
 * - Set [nextWriteResult] to control what write methods return.
 * - Populate [statusMap] to control `getFriendshipStatus` results.
 * - Inspect [callLog] to assert which write methods were invoked and with what args.
 */
class FakeFriendshipRepository : FriendshipRepository {

    // ─── Observable state ────────────────────────────────────────────
    private val _friends = MutableStateFlow<List<FriendEntry>>(emptyList())
    private val _incomingRequests = MutableStateFlow<List<RequestEntry>>(emptyList())
    private val _outgoingRequests = MutableStateFlow<List<RequestEntry>>(emptyList())
    private val _socialSummary = MutableStateFlow(SocialSummary())
    private val _blockedUsers = MutableStateFlow<List<BlockEntry>>(emptyList())

    // ─── Test setup helpers ──────────────────────────────────────────
    fun setFriends(friends: List<FriendEntry>) {
        _friends.value = friends
    }

    fun setIncomingRequests(requests: List<RequestEntry>) {
        _incomingRequests.value = requests
    }

    fun setOutgoingRequests(requests: List<RequestEntry>) {
        _outgoingRequests.value = requests
    }

    fun setSocialSummary(summary: SocialSummary) {
        _socialSummary.value = summary
    }

    fun setBlockedUsers(blocked: List<BlockEntry>) {
        _blockedUsers.value = blocked
    }

    // ─── Call recording ──────────────────────────────────────────────
    val callLog: MutableList<Pair<String, Map<String, Any>>> = mutableListOf()

    // ─── Configurable write result ───────────────────────────────────
    var nextWriteResult: Resource<Unit> = Resource.Success(Unit)

    // ─── Configurable friendship status map ──────────────────────────
    val statusMap: MutableMap<String, FriendshipStatus?> = mutableMapOf()

    // ─── Configurable friendship map (full objects) ──────────────────
    val friendshipMap: MutableMap<String, Friendship?> = mutableMapOf()

    // ─── Writes — record and return configurable result ──────────────

    override suspend fun sendFriendRequest(receiverId: String): Resource<Unit> {
        callLog.add("sendFriendRequest" to mapOf("receiverId" to receiverId))
        return nextWriteResult
    }

    override suspend fun cancelFriendRequest(receiverId: String): Resource<Unit> {
        callLog.add("cancelFriendRequest" to mapOf("receiverId" to receiverId))
        return nextWriteResult
    }

    override suspend fun acceptFriendRequest(requesterId: String): Resource<Unit> {
        callLog.add("acceptFriendRequest" to mapOf("requesterId" to requesterId))
        return nextWriteResult
    }

    override suspend fun declineFriendRequest(requesterId: String): Resource<Unit> {
        callLog.add("declineFriendRequest" to mapOf("requesterId" to requesterId))
        return nextWriteResult
    }

    override suspend fun removeFriend(friendId: String): Resource<Unit> {
        callLog.add("removeFriend" to mapOf("friendId" to friendId))
        return nextWriteResult
    }

    override suspend fun blockUser(userId: String): Resource<Unit> {
        callLog.add("blockUser" to mapOf("userId" to userId))
        return nextWriteResult
    }

    override suspend fun unblockUser(userId: String): Resource<Unit> {
        callLog.add("unblockUser" to mapOf("userId" to userId))
        return nextWriteResult
    }

    // ─── Reads — return MutableStateFlow-backed flows ────────────────

    override fun observeFriends(): Flow<List<FriendEntry>> = _friends

    override fun observeIncomingRequests(): Flow<List<RequestEntry>> = _incomingRequests

    override fun observeOutgoingRequests(): Flow<List<RequestEntry>> = _outgoingRequests

    override fun observeSocialSummary(): Flow<SocialSummary> = _socialSummary

    override fun observeBlockedUsers(): Flow<List<BlockEntry>> = _blockedUsers

    // ─── One-shot ────────────────────────────────────────────────────

    override suspend fun getFriendshipStatus(otherUserId: String): FriendshipStatus? {
        return statusMap[otherUserId]
    }

    override suspend fun getFriendship(otherUserId: String): Friendship? {
        return friendshipMap[otherUserId]
    }

    override fun observeAllFriendLocations(): Flow<List<SharedLocation>> = flowOf(emptyList())
}
