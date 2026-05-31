package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: String?
    val currentUser: Flow<User?>

    suspend fun signInWithEmail(email: String, password: String): Resource<User>
    suspend fun registerWithEmail(email: String, password: String): Resource<User>
    suspend fun signInWithGoogle(idToken: String): Resource<User>
    suspend fun signOut()
    fun resetPassword(email: String): Flow<Resource<Unit>>
    suspend fun updateProfile(displayName: String, photoUrl: String?): Resource<User>
    suspend fun updateBio(bio: String): Resource<User>
    suspend fun updateUsername(username: String): Resource<User>
    /** Returns true if the username is not already taken. */
    suspend fun checkUsernameAvailable(username: String): Boolean

    /**
     * Persists the user's home location. Passing all-zero coordinates clears
     * the home (the UI uses this for a "Remove home" action).
     */
    suspend fun updateHome(latitude: Double, longitude: Double, label: String): Resource<User>

    /**
     * Persists the user's social links. Each argument is stored verbatim;
     * empty string clears that link.
     */
    suspend fun updateSocialLinks(
        facebookUrl: String,
        instagramUrl: String,
        linkedinUrl: String
    ): Resource<User>

    // ── New: email verification & profile gating ─────────────────────────────
    /** Sends email verification to the currently signed-in Firebase Auth user. */
    suspend fun sendEmailVerification(): Resource<Unit>
    /** Reloads the Firebase Auth user (to refresh isEmailVerified). */
    suspend fun reloadUser(): Resource<User>
    suspend fun completeProfile(
        displayName: String,
        username: String,
        photoUrl: String?
    ): Resource<User>
}
