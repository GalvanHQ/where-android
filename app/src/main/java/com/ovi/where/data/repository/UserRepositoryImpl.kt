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
                .document(userId).get().await()
            val user = doc.toObject(User::class.java)
            if (user != null) Resource.Success(user) else Resource.Error("User not found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch user")
        }
    }

    /**
     * Firestore `whereIn` supports a maximum of 10 values.
     * We chunk the list and merge the results.
     */
    override suspend fun getUsers(userIds: List<String>): Resource<List<User>> {
        return try {
            if (userIds.isEmpty()) return Resource.Success(emptyList())
            val users = mutableListOf<User>()
            // Chunk into batches of 10 (Firestore whereIn limit)
            for (chunk in userIds.distinct().chunked(10)) {
                val snapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .whereIn("id", chunk)
                    .get().await()
                users.addAll(snapshot.toObjects(User::class.java))
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
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObject(User::class.java)).isSuccess
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateUserStatus(isOnline: Boolean): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(uid)
                .update(
                    "isOnline", isOnline,
                    "lastSeen", System.currentTimeMillis()
                ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update user status")
        }
    }

    /**
     * Searches by both displayName prefix and username prefix, merges and deduplicates results.
     */
    override suspend fun searchUsers(query: String): Resource<List<User>> {
        return try {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return Resource.Success(emptyList())

            val byName = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .whereGreaterThanOrEqualTo("displayName", trimmed)
                .whereLessThanOrEqualTo("displayName", trimmed + "\uf8ff")
                .limit(20).get().await().toObjects(User::class.java)

            val byUsername = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .whereGreaterThanOrEqualTo("username", trimmed.lowercase())
                .whereLessThanOrEqualTo("username", trimmed.lowercase() + "\uf8ff")
                .limit(20).get().await().toObjects(User::class.java)

            // Merge + deduplicate, exclude self
            val uid = currentUid
            val merged = (byName + byUsername)
                .distinctBy { it.id }
                .filter { it.id != uid }
            Resource.Success(merged)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to search users")
        }
    }
}
