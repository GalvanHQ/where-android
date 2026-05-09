package com.ovi.where.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    /** Background scope for non-critical Firestore writes (profile creation, token save). */
    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    override val currentUser: Flow<User?>
        get() = callbackFlow {
            val authListener = FirebaseAuth.AuthStateListener { auth ->
                val firebaseUser = auth.currentUser
                if (firebaseUser != null) {
                    firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                        .document(firebaseUser.uid)
                        .get()
                        .addOnSuccessListener { doc ->
                            val user = doc.toObject(User::class.java)
                            // Merge email-verified flag from Firebase Auth
                            val merged = (user ?: User(
                                id          = firebaseUser.uid,
                                displayName = firebaseUser.displayName ?: "",
                                email       = firebaseUser.email ?: "",
                                photoUrl    = firebaseUser.photoUrl?.toString()
                            )).copy(isEmailVerified = firebaseUser.isEmailVerified)
                            trySend(merged).isSuccess
                        }
                        .addOnFailureListener {
                            // Firestore unavailable — emit minimal user from Firebase Auth so the
                            // app can still navigate to the main screen.
                            Timber.w(it, "currentUser Firestore read failed, using Auth data")
                            trySend(User(
                                id              = firebaseUser.uid,
                                displayName     = firebaseUser.displayName ?: "",
                                email           = firebaseUser.email ?: "",
                                photoUrl        = firebaseUser.photoUrl?.toString(),
                                isEmailVerified = firebaseUser.isEmailVerified
                            )).isSuccess
                        }
                } else {
                    trySend(null).isSuccess
                }
            }
            firebaseAuth.addAuthStateListener(authListener)
            awaitClose { firebaseAuth.removeAuthStateListener(authListener) }
        }

    // ── Email sign-in ─────────────────────────────────────────────────────────

    override suspend fun signInWithEmail(email: String, password: String): Resource<User> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val userId      = result.user?.uid ?: return Resource.Error("User ID not found")
            val firebaseUser = result.user!!

            // Try Firestore first; fall back to Firebase Auth data if Firestore is offline.
            val user = fetchUserWithFallback(userId, email, firebaseUser.displayName)
            
            // If the user document was never created, let's create it
            if (user.createdAt == 0L) {
                val newUser = user.copy(
                    createdAt       = System.currentTimeMillis(),
                    isEmailVerified = firebaseUser.isEmailVerified
                )
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId).set(newUser).await()
                return Resource.Success(newUser)
            }

            // Always merge the latest verification state from Firebase Auth
            return Resource.Success(user.copy(isEmailVerified = firebaseUser.isEmailVerified))
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    // ── Email registration ────────────────────────────────────────────────────

    override suspend fun registerWithEmail(
        name: String,
        username: String,
        email: String,
        password: String
    ): Resource<User> {
        return try {
            // Username uniqueness check — non-fatal if Firestore is offline
            if (username.isNotBlank() && !checkUsernameAvailable(username)) {
                return Resource.Error("This username is already taken.")
            }

            // ① Create the Firebase Auth account
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Resource.Error("User creation failed")

            // ② Send email verification immediately
            result.user?.sendEmailVerification()?.await()

            val user = User(
                id              = userId,
                displayName     = name,
                username        = username.lowercase(),
                email           = email,
                createdAt       = System.currentTimeMillis(),
                isEmailVerified = false  // just registered, not yet verified
            )

            // Write to Firestore and wait for it
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId)
                .set(user)
                .await()

            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    // ── Google sign-in ────────────────────────────────────────────────────────
    //
    // Key change: Google-signed-in users whose Firestore doc has no username
    // are NOT auto-completed. The caller gates on User.isProfileComplete.

    override suspend fun signInWithGoogle(idToken: String): Resource<User> {
        return try {
            val credential   = GoogleAuthProvider.getCredential(idToken, null)
            val result       = firebaseAuth.signInWithCredential(credential).await()
            val userId       = result.user?.uid ?: return Resource.Error("User ID not found")
            val firebaseUser = result.user!!

            // Try Firestore; if offline create profile from Firebase Auth data.
            val user = fetchUserWithFallback(userId, firebaseUser.email ?: "", firebaseUser.displayName)

            // If this is a new Google user whose profile doesn't exist in Firestore yet,
            // create a minimal doc (no username → isProfileComplete = false).
            if (user.createdAt == 0L) {
                val newUser = User(
                    id              = userId,
                    displayName     = firebaseUser.displayName ?: "",
                    email           = firebaseUser.email ?: "",
                    photoUrl        = firebaseUser.photoUrl?.toString(),
                    createdAt       = System.currentTimeMillis(),
                    isEmailVerified = firebaseUser.isEmailVerified
                    // username is "" → isProfileComplete = false
                )
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId).set(newUser).await()
                Resource.Success(newUser)
            } else {
                Resource.Success(user.copy(
                    photoUrl        = user.photoUrl ?: firebaseUser.photoUrl?.toString(),
                    isEmailVerified = firebaseUser.isEmailVerified
                ))
            }
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    // ── Sign-out ──────────────────────────────────────────────────────────────

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    // ── Password reset ────────────────────────────────────────────────────────

    override fun resetPassword(email: String): Flow<Resource<Unit>> = callbackFlow {
        try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            trySend(Resource.Success(Unit)).isSuccess
        } catch (e: Exception) {
            trySend(Resource.Error(e.message ?: "Failed to send reset email")).isSuccess
        }
        close()
    }

    // ── Profile updates ───────────────────────────────────────────────────────

    override suspend fun updateProfile(displayName: String, photoUrl: String?): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")
            val updates = hashMapOf<String, Any>("displayName" to displayName)
            if (photoUrl != null) updates["photoUrl"] = photoUrl
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId).update(updates).await()
            Resource.Success(fetchUserWithFallback(userId, "", displayName))
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    override suspend fun updateBio(bio: String): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId).update("bio", bio).await()
            Resource.Success(fetchUserWithFallback(userId, "", null))
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    override suspend fun updateUsername(username: String): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")
            if (!checkUsernameAvailable(username)) {
                return Resource.Error("This username is already taken.")
            }
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId).update("username", username.lowercase()).await()
            Resource.Success(fetchUserWithFallback(userId, "", null))
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    override suspend fun checkUsernameAvailable(username: String): Boolean {
        return try {
            val currentUid = currentUserId
            val docs = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .whereEqualTo("username", username.lowercase())
                .limit(1)
                .get().await()
            // Available if empty OR the only match is the current user
            docs.isEmpty || (docs.size() == 1 && docs.documents[0].id == currentUid)
        } catch (e: Exception) {
            true // assume available on error — don't block registration
        }
    }

    // ── Email verification ───────────────────────────────────────────────────

    override suspend fun sendEmailVerification(): Resource<Unit> {
        return try {
            val user = firebaseAuth.currentUser ?: return Resource.Error("Not authenticated")
            user.sendEmailVerification().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    override suspend fun reloadUser(): Resource<User> {
        return try {
            val fbUser = firebaseAuth.currentUser ?: return Resource.Error("Not authenticated")
            fbUser.reload().await()
            val userId = fbUser.uid

            // Sync verification status to Firestore
            if (fbUser.isEmailVerified) {
                try {
                    firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                        .document(userId)
                        .update("isEmailVerified", true)
                        .await()
                } catch (_: Exception) { /* best-effort */ }
            }

            val user = fetchUserWithFallback(userId, fbUser.email ?: "", fbUser.displayName)
            Resource.Success(user.copy(isEmailVerified = fbUser.isEmailVerified))
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    // ── Profile completion (Google sign-in gating) ───────────────────────────

    override suspend fun completeProfile(
        displayName: String,
        username: String
    ): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")

            if (!checkUsernameAvailable(username)) {
                return Resource.Error("This username is already taken.")
            }

            val updates = hashMapOf<String, Any>(
                "displayName" to displayName,
                "username"    to username.lowercase()
            )
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId).update(updates).await()

            Resource.Success(fetchUserWithFallback(userId, "", displayName))
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fetches the user document from Firestore.
     * If Firestore is offline or the doc doesn't exist yet, returns a minimal [User]
     * constructed from Firebase Auth data so auth flows are never blocked.
     */
    private suspend fun fetchUserWithFallback(
        userId: String,
        email: String,
        displayName: String?
    ): User {
        return try {
            val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId)
                .get()   // explicit server fetch; no stale cache confusion
                .await()
            doc.toObject(User::class.java)
                ?: User(id = userId, displayName = displayName ?: "", email = email)
        } catch (e: Exception) {
            Timber.w(e, "Firestore unavailable for user $userId, using Auth data as fallback")
            // Return a minimal User from whatever we know from Firebase Auth
            val firebaseUser = firebaseAuth.currentUser
            User(
                id          = userId,
                displayName = displayName ?: firebaseUser?.displayName ?: "",
                email       = email.ifBlank { firebaseUser?.email ?: "" },
                photoUrl    = firebaseUser?.photoUrl?.toString()
            )
        }
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    private fun mapFirebaseError(e: Exception): String {
        val message = e.message ?: ""
        return when {
            // ── Firestore offline / unavailable ──────────────────────────────
            message.contains("client is offline", ignoreCase = true) ||
            message.contains("UNAVAILABLE", ignoreCase = true) ||
            e is FirebaseNetworkException ||
            e is java.io.IOException ->
                "Cannot reach the server. Please check your internet connection and try again."

            // ── Auth errors ──────────────────────────────────────────────────
            message.contains("INVALID_LOGIN_CREDENTIALS") || message.contains("WRONG_PASSWORD") ||
            message.contains("wrong-password") ->
                "Incorrect password. Please try again."

            message.contains("USER_NOT_FOUND") || message.contains("user-not-found") ->
                "No account found with this email."

            message.contains("EMAIL_ALREADY_IN_USE") || message.contains("email-already-in-use") ->
                "This email is already registered."

            message.contains("NETWORK_ERROR") || message.contains("network-request-failed") ->
                "No internet connection. Please check your network."

            message.contains("TOO_MANY_REQUESTS") || message.contains("too-many-requests") ->
                "Too many attempts. Please wait before trying again."

            message.contains("WEAK_PASSWORD") || message.contains("weak-password") ->
                "Password is too weak. Use at least 6 characters."

            message.contains("INVALID_EMAIL") || message.contains("invalid-email") ->
                "Invalid email format."

            message.contains("CREDENTIAL_TOO_OLD") || message.contains("requires-recent-login") ->
                "Please sign in again to continue."

            message.contains("PERMISSION_DENIED", ignoreCase = true) ||
            message.contains("permission-denied", ignoreCase = true) ->
                "Permission denied. Please check your Firestore Security Rules."

            else -> "Something went wrong. Please try again. (${e.message})"
        }
    }
}
