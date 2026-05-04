package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

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
                            trySend(doc.toObject(User::class.java)).isSuccess
                        }
                        .addOnFailureListener {
                            trySend(null).isSuccess
                        }
                } else {
                    trySend(null).isSuccess
                }
            }
            firebaseAuth.addAuthStateListener(authListener)
            awaitClose { firebaseAuth.removeAuthStateListener(authListener) }
        }

    override suspend fun signInWithEmail(email: String, password: String): Resource<User> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Resource.Error("User ID not found")
            val userDoc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
            val user = userDoc.toObject(User::class.java)
                ?: return Resource.Error("User data not found")
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun registerWithEmail(name: String, email: String, password: String): Resource<User> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Resource.Error("User creation failed")
            val user = User(
                id = userId,
                displayName = name,
                email = email,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId)
                .set(user)
                .await()
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Registration failed")
        }
    }

    override suspend fun signInWithGoogle(): Resource<User> {
        return Resource.Error("Google Sign-In not implemented yet")
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    override fun resetPassword(email: String): Flow<Resource<Unit>> = callbackFlow {
        try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            trySend(Resource.Success(Unit)).isSuccess
        } catch (e: Exception) {
            trySend(Resource.Error(e.message ?: "Failed to send reset email")).isSuccess
        }
        close()
    }

    override suspend fun updateProfile(displayName: String, photoUrl: String?): Resource<User> {
        return try {
            val userId = currentUserId ?: return Resource.Error("Not authenticated")
            val updates = hashMapOf<String, Any>(
                "displayName" to displayName
            )
            if (photoUrl != null) {
                updates["photoUrl"] = photoUrl
            }
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId)
                .update(updates)
                .await()
            val userDoc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
            val user = userDoc.toObject(User::class.java)
                ?: return Resource.Error("User data not found")
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update profile")
        }
    }
}
