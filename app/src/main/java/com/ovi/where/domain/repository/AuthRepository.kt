package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: String?
    val currentUser: Flow<User?>

    suspend fun signInWithEmail(email: String, password: String): Resource<User>
    suspend fun registerWithEmail(
        name: String,
        username: String,
        email: String,
        password: String
    ): Resource<User>
    suspend fun signInWithGoogle(idToken: String): Resource<User>
    suspend fun signOut()
    fun resetPassword(email: String): Flow<Resource<Unit>>
    suspend fun updateProfile(displayName: String, photoUrl: String?): Resource<User>
    suspend fun updateBio(bio: String): Resource<User>
    suspend fun updateUsername(username: String): Resource<User>
    /** Returns true if the username is not already taken. */
    suspend fun checkUsernameAvailable(username: String): Boolean
}
