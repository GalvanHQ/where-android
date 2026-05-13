package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_MINUTE
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.LocationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : LocationRepository {

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val activeSharingSessions = mutableMapOf<String, Long>()

    // Write throttle tracking
    @Volatile
    private var lastWriteTimestamp = 0L

    private fun isDirectTarget(targetId: String): Boolean = targetId.startsWith("direct:")

    private fun directFriendId(targetId: String): String = targetId.removePrefix("direct:")

    private fun directShareId(uid: String, friendId: String): String =
        listOf(uid, friendId).sorted().joinToString("_")

    private fun directShareDoc(uid: String, friendId: String) =
        firestore.collection(AppConstants.FIRESTORE_COLLECTION_DIRECT_LOCATION_SHARES)
            .document(directShareId(uid, friendId))

    override suspend fun startLocationSharing(groupId: String, durationMinutes: Long): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val expiryTime = if (durationMinutes > 0) {
                System.currentTimeMillis() + (durationMinutes * MILLIS_PER_MINUTE)
            } else {
                Long.MAX_VALUE
            }

            activeSharingSessions[groupId] = expiryTime

            val isDirect = isDirectTarget(groupId)
            val targetType = if (isDirect) "direct" else "group"

            // Build visibleTo list
            val visibleTo = if (isDirect) {
                val friendId = directFriendId(groupId)
                listOf(uid, friendId)
            } else {
                getGroupMemberIds(groupId)
            }

            // Write to consolidated activeLocations collection
            val activeLocationData = hashMapOf(
                "userId" to uid,
                "targetType" to targetType,
                "targetId" to groupId,
                "isSharingActive" to true,
                "sharingExpiresAt" to expiryTime,
                "timestamp" to System.currentTimeMillis(),
                "visibleTo" to visibleTo,
                "latitude" to 0.0,
                "longitude" to 0.0,
                "accuracy" to 0f,
                "speed" to 0f,
                "bearing" to 0f
            )
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .set(activeLocationData, SetOptions.merge())
                .await()

            // Legacy dual-write
            val locationData = hashMapOf(
                "userId" to uid,
                "groupId" to groupId,
                "isSharingActive" to true,
                "sharingExpiresAt" to expiryTime,
                "timestamp" to System.currentTimeMillis()
            )
            if (isDirect) {
                val friendId = directFriendId(groupId)
                val shareDoc = directShareDoc(uid, friendId)
                shareDoc.set(
                    mapOf(
                        "participantIds" to listOf(uid, friendId),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
                shareDoc.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                    .document(uid)
                    .set(locationData, SetOptions.merge())
                    .await()
            } else {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                    .document(groupId)
                    .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                    .document(uid)
                    .set(locationData, SetOptions.merge())
                    .await()
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start location sharing")
            Resource.Error(e.message ?: "Failed to start location sharing")
        }
    }

    override suspend fun stopLocationSharing(groupId: String): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")

            activeSharingSessions.remove(groupId)

            // Update consolidated doc
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .set(
                    mapOf(
                        "isSharingActive" to false,
                        "timestamp" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()

            // Legacy dual-write
            val updates = mapOf(
                "isSharingActive" to false,
                "timestamp" to System.currentTimeMillis()
            )
            if (isDirectTarget(groupId)) {
                directShareDoc(uid, directFriendId(groupId))
                    .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                    .document(uid)
                    .set(updates, SetOptions.merge())
                    .await()
            } else {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                    .document(groupId)
                    .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                    .document(uid)
                    .set(updates, SetOptions.merge())
                    .await()
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop location sharing")
            Resource.Error(e.message ?: "Failed to stop location sharing")
        }
    }

    override suspend fun updateLocation(
        groupId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float,
        bearing: Float
    ): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val now = System.currentTimeMillis()

            // Throttle writes — skip if within throttle window
            val shouldThrottle = (now - lastWriteTimestamp) < AppConstants.LOCATION_WRITE_THROTTLE_MS
            if (shouldThrottle) {
                return Resource.Success(Unit)
            }
            lastWriteTimestamp = now

            // Consolidated write
            val activeData = hashMapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "speed" to speed,
                "bearing" to bearing,
                "timestamp" to now,
                "isSharingActive" to true
            )
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .set(activeData, SetOptions.merge())
                .await()

            // Legacy dual-write
            val locationData = hashMapOf(
                "id" to uid,
                "userId" to uid,
                "groupId" to groupId,
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "speed" to speed,
                "bearing" to bearing,
                "timestamp" to now,
                "isSharingActive" to true
            )
            if (isDirectTarget(groupId)) {
                directShareDoc(uid, directFriendId(groupId))
                    .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                    .document(uid)
                    .set(locationData, SetOptions.merge())
                    .await()
            } else {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                    .document(groupId)
                    .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                    .document(uid)
                    .set(locationData, SetOptions.merge())
                    .await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update location")
            Resource.Error(e.message ?: "Failed to update location")
        }
    }

    // ── Consolidated listener (Phase 1 key) ──────────────────────────────────

    override fun observeActiveLocations(): Flow<List<SharedLocation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val listener: ListenerRegistration = firestore
            .collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
            .whereArrayContains("visibleTo", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "activeLocations listener error")
                    // Gracefully degrade — emit empty list instead of crashing
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    val location = doc.toObject(SharedLocation::class.java)
                    if (location != null) {
                        val now = System.currentTimeMillis()
                        val stillActive = location.isSharingActive &&
                            (location.sharingExpiresAt == 0L ||
                             location.sharingExpiresAt == Long.MAX_VALUE ||
                             now < location.sharingExpiresAt)
                        if (stillActive && location.userId != uid) {
                            location.copy(
                                id = doc.id,
                                isSharingActive = true,
                                groupId = location.targetId
                            )
                        } else null
                    } else null
                } ?: emptyList()
                trySend(locations).isSuccess
            }
        awaitClose { listener.remove() }
    }

    // ── Legacy listeners (kept for backward compat) ──────────────────────────

    override fun observeGroupLocations(groupId: String): Flow<List<SharedLocation>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    val location = doc.toObject(SharedLocation::class.java)
                    if (location != null) {
                        val now = System.currentTimeMillis()
                        val stillActive = location.isSharingActive &&
                            (location.sharingExpiresAt == 0L || location.sharingExpiresAt == Long.MAX_VALUE || now < location.sharingExpiresAt)
                        location.copy(id = doc.id, isSharingActive = stillActive)
                    } else null
                } ?: emptyList()
                trySend(locations).isSuccess
            }
        awaitClose { listener.remove() }
    }

    override fun observeDirectLocationShares(friendIds: List<String>): Flow<List<SharedLocation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        if (friendIds.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val locations = mutableMapOf<String, SharedLocation>()
        val listeners = friendIds.map { friendId ->
            directShareDoc(uid, friendId)
                .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                .document(friendId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    val location = snapshot?.toObject(SharedLocation::class.java)
                    if (location != null) {
                        val now = System.currentTimeMillis()
                        val stillActive = location.isSharingActive &&
                            (location.sharingExpiresAt == 0L || location.sharingExpiresAt == Long.MAX_VALUE || now < location.sharingExpiresAt)
                        if (stillActive) {
                            locations[friendId] = location.copy(id = snapshot.id, groupId = "direct:$friendId", isSharingActive = true)
                        } else {
                            locations.remove(friendId)
                        }
                    } else {
                        locations.remove(friendId)
                    }
                    trySend(locations.values.toList()).isSuccess
                }
        }

        awaitClose { listeners.forEach { it.remove() } }
    }

    override fun observeUserLocation(userId: String, groupId: String): Flow<SharedLocation?> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val location = snapshot?.toObject(SharedLocation::class.java)
                val resolvedLocation = if (location != null) {
                    val now = System.currentTimeMillis()
                    val stillActive = location.isSharingActive &&
                        (location.sharingExpiresAt == 0L || location.sharingExpiresAt == Long.MAX_VALUE || now < location.sharingExpiresAt)
                    location.copy(id = snapshot.id, isSharingActive = stillActive)
                } else null
                trySend(resolvedLocation).isSuccess
            }
        awaitClose { listener.remove() }
    }

    override fun isSharingLocation(groupId: String): Boolean {
        val expiryTime = activeSharingSessions[groupId] ?: return false
        return System.currentTimeMillis() < expiryTime
    }

    override fun getSharingExpiryTime(groupId: String): Long? {
        return activeSharingSessions[groupId]
    }

    override suspend fun checkSharingStatus(groupId: String): Boolean {
        return try {
            val uid = currentUid ?: return false

            // Check consolidated doc first
            val activeDoc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .get()
                .await()

            if (activeDoc.exists()) {
                val isActive = activeDoc.getBoolean("isSharingActive") ?: false
                val expiresAt = activeDoc.getLong("sharingExpiresAt") ?: 0L
                val targetId = activeDoc.getString("targetId") ?: ""
                val now = System.currentTimeMillis()
                val stillActive = isActive && targetId == groupId &&
                    (expiresAt == Long.MAX_VALUE || now < expiresAt)
                if (stillActive) {
                    activeSharingSessions[groupId] = expiresAt
                    return true
                }
            }

            // Fallback to legacy
            val doc = if (isDirectTarget(groupId)) {
                directShareDoc(uid, directFriendId(groupId))
                    .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                    .document(uid)
                    .get()
                    .await()
            } else {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                    .document(groupId)
                    .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                    .document(uid)
                    .get()
                    .await()
            }

            val isActive = doc.getBoolean("isSharingActive") ?: false
            val expiresAt = doc.getLong("sharingExpiresAt") ?: 0L
            val now = System.currentTimeMillis()
            val stillActive = isActive && (expiresAt == Long.MAX_VALUE || now < expiresAt)

            if (stillActive) {
                activeSharingSessions[groupId] = expiresAt
            } else {
                activeSharingSessions.remove(groupId)
            }
            stillActive
        } catch (e: Exception) {
            Timber.e(e, "Failed to check sharing status")
            false
        }
    }

    override suspend fun getGroupMemberIds(groupId: String): List<String> {
        return try {
            val snapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.getString("userId") ?: it.id }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch group members")
            val uid = currentUid
            if (uid != null) listOf(uid) else emptyList()
        }
    }
}
