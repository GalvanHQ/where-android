package com.ovi.where.data.repository

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.AuthRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class AuthRepositoryImpl
@Inject
constructor(
        private val firebaseAuth: FirebaseAuth,
        private val firestore: FirebaseFirestore,
        @ApplicationContext private val context: Context,
        private val lazyChatSocketIoClient: Lazy<com.ovi.where.data.remote.chat.ChatSocketIoClient>
) : AuthRepository {

    override val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    override val currentUser: Flow<User?>
        get() = callbackFlow {
            var snapshotRegistration: ListenerRegistration? = null

            val authListener =
                    FirebaseAuth.AuthStateListener { auth ->
                        val firebaseUser = auth.currentUser
                        // Remove previous snapshot listener on any auth state change
                        snapshotRegistration?.remove()
                        snapshotRegistration = null

                        if (firebaseUser != null) {
                            // Attach real-time snapshot listener on user document
                            snapshotRegistration =
                                    firestore
                                            .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                                            .document(firebaseUser.uid)
                                            .addSnapshotListener { snapshot, error ->
                                                if (error != null) {
                                                    Timber.w(
                                                            error,
                                                            "Snapshot listener error, falling back to Auth data"
                                                    )
                                                    trySend(
                                                                    User(
                                                                            id = firebaseUser.uid,
                                                                            displayName =
                                                                                    firebaseUser
                                                                                            .displayName
                                                                                            ?: "",
                                                                            email =
                                                                                    firebaseUser
                                                                                            .email
                                                                                            ?: "",
                                                                            photoUrl =
                                                                                    firebaseUser
                                                                                            .photoUrl
                                                                                            ?.toString(),
                                                                            isEmailVerified =
                                                                                    firebaseUser
                                                                                            .isEmailVerified
                                                                    )
                                                            )
                                                            .isSuccess
                                                    return@addSnapshotListener
                                                }
                                                // Cache-snapshot guard: a
                                                // cache snapshot showing
                                                // `exists() == false` for a
                                                // user we KNOW is signed in
                                                // is "not synced yet", not
                                                // "user doc deleted". Wait
                                                // for server snapshot
                                                // before falling through —
                                                // otherwise we briefly emit
                                                // a partial Auth-only User
                                                // and downstream UI flickers.
                                                if (snapshot != null
                                                    && !snapshot.exists()
                                                    && snapshot.metadata.isFromCache
                                                ) {
                                                    return@addSnapshotListener
                                                }
                                                if (snapshot != null && snapshot.exists()) {
                                                    val user =
                                                            snapshot.toObject(User::class.java)
                                                    val merged =
                                                            (user
                                                                            ?: User(
                                                                                    id =
                                                                                            firebaseUser
                                                                                                    .uid,
                                                                                    displayName =
                                                                                            firebaseUser
                                                                                                    .displayName
                                                                                                    ?: "",
                                                                                    email =
                                                                                            firebaseUser
                                                                                                    .email
                                                                                                    ?: "",
                                                                                    photoUrl =
                                                                                            firebaseUser
                                                                                                    .photoUrl
                                                                                                    ?.toString()
                                                                            ))
                                                                    .copy(
                                                                            isEmailVerified =
                                                                                    firebaseUser
                                                                                            .isEmailVerified
                                                                    )
                                                    trySend(merged).isSuccess
                                                }
                                            }
                        } else {
                            trySend(null).isSuccess
                        }
                    }

            firebaseAuth.addAuthStateListener(authListener)
            awaitClose {
                snapshotRegistration?.remove()
                firebaseAuth.removeAuthStateListener(authListener)
            }
        }

    // ── Email sign-in ─────────────────────────────────────────────────────────

    override suspend fun signInWithEmail(email: String, password: String): Resource<User> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Resource.Error("User ID not found")
            val firebaseUser = result.user!!

            // Try Firestore first; fall back to Firebase Auth data if Firestore is offline.
            val user = fetchUserWithFallback(userId, email, firebaseUser.displayName)

            // If the user document was never created, let's create it
            if (user.createdAt == 0L) {
                val newUser =
                        user.copy(
                                createdAt = System.currentTimeMillis(),
                                isEmailVerified = firebaseUser.isEmailVerified
                        )
                firestore
                        .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                        .document(userId)
                        .set(newUser)
                        .await()
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
            email: String,
            password: String
    ): Resource<User> {
        return try {
            // ① Create the Firebase Auth account
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Resource.Error("User creation failed")

            // ② Send email verification immediately
            result.user?.sendEmailVerification()?.await()

            // ③ Create a minimal Firestore user doc (no name/username → isProfileComplete = false)
            val user =
                    User(
                            id = userId,
                            email = email,
                            createdAt = System.currentTimeMillis(),
                            isEmailVerified = false
                    )

            firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
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
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val userId = result.user?.uid ?: return Resource.Error("User ID not found")
            val firebaseUser = result.user!!

            // Try Firestore; if offline create profile from Firebase Auth data.
            val user =
                    fetchUserWithFallback(
                            userId,
                            firebaseUser.email ?: "",
                            firebaseUser.displayName
                    )

            // If this is a new Google user whose profile doesn't exist in Firestore yet,
            // create a minimal doc (no username → isProfileComplete = false).
            if (user.createdAt == 0L) {
                val newUser =
                        User(
                                id = userId,
                                displayName = firebaseUser.displayName ?: "",
                                email = firebaseUser.email ?: "",
                                photoUrl = firebaseUser.photoUrl?.toString(),
                                createdAt = System.currentTimeMillis(),
                                isEmailVerified = firebaseUser.isEmailVerified
                                // username is "" → isProfileComplete = false
                                )
                firestore
                        .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                        .document(userId)
                        .set(newUser)
                        .await()
                Resource.Success(newUser)
            } else {
                Resource.Success(
                        user.copy(
                                photoUrl = user.photoUrl ?: firebaseUser.photoUrl?.toString(),
                                isEmailVerified = firebaseUser.isEmailVerified
                        )
                )
            }
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    // ── Sign-out ──────────────────────────────────────────────────────────────

    override suspend fun signOut() {
        // Best-effort FCM token cleanup BEFORE the Firebase sign-out wipes
        // our auth context. Leaving the token on `users/{uid}` would route
        // the previous account's pushes to whoever signs in next on this
        // device — a privacy bug we want to avoid.
        val uidToClear = currentUserId
        if (uidToClear != null) {
            try {
                firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(uidToClear)
                    .update(
                        "fcmToken",
                        com.google.firebase.firestore.FieldValue.delete()
                    )
                    .await()
            } catch (e: Exception) {
                // Non-fatal — the device may also delete the token client-side
                // below, and the Firestore trigger keeps `fcmToken` in sync on
                // the next session. Worst case is one orphaned push.
                Timber.w(e, "Failed to clear FCM token on sign-out")
            }
        }
        // Drop the local FCM token entirely so the next signed-in user gets
        // a freshly-issued one (FirebaseMessaging.onNewToken fires on the
        // re-login path and writes it back to Firestore).
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .deleteToken()
                .await()
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete local FCM token on sign-out")
        }

        // Requirement 21.2: On logout, cancel ChatSocketIoClient scope, disconnect socket,
        // cancel reconnection job, and reset TypingIndicatorManager.
        lazyChatSocketIoClient.get().logout()

        firebaseAuth.signOut()
        // Also sign out from Google Sign-In client to clear cached account,
        // so the account chooser appears on next Google sign-in attempt.
        try {
            val gso =
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions
                                            .DEFAULT_SIGN_IN
                            )
                            .build()
            val googleClient =
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
            googleClient.signOut().await()
        } catch (e: Exception) {
            Timber.w(e, "Google sign-out failed (non-fatal)")
        }
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
            firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId)
                    .update(updates)
                    .await()
            Resource.Success(fetchUserWithFallback(userId, "", displayName))
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    override suspend fun updateBio(bio: String): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")
            firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId)
                    .update("bio", bio)
                    .await()
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
            firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId)
                    .update("username", username.lowercase())
                    .await()
            Resource.Success(fetchUserWithFallback(userId, "", null))
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    override suspend fun checkUsernameAvailable(username: String): Boolean {
        val currentUid = currentUserId
        val docs =
                firestore
                        .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                        .whereEqualTo("username", username.lowercase())
                        .limit(1)
                        .get()
                        .await()
        // Available if empty OR the only match is the current user
        return docs.isEmpty || (docs.size() == 1 && docs.documents[0].id == currentUid)
    }

    // ── Home location ──────────────────────────────────────────────────────────

    override suspend fun updateHome(
            latitude: Double,
            longitude: Double,
            label: String
    ): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")
            firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId)
                    .update(
                            mapOf(
                                    "homeLatitude" to latitude,
                                    "homeLongitude" to longitude,
                                    "homeLabel" to label
                            )
                    )
                    .await()
            Resource.Success(
                    fetchUserWithFallback(userId, "", null)
                            .copy(
                                    homeLatitude = latitude,
                                    homeLongitude = longitude,
                                    homeLabel = label
                            )
            )
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    // ── Social links ─────────────────────────────────────────────────────────

    override suspend fun updateSocialLinks(
            facebookUrl: String,
            instagramUrl: String,
            linkedinUrl: String
    ): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")
            firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId)
                    .update(
                            mapOf(
                                    "facebookUrl" to facebookUrl,
                                    "instagramUrl" to instagramUrl,
                                    "linkedinUrl" to linkedinUrl
                            )
                    )
                    .await()
            Resource.Success(
                    fetchUserWithFallback(userId, "", null)
                            .copy(
                                    facebookUrl = facebookUrl,
                                    instagramUrl = instagramUrl,
                                    linkedinUrl = linkedinUrl
                            )
            )
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
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
                    firestore
                            .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                            .document(userId)
                            .update("isEmailVerified", true)
                            .await()
                } catch (_: Exception) {
                    /* best-effort */
                }
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
            username: String,
            photoUrl: String?
    ): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")

            if (!checkUsernameAvailable(username)) {
                return Resource.Error("This username is already taken.")
            }

            val updates =
                    hashMapOf<String, Any>(
                            "displayName" to displayName,
                            "username" to username.lowercase()
                    )
            if (photoUrl != null) {
                updates["photoUrl"] = photoUrl
            }

            firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(userId)
                    .update(updates)
                    .await()

            val updatedUser = fetchUserWithFallback(userId, "", displayName)
            Resource.Success(
                    if (photoUrl != null) updatedUser.copy(photoUrl = photoUrl) else updatedUser
            )
        } catch (e: Exception) {
            Resource.Error(mapFirebaseError(e))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fetches the user document from Firestore. If Firestore is offline or the doc doesn't exist
     * yet, returns a minimal [User] constructed from Firebase Auth data so auth flows are never
     * blocked.
     */
    private suspend fun fetchUserWithFallback(
            userId: String,
            email: String,
            displayName: String?
    ): User {
        return try {
            val doc =
                    firestore
                            .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                            .document(userId)
                            .get() // explicit server fetch; no stale cache confusion
                            .await()
            doc.toObject(User::class.java)
                    ?: User(id = userId, displayName = displayName ?: "", email = email)
        } catch (e: Exception) {
            Timber.w(e, "Firestore unavailable for user $userId, using Auth data as fallback")
            // Return a minimal User from whatever we know from Firebase Auth
            val firebaseUser = firebaseAuth.currentUser
            User(
                    id = userId,
                    displayName = displayName ?: firebaseUser?.displayName ?: "",
                    email = email.ifBlank { firebaseUser?.email ?: "" },
                    photoUrl = firebaseUser?.photoUrl?.toString()
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
            message.contains("INVALID_LOGIN_CREDENTIALS") ||
                    message.contains("WRONG_PASSWORD") ||
                    message.contains("wrong-password") -> "Incorrect password. Please try again."
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
