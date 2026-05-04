package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUser(userId: String): Resource<User>
    suspend fun getUsers(userIds: List<String>): Resource<List<User>>
    fun observeUser(userId: String): Flow<User?>
    suspend fun updateUserStatus(isOnline: Boolean): Resource<Unit>
    suspend fun searchUsers(query: String): Resource<List<User>>
}
