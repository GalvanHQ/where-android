package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.FriendshipRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendshipRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : FriendshipRepository {

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    override suspend fun sendFriendRequest(receiverId: String): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val id = UUID.randomUUID().toString()
            val friendship = Friendship(
                id = id,
                requesterId = uid,
                receiverId = receiverId,
                status = FriendshipStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
                .document(id)
                .set(friendship)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send friend request")
        }
    }

    override suspend fun acceptFriendRequest(friendshipId: String): Resource<Unit> {
        return try {
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
                .document(friendshipId)
                .update(
                    mapOf(
                        "status" to FriendshipStatus.ACCEPTED.name,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to accept request")
        }
    }

    override suspend fun declineFriendRequest(friendshipId: String): Resource<Unit> {
        return try {
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
                .document(friendshipId)
                .delete()
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to decline request")
        }
    }

    override suspend fun removeFriend(userId: String): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            // Find the friendship doc (could be in either direction)
            val asRequester = firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
                .whereEqualTo("requesterId", uid)
                .whereEqualTo("receiverId", userId)
                .get().await()
            val asReceiver = firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
                .whereEqualTo("requesterId", userId)
                .whereEqualTo("receiverId", uid)
                .get().await()

            val docs = asRequester.documents + asReceiver.documents
            for (doc in docs) {
                doc.reference.delete().await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to remove friend")
        }
    }

    override fun observeFriends(): Flow<List<User>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        // Listen to all accepted friendships where user is either requester or receiver
        val listener1 = firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
            .whereEqualTo("requesterId", uid)
            .whereEqualTo("status", FriendshipStatus.ACCEPTED.name)
            .addSnapshotListener { snapshot, _ ->
                val friendIds = snapshot?.toObjects(Friendship::class.java)
                    ?.map { it.receiverId } ?: emptyList()
                fetchUsersAndSend(friendIds, uid)
            }

        val listener2 = firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
            .whereEqualTo("receiverId", uid)
            .whereEqualTo("status", FriendshipStatus.ACCEPTED.name)
            .addSnapshotListener { snapshot, _ ->
                val friendIds = snapshot?.toObjects(Friendship::class.java)
                    ?.map { it.requesterId } ?: emptyList()
                fetchUsersAndSend(friendIds, uid)
            }

        awaitClose {
            listener1.remove()
            listener2.remove()
        }
    }

    private fun kotlinx.coroutines.channels.ProducerScope<List<User>>.fetchUsersAndSend(
        friendIds: List<String>,
        currentUid: String
    ) {
        if (friendIds.isEmpty()) {
            trySend(emptyList())
            return
        }
        // Chunk into 10s for Firestore whereIn limit
        val allUsers = mutableListOf<User>()
        val chunks = friendIds.chunked(10)
        // Use a simple fire-and-forget approach for the callback
        firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
            .whereIn("id", friendIds.take(10))
            .get()
            .addOnSuccessListener { snapshot ->
                allUsers.addAll(snapshot.toObjects(User::class.java))
                trySend(allUsers.toList())
            }
    }

    override fun observeFriendRequests(): Flow<List<Friendship>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val listener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
            .whereEqualTo("receiverId", uid)
            .whereEqualTo("status", FriendshipStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val requests = snapshot?.toObjects(Friendship::class.java) ?: emptyList()
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getFriendshipStatus(otherUserId: String): FriendshipStatus? {
        val uid = currentUid ?: return null
        return try {
            val asRequester = firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
                .whereEqualTo("requesterId", uid)
                .whereEqualTo("receiverId", otherUserId)
                .get().await()
            if (asRequester.documents.isNotEmpty()) {
                val friendship = asRequester.documents[0].toObject(Friendship::class.java)
                return friendship?.status
            }
            val asReceiver = firestore.collection(AppConstants.FIRESTORE_COLLECTION_FRIENDSHIPS)
                .whereEqualTo("requesterId", otherUserId)
                .whereEqualTo("receiverId", uid)
                .get().await()
            if (asReceiver.documents.isNotEmpty()) {
                val friendship = asReceiver.documents[0].toObject(Friendship::class.java)
                return friendship?.status
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun observeAllFriendLocations(): Flow<List<SharedLocation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        // Get user's groups first, then listen to all their locations
        val groupsListener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { groupSnapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val groupIds = groupSnapshot?.documents?.mapNotNull { it.id } ?: emptyList()
                val allLocations = mutableListOf<SharedLocation>()

                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                // Listen to locations from each group
                for (groupId in groupIds.take(5)) { // Limit to 5 groups for performance
                    firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                        .document(groupId)
                        .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                        .whereEqualTo("isSharingActive", true)
                        .get()
                        .addOnSuccessListener { locationSnapshot ->
                            val locations = locationSnapshot.toObjects(SharedLocation::class.java)
                                .filter { it.userId != uid }
                            allLocations.addAll(locations)
                            // Deduplicate by userId (keep most recent)
                            val deduped = allLocations
                                .groupBy { it.userId }
                                .mapValues { entry -> entry.value.maxByOrNull { it.timestamp }!! }
                                .values.toList()
                            trySend(deduped)
                        }
                }
            }

        awaitClose { groupsListener.remove() }
    }
}
