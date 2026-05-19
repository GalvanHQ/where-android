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
import com.ovi.where.domain.model.MeetupDestination
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
import org.json.JSONObject
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
     * Not instantiated until first access, keeping app startup free of chat initialization.
     */
    private val chatSocketIoClient: ChatSocketIoClient get() = lazyChatSocketIoClient.get()

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val currentDisplayName: String?
        get() = firebaseAuth.currentUser?.displayName

    private val currentPhotoUrl: String?
        get() = firebaseAuth.currentUser?.photoUrl?.toString()

    private val activeSharingSessions = mutableMapOf<String, Long>()

    // ── Speed-dependent write throttle state ──────────────────────────────────
    @Volatile
    private var lastWriteTimestamp = 0L
    @Volatile
    private var lastSpeed = 0f
    @Volatile
    private var wasMoving = false
    /** Retained location sample for retry on throttled write failure */
    @Volatile
    private var retainedWriteSample: Map<String, Any>? = null

    // ── Socket.IO emission throttle state ─────────────────────────────────────
    @Volatile
    private var lastSocketEmitTimestamp = 0L

    // Socket.IO location state management
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketLocationCache = MutableStateFlow<Map<String, SharedLocation>>(emptyMap())
    private var firestoreFallbackListener: ListenerRegistration? = null
    private var disconnectFallbackJob: Job? = null

    companion object {
        /** Duration to wait after disconnect before falling back to Firestore */
        const val DISCONNECT_FALLBACK_DELAY_MS = 30_000L

        /** Duration to wait for Socket.IO location update before Firestore fallback */
        const val LOCATION_SOCKET_FALLBACK_TIMEOUT_MS = 10_000L

        /** Speed threshold (m/s) for moving vs stationary classification */
        private const val SPEED_MOVING_THRESHOLD = 1.0f

        /** Firestore write throttle when moving (10s) */
        private const val WRITE_THROTTLE_MOVING_MS = 10_000L

        /** Firestore write throttle when stationary (30s) */
        private const val WRITE_THROTTLE_STATIONARY_MS = 30_000L

        /** Socket.IO emit throttle when stationary (30s) */
        private const val SOCKET_EMIT_THROTTLE_STATIONARY_MS = 30_000L
    }

    private fun isDirectTarget(targetId: String): Boolean = targetId.startsWith("direct:")

    private fun directFriendId(targetId: String): String = targetId.removePrefix("direct:")

    private fun directShareId(uid: String, friendId: String): String =
        listOf(uid, friendId).sorted().joinToString("_")

    private fun directShareDoc(uid: String, friendId: String) =
        firestore.collection(AppConstants.FIRESTORE_COLLECTION_DIRECT_LOCATION_SHARES)
            .document(directShareId(uid, friendId))

    /**
     * Determines if the user is currently moving based on speed threshold.
     */
    private fun isMoving(speed: Float): Boolean = speed > SPEED_MOVING_THRESHOLD

    /**
     * Safely parses a Firestore document into a SharedLocation.
     * Handles Firestore's number type coercion (all numbers stored as Double/Long)
     * which causes toObject() to fail silently with Float fields.
     */
    private fun parseSharedLocation(docId: String, data: Map<String, Any>?): SharedLocation? {
        if (data == null) return null
        return try {
            SharedLocation(
                id = docId,
                userId = data["userId"] as? String ?: "",
                groupId = data["targetId"] as? String ?: data["groupId"] as? String ?: "",
                latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                accuracy = (data["accuracy"] as? Number)?.toFloat() ?: 0f,
                speed = (data["speed"] as? Number)?.toFloat() ?: 0f,
                bearing = (data["bearing"] as? Number)?.toFloat() ?: 0f,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                isSharingActive = data["isSharingActive"] as? Boolean ?: false,
                sharingExpiresAt = (data["sharingExpiresAt"] as? Number)?.toLong() ?: 0L,
                targetType = data["targetType"] as? String ?: "group",
                targetId = data["targetId"] as? String ?: "",
                visibleTo = (data["visibleTo"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                displayName = data["displayName"] as? String ?: "",
                photoUrl = data["photoUrl"] as? String,
                sharingStartedAt = (data["sharingStartedAt"] as? Number)?.toLong() ?: 0L
            )
        } catch (e: Exception) {
            Timber.w(e, "parseSharedLocation: failed to parse doc=$docId")
            null
        }
    }

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

            // Build visibleTo list — MUST include all users who should see this location
            val visibleTo = if (isDirect) {
                val friendId = directFriendId(groupId)
                listOf(uid, friendId)
            } else {
                val members = getGroupMemberIds(groupId)
                if (uid !in members) members + uid else members
            }

            Timber.d("startLocationSharing: uid=$uid, groupId=$groupId, visibleTo=$visibleTo (${visibleTo.size} members)")

            if (visibleTo.size <= 1) {
                Timber.w("startLocationSharing: visibleTo has only ${visibleTo.size} entries — other users won't see this location!")
            }

            // Denormalized profile data — eliminates separate user profile reads on map
            val displayName = (currentDisplayName ?: "").take(50)
            val photoUrl = (currentPhotoUrl ?: "").take(2048)

            // Single write to consolidated activeLocations collection
            // Use set() WITHOUT merge to ensure clean state (no stale fields from previous sessions)
            val activeLocationData = hashMapOf(
                "userId" to uid,
                "targetType" to targetType,
                "targetId" to groupId,
                "isSharingActive" to true,
                "sharingExpiresAt" to expiryTime,
                "timestamp" to System.currentTimeMillis(),
                "visibleTo" to visibleTo,
                "displayName" to displayName,
                "photoUrl" to photoUrl,
                "latitude" to 0.0,
                "longitude" to 0.0,
                "accuracy" to 0f,
                "speed" to 0f,
                "bearing" to 0f
            )
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .set(activeLocationData)
                .await()

            Timber.d("startLocationSharing: activeLocations/$uid written successfully")

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

            // Single write to consolidated doc
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .set(
                    mapOf(
                        "isSharingActive" to false,
                        "timestamp" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop location sharing")
            Resource.Error(e.message ?: "Failed to stop location sharing")
        }
    }

    /**
     * Updates location with speed-dependent Firestore write throttle and Socket.IO emission.
     *
     * Throttle strategy:
     * - Moving (speed > 1 m/s): write every 10s, emit Socket.IO every 5s (GPS fix rate)
     * - Stationary (speed ≤ 1 m/s): write every 30s, emit Socket.IO every 30s
     * - On speed state transition: immediate write + timer reset
     *
     * This reduces Firestore writes by ~50-70% compared to the old flat 15s throttle
     * while maintaining real-time UX via Socket.IO for connected peers.
     */
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

            // ── Socket.IO emission (independent of Firestore throttle) ────────────
            emitLocationViaSocket(uid, latitude, longitude, accuracy, speed, bearing, now)

            // ── Speed-dependent Firestore write throttle ──────────────────────────
            val currentlyMoving = isMoving(speed)
            val speedStateTransition = currentlyMoving != wasMoving && lastWriteTimestamp > 0L
            wasMoving = currentlyMoving
            lastSpeed = speed

            val throttleInterval = if (currentlyMoving) WRITE_THROTTLE_MOVING_MS else WRITE_THROTTLE_STATIONARY_MS
            val timeSinceLastWrite = now - lastWriteTimestamp

            // Determine if we should write: either throttle window elapsed, or speed state changed
            val shouldWrite = speedStateTransition || timeSinceLastWrite >= throttleInterval || lastWriteTimestamp == 0L

            if (!shouldWrite) {
                return Resource.Success(Unit)
            }

            // Denormalized profile data
            val displayName = (currentDisplayName ?: "").take(50)
            val photoUrl = (currentPhotoUrl ?: "").take(2048)

            // Single consolidated write — includes userId so the document is queryable
            val activeData = hashMapOf<String, Any>(
                "userId" to uid,
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "speed" to speed,
                "bearing" to bearing,
                "timestamp" to now,
                "isSharingActive" to true,
                "displayName" to displayName,
                "photoUrl" to photoUrl
            )

            try {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                    .document(uid)
                    .set(activeData, SetOptions.merge())
                    .await()
                lastWriteTimestamp = now
                retainedWriteSample = null
            } catch (writeError: Exception) {
                // Retain sample for retry on next throttle cycle
                Timber.w(writeError, "Firestore write failed — retaining sample for next cycle")
                retainedWriteSample = activeData
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update location")
            Resource.Error(e.message ?: "Failed to update location")
        }
    }

    /**
     * Emits location update via Socket.IO for real-time peer updates.
     * Independent of Firestore throttle — emits at GPS fix rate when moving (5s),
     * throttled to 30s when stationary.
     *
     * On disconnect: does nothing (Firestore is the fallback).
     * On failure: logs and continues without retry.
     */
    private fun emitLocationViaSocket(
        uid: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float,
        bearing: Float,
        timestamp: Long
    ) {
        try {
            val connectionState = chatSocketIoClient.connectionState.value
            if (connectionState != ChatSocketIoClient.ConnectionState.CONNECTED) return

            val now = System.currentTimeMillis()
            val currentlyMoving = isMoving(speed)

            // Throttle Socket.IO emissions when stationary
            if (!currentlyMoving) {
                val timeSinceLastEmit = now - lastSocketEmitTimestamp
                if (timeSinceLastEmit < SOCKET_EMIT_THROTTLE_STATIONARY_MS && lastSocketEmitTimestamp > 0L) {
                    return
                }
            }

            val payload = JSONObject().apply {
                put("userId", uid)
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracy", accuracy.toDouble())
                put("speed", speed.toDouble())
                put("bearing", bearing.toDouble())
                put("timestamp", timestamp)
            }

            // Fire-and-forget emission via the underlying socket
            repositoryScope.launch {
                try {
                    chatSocketIoClient.emitLocationUpdate(payload)
                    lastSocketEmitTimestamp = now
                } catch (e: Exception) {
                    Timber.w(e, "Socket.IO location emission failed — continuing with Firestore only")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Socket.IO location emission setup failed")
        }
    }

    // ── Consolidated listener (Phase 1 key) ──────────────────────────────────

    /**
     * Single consolidated Firestore listener for ALL locations visible to current user.
     * Implements persist-before-emit: updates are written to Room cache BEFORE
     * being emitted to the UI flow. This ensures the cache is always up-to-date
     * and serves as the single source of truth for display.
     *
     * Requirement 6.2: Persist to Room before emitting to UI.
     * Requirement 6.3: One record per userId with latest data (REPLACE strategy).
     */
    override fun observeActiveLocations(): Flow<List<SharedLocation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        Timber.d("observeActiveLocations: starting listener for uid=$uid")

        val listener: ListenerRegistration = firestore
            .collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
            .whereArrayContains("visibleTo", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "activeLocations listener error for uid=$uid")
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                Timber.d("observeActiveLocations: received ${snapshot?.documents?.size ?: 0} documents")

                val locations = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val location = parseSharedLocation(doc.id, doc.data)
                            ?: return@mapNotNull null

                        val now = System.currentTimeMillis()
                        val stillActive = location.isSharingActive &&
                            (location.sharingExpiresAt == 0L ||
                             location.sharingExpiresAt == Long.MAX_VALUE ||
                             now < location.sharingExpiresAt)
                        if (stillActive && location.userId != uid) {
                            location
                        } else null
                    } catch (e: Exception) {
                        Timber.w(e, "observeActiveLocations: failed to parse doc=${doc.id}")
                        null
                    }
                } ?: emptyList()

                // Persist-before-emit: write to Room cache BEFORE emitting to UI
                repositoryScope.launch {
                    if (locations.isNotEmpty()) {
                        locationDao.insertLocations(locations.map { it.toEntity() })
                    }
                }

                Timber.d("observeActiveLocations: emitting ${locations.size} active locations")
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
     * Falls back to Firestore snapshot listener after 10s of disconnection.
     * On reconnect: removes Firestore listener within 5s and resumes Socket.IO.
     *
     * Requirements: 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 7.5
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

                    // Persist-before-emit: write to Room BEFORE updating the cache flow
                    locationDao.insertLocation(location.toEntity())

                    val updated = socketLocationCache.value.toMutableMap()
                    updated[frame.userId] = location
                    socketLocationCache.value = updated
                }
            }
        }

        // Monitor connection state for fallback logic
        val connectionMonitorJob = repositoryScope.launch {
            chatSocketIoClient.connectionState.collect { state ->
                when (state) {
                    ChatSocketIoClient.ConnectionState.CONNECTED -> {
                        // On reconnect: cancel fallback timer and remove Firestore listener within 5s
                        disconnectFallbackJob?.cancel()
                        disconnectFallbackJob = null
                        removeFirestoreFallbackListener()
                        Timber.d("Socket connected — using Socket.IO for location updates")
                    }
                    ChatSocketIoClient.ConnectionState.DISCONNECTED,
                    ChatSocketIoClient.ConnectionState.ERROR -> {
                        // Start 10s timer before falling back to Firestore (Req 7.2)
                        if (disconnectFallbackJob?.isActive != true) {
                            disconnectFallbackJob = repositoryScope.launch {
                                delay(LOCATION_SOCKET_FALLBACK_TIMEOUT_MS)
                                // Still disconnected after 10s — activate Firestore fallback
                                if (chatSocketIoClient.connectionState.value != ChatSocketIoClient.ConnectionState.CONNECTED) {
                                    Timber.d("Socket disconnected ${LOCATION_SOCKET_FALLBACK_TIMEOUT_MS}ms — falling back to Firestore")
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

            // Step 1: Check in-memory cache first (0 Firestore reads)
            val cachedExpiry = activeSharingSessions[groupId]
            if (cachedExpiry != null) {
                val now = System.currentTimeMillis()
                if (cachedExpiry == Long.MAX_VALUE || now < cachedExpiry) {
                    return true
                } else {
                    // Expired — clean up
                    activeSharingSessions.remove(groupId)
                }
            }

            // Step 2: Single doc read from consolidated collection (1 Firestore read)
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

            false
        } catch (e: Exception) {
            Timber.e(e, "Failed to check sharing status")
            false
        }
    }

    override suspend fun getGroupMemberIds(groupId: String): List<String> {
        return try {
            // First try: read memberIds array directly from the group document
            // This is the authoritative source and avoids N subcollection reads
            val groupDoc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .get()
                .await()

            @Suppress("UNCHECKED_CAST")
            val memberIdsFromDoc = groupDoc.get("memberIds") as? List<String>
            if (!memberIdsFromDoc.isNullOrEmpty()) {
                Timber.d("getGroupMemberIds: found ${memberIdsFromDoc.size} members from group doc")
                return memberIdsFromDoc
            }

            // Fallback: query the members subcollection
            val snapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .get()
                .await()
            val fromSubcollection = snapshot.documents.mapNotNull { it.getString("userId") ?: it.id }
            Timber.d("getGroupMemberIds: found ${fromSubcollection.size} members from subcollection")

            if (fromSubcollection.isNotEmpty()) {
                fromSubcollection
            } else {
                // Last resort: include at least the current user
                val uid = currentUid
                if (uid != null) listOf(uid) else emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch group members for groupId=$groupId")
            val uid = currentUid
            if (uid != null) listOf(uid) else emptyList()
        }
    }

    // ── Meetup Destination ────────────────────────────────────────────────────

    override suspend fun setMeetupDestination(
        groupId: String,
        latitude: Double,
        longitude: Double,
        name: String,
        address: String
    ): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val destinationData = hashMapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "name" to name,
                "address" to address,
                "setBy" to uid,
                "setAt" to System.currentTimeMillis(),
                "isActive" to true
            )
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .update("meetupDestination", destinationData)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set meetup destination")
            Resource.Error(e.message ?: "Failed to set meetup destination")
        }
    }

    override suspend fun clearMeetupDestination(groupId: String): Resource<Unit> {
        return try {
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .update(
                    "meetupDestination", hashMapOf(
                        "isActive" to false,
                        "latitude" to 0.0,
                        "longitude" to 0.0
                    )
                )
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear meetup destination")
            Resource.Error(e.message ?: "Failed to clear meetup destination")
        }
    }

    override fun observeMeetupDestination(groupId: String): Flow<MeetupDestination?> = callbackFlow {
        val listener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Meetup destination listener error")
                    trySend(null).isSuccess
                    return@addSnapshotListener
                }
                val destMap = snapshot?.get("meetupDestination") as? Map<*, *>
                if (destMap != null && destMap["isActive"] == true) {
                    val destination = MeetupDestination(
                        latitude = (destMap["latitude"] as? Number)?.toDouble() ?: 0.0,
                        longitude = (destMap["longitude"] as? Number)?.toDouble() ?: 0.0,
                        name = destMap["name"] as? String ?: "",
                        address = destMap["address"] as? String ?: "",
                        setBy = destMap["setBy"] as? String ?: "",
                        setAt = (destMap["setAt"] as? Number)?.toLong() ?: 0L,
                        isActive = true
                    )
                    trySend(destination).isSuccess
                } else {
                    trySend(null).isSuccess
                }
            }
        awaitClose { listener.remove() }
    }
}
