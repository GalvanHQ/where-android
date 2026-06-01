package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_MINUTE
import com.ovi.where.data.local.dao.LocationDao
import com.ovi.where.data.local.dao.MeetupDestinationDao
import com.ovi.where.data.local.entity.toDomain
import com.ovi.where.data.local.entity.toEntity
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.domain.model.ActiveSharingState
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.LocationRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val lazyChatSocketIoClient: Lazy<ChatSocketIoClient>,
    private val locationDao: LocationDao,
    private val meetupDestinationDao: MeetupDestinationDao,
    private val privacyPolicyRepository: PrivacyPolicyRepository,
    private val lazyFriendshipRepository: Lazy<com.ovi.where.domain.repository.FriendshipRepository>
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

    /**
     * Active sharing session state.
     * - targetExpiries: per-target expiry map. The session is over when this is empty.
     * - visibleTo: union of all member ids across all live targets (recomputed on add/remove).
     */
    private data class ActiveSharingSession(
        val targetExpiries: Map<String, Long>,
        val visibleTo: List<String>
    ) {
        val targetIds: List<String> get() = targetExpiries.keys.toList()
        /** Latest expiry across all targets, used as the overall session deadline. */
        val overallExpiry: Long? get() = targetExpiries.values.maxOrNull()
    }
    @Volatile
    private var currentSession: ActiveSharingSession? = null

    /** Atomic update + publish so every mutation reaches both screens. */
    private fun setCurrentSession(session: ActiveSharingSession?) {
        currentSession = session
        publishSharingState()
    }

    // ── Unified active-sharing StateFlow (chat + map share this) ────────────
    private val _activeSharingState = MutableStateFlow(ActiveSharingState())
    override val activeSharingState: StateFlow<ActiveSharingState> = _activeSharingState

    /** Ticker that re-emits [activeSharingState] every 15s while sharing is active. */
    private var sharingTickerJob: Job? = null

    private fun publishSharingState() {
        val expiries = currentSession?.targetExpiries.orEmpty()
        _activeSharingState.value = ActiveSharingState(
            targetExpiries = expiries,
            nowMs = System.currentTimeMillis()
        )
        if (expiries.isEmpty()) {
            sharingTickerJob?.cancel()
            sharingTickerJob = null
        } else if (sharingTickerJob?.isActive != true) {
            sharingTickerJob = repositoryScope.launch {
                while (true) {
                    delay(15_000L)
                    val active = currentSession?.let { pruneExpired(it.targetExpiries) }.orEmpty()
                    if (active.isEmpty()) {
                        currentSession = null
                        _activeSharingState.value = ActiveSharingState(
                            targetExpiries = emptyMap(),
                            nowMs = System.currentTimeMillis()
                        )
                        break
                    } else {
                        if (active.size != currentSession?.targetExpiries?.size) {
                            currentSession = currentSession?.copy(targetExpiries = active)
                        }
                        _activeSharingState.value = ActiveSharingState(
                            targetExpiries = active,
                            nowMs = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

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

    // ── At-home detection state ───────────────────────────────────────────────
    // The sharer's own device is the only one that knows their home coords,
    // so we cache them here (refreshed on a short TTL) and stamp an `isAtHome`
    // flag onto every location write. Viewers read the boolean — they never
    // see the home coordinates, keeping home private.
    @Volatile
    private var cachedHomeUid: String? = null
    @Volatile
    private var cachedHomeLat: Double = 0.0
    @Volatile
    private var cachedHomeLng: Double = 0.0
    @Volatile
    private var cachedHomeHasHome: Boolean = false
    @Volatile
    private var cachedHomeAt: Long = 0L

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

        /** Home coords cache TTL — re-read `users/{uid}` home at most this often. */
        private const val HOME_CACHE_TTL_MS = 60_000L

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
     * Wrapper preserving the legacy private signature. Delegates to the
     * canonical [com.ovi.where.data.remote.firestore.SharedLocationFirestoreMapper]
     * so the parsing rules live in one place — see that file for the
     * Float-vs-Double rationale and field schema.
     */
    private fun parseSharedLocation(docId: String, data: Map<String, Any>?): SharedLocation? =
        com.ovi.where.data.remote.firestore.SharedLocationFirestoreMapper.fromMap(docId, data)

    /**
     * Computes the union of member ids visible across the given target ids.
     * For "direct:{friendId}" → [uid, friendId].
     * For groupId → group member ids ∪ uid.
     */
    private suspend fun computeVisibleTo(uid: String, targetIds: List<String>): List<String> {
        val visible = LinkedHashSet<String>()
        visible.add(uid)
        for (tid in targetIds) {
            if (isDirectTarget(tid)) {
                visible.add(directFriendId(tid))
            } else {
                runCatching { getGroupMemberIds(tid) }
                    .getOrNull()
                    ?.let { visible.addAll(it) }
            }
        }
        return visible.toList()
    }

    /**
     * Determines the targetType label for the location doc based on the target list.
     * - empty → "group"
     * - 1 direct → "direct"
     * - 1 group → "group"
     * - mixed / multiple → "multi"
     */
    private fun computeTargetType(targetIds: List<String>): String {
        if (targetIds.size != 1) return if (targetIds.isEmpty()) "group" else "multi"
        return if (isDirectTarget(targetIds[0])) "direct" else "group"
    }

    /**
     * Computes an expiry epoch ms from a duration in minutes.
     * 0 (or negative) means "until stopped" → Long.MAX_VALUE.
     */
    private fun expiryFromDuration(durationMinutes: Long): Long {
        return if (durationMinutes > 0) {
            System.currentTimeMillis() + (durationMinutes * MILLIS_PER_MINUTE)
        } else {
            Long.MAX_VALUE
        }
    }

    /**
     * Drops any expired entries from a target→expiry map.
     */
    private fun pruneExpired(expiries: Map<String, Long>): Map<String, Long> {
        val now = System.currentTimeMillis()
        return expiries.filterValues { it == Long.MAX_VALUE || it > now }
    }

    /**
     * Enforces the user's [PrivacyPolicyRepository.LocationSharingMode] before
     * any Firestore write happens. Returns Resource.Error with a copy that
     * names the active mode so the UI can surface a meaningful message
     * instead of a generic "permission denied".
     *
     * Mode semantics:
     *  • ALWAYS — no extra check; the caller's targets pass through.
     *  • FRIENDS — every target must be either:
     *      • a direct share to a confirmed friend, OR
     *      • a group where every member is a confirmed friend.
     *    Group sharing with non-friend members is the trickiest case —
     *    we err on the side of refusal because the user opted into a
     *    "friends-only" policy, and group ownership doesn't imply that
     *    every other member is a contact they trust.
     *  • NEVER — categorical rejection. Users who picked this kill switch
     *    expect "no" to mean "no", even via a group they're already in.
     *
     * The check runs against the local DataStore (not Firestore) so it
     * stays instant and works offline. Friendship lookups go through the
     * existing reactive friend stream — fetched once per call.
     */
    private suspend fun enforceLocationPolicy(targetIds: List<String>): Resource<Unit> {
        val mode = privacyPolicyRepository.currentLocationSharingMode()

        when (mode) {
            PrivacyPolicyRepository.LocationSharingMode.ALWAYS -> return Resource.Success(Unit)

            PrivacyPolicyRepository.LocationSharingMode.NEVER ->
                return Resource.Error(
                    "Location sharing is disabled in your privacy settings. " +
                        "Open Settings to change."
                )

            PrivacyPolicyRepository.LocationSharingMode.FRIENDS -> {
                val friendIds = runCatching {
                    lazyFriendshipRepository.get().observeFriends().first()
                        .map { it.friendUid }
                        .toSet()
                }.getOrDefault(emptySet())

                for (target in targetIds) {
                    if (isDirectTarget(target)) {
                        val friendId = directFriendId(target)
                        if (friendId !in friendIds) {
                            return Resource.Error(
                                "Friends-only mode: you can only share with friends. " +
                                    "Add this person before sharing."
                            )
                        }
                    } else {
                        // Group target — every member must be a friend.
                        val members = runCatching { getGroupMemberIds(target) }
                            .getOrDefault(emptyList())
                            .filter { it != currentUid } // exclude self
                        val nonFriendMembers = members - friendIds
                        if (nonFriendMembers.isNotEmpty()) {
                            return Resource.Error(
                                "Friends-only mode: this group has members who aren't your friends. " +
                                    "Switch to \"Always\" in Privacy settings to share."
                            )
                        }
                    }
                }
                return Resource.Success(Unit)
            }
        }
    }

    override suspend fun startLocationSharing(targetIds: List<String>, durationMinutes: Long): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val sanitized = targetIds.filter { it.isNotBlank() }.distinct()
            if (sanitized.isEmpty()) {
                return Resource.Error("Pick at least one friend or group to share with")
            }

            // Privacy policy check — runs BEFORE any state mutation or
            // Firestore write so a rejected share leaves no trace.
            val policyResult = enforceLocationPolicy(sanitized)
            if (policyResult is Resource.Error) return policyResult

            val expiry = expiryFromDuration(durationMinutes)
            // All targets in this start call get the same expiry. Existing targets
            // are replaced wholesale (this is the "fresh start" entry point).
            val targetExpiries = sanitized.associateWith { expiry }

            val visibleTo = computeVisibleTo(uid, sanitized)
            setCurrentSession(
                ActiveSharingSession(
                    targetExpiries = targetExpiries,
                    visibleTo = visibleTo
                )
            )

            val targetType = computeTargetType(sanitized)
            val displayName = (currentDisplayName ?: "").take(50)
            val photoUrl = (currentPhotoUrl ?: "").take(2048)

            // Single write to consolidated activeLocations collection.
            // sharingExpiresAt = max(targetExpiries) so legacy readers still work.
            val activeLocationData = hashMapOf(
                "userId" to uid,
                "targetType" to targetType,
                "targetId" to sanitized.first(),
                "targetIds" to sanitized,
                "targetExpiries" to targetExpiries,
                "isSharingActive" to true,
                "sharingExpiresAt" to expiry,
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

            Timber.d("startLocationSharing: uid=$uid, targets=${sanitized.size}, visibleTo=${visibleTo.size}")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start location sharing")
            Resource.Error(e.message ?: "Failed to start location sharing")
        }
    }

    override suspend fun stopLocationSharing(): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            setCurrentSession(null)

            firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .set(
                    mapOf(
                        "isSharingActive" to false,
                        "timestamp" to System.currentTimeMillis(),
                        "targetIds" to emptyList<String>(),
                        "targetExpiries" to emptyMap<String, Long>()
                    ),
                    SetOptions.merge()
                ).await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop location sharing")
            Resource.Error(e.message ?: "Failed to stop location sharing")
        }
    }

    override suspend fun addSharingTarget(targetId: String, durationMinutes: Long): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val session = currentSession
                ?: return Resource.Error("No active sharing session — start sharing first")
            if (targetId.isBlank() || targetId in session.targetExpiries) {
                return Resource.Success(Unit)
            }

            // Privacy policy check — same gate as startLocationSharing.
            // We check just the new target since existing targets in the
            // session were already validated when added.
            val policyResult = enforceLocationPolicy(listOf(targetId))
            if (policyResult is Resource.Error) return policyResult

            val newExpiry = expiryFromDuration(durationMinutes)
            // Existing per-target expiries are preserved — only the new target gets newExpiry.
            val newExpiries = pruneExpired(session.targetExpiries) + (targetId to newExpiry)
            val newTargets = newExpiries.keys.toList()
            val visibleTo = computeVisibleTo(uid, newTargets)
            setCurrentSession(session.copy(targetExpiries = newExpiries, visibleTo = visibleTo))

            firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .set(
                    mapOf(
                        "targetIds" to newTargets,
                        "targetExpiries" to newExpiries,
                        "targetType" to computeTargetType(newTargets),
                        "visibleTo" to visibleTo,
                        // sharingExpiresAt = max of all per-target expiries (legacy compat)
                        "sharingExpiresAt" to (newExpiries.values.maxOrNull() ?: Long.MAX_VALUE),
                        "timestamp" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add sharing target")
            Resource.Error(e.message ?: "Failed to add sharing target")
        }
    }

    override suspend fun removeSharingTarget(targetId: String): Resource<Unit> {
        val session = currentSession ?: return Resource.Success(Unit)
        if (targetId !in session.targetExpiries) return Resource.Success(Unit)

        val newExpiries = session.targetExpiries - targetId
        if (newExpiries.isEmpty()) {
            // Last target removed → stop sharing entirely
            return stopLocationSharing()
        }
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val newTargets = newExpiries.keys.toList()
            val visibleTo = computeVisibleTo(uid, newTargets)
            setCurrentSession(session.copy(targetExpiries = newExpiries, visibleTo = visibleTo))

            firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .set(
                    mapOf(
                        "targetIds" to newTargets,
                        "targetExpiries" to newExpiries,
                        "targetType" to computeTargetType(newTargets),
                        "visibleTo" to visibleTo,
                        "sharingExpiresAt" to (newExpiries.values.maxOrNull() ?: Long.MAX_VALUE),
                        "timestamp" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove sharing target")
            Resource.Error(e.message ?: "Failed to remove sharing target")
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
    /**
     * Resolves whether the live fix at ([latitude], [longitude]) falls within
     * the sharer's saved home geofence. Returns false when no home is set.
     *
     * Home coordinates are cached for 60s so we don't read `users/{uid}` on
     * every throttled location write. Only the user's own device runs this —
     * viewers read the denormalized boolean off the location doc.
     */
    private suspend fun computeIsAtHome(uid: String, latitude: Double, longitude: Double): Boolean {
        val now = System.currentTimeMillis()
        if (cachedHomeUid != uid || now - cachedHomeAt > HOME_CACHE_TTL_MS) {
            runCatching {
                val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(uid)
                    .get()
                    .await()
                val hLat = (doc.get("homeLatitude") as? Number)?.toDouble() ?: 0.0
                val hLng = (doc.get("homeLongitude") as? Number)?.toDouble() ?: 0.0
                cachedHomeLat = hLat
                cachedHomeLng = hLng
                cachedHomeHasHome = hLat != 0.0 || hLng != 0.0
                cachedHomeUid = uid
                cachedHomeAt = now
            }.onFailure {
                // On a read failure keep whatever we had; default is "not home".
                Timber.w(it, "computeIsAtHome: failed to read home coords")
            }
        }
        if (!cachedHomeHasHome) return false
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            latitude, longitude, cachedHomeLat, cachedHomeLng, results
        )
        return results[0] <= AppConstants.HOME_RADIUS_METERS
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
            val isAtHome = computeIsAtHome(uid, latitude, longitude)

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
                "photoUrl" to photoUrl,
                "isAtHome" to isAtHome
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
                    // Don't emit empty list on transient errors — that
                    // would wipe live chips while the network blips. The
                    // Flow consumer keeps the last good value; Firestore
                    // will retry the listener internally and re-emit when
                    // it recovers.
                    return@addSnapshotListener
                }

                Timber.d("observeActiveLocations: received ${snapshot?.documents?.size ?: 0} documents")

                // Cache-snapshot guard: a cache snapshot with zero documents
                // is "I have no info" — *not* "no one is sharing". Emitting
                // [] here would briefly wipe the top-bar location chips on
                // every cold start until the server snapshot landed.
                val isFromCache = snapshot?.metadata?.isFromCache == true
                val docCount = snapshot?.documents?.size ?: 0
                if (isFromCache && docCount == 0) {
                    Timber.d("observeActiveLocations: ignoring empty cache snapshot")
                    return@addSnapshotListener
                }

                // Parse every doc (active or inactive) so we can reconcile
                // Room — inactive rows must be *deleted* locally, otherwise
                // the cached-locations Flow keeps replaying old "Sharing"
                // chips long after the user stopped.
                val parsed = snapshot?.documents?.mapNotNull { doc ->
                    try { parseSharedLocation(doc.id, doc.data) }
                    catch (e: Exception) {
                        Timber.w(e, "observeActiveLocations: failed to parse doc=${doc.id}")
                        null
                    }
                } ?: emptyList()

                val now = System.currentTimeMillis()
                val (active, inactive) = parsed.partition { location ->
                    location.isSharingActive &&
                        (location.sharingExpiresAt == 0L ||
                         location.sharingExpiresAt == Long.MAX_VALUE ||
                         now < location.sharingExpiresAt) &&
                        location.userId != uid
                }

                // Persist-before-emit: write to Room cache BEFORE emitting to UI
                repositoryScope.launch {
                    if (active.isNotEmpty()) {
                        locationDao.insertLocations(active.map { it.toEntity() })
                    }
                    // Reconcile: delete Room rows for now-inactive sharers
                    // and for users who are no longer in our `visibleTo`
                    // set (i.e. they unshared with us specifically).
                    if (!isFromCache) {
                        // Inactive rows present in this snapshot — delete them.
                        for (loc in inactive) {
                            locationDao.deleteByUserAndTarget(loc.userId, loc.groupId)
                        }
                        // Also delete rows in Room for users not present in
                        // this server snapshot at all (they removed us from
                        // visibleTo). We compute the diff against the active
                        // set since we want to keep their last-known position
                        // as long as we're still authorized to see it.
                        val seenUids = parsed.map { it.userId }.toSet()
                        val missing = locationDao.getAllActive()
                            .filter { it.userId != uid && it.userId !in seenUids }
                        for (row in missing) {
                            locationDao.deleteById(row.id)
                        }
                    }
                }

                Timber.d("observeActiveLocations: emitting ${active.size} active locations")
                trySend(active).isSuccess
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
     * Observes the live set of locations the current user can see.
     *
     * **Architecture:**
     *   • Firestore is the truth for *membership* — i.e. "who is currently
     *     sharing with me, and where they were last known to be." We start
     *     a snapshot listener immediately so the UI sees the right roster
     *     within milliseconds of the screen opening.
     *   • Socket.IO is the channel for *position updates* — incremental
     *     coordinate / speed / bearing changes for users we already know
     *     about from Firestore. Sockets don't tell us "user X started
     *     sharing"; that arrives via Firestore.
     *   • Room is the single source of truth the UI actually reads from.
     *     Both channels write through to Room; the Flow that the screen
     *     collects is `locationDao.observeAllActive()`.
     *
     * **Why the rewrite:** the previous design waited 10 seconds for a
     * socket frame before activating the Firestore listener. That meant
     * users opening a chat or the map saw nothing for the first 10s after
     * cold start (or whenever they didn't have an active socket frame in
     * flight). The fallback was misnamed — Firestore *is* the primary
     * roster source; the socket is a position firehose layered on top.
     *
     * The [fallbackTimeoutMs] parameter is retained for source compat but
     * is no longer used as a gate — it's only logged.
     */
    override fun observeLocationsWithCacheFallback(fallbackTimeoutMs: Long): Flow<List<SharedLocation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        // ── 1. Firestore listener (membership truth) ──
        //
        // Always running while this Flow is collected. Writes through to
        // Room and reconciles stale rows when the snapshot says someone
        // stopped sharing.
        val firestoreListener = firestore
            .collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
            .whereArrayContains("visibleTo", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeLocationsWithCacheFallback Firestore error")
                    return@addSnapshotListener
                }
                val isFromCache = snapshot?.metadata?.isFromCache == true
                val docCount = snapshot?.documents?.size ?: 0
                if (isFromCache && docCount == 0) return@addSnapshotListener

                val parsed = snapshot?.documents?.mapNotNull { doc ->
                    try { parseSharedLocation(doc.id, doc.data) }
                    catch (e: Exception) {
                        Timber.w(e, "membership parse failed: ${doc.id}")
                        null
                    }
                } ?: emptyList()

                val now = System.currentTimeMillis()
                val (active, inactive) = parsed.partition { location ->
                    location.isSharingActive &&
                        (location.sharingExpiresAt == 0L ||
                         location.sharingExpiresAt == Long.MAX_VALUE ||
                         now < location.sharingExpiresAt) &&
                        location.userId != uid
                }

                repositoryScope.launch {
                    if (active.isNotEmpty()) {
                        locationDao.insertLocations(active.map { it.toEntity() })
                    }
                    if (!isFromCache) {
                        for (loc in inactive) {
                            locationDao.deleteByUserAndTarget(loc.userId, loc.groupId)
                        }
                        val seenUids = parsed.map { it.userId }.toSet()
                        val missing = locationDao.getAllActive()
                            .filter { it.userId != uid && it.userId !in seenUids }
                        for (row in missing) locationDao.deleteById(row.id)
                    }
                    // Always emit the current Room state — that's the SSOT.
                    val allActive = locationDao.getAllActive()
                        .map { it.toDomain() }
                        .filter { it.userId != uid }
                    trySend(allActive).isSuccess
                }
            }

        // ── 2. Socket.IO position updates ──
        //
        // Live coordinate stream for users Firestore already told us are
        // sharing. We update Room with the new coords and re-emit the
        // current set so the map / chat header animate in real time.
        val socketJob = repositoryScope.launch {
            chatSocketIoClient.incomingFrames.collect { frame ->
                if (frame !is ServerFrame.LocationUpdate) return@collect
                val location = SharedLocation(
                    id = frame.userId,
                    userId = frame.userId,
                    latitude = frame.lat,
                    longitude = frame.lng,
                    accuracy = frame.accuracy,
                    timestamp = frame.timestamp,
                    isSharingActive = true
                )
                locationDao.insertLocation(location.toEntity())
                val allActive = locationDao.getAllActive()
                    .map { it.toDomain() }
                    .filter { it.userId != uid }
                trySend(allActive).isSuccess
            }
        }

        Timber.d("observeLocationsWithCacheFallback started (timeout param ignored: ${fallbackTimeoutMs}ms)")

        awaitClose {
            socketJob.cancel()
            firestoreListener.remove()
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
                // Cache-snapshot guard — same reasoning as the primary
                // listener; refuse to overwrite the live in-memory cache
                // with [] from a stale cache read.
                val isFromCache = snapshot?.metadata?.isFromCache == true
                val docCount = snapshot?.documents?.size ?: 0
                if (isFromCache && docCount == 0) return@addSnapshotListener

                // Use the manual parser instead of `toObject` — Firestore
                // stores Float fields as Double, so `toObject` returns
                // partial rows. (See observeLocationsWithCacheFallback for
                // the full rationale.)
                val parsed = snapshot?.documents?.mapNotNull { doc ->
                    try { parseSharedLocation(doc.id, doc.data) }
                    catch (e: Exception) {
                        Timber.w(e, "fallback listener parse failed: ${doc.id}")
                        null
                    }
                } ?: emptyList()

                val now = System.currentTimeMillis()
                val (active, inactive) = parsed.partition { location ->
                    location.isSharingActive &&
                        (location.sharingExpiresAt == 0L ||
                         location.sharingExpiresAt == Long.MAX_VALUE ||
                         now < location.sharingExpiresAt) &&
                        location.userId != uid
                }

                // Update the shared in-memory cache so the flow emits
                val updated = mutableMapOf<String, SharedLocation>()
                active.forEach { updated[it.userId] = it }
                socketLocationCache.value = updated

                // Persist to Room + reconcile stale rows on server snapshots
                repositoryScope.launch {
                    if (active.isNotEmpty()) {
                        locationDao.insertLocations(active.map { it.toEntity() })
                    }
                    if (!isFromCache) {
                        for (loc in inactive) {
                            locationDao.deleteByUserAndTarget(loc.userId, loc.groupId)
                        }
                        val seenUids = parsed.map { it.userId }.toSet()
                        val missing = locationDao.getAllActive()
                            .filter { it.userId != uid && it.userId !in seenUids }
                        for (row in missing) locationDao.deleteById(row.id)
                    }
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
                // Cache-snapshot guard.
                val isFromCache = snapshot?.metadata?.isFromCache == true
                val docCount = snapshot?.documents?.size ?: 0
                if (isFromCache && docCount == 0) return@addSnapshotListener

                // Use the manual parser — toObject() silently fails on the
                // Float fields (Firestore stores as Double).
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val location = parseSharedLocation(doc.id, doc.data)
                            ?: return@mapNotNull null
                        val now = System.currentTimeMillis()
                        val stillActive = location.isSharingActive &&
                            (location.sharingExpiresAt == 0L ||
                             location.sharingExpiresAt == Long.MAX_VALUE ||
                             now < location.sharingExpiresAt)
                        location.copy(isSharingActive = stillActive)
                    } catch (e: Exception) {
                        Timber.w(e, "observeGroupLocations parse failed: ${doc.id}")
                        null
                    }
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
                    // Cache-snapshot guard: if the doc doesn't exist yet
                    // in the cache, leave the in-memory map untouched
                    // until the server confirms.
                    if (snapshot != null && !snapshot.exists() && snapshot.metadata.isFromCache) {
                        return@addSnapshotListener
                    }
                    // Use the manual parser — toObject() silently fails on
                    // accuracy/speed/bearing (Float vs Double).
                    val location = snapshot?.let {
                        try { parseSharedLocation(it.id, it.data) }
                        catch (e: Exception) {
                            Timber.w(e, "observeDirectLocationShares parse failed: ${it.id}")
                            null
                        }
                    }
                    if (location != null) {
                        val now = System.currentTimeMillis()
                        val stillActive = location.isSharingActive &&
                            (location.sharingExpiresAt == 0L || location.sharingExpiresAt == Long.MAX_VALUE || now < location.sharingExpiresAt)
                        if (stillActive) {
                            locations[friendId] = location.copy(groupId = "direct:$friendId", isSharingActive = true)
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
                // Cache-snapshot guard: don't emit null on a cache miss
                // for a user we know is sharing — wait for server snapshot.
                if (snapshot != null && !snapshot.exists() && snapshot.metadata.isFromCache) {
                    return@addSnapshotListener
                }
                // Use the manual parser — toObject() silently fails on
                // accuracy/speed/bearing (Float vs Double).
                val location = snapshot?.let {
                    try { parseSharedLocation(it.id, it.data) }
                    catch (e: Exception) {
                        Timber.w(e, "observeUserLocation parse failed: ${it.id}")
                        null
                    }
                }
                val resolvedLocation = if (location != null) {
                    val now = System.currentTimeMillis()
                    val stillActive = location.isSharingActive &&
                        (location.sharingExpiresAt == 0L || location.sharingExpiresAt == Long.MAX_VALUE || now < location.sharingExpiresAt)
                    location.copy(isSharingActive = stillActive)
                } else null
                trySend(resolvedLocation).isSuccess
            }
        awaitClose { listener.remove() }
    }

    override fun isSharingLocation(): Boolean {
        val session = currentSession ?: return false
        // Prune any expired targets; if nothing is left, the session is over.
        val active = pruneExpired(session.targetExpiries)
        if (active.isEmpty()) {
            setCurrentSession(null)
            return false
        }
        if (active.size != session.targetExpiries.size) {
            // Reflect pruning in memory state
            setCurrentSession(session.copy(targetExpiries = active))
        }
        return true
    }

    override fun getSharingTargetIds(): List<String> {
        return if (isSharingLocation()) currentSession?.targetIds ?: emptyList() else emptyList()
    }

    override fun getTargetExpiries(): Map<String, Long> {
        return if (isSharingLocation()) currentSession?.targetExpiries ?: emptyMap() else emptyMap()
    }

    override fun getSharingExpiryTime(): Long? {
        return if (isSharingLocation()) currentSession?.overallExpiry else null
    }

    override suspend fun checkSharingStatus(): List<String> {
        return try {
            val uid = currentUid ?: return emptyList()

            // Step 1: in-memory cache (0 reads)
            val cached = currentSession
            if (cached != null) {
                val active = pruneExpired(cached.targetExpiries)
                if (active.isNotEmpty()) {
                    if (active.size != cached.targetExpiries.size) {
                        setCurrentSession(cached.copy(targetExpiries = active))
                    }
                    return active.keys.toList()
                } else {
                    setCurrentSession(null)
                }
            }

            // Step 2: single Firestore read
            val activeDoc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)
                .document(uid)
                .get()
                .await()

            if (activeDoc.exists()) {
                val isActive = activeDoc.getBoolean("isSharingActive") ?: false
                val legacyExpiresAt = activeDoc.getLong("sharingExpiresAt") ?: 0L
                @Suppress("UNCHECKED_CAST")
                val rawExpiries = activeDoc.get("targetExpiries") as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val targetIds = (activeDoc.get("targetIds") as? List<String>)
                    ?: activeDoc.getString("targetId")?.takeIf { it.isNotEmpty() }?.let { listOf(it) }
                    ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val visibleTo = (activeDoc.get("visibleTo") as? List<String>) ?: emptyList()

                // Build per-target expiries: prefer doc map, fall back to legacy single value
                val targetExpiries: Map<String, Long> = when {
                    rawExpiries != null -> rawExpiries
                        .mapNotNull { (k, v) ->
                            val ms = (v as? Number)?.toLong() ?: return@mapNotNull null
                            k to ms
                        }
                        .toMap()
                    else -> targetIds.associateWith {
                        if (legacyExpiresAt > 0) legacyExpiresAt else Long.MAX_VALUE
                    }
                }
                val activeExpiries = pruneExpired(targetExpiries)

                if (isActive && activeExpiries.isNotEmpty()) {
                    setCurrentSession(
                        ActiveSharingSession(
                            targetExpiries = activeExpiries,
                            visibleTo = visibleTo
                        )
                    )
                    return activeExpiries.keys.toList()
                }
            }

            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to check sharing status")
            emptyList()
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

            fromSubcollection.ifEmpty {
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
        address: String,
        memberIds: List<String>
    ): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val now = System.currentTimeMillis()

            // Seed the participants map so every group member starts in
            // ON_THE_WAY. This is what drives every member's auto-share on
            // their device when the snapshot lands. Falls back to just the
            // creator if memberIds is empty (defensive — shouldn't happen
            // when called from the VM, but a meetup with the creator alone
            // is still valid).
            val seedMembers = memberIds.ifEmpty { listOf(uid) }
            val participantsMap = seedMembers.associateWith {
                hashMapOf(
                    "status" to com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY.name,
                    "updatedAt" to now
                )
            }

            val destinationData = hashMapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "name" to name,
                "address" to address,
                "setBy" to uid,
                "setAt" to now,
                "isActive" to true,
                "participants" to participantsMap
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
                        "longitude" to 0.0,
                        "participants" to emptyMap<String, Any>()
                    )
                )
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear meetup destination")
            Resource.Error(e.message ?: "Failed to clear meetup destination")
        }
    }

    /**
     * Updates only the calling user's entry in the participants map.
     * Uses Firestore's dotted-path syntax so other participants' entries
     * are untouched and we minimize the document write footprint.
     */
    override suspend fun updateMeetupParticipantStatus(
        groupId: String,
        status: com.ovi.where.domain.model.MeetupParticipantStatus
    ): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val now = System.currentTimeMillis()
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .update(
                    mapOf(
                        "meetupDestination.participants.$uid.status" to status.name,
                        "meetupDestination.participants.$uid.updatedAt" to now
                    )
                )
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update meetup participant status")
            Resource.Error(e.message ?: "Failed to update status")
        }
    }

    /**
     * Updates only the calling user's free-form note ("custom status") on
     * the destination's participants map. Empty string clears the note.
     * Same dotted-path strategy as [updateMeetupParticipantStatus] so we
     * never touch other participants' entries.
     */
    override suspend fun updateMeetupParticipantNote(
        groupId: String,
        note: String
    ): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val now = System.currentTimeMillis()
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .update(
                    mapOf(
                        "meetupDestination.participants.$uid.note" to note,
                        "meetupDestination.participants.$uid.updatedAt" to now
                    )
                )
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update meetup participant note")
            Resource.Error(e.message ?: "Failed to update status")
        }
    }

    /**
     * Per-group meetup-destination listener registry. Keeps one Firestore
     * snapshot listener per group active for the lifetime of the process,
     * mirroring snapshots into Room (SSOT). Multiple UI consumers can
     * subscribe to the same group through Room without spawning new
     * listeners.
     */
    private val meetupListeners = mutableMapOf<String, ListenerRegistration>()
    private val meetupListenerLock = Any()

    override fun observeMeetupDestination(groupId: String): Flow<MeetupDestination?> {
        ensureMeetupListener(groupId)
        return meetupDestinationDao.observeByGroup(groupId).map { entity ->
            entity?.takeIf { it.isActive }?.toDomain()
        }
    }

    /**
     * Starts (or reuses) the Firestore listener for [groupId] that mirrors
     * the meetup destination into Room. Idempotent — calling it multiple
     * times just keeps the same listener around.
     */
    private fun ensureMeetupListener(groupId: String) {
        synchronized(meetupListenerLock) {
            if (meetupListeners.containsKey(groupId)) return
            val reg = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "Meetup destination listener error for $groupId")
                        return@addSnapshotListener
                    }
                    val destMap = snapshot?.get("meetupDestination") as? Map<*, *>
                    val isFromCache = snapshot?.metadata?.isFromCache == true

                    if (destMap != null && destMap["isActive"] == true) {
                        val rawParticipants = destMap["participants"] as? Map<*, *>
                        val participants = rawParticipants
                            ?.mapNotNull { (k, v) ->
                                val uid = k as? String ?: return@mapNotNull null
                                val entry = v as? Map<*, *> ?: return@mapNotNull null
                                uid to com.ovi.where.domain.model.MeetupParticipant(
                                    status = com.ovi.where.domain.model.MeetupParticipantStatus
                                        .fromString(entry["status"] as? String),
                                    updatedAt = (entry["updatedAt"] as? Number)?.toLong() ?: 0L,
                                    note = (entry["note"] as? String).orEmpty()
                                )
                            }
                            ?.toMap()
                            .orEmpty()

                        val destination = MeetupDestination(
                            latitude = (destMap["latitude"] as? Number)?.toDouble() ?: 0.0,
                            longitude = (destMap["longitude"] as? Number)?.toDouble() ?: 0.0,
                            name = destMap["name"] as? String ?: "",
                            address = destMap["address"] as? String ?: "",
                            setBy = destMap["setBy"] as? String ?: "",
                            setAt = (destMap["setAt"] as? Number)?.toLong() ?: 0L,
                            isActive = true,
                            participants = participants
                        )
                        repositoryScope.launch {
                            meetupDestinationDao.upsert(destination.toEntity(groupId))
                        }
                    } else {
                        // Cache-snapshot guard: a cache snapshot that's
                        // missing the meetupDestination field doesn't prove
                        // the destination has been cleared — only the server
                        // snapshot is authoritative for deletions. Without
                        // this guard a momentary cache hit would erase the
                        // active destination from Room and the bottom sheet
                        // would briefly show no meetup.
                        if (isFromCache) {
                            Timber.d("Meetup listener: ignoring inactive cache snapshot for $groupId")
                            return@addSnapshotListener
                        }
                        // Inactive / missing — drop the row so observers see null.
                        repositoryScope.launch {
                            meetupDestinationDao.deleteByGroup(groupId)
                        }
                    }
                }
            meetupListeners[groupId] = reg
        }
    }
}
