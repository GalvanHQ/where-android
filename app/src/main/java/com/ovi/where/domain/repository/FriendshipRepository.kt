package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.User
import kotlinx.coroutines.flow.Flow

interface FriendshipRepository {
    suspend fun sendFriendRequest(receiverId: String): Resource<Unit>
    suspend fun acceptFriendRequest(friendshipId: String): Resource<Unit>
    suspend fun acceptFriendRequestByUserId(userId: String): Resource<Unit>
    suspend fun declineFriendRequest(friendshipId: String): Resource<Unit>
    suspend fun declineFriendRequestByUserId(userId: String): Resource<Unit>
    suspend fun removeFriend(userId: String): Resource<Unit>
    fun observeFriends(): Flow<List<User>>
    fun observeFriendRequests(): Flow<List<Friendship>>
    suspend fun getFriendshipStatus(otherUserId: String): FriendshipStatus?
    fun observeAllFriendLocations(): Flow<List<SharedLocation>>
}
