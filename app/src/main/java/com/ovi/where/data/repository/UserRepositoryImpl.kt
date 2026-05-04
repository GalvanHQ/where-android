package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : UserRepository {

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    override suspend fun getUser(userId: String): Resource<User> {
        return try {
            val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
            val user = doc.toObject(User::class.java)
            if (user != null) Resource.Success(user)
            else Resource.Error("User not found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch user")
        }
    }

    override suspend fun getUsers(userIds: List<String>): Resource<List<User>> {
        return try {
            val users = mutableListOf<User>()
            if (userIds.isNotEmpty()) {
                val query = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .whereIn("id", userIds)
                    .get()
                    .await()
                users.addAll(query.toObjects(User::class.java))
            }
            Resource.Success(users)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch users")
        }
    }

    override fun observeUser(userId: String): Flow<User?> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(User::class.java)
                trySend(user).isSuccess
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateUserStatus(isOnline: Boolean): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            
            val updates = hashMapOf<String, Any>(
                "isOnline" to isOnline,
                "lastSeen" to System.currentTimeMillis()
            )
            
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(uid)
                .update(updates)
                .await()
            
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update user status")
        }
    }

    override suspend fun searchUsers(query: String): Resource<List<User>> {
        return try {
            val results = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .whereGreaterThanOrEqualTo("displayName", query)
                .whereLessThanOrEqualTo("displayName", query + "\uf8ff")
                .limit(20)
                .get()
                .await()
                .toObjects(User::class.java)
            Resource.Success(results)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to search users")
        }
    }
}
