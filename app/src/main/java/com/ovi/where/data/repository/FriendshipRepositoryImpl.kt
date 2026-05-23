package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.model.BlockEntry
import com.ovi.where.domain.model.FriendEntry
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.FriendshipIds
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.RequestEntry
import com.ovi.where.domain.model.RequestInbox
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.SocialSummary
import com.ovi.where.domain.repository.FriendshipRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [FriendshipRepository] against the new
 * denormalized data model (design §5.2).
 *
 * - Read methods use single snapshot listeners on per-user subcollections/docs.
 * - Write methods route through Firebase Cloud Functions callables.
 * - [observeAllFriendLocations] is unchanged from the legacy implementation.
 */
@Singleton
class FriendshipRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : FriendshipRepository {

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    // ═══════════════════════════════════════════════════════════════════
    // Reads — listeners (tasks 4.1–4.7)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Task 4.1 — Single listener on `users/{uid}/friends` ordered by displayName.
     * Emits empty list when unauthenticated. No truncation.
     */
    override fun observeFriends(): Flow<List<FriendEntry>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val reg = firestore.collection("users").document(uid).collection("friends")
            .orderBy("displayName")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                // Cache-snapshot guard: an empty cache snapshot for a user
                // who has friends doesn't mean they unfriended everyone.
                // Suppress so the People tab doesn't briefly empty out.
                if (snap != null && snap.metadata.isFromCache && snap.isEmpty) {
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(FriendEntry::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Task 4.2 — Single-doc listener on `users/{uid}/inbox/friendRequests`.
     * Emits entries sorted by sentAt descending. Empty list when doc absent.
     */
    override fun observeIncomingRequests(): Flow<List<RequestEntry>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val reg = firestore.collection("users").document(uid)
            .collection("inbox").document("friendRequests")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap != null && !snap.exists() && snap.metadata.isFromCache) {
                    return@addSnapshotListener
                }
                val inbox = snap?.toObject(RequestInbox::class.java) ?: RequestInbox()
                trySend(inbox.entries.values.sortedByDescending { it.sentAt })
            }
        awaitClose { reg.remove() }
    }

    /**
     * Task 4.3 — Single-doc listener on `users/{uid}/outbox/friendRequests`.
     */
    override fun observeOutgoingRequests(): Flow<List<RequestEntry>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val reg = firestore.collection("users").document(uid)
            .collection("outbox").document("friendRequests")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap != null && !snap.exists() && snap.metadata.isFromCache) {
                    return@addSnapshotListener
                }
                val outbox = snap?.toObject(RequestInbox::class.java) ?: RequestInbox()
                trySend(outbox.entries.values.sortedByDescending { it.sentAt })
            }
        awaitClose { reg.remove() }
    }

    /**
     * Task 4.4 — Single-doc listener on `users/{uid}/summary/social`.
     * Emits zero-valued SocialSummary when doc absent.
     */
    override fun observeSocialSummary(): Flow<SocialSummary> = callbackFlow {
        val uid = currentUid ?: run { trySend(SocialSummary()); close(); return@callbackFlow }
        val reg = firestore.collection("users").document(uid)
            .collection("summary").document("social")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap != null && !snap.exists() && snap.metadata.isFromCache) {
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(SocialSummary::class.java) ?: SocialSummary())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Task 4.5 — Subcollection listener on `users/{uid}/blocks`.
     */
    override fun observeBlockedUsers(): Flow<List<BlockEntry>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val reg = firestore.collection("users").document(uid).collection("blocks")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap != null && snap.metadata.isFromCache && snap.isEmpty) {
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(BlockEntry::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Task 4.6 — Single deterministic get on `friendships/{pairId}`.
     * Returns null when no document exists (absence == NONE).
     */
    override suspend fun getFriendshipStatus(otherUserId: String): FriendshipStatus? {
        val uid = currentUid ?: return null
        return try {
            val pairId = FriendshipIds.pairId(uid, otherUserId)
            val doc = firestore.collection("friendships").document(pairId).get().await()
            doc.toObject(Friendship::class.java)?.status
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the full [Friendship] object for the pair (caller, otherUserId).
     * Returns null when no document exists or the user is unauthenticated.
     */
    override suspend fun getFriendship(otherUserId: String): Friendship? {
        val uid = currentUid ?: return null
        return try {
            val pairId = FriendshipIds.pairId(uid, otherUserId)
            val doc = firestore.collection("friendships").document(pairId).get().await()
            doc.toObject(Friendship::class.java)
        } catch (_: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Writes — Cloud Functions callables (tasks 5.1–5.7)
    // ═══════════════════════════════════════════════════════════════════

    /** Task 5.1 */
    override suspend fun sendFriendRequest(receiverId: String): Resource<Unit> =
        callCallable("sendFriendRequest", mapOf("receiverId" to receiverId))

    /** Task 5.2 */
    override suspend fun cancelFriendRequest(receiverId: String): Resource<Unit> =
        callCallable("cancelFriendRequest", mapOf("receiverId" to receiverId))

    /** Task 5.3 */
    override suspend fun acceptFriendRequest(requesterId: String): Resource<Unit> =
        callCallable("acceptFriendRequest", mapOf("requesterId" to requesterId))

    /** Task 5.4 */
    override suspend fun declineFriendRequest(requesterId: String): Resource<Unit> =
        callCallable("declineFriendRequest", mapOf("requesterId" to requesterId))

    /** Task 5.5 */
    override suspend fun removeFriend(friendId: String): Resource<Unit> =
        callCallable("removeFriend", mapOf("friendId" to friendId))

    /** Task 5.6 */
    override suspend fun blockUser(userId: String): Resource<Unit> =
        callCallable("blockUser", mapOf("userId" to userId))

    /** Task 5.7 */
    override suspend fun unblockUser(userId: String): Resource<Unit> =
        callCallable("unblockUser", mapOf("userId" to userId))

    /**
     * Shared helper that invokes a named Cloud Function callable with the given
     * payload and maps the result to [Resource]. Zero client-side Firestore reads.
     */
    private suspend fun callCallable(name: String, data: Map<String, Any>): Resource<Unit> {
        return try {
            timber.log.Timber.d("FriendshipRepo: calling '$name' with data=$data")
            val result = functions.getHttpsCallable(name).call(data).await()
            timber.log.Timber.d("FriendshipRepo: '$name' success, result=${result.getData()}")
            Resource.Success(Unit)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "FriendshipRepo: '$name' FAILED: ${e.message}")
            Resource.Error(e.message ?: "Failed to call $name")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Unchanged per design §5.1
    // ═══════════════════════════════════════════════════════════════════

    override fun observeAllFriendLocations(): Flow<List<SharedLocation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val groupsListener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { groupSnapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }

                // Cache-snapshot guard: if the cache hasn't loaded our
                // groups yet, suppress instead of saying "no locations".
                if (groupSnapshot != null
                    && groupSnapshot.metadata.isFromCache
                    && groupSnapshot.isEmpty
                ) {
                    return@addSnapshotListener
                }

                val groupIds = groupSnapshot?.documents?.map { it.id } ?: emptyList()
                val allLocations = mutableListOf<SharedLocation>()

                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                for (groupId in groupIds.take(5)) {
                    firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                        .document(groupId)
                        .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                        .whereEqualTo("isSharingActive", true)
                        .get()
                        .addOnSuccessListener { locationSnapshot ->
                            val locations = locationSnapshot.toObjects(SharedLocation::class.java)
                                .filter { it.userId != uid }
                            allLocations.addAll(locations)
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
