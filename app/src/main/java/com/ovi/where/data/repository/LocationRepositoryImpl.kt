package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_MINUTE
import com.ovi.where.data.local.dao.LocationDao
import com.ovi.where.data.local.entity.SharedLocationEntity
import com.ovi.where.data.local.entity.toDomain
import com.ovi.where.data.local.entity.toEntity
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val lazyChatSocketIoClient: Lazy<ChatSocketIoClient>,
    private val locationDao: LocationDao
) : LocationRepository {

    /**
     * Lazily-resolved ChatSocketIoClient instance.
     * Not instantiated until first access, keeping app startup free of chat initialization (Req 20.1, 20.4, 20.5).
     */
    private val chatSocketIoClient: ChatSocketIoClient get() = lazyChatSocketIoClient.get()

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val activeSharingSessions = mutableMapOf<String, Long>()

    // Write throttle tracking
    @Volatile
    private var lastWriteTimestamp = 0L

    // Socket.IO location state management
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketLocationCache = MutableStateFlow<Map<String, SharedLocation>>(emptyMap())
    private var firestoreFallbackListener: ListenerRegistration? = null
    private var disconnectFallbackJob: Job? = null

    companion object {
        /** Duration to wait after disconnect before falling back to Firestore (Req 6.5) */
        const val DISCONNECT_FALLBACK_DELAY_MS = 30_000L

        /** Duration to wait for Socket.IO location update before Firestore fallback (Req 7.3) */
        const val LOCATION_SOCKET_FALLBACK_TIMEOUT_MS = 10_000L
    }

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

    // ── Socket.IO primary with Firestore fallback (Req 6.4, 6.5) ────────────

    /**
     * Observes cached locations from Room database.
     * Serves cached positions within 100ms of screen open (Requirement 7.2).
     * Room is the single source of truth for location display.
     */
    override fun observeCachedLocations(): Flow<List<SharedLocation>> {
        return locationDao.observeAllActive().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Observes locations with Socket.IO as primary source and Firestore as fallback.
     * If Socket.IO fails to deliver a location update within [fallbackTimeoutMs],
     * falls back to Firestore listener. All updates are persisted to Room cache.
     *
     * Requirement 7.3: Display cached locations within 100ms, subscribe to Socket.IO,
     * fall back to Firestore after 10s timeout.
     */
    override fun observeLocationsWithCacheFallback(fallbackTimeoutMs: Long): Flow<List<SharedLocation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        var receivedSocketUpdate = false
        var fallbackListenerActive = false
        var fallbackListener: ListenerRegistration? = null

        // Start a timeout job: if no Socket.IO update within fallbackTimeoutMs, activate Firestore
        val timeoutJob = repositoryScope.launch {
            delay(fallbackTimeoutMs)
            if (!receivedSocketUpdate && !fallbackListenerActive) {
                Timber.d("No Socket.IO location update within ${fallbackTimeoutMs}ms — falling back to Firestore")
                fallbackListenerActive = true
                fallbackListener = firestore
                    .collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                    .whereArrayContains("visibleTo", uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Timber.e(error, "Firestore cache-fallback listener error")
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
                                    location.copy(id = doc.id, isSharingActive = true, groupId = location.targetId)
                                } else null
                            } else null
                        } ?: emptyList()

                        // Persist to Room and emit
                        repositoryScope.launch {
                            locationDao.insertLocations(locations.map { it.toEntity() })
                        }
                        trySend(locations).isSuccess
                    }
            }
        }

        // Collect Socket.IO location_update frames
        val socketJob = repositoryScope.launch {
            chatSocketIoClient.incomingFrames.collect { frame ->
                if (frame is ServerFrame.LocationUpdate) {
                    receivedSocketUpdate = true
                    // Cancel Firestore fallback if it was activated
                    if (fallbackListenerActive) {
                        fallbackListener?.remove()
                        fallbackListener = null
                        fallbackListenerActive = false
                    }
                    timeoutJob.cancel()

                    val location = SharedLocation(
                        id = frame.userId,
                        userId = frame.userId,
                        latitude = frame.lat,
                        longitude = frame.lng,
                        accuracy = frame.accuracy,
                        timestamp = frame.timestamp,
                        isSharingActive = true
                    )
                    // Persist to Room for offline access
                    locationDao.insertLocation(location.toEntity())

                    // Emit updated list from cache
                    val allActive = locationDao.getAllActive().map { it.toDomain() }
                        .filter { it.userId != uid }
                    trySend(allActive).isSuccess
                }
            }
        }

        awaitClose {
            timeoutJob.cancel()
            socketJob.cancel()
            fallbackListener?.remove()
        }
    }

    /**
     * Observes locations using Socket.IO as the primary source while connected.
     * Falls back to Firestore snapshot listener after 30s of disconnection.
     * On reconnect: removes Firestore listener and resumes Socket.IO.
     *
     * Requirements: 6.4, 6.5
     */
    override fun observeLocationsWithSocketFallback(): Flow<List<SharedLocation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        // Collect Socket.IO location_update frames and update in-memory cache
        val socketCollectionJob = repositoryScope.launch {
            chatSocketIoClient.incomingFrames.collect { frame ->
                if (frame is ServerFrame.LocationUpdate) {
                    val location = SharedLocation(
                        id = frame.userId,
                        userId = frame.userId,
                        latitude = frame.lat,
                        longitude = frame.lng,
                        accuracy = frame.accuracy,
                        timestamp = frame.timestamp,
                        isSharingActive = true
                    )
                    val updated = socketLocationCache.value.toMutableMap()
                    updated[frame.userId] = location
                    socketLocationCache.value = updated

                    // Persist to Room for offline access
                    locationDao.insertLocation(location.toEntity())
                }
            }
        }

        // Monitor connection state for fallback logic
        val connectionMonitorJob = repositoryScope.launch {
            chatSocketIoClient.connectionState.collect { state ->
                when (state) {
                    ChatSocketIoClient.ConnectionState.CONNECTED -> {
                        // On reconnect: cancel fallback timer and remove Firestore listener
                        disconnectFallbackJob?.cancel()
                        disconnectFallbackJob = null
                        removeFirestoreFallbackListener()
                        Timber.d("Socket connected — using Socket.IO for location updates")
                    }
                    ChatSocketIoClient.ConnectionState.DISCONNECTED,
                    ChatSocketIoClient.ConnectionState.ERROR -> {
                        // Start 30s timer before falling back to Firestore
                        if (disconnectFallbackJob?.isActive != true) {
                            disconnectFallbackJob = repositoryScope.launch {
                                delay(DISCONNECT_FALLBACK_DELAY_MS)
                                // Still disconnected after 30s — activate Firestore fallback
                                if (chatSocketIoClient.connectionState.value != ChatSocketIoClient.ConnectionState.CONNECTED) {
                                    Timber.d("Socket disconnected 30s — falling back to Firestore")
                                    startFirestoreFallbackListener(uid)
                                }
                            }
                        }
                    }
                    else -> { /* CONNECTING — no action */ }
                }
            }
        }

        // Emit from the socket location cache (updated by both Socket.IO and Firestore fallback)
        val emitJob = repositoryScope.launch {
            socketLocationCache.collect { cache ->
                val locations = cache.values.filter { it.userId != uid && it.isSharingActive }
                trySend(locations.toList()).isSuccess
            }
        }

        awaitClose {
            socketCollectionJob.cancel()
            connectionMonitorJob.cancel()
            emitJob.cancel()
            disconnectFallbackJob?.cancel()
            removeFirestoreFallbackListener()
        }
    }

    /**
     * Starts a Firestore snapshot listener as fallback when Socket.IO is disconnected > 30s.
     */
    private fun startFirestoreFallbackListener(uid: String) {
        if (firestoreFallbackListener != null) return

        firestoreFallbackListener = firestore
            .collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
            .whereArrayContains("visibleTo", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Firestore fallback listener error")
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

                // Update the shared cache so the flow emits
                val updated = mutableMapOf<String, SharedLocation>()
                locations.forEach { updated[it.userId] = it }
                socketLocationCache.value = updated

                // Persist to Room for offline access
                repositoryScope.launch {
                    locationDao.insertLocations(locations.map { it.toEntity() })
                }
            }
    }

    /**
     * Removes the Firestore fallback listener (called on Socket.IO reconnect).
     */
    private fun removeFirestoreFallbackListener() {
        firestoreFallbackListener?.remove()
        firestoreFallbackListener = null
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
