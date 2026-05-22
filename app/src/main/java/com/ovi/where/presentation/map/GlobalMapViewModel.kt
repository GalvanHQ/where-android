package com.ovi.where.presentation.map

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager as SystemLocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.core.constants.AppConstants.DISTANCE_KM_THRESHOLD_METERS
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_DAY
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_HOUR
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_MINUTE
import com.ovi.where.core.constants.AppConstants.TIME_AGO_REFRESH_INTERVAL_MS
import com.ovi.where.core.constants.AppConstants.USER_FETCH_DEBOUNCE_MS
import com.ovi.where.data.local.prefs.UserPreferences
import com.ovi.where.data.location.LocationManager
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.SystemEventType
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
import com.ovi.where.domain.usecase.location.ClearMeetupDestinationUseCase
import com.ovi.where.domain.usecase.location.DetectArrivalUseCase
import com.ovi.where.domain.usecase.location.ObserveActiveLocationsUseCase
import com.ovi.where.domain.usecase.location.ObserveMeetupDestinationUseCase
import com.ovi.where.domain.usecase.location.SetMeetupDestinationUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A single friend's location as shown on the global map.
 */
data class FriendLocationUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val timeAgo: String,
    val isActive: Boolean,
    val groupId: String,
    val groupName: String,
    val distanceMeters: Float? = null,
    /** Formatted ETA string (e.g., "5 min", "1h 12m"). Null if distance < 50m or no user location. */
    val etaText: String? = null,
    /** Raw epoch millis for timeAgo recalculation on ticker */
    val lastUpdatedTimestamp: Long = 0L
)

/**
 * Maximum number of member avatars previewed per group in stacked-avatar
 * surfaces (the meetup-destination picker, etc.). Three balances visual
 * density with read cost.
 */
private const val MAX_PREVIEW_AVATARS = 3

/** Simple group representation for the filter pill / sheet. */
data class GroupFilter(
    val id: String,
    val name: String,
    val isDirect: Boolean = false,
    val photoUrl: String? = null,
    /**
     * Photo URLs of the first few group members (in arbitrary order). Used
     * by stacked-avatar previews — e.g. the meetup-destination group picker.
     * Empty for direct targets and for groups whose user profiles haven't
     * been fetched yet. Items may be null when a member has no photo set.
     */
    val memberPhotos: List<String?> = emptyList()
)

@HiltViewModel
class GlobalMapViewModel @Inject constructor(
    application: Application,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val observeActiveLocationsUseCase: ObserveActiveLocationsUseCase,
    private val startLocationSharingUseCase: StartLocationSharingUseCase,
    private val stopLocationSharingUseCase: StopLocationSharingUseCase,
    private val getUsersUseCase: GetUsersUseCase,
    private val locationRepository: LocationRepository,
    private val locationManager: LocationManager,
    private val firebaseAuth: FirebaseAuth,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val userPreferences: UserPreferences,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val setMeetupDestinationUseCase: SetMeetupDestinationUseCase,
    private val clearMeetupDestinationUseCase: ClearMeetupDestinationUseCase,
    private val observeMeetupDestinationUseCase: ObserveMeetupDestinationUseCase,
    private val detectArrivalUseCase: DetectArrivalUseCase,
    private val conversationRepository: ConversationRepository,
    private val systemMessageWriter: com.ovi.where.data.repository.SystemMessageWriter
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GlobalMapUiState())
    val uiState: StateFlow<GlobalMapUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _navigateToChat = MutableStateFlow<String?>(null)
    val navigateToChat: StateFlow<String?> = _navigateToChat.asStateFlow()

    // User profile cache (userId → User)
    private val userCache = mutableMapOf<String, User>()

    // Debounced user-fetch tracking
    private val pendingUserFetchIds = mutableSetOf<String>()
    private var userFetchJob: Job? = null

    // Single consolidated location stream job
    private var locationStreamJob: Job? = null
    private var friendsJob: Job? = null

    // Auto-refresh timeAgo ticker
    private var timeAgoRefreshJob: Job? = null

    // Sharing countdown ticker
    private var countdownJob: Job? = null

    // Meetup destination listener (per active group filter)
    private var meetupDestinationJob: Job? = null

    // Cache raw SharedLocation list for re-processing without re-subscribing
    @Volatile
    private var lastRawLocations: List<SharedLocation> = emptyList()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    private val locationProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SystemLocationManager.PROVIDERS_CHANGED_ACTION) {
                checkGpsEnabled()
            }
        }
    }

    init {
        restoreLastCameraPosition()
        loadGroupsAndStartObserving()
        observeFriends()
        observeConsolidatedLocations()
        observeLocationOffDialogPref()
        observeCurrentUser()
        checkGpsEnabled()
        registerLocationProviderReceiver()
        startTimeAgoTicker()
        autoLocateOnLaunch()
        restoreSharingState()
        observeRepoSharingState()
    }

    /**
     * Loads the last-known user location from DataStore so the map camera can
     * open near the user immediately — even if location services are currently
     * off. Always flips [GlobalMapUiState.cameraRestored] once the read finishes
     * (regardless of whether a saved coord exists), unblocking GoogleMap composition.
     */
    private fun restoreLastCameraPosition() {
        viewModelScope.launch {
            val saved = userPreferences.getLastKnownLocation()
            _uiState.value = if (saved != null) {
                _uiState.value.copy(
                    initialCameraLat = saved.first,
                    initialCameraLng = saved.second,
                    initialCameraZoom = 14f,
                    cameraRestored = true
                )
            } else {
                // First-ever launch — leave (0,0) zoom 2 default.
                _uiState.value.copy(cameraRestored = true)
            }
        }
    }

    /**
     * Silent auto-locate on ViewModel init (and when the user returns to the
     * Map tab after the previous instance was cleared). Updates the marker
     * position but does **not** request a camera move — otherwise every tab
     * return would visibly zoom from the saved-state camera to the live fix.
     */
    private fun autoLocateOnLaunch() {
        viewModelScope.launch {
            locateMeInternal(moveCamera = false)
        }
    }

    /**
     * Restores sharing state from persisted session data on ViewModel init.
     * If the persisted session has already expired, clears it and stops the service.
     * Otherwise, restores the countdown and sharing indicator.
     *
     * Requirement 12.5: Restore within 2 seconds of ViewModel initialization.
     * Requirement 12.6: If expired, set inactive and clear instead of restoring.
     */
    private fun restoreSharingState() {
        viewModelScope.launch {
            try {
                val savedMarker = userPreferences.sharingTargetId.first()
                val savedExpiry = userPreferences.sharingExpiresAt.first()

                if (savedMarker.isNullOrEmpty()) return@launch

                val now = System.currentTimeMillis()

                // Quick check using the persisted overall expiry
                if (savedExpiry != null && savedExpiry != Long.MAX_VALUE && now >= savedExpiry) {
                    Timber.d("restoreSharingState: persisted session expired, cleaning up")
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingTargetIds = emptyList(),
                        sharingTargetExpiries = emptyMap(),
                        sharingExpiresAt = null,
                        sharingCountdown = null
                    )
                    userPreferences.clearSharingSession()
                    stopLocationService()
                    return@launch
                }

                // Restore active session — fetch authoritative targetIds + expiries from Firestore
                val targetIds = locationRepository.checkSharingStatus()
                if (targetIds.isEmpty()) {
                    userPreferences.clearSharingSession()
                    return@launch
                }
                val expiries = locationRepository.getTargetExpiries()
                val overall = expiries.values.maxOrNull()
                _uiState.value = _uiState.value.copy(
                    isSharing = true,
                    sharingTargetIds = targetIds,
                    sharingTargetExpiries = expiries,
                    sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                    sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall)
                )
                startCountdownTicker()
            } catch (e: Exception) {
                Timber.e(e, "restoreSharingState failed")
            }
        }
    }

    private fun startCountdownTicker() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(15_000L) // poll every 15s for finer per-target expiry granularity
                val state = _uiState.value
                val expiries = state.sharingTargetExpiries
                if (expiries.isEmpty()) break

                val now = System.currentTimeMillis()
                val expired = expiries.filterValues { it != Long.MAX_VALUE && it <= now }.keys
                val active = expiries - expired

                if (expired.isNotEmpty()) {
                    // Stop each expired target server-side. Last one will end the session.
                    expired.forEach { targetId ->
                        stopLocationSharingUseCase(targetId)
                    }
                    val targetNames = expired.joinToString(", ") { id ->
                        (state.groups + state.directTargets).firstOrNull { it.id == id }?.name ?: id
                    }
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.DynamicString("Stopped sharing with $targetNames")))
                }

                if (active.isEmpty()) {
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingTargetIds = emptyList(),
                        sharingTargetExpiries = emptyMap(),
                        sharingExpiresAt = null,
                        sharingCountdown = null
                    )
                    stopLocationService()
                    userPreferences.clearSharingSession()
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.toast_sharing_stopped)))
                    break
                }

                val overall = active.values.maxOrNull()
                _uiState.value = _uiState.value.copy(
                    sharingTargetIds = active.keys.toList(),
                    sharingTargetExpiries = active,
                    sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                    sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall)
                )
            }
        }
    }

    /**
     * Mirrors `LocationRepository.activeSharingState` into the UI state so the
     * map's sharing pill, FAB countdown, and "My shares" tab always match the
     * single repo flow. Using `ActiveSharingState`'s helper properties means
     * we never recompute `targetIds` or `overallExpiry` ad-hoc here — the
     * domain model owns that derivation.
     */
    private fun observeRepoSharingState() {
        viewModelScope.launch {
            locationRepository.activeSharingState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isSharing = state.isSharing,
                    sharingTargetIds = state.targetIds,
                    sharingTargetExpiries = state.targetExpiries,
                    sharingExpiresAt = state.overallExpiry,
                    sharingCountdown = formatCountdown(state.overallExpiry)
                )
            }
        }
    }

    /**
     * Formats the remaining time until expiry as a countdown string.
     * Delegates to [SharingTimeFormatter] so the map FAB pill and the chat
     * header pill always render the same value for the same expiry.
     */
    private fun formatCountdown(expiresAt: Long?): String? {
        return com.ovi.where.presentation.common.SharingTimeFormatter
            .formatRemaining(expiresAt)
    }

    private fun checkGpsEnabled() {
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as SystemLocationManager
        val enabled = lm.isProviderEnabled(SystemLocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)
        _uiState.value = _uiState.value.copy(isLocationEnabled = enabled)
    }

    private fun registerLocationProviderReceiver() {
        val app = getApplication<Application>()
        val filter = IntentFilter(SystemLocationManager.PROVIDERS_CHANGED_ACTION)
        app.registerReceiver(locationProviderReceiver, filter)
    }

    private fun unregisterLocationProviderReceiver() {
        try {
            getApplication<Application>().unregisterReceiver(locationProviderReceiver)
        } catch (_: IllegalArgumentException) { /* not registered */ }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                _uiState.value = _uiState.value.copy(myPhotoUrl = user?.photoUrl)
            }
        }
    }

    private fun observeLocationOffDialogPref() {
        viewModelScope.launch {
            userPreferences.isLocationOffDialogShown.collect { shown ->
                if (!shown) {
                    _uiState.value = _uiState.value.copy(showLocationOffDialog = true)
                }
            }
        }
    }

    // ── Consolidated Location Stream (replaces N groupJobs + directLocationJob) ──

    private fun loadGroupsAndStartObserving() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = getUserGroupsUseCase()) {
                is Resource.Success -> {
                    val groups = result.data ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        groups = groups.map {
                            GroupFilter(
                                id = it.id,
                                name = it.name,
                                photoUrl = it.avatarUrl,
                                // Seed empty — photos arrive asynchronously via
                                // resolveGroupMemberPhotos() below.
                                memberPhotos = emptyList()
                            )
                        }
                    )
                    // Hydrate member photos in the background so the meetup
                    // group picker (and any other stacked-avatar surface)
                    // shows real faces.
                    resolveGroupMemberPhotos(groups)
                    // Check sharing sessions in background
                    checkAllSharingSessions()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Fetches profile photos for up to `MAX_PREVIEW_AVATARS` members of each
     * group and updates [GlobalMapUiState.groups] in place. Reuses the
     * shared [userCache] so groups that share members don't trigger
     * duplicate fetches, and uses [getUsersUseCase] for the missing ids.
     */
    private fun resolveGroupMemberPhotos(groups: List<com.ovi.where.domain.model.Group>) {
        if (groups.isEmpty()) return
        viewModelScope.launch {
            val maxPreview = MAX_PREVIEW_AVATARS
            // Pick the first N member ids per group, then dedupe globally so
            // we hit Firestore once per unique uid.
            val previewIds: Map<String, List<String>> = groups.associate { g ->
                g.id to g.memberIds.take(maxPreview)
            }
            val unknownIds = previewIds.values.flatten().distinct()
                .filter { it.isNotBlank() && it !in userCache }
            if (unknownIds.isNotEmpty()) {
                when (val result = getUsersUseCase(unknownIds)) {
                    is Resource.Success ->
                        result.data?.forEach { userCache[it.id] = it }
                    else -> {} // Best-effort — fall back to color dots.
                }
            }
            // Re-map state with photos pulled from the now-warm cache.
            val updated = _uiState.value.groups.map { gf ->
                val ids = previewIds[gf.id].orEmpty()
                if (ids.isEmpty()) gf
                else gf.copy(
                    memberPhotos = ids.map { uid -> userCache[uid]?.photoUrl }
                )
            }
            _uiState.value = _uiState.value.copy(groups = updated)
        }
    }

    private fun observeFriends() {
        friendsJob?.cancel()
        friendsJob = viewModelScope.launch {
            observeFriendsUseCase().collect { friends ->
                // FriendEntry already carries display fields — no need to cache as User.
                val directTargets = friends.map {
                    GroupFilter(
                        id = "direct:${it.friendUid}",
                        name = it.displayName,
                        isDirect = true,
                        photoUrl = it.photoUrl
                    )
                }
                _uiState.value = _uiState.value.copy(directTargets = directTargets)
                checkAllSharingSessions()
            }
        }
    }

    /**
     * Single consolidated Firestore listener for ALL locations visible to current user.
     * Replaces N per-group listeners + direct location listener.
     *
     * Firebase read optimization: This is a SINGLE snapshot listener on the
     * `activeLocations` collection filtered by `visibleTo` array-contains.
     * Firestore counts 1 read per document returned per snapshot emission.
     * Re-processing (filter change, user cache update) reuses [lastRawLocations]
     * without triggering additional Firestore reads.
     */
    private fun observeConsolidatedLocations() {
        locationStreamJob?.cancel()
        locationStreamJob = viewModelScope.launch {
            observeActiveLocationsUseCase()
                .distinctUntilChanged()
                .collect { locations ->
                    lastRawLocations = locations
                    processLocationUpdates(locations)
                }
        }
    }

    private fun processLocationUpdates(locations: List<SharedLocation>) {
        val uid = currentUserId ?: return
        val activeFilter = _uiState.value.activeGroupFilter

        // Client-side filter: match if filter id is in targetIds (or legacy targetId)
        val filtered = if (activeFilter != null) {
            locations.filter { activeFilter.id in it.targetIds || it.targetId == activeFilter.id }
        } else {
            locations
        }

        // Deduplicate by userId — keep latest timestamp per user
        val deduped = filtered
            .filter { it.userId != uid && it.isSharingActive }
            .groupBy { it.userId }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.timestamp }!! }
            .values.toList()

        // Build UI models with distance-from-me
        // Use denormalized displayName/photoUrl from location documents first,
        // then fall back to in-memory user cache, then userId as last resort.
        // This eliminates separate Firestore user profile reads.
        val myLat = _uiState.value.myLatitude
        val myLng = _uiState.value.myLongitude
        val hasMyLoc = _uiState.value.hasMyLocation
        val groups = _uiState.value.groups
        val directTargets = _uiState.value.directTargets

        // Only fetch user profiles for locations with empty denormalized fields
        val unknownIds = deduped
            .filter { it.displayName.isEmpty() && !userCache.containsKey(it.userId) }
            .map { it.userId }
        if (unknownIds.isNotEmpty()) {
            debouncedFetchUsers(unknownIds)
        }

        val friendLocations = deduped.map { loc ->
            // Fallback chain: denormalized field → in-memory cache → userId
            val displayName = loc.displayName.ifEmpty {
                userCache[loc.userId]?.displayName ?: loc.userId
            }
            val photoUrl = loc.photoUrl?.takeIf { it.isNotEmpty() }
                ?: userCache[loc.userId]?.photoUrl

            val target = groups.firstOrNull { it.id == loc.targetId }
                ?: directTargets.firstOrNull { it.id == loc.targetId }
            val groupName = target?.name ?: if (loc.targetType == "direct") "Direct" else ""

            val distance = if (hasMyLoc && loc.latitude != 0.0 && loc.longitude != 0.0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(myLat, myLng, loc.latitude, loc.longitude, results)
                results[0]
            } else null

            // Stale indicator: mark locations > 5 minutes old
            val isStale = loc.timestamp > 0L &&
                (System.currentTimeMillis() - loc.timestamp) > 5 * 60_000L

            FriendLocationUiModel(
                userId = loc.userId,
                displayName = displayName,
                username = userCache[loc.userId]?.username ?: "",
                photoUrl = photoUrl,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                speed = loc.speed,
                bearing = loc.bearing,
                timeAgo = formatTimeAgo(loc.timestamp),
                isActive = loc.isSharingActive && !isStale,
                groupId = loc.targetId,
                groupName = groupName,
                distanceMeters = distance,
                etaText = computeEta(distance, loc.speed),
                lastUpdatedTimestamp = loc.timestamp
            )
        }

        _uiState.value = _uiState.value.copy(
            friendLocations = friendLocations,
            isLoading = false
        )
        // Destination metrics depend on the local user's speed (sourced from
        // their own SharedLocation row). Recompute on every batch so the ETA
        // refreshes as the user moves.
        recomputeDestinationMetrics()
        if (_uiState.value.hasMyLocation) {
            checkSelfArrival(_uiState.value.myLatitude, _uiState.value.myLongitude)
        }
    }

    /** Debounced user fetch — collect IDs for 200ms then batch fetch. */
    private fun debouncedFetchUsers(ids: List<String>) {
        synchronized(pendingUserFetchIds) { pendingUserFetchIds.addAll(ids) }
        userFetchJob?.cancel()
        userFetchJob = viewModelScope.launch {
            delay(USER_FETCH_DEBOUNCE_MS)
            val toFetch: List<String>
            synchronized(pendingUserFetchIds) {
                toFetch = pendingUserFetchIds.toList()
                pendingUserFetchIds.clear()
            }
            if (toFetch.isNotEmpty()) fetchUsers(toFetch)
        }
    }

    /**
     * Fetches user profiles and re-processes current locations to update display names.
     * Uses [lastRawLocations] to avoid triggering another Firestore read.
     */
    private suspend fun fetchUsers(userIds: List<String>) {
        when (val result = getUsersUseCase(userIds)) {
            is Resource.Success -> {
                result.data?.forEach { user -> userCache[user.id] = user }
                // Re-process with cached data — no additional Firestore reads
                if (lastRawLocations.isNotEmpty()) {
                    processLocationUpdates(lastRawLocations)
                }
            }
            else -> {}
        }
    }

    /**
     * Auto-refresh timeAgo every 30s.
     * Recalculates formatted time strings from raw timestamps and increments
     * [GlobalMapUiState.timeAgoTick] to break StateFlow structural equality.
     */
    private fun startTimeAgoTicker() {
        timeAgoRefreshJob?.cancel()
        timeAgoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(TIME_AGO_REFRESH_INTERVAL_MS)
                val current = _uiState.value
                if (current.friendLocations.isNotEmpty()) {
                    _uiState.value = current.copy(
                        friendLocations = current.friendLocations.map { friend ->
                            friend.copy(timeAgo = formatTimeAgo(friend.lastUpdatedTimestamp))
                        },
                        timeAgoTick = current.timeAgoTick + 1
                    )
                }
            }
        }
    }

    /**
     * Checks sharing status using a SINGLE Firestore read on the consolidated
     * `activeLocations/{uid}` document. Restores the in-memory session for any
     * still-active session.
     */
    private fun checkAllSharingSessions() {
        viewModelScope.launch {
            try {
                currentUserId ?: return@launch
                val activeTargetIds = locationRepository.checkSharingStatus()
                if (activeTargetIds.isNotEmpty()) {
                    val expiries = locationRepository.getTargetExpiries()
                    val overall = expiries.values.maxOrNull()
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingTargetIds = activeTargetIds,
                        sharingTargetExpiries = expiries,
                        sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall
                    )
                    startCountdownTicker()
                }
            } catch (e: Exception) {
                Timber.e(e, "checkAllSharingSessions failed")
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Applies a group filter and re-processes cached locations.
     * Does NOT restart the Firestore listener — reuses [lastRawLocations].
     * Firebase read optimization: 0 additional reads on filter change.
     */
    fun setGroupFilter(filter: GroupFilter?) {
        _uiState.value = _uiState.value.copy(activeGroupFilter = filter)
        // Re-process cached data without re-subscribing to Firestore
        if (lastRawLocations.isNotEmpty()) {
            processLocationUpdates(lastRawLocations)
        }
        // Switch the destination listener to the newly-selected group.
        observeMeetupDestinationFor(filter)
    }

    // ── Meetup destination ──────────────────────────────────────────────────

    /**
     * Single Firestore listener per active group filter. Direct conversations
     * and the "all groups" view (filter == null) clear the destination state
     * and cancel the listener so we don't pay for a stray snapshot listener
     * outside of a group context.
     */
    private fun observeMeetupDestinationFor(filter: GroupFilter?) {
        meetupDestinationJob?.cancel()
        if (filter == null || filter.isDirect || filter.id.isBlank()) {
            _uiState.value = _uiState.value.copy(
                meetupDestination = null,
                meetupDestinationDistanceText = null,
                meetupDestinationEtaText = null,
                arrivedDestinationKey = null
            )
            return
        }
        val groupId = filter.id
        meetupDestinationJob = viewModelScope.launch {
            observeMeetupDestinationUseCase(groupId).collect { destination ->
                val previous = _uiState.value.meetupDestination
                val newKey = destination?.let { destinationKey(groupId, it) }
                val previousKey = previous?.let { destinationKey(groupId, it) }
                _uiState.value = _uiState.value.copy(
                    meetupDestination = destination,
                    arrivedDestinationKey = if (newKey != previousKey) null else _uiState.value.arrivedDestinationKey
                )
                recomputeDestinationMetrics()
                // Re-check arrival immediately when a new destination lands —
                // the user may already be inside the radius.
                if (_uiState.value.hasMyLocation) {
                    checkSelfArrival(_uiState.value.myLatitude, _uiState.value.myLongitude)
                }
            }
        }
    }

    /** Stable per-session id used to deduplicate `MEETUP_ARRIVED` writes. */
    private fun destinationKey(groupId: String, destination: MeetupDestination): String =
        "${groupId}_${destination.setAt}"

    /**
     * Re-renders the distance + ETA labels on the destination card. Cheap —
     * just reformats existing values, no Firestore reads.
     */
    private fun recomputeDestinationMetrics() {
        val state = _uiState.value
        val destination = state.meetupDestination
        if (destination == null || !destination.hasValidLocation || !state.hasMyLocation) {
            _uiState.value = state.copy(
                meetupDestinationDistanceText = null,
                meetupDestinationEtaText = null
            )
            return
        }
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            state.myLatitude, state.myLongitude,
            destination.latitude, destination.longitude,
            results
        )
        val distance = results[0]
        // Find the local user's current speed from cached locations (if they're
        // sharing). Falls back to 0 → ETA helper assumes ~50 km/h.
        val mySpeed = lastRawLocations.firstOrNull { it.userId == currentUserId }?.speed ?: 0f
        _uiState.value = state.copy(
            meetupDestinationDistanceText = formatDistance(distance),
            meetupDestinationEtaText = computeEta(distance, mySpeed)
        )
    }

    /**
     * Called by [GlobalMapScreen] when the user long-presses the map.
     * Captures the lat/lng, kicks off a best-effort reverse-geocode, then
     * shows the "Set meetup point" sheet.
     */
    fun onMapLongClick(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            // If there are no groups, we can't pick a destination — bail out.
            if (_uiState.value.groups.isEmpty()) {
                _uiEvent.send(
                    UiEvent.ShowSnackbar(
                        UiText.DynamicString("Join or create a group to set a meetup point")
                    )
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                pendingDestinationPick = PendingDestinationPick(
                    latitude = latitude,
                    longitude = longitude,
                    address = null,
                    isResolvingAddress = true
                ),
                showSetDestinationSheet = true
            )
            val resolved = geocodeAddress(latitude, longitude)
            // Only update if the user hasn't already cancelled / re-picked.
            val currentPick = _uiState.value.pendingDestinationPick
            if (currentPick != null &&
                currentPick.latitude == latitude &&
                currentPick.longitude == longitude
            ) {
                _uiState.value = _uiState.value.copy(
                    pendingDestinationPick = currentPick.copy(
                        address = resolved,
                        isResolvingAddress = false
                    )
                )
            }
        }
    }

    fun dismissSetDestinationSheet() {
        _uiState.value = _uiState.value.copy(
            showSetDestinationSheet = false,
            pendingDestinationPick = null
        )
    }

    /**
     * Enters discoverable placement mode. The map screen renders a centered
     * crosshair + bottom action bar instead of the regular FABs. The user
     * pans/zooms to the spot and confirms via the action bar.
     *
     * No-op (with a snackbar) when the user has no groups, since the
     * destination is group-scoped and there's nothing to pin it to.
     */
    fun enterMeetupPlacement() {
        if (_uiState.value.groups.isEmpty()) {
            viewModelScope.launch {
                _uiEvent.send(
                    UiEvent.ShowSnackbar(
                        UiText.DynamicString("Join or create a group to set a meetup point")
                    )
                )
            }
            return
        }
        _uiState.value = _uiState.value.copy(
            isMeetupPlacementMode = true,
            placementAddress = null,
            isResolvingPlacementAddress = false,
            // Make sure no stale modal is still open.
            showSetDestinationSheet = false,
            pendingDestinationPick = null
        )
    }

    /** Cancels placement mode without setting a destination. */
    fun cancelMeetupPlacement() {
        _uiState.value = _uiState.value.copy(
            isMeetupPlacementMode = false,
            placementAddress = null,
            isResolvingPlacementAddress = false
        )
    }

    /**
     * Called by the screen each time the camera idles while in placement
     * mode. Reverse-geocodes the centered coordinates so the action bar can
     * show the resolved address without spamming the geocoder while the
     * user pans.
     */
    fun onPlacementCameraIdle(latitude: Double, longitude: Double) {
        if (!_uiState.value.isMeetupPlacementMode) return
        _uiState.value = _uiState.value.copy(isResolvingPlacementAddress = true)
        viewModelScope.launch {
            val resolved = geocodeAddress(latitude, longitude)
            // Only apply the result if the user is still in placement mode —
            // they may have cancelled while geocoding was in flight.
            if (_uiState.value.isMeetupPlacementMode) {
                _uiState.value = _uiState.value.copy(
                    placementAddress = resolved,
                    isResolvingPlacementAddress = false
                )
            }
        }
    }

    /**
     * Confirms the placement mode pick. Mirrors the long-press flow — sets
     * the pending pick, then either auto-confirms when there's exactly one
     * eligible group (or the active filter resolves to one) or opens the
     * redesigned sheet for group selection.
     */
    fun confirmMeetupPlacement(latitude: Double, longitude: Double) {
        val state = _uiState.value
        if (!state.isMeetupPlacementMode) return
        val groups = state.groups
        if (groups.isEmpty()) {
            cancelMeetupPlacement()
            return
        }

        val pendingPick = PendingDestinationPick(
            latitude = latitude,
            longitude = longitude,
            address = state.placementAddress,
            isResolvingAddress = state.isResolvingPlacementAddress
        )
        _uiState.value = state.copy(
            isMeetupPlacementMode = false,
            placementAddress = null,
            isResolvingPlacementAddress = false,
            pendingDestinationPick = pendingPick,
            showSetDestinationSheet = true
        )

        // Kick off a fresh geocode if we don't have an address yet — the
        // sheet will pick it up via the existing pendingDestinationPick flow.
        if (pendingPick.address.isNullOrBlank()) {
            viewModelScope.launch {
                val resolved = geocodeAddress(latitude, longitude)
                val current = _uiState.value.pendingDestinationPick
                if (current != null &&
                    current.latitude == latitude &&
                    current.longitude == longitude
                ) {
                    _uiState.value = _uiState.value.copy(
                        pendingDestinationPick = current.copy(
                            address = resolved,
                            isResolvingAddress = false
                        )
                    )
                }
            }
        }
    }

    /**
     * Confirms the picked destination for [groupId]. Authors a
     * `MEETUP_DESTINATION_SET` system message into the group's chat once the
     * Firestore write succeeds.
     */
    fun confirmDestinationPick(groupId: String, name: String) {
        val pick = _uiState.value.pendingDestinationPick ?: return
        val trimmed = name.trim().ifBlank { pick.address?.substringBefore(',')?.trim().orEmpty() }
            .ifBlank { "Meetup point" }
        viewModelScope.launch {
            when (
                setMeetupDestinationUseCase(
                    groupId = groupId,
                    latitude = pick.latitude,
                    longitude = pick.longitude,
                    name = trimmed,
                    address = pick.address.orEmpty()
                )
            ) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showSetDestinationSheet = false,
                        pendingDestinationPick = null
                    )
                    writeMeetupSystemMessage(
                        groupId = groupId,
                        eventType = SystemEventType.MEETUP_DESTINATION_SET,
                        payload = mapOf(
                            "name" to trimmed,
                            "address" to pick.address.orEmpty()
                        ),
                        fallbackText = run {
                            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                            "$actor set the meetup point at \"$trimmed\""
                        }
                    )
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Meetup point set")
                        )
                    )
                }
                is Resource.Error -> {
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Couldn't set meetup point. Try again.")
                        )
                    )
                }
                else -> {}
            }
        }
    }

    /** Clears the active destination for the currently-filtered group. */
    fun clearMeetupDestination() {
        val filter = _uiState.value.activeGroupFilter ?: return
        if (filter.isDirect || filter.id.isBlank()) return
        val previous = _uiState.value.meetupDestination
        viewModelScope.launch {
            when (clearMeetupDestinationUseCase(filter.id)) {
                is Resource.Success -> {
                    writeMeetupSystemMessage(
                        groupId = filter.id,
                        eventType = SystemEventType.MEETUP_DESTINATION_CLEARED,
                        payload = mapOf("name" to (previous?.name ?: "")),
                        fallbackText = run {
                            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                            "$actor cleared the meetup point"
                        }
                    )
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Meetup point cleared")
                        )
                    )
                }
                is Resource.Error -> {
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Couldn't clear meetup point")
                        )
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * One-shot arrival check. If the local user has just entered the arrival
     * radius for the current destination and we haven't authored
     * `MEETUP_ARRIVED` for this destination key yet, write the system message
     * and remember the key.
     */
    private fun checkSelfArrival(myLat: Double, myLng: Double) {
        val state = _uiState.value
        val destination = state.meetupDestination ?: return
        val filter = state.activeGroupFilter ?: return
        if (filter.isDirect || filter.id.isBlank()) return
        if (!destination.hasValidLocation || !destination.isActive) return
        val key = destinationKey(filter.id, destination)
        if (state.arrivedDestinationKey == key) return // already announced
        if (!detectArrivalUseCase.hasArrived(myLat, myLng, destination)) return

        _uiState.value = state.copy(arrivedDestinationKey = key)
        viewModelScope.launch {
            writeMeetupSystemMessage(
                groupId = filter.id,
                eventType = SystemEventType.MEETUP_ARRIVED,
                payload = mapOf("name" to destination.name),
                fallbackText = run {
                    val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                    "$actor arrived at the meetup point"
                }
            )
        }
    }

    /**
     * Resolves the conversation for [groupId] and writes a system message via
     * [SystemMessageWriter]. Failures are swallowed — the destination state
     * change matters more than the cosmetic timeline entry.
     */
    private suspend fun writeMeetupSystemMessage(
        groupId: String,
        eventType: SystemEventType,
        payload: Map<String, String>,
        fallbackText: String
    ) {
        val conversationId = try {
            conversationRepository.getConversationIdByGroupId(groupId)
        } catch (e: Exception) {
            Timber.w(e, "writeMeetupSystemMessage: failed to resolve conversationId for $groupId")
            return
        } ?: return
        systemMessageWriter.writeSystemMessage(
            conversationId = conversationId,
            eventType = eventType,
            payload = payload,
            fallbackText = fallbackText
        )
    }

    /**
     * Reverse-geocode helper. Best-effort: returns null on failure or empty
     * results. Stays off the main thread; uses [android.location.Geocoder]'s
     * synchronous overload (Android 13+ has an async API but the sync one is
     * fine on `Dispatchers.IO`).
     */
    private suspend fun geocodeAddress(latitude: Double, longitude: Double): String? {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val geocoder = android.location.Geocoder(getApplication(), java.util.Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(latitude, longitude, 1)
                results?.firstOrNull()?.getAddressLine(0)
            }
        } catch (e: Exception) {
            Timber.w(e, "geocodeAddress failed")
            null
        }
    }

    fun selectFriend(friend: FriendLocationUiModel?) {
        _uiState.value = _uiState.value.copy(selectedFriend = friend, showFriendSheet = friend != null)
    }

    fun dismissFriendSheet() {
        _uiState.value = _uiState.value.copy(showFriendSheet = false, selectedFriend = null)
    }

    fun showGroupPicker(show: Boolean) {
        _uiState.value = _uiState.value.copy(showGroupPicker = show)
    }

    fun showShareSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showShareSheet = show)
    }

    fun showLocationOffDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLocationOffDialog = show)
    }

    /**
     * Starts a multi-target sharing session.
     * @param targetIds list of group ids and "direct:{friendId}" entries.
     */
    fun startSharing(targetIds: List<String>, durationMinutes: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, showShareSheet = false)
            when (startLocationSharingUseCase(targetIds, durationMinutes)) {
                is Resource.Success -> {
                    val expiry = if (durationMinutes > 0) {
                        System.currentTimeMillis() + durationMinutes * MILLIS_PER_MINUTE
                    } else Long.MAX_VALUE
                    val expiries = targetIds.associateWith { expiry }
                    val overall = expiries.values.maxOrNull()
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingTargetIds = targetIds,
                        sharingTargetExpiries = expiries,
                        sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                        sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall),
                        isLoading = false
                    )
                    startLocationService(durationMinutes)
                    startCountdownTicker()
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.toast_sharing_started)))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_failed_start_sharing)))
                }
                else -> {}
            }
        }
    }

    /**
     * Adds a single target with its own duration to the active sharing session.
     * Existing targets keep their original expiries.
     */
    fun addSharingTarget(targetId: String, durationMinutes: Long) {
        viewModelScope.launch {
            // If no active session, fall back to start
            if (!_uiState.value.isSharing) {
                startSharing(listOf(targetId), durationMinutes)
                return@launch
            }
            when (locationRepository.addSharingTarget(targetId, durationMinutes)) {
                is Resource.Success -> {
                    val newExpiry = if (durationMinutes > 0) {
                        System.currentTimeMillis() + durationMinutes * MILLIS_PER_MINUTE
                    } else Long.MAX_VALUE
                    val newExpiries = _uiState.value.sharingTargetExpiries + (targetId to newExpiry)
                    val overall = newExpiries.values.maxOrNull()
                    _uiState.value = _uiState.value.copy(
                        sharingTargetIds = newExpiries.keys.toList(),
                        sharingTargetExpiries = newExpiries,
                        sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                        sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall)
                    )
                    // Ensure the ticker covers the new expiry
                    startCountdownTicker()
                }
                is Resource.Error -> {
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_failed_start_sharing)))
                }
                else -> {}
            }
        }
    }

    /** Stops the entire sharing session. */
    fun stopSharing() {
        if (!_uiState.value.isSharing) return
        viewModelScope.launch {
            when (stopLocationSharingUseCase()) {
                is Resource.Success -> {
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingTargetIds = emptyList(),
                        sharingTargetExpiries = emptyMap(),
                        sharingExpiresAt = null,
                        sharingCountdown = null
                    )
                    stopLocationService()
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.toast_sharing_stopped)))
                }
                is Resource.Error -> {
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_failed_stop_sharing)))
                }
                else -> {}
            }
        }
    }

    /** Removes one target from the active sharing session. */
    fun removeSharingTarget(targetId: String) {
        viewModelScope.launch {
            when (stopLocationSharingUseCase(targetId)) {
                is Resource.Success -> {
                    val newExpiries = _uiState.value.sharingTargetExpiries - targetId
                    if (newExpiries.isEmpty()) {
                        countdownJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isSharing = false,
                            sharingTargetIds = emptyList(),
                            sharingTargetExpiries = emptyMap(),
                            sharingExpiresAt = null,
                            sharingCountdown = null
                        )
                        stopLocationService()
                    } else {
                        val overall = newExpiries.values.maxOrNull()
                        _uiState.value = _uiState.value.copy(
                            sharingTargetIds = newExpiries.keys.toList(),
                            sharingTargetExpiries = newExpiries,
                            sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                            sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall)
                        )
                    }
                }
                else -> {}
            }
        }
    }

    /** Public entry point — user-initiated (FAB tap, permission grant). Animates the camera. */
    fun locateMe() {
        viewModelScope.launch { locateMeInternal(moveCamera = true) }
    }

    /**
     * Shared implementation. [moveCamera] controls whether we set
     * `requestCameraMove = true` — only the user-initiated path should,
     * so silent re-locates after tab navigation don't visibly snap.
     */
    @Suppress("MissingPermission")
    private suspend fun locateMeInternal(moveCamera: Boolean) {
        locationManager.getCurrentLocation()?.let { loc ->
            _uiState.value = _uiState.value.copy(
                myLatitude = loc.latitude,
                myLongitude = loc.longitude,
                hasMyLocation = true,
                requestCameraMove = moveCamera || _uiState.value.requestCameraMove
            )
            // Refresh distance/ETA + arrival detection now that we have a fix.
            recomputeDestinationMetrics()
            checkSelfArrival(loc.latitude, loc.longitude)
            // Throttle the DataStore write: only persist when the new fix is
            // meaningfully different from the saved one. Stops every tab return
            // (which recreates this ViewModel) from spamming a write with the
            // same coords. Threshold ~50m balances accuracy with write count.
            val saved = userPreferences.getLastKnownLocation()
            val shouldWrite = saved == null || run {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    saved.first, saved.second, loc.latitude, loc.longitude, results
                )
                results[0] >= 50f
            }
            if (shouldWrite) {
                userPreferences.saveLastKnownLocation(loc.latitude, loc.longitude)
            }
        }
    }

    fun onCameraMoveConsumed() {
        _uiState.value = _uiState.value.copy(requestCameraMove = false)
    }

    /** Asks the screen to animate the camera onto the active meetup destination. */
    fun requestDestinationFocus() {
        if (_uiState.value.meetupDestination?.hasValidLocation == true) {
            _uiState.value = _uiState.value.copy(requestDestinationFocus = true)
        }
    }

    fun onDestinationFocusConsumed() {
        _uiState.value = _uiState.value.copy(requestDestinationFocus = false)
    }

    /** Opens the place-card sheet for the active destination. No-op when none. */
    fun openMeetupPlaceCard() {
        if (_uiState.value.meetupDestination?.hasValidLocation == true) {
            _uiState.value = _uiState.value.copy(showMeetupPlaceCard = true)
        }
    }

    fun dismissMeetupPlaceCard() {
        _uiState.value = _uiState.value.copy(showMeetupPlaceCard = false)
    }

    fun onAutoZoomConsumed() {
        _uiState.value = _uiState.value.copy(hasAutoZoomed = true)
    }

    fun openOrCreateDm(userId: String) {
        viewModelScope.launch {
            when (val result = getOrCreateDirectConversationUseCase(userId)) {
                is Resource.Success -> {
                    result.data?.id?.let { conversationId ->
                        _navigateToChat.value = conversationId
                    }
                }
                else -> { /* Handle error if needed */ }
            }
        }
    }

    fun onChatNavigated() {
        _navigateToChat.value = null
    }

    /** Format distance for display. */
    fun formatDistance(meters: Float?): String {
        if (meters == null) return ""
        return when {
            meters < DISTANCE_KM_THRESHOLD_METERS.toFloat() -> "${meters.toInt()}m away"
            else -> String.format("%.1fkm away", meters / DISTANCE_KM_THRESHOLD_METERS.toFloat())
        }
    }

    /**
     * Computes a human-readable ETA string based on distance and speed.
     *
     * Strategy:
     * - If the friend is moving (speed > 1 m/s), use their actual speed
     * - Otherwise, assume average driving speed (~50 km/h = 13.9 m/s)
     * - Returns null if distance < 50m (already arrived) or distance is null
     */
    private fun computeEta(distanceMeters: Float?, friendSpeed: Float): String? {
        if (distanceMeters == null || distanceMeters < 50f) return null
        val speedMps = if (friendSpeed > 1f) friendSpeed.toDouble() else 13.9
        val etaSeconds = (distanceMeters / speedMps).toLong()
        return when {
            etaSeconds < 60 -> "< 1 min"
            etaSeconds < 3600 -> "${etaSeconds / 60} min"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }
    }

    private fun startLocationService(durationMinutes: Long) {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(LocationTrackingService.createStartIntent(ctx, durationMinutes))
    }

    private fun stopLocationService() {
        val ctx = getApplication<Application>()
        ctx.startService(LocationTrackingService.createStopIntent(ctx))
    }

    private fun formatTimeAgo(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val ctx = getApplication<Application>()
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < MILLIS_PER_MINUTE  -> ctx.getString(R.string.time_just_now)
            diff < MILLIS_PER_HOUR    -> ctx.getString(R.string.time_minutes_ago, diff / MILLIS_PER_MINUTE)
            diff < MILLIS_PER_DAY     -> ctx.getString(R.string.time_hours_ago, diff / MILLIS_PER_HOUR)
            else                      -> ctx.getString(R.string.time_days_ago, diff / MILLIS_PER_DAY)
        }
    }

    fun setLocationOffDialogShown() {
        viewModelScope.launch {
            userPreferences.setLocationOffDialogShown(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationStreamJob?.cancel()
        friendsJob?.cancel()
        timeAgoRefreshJob?.cancel()
        countdownJob?.cancel()
        meetupDestinationJob?.cancel()
        unregisterLocationProviderReceiver()
    }
}

data class GlobalMapUiState(
    // Map data
    val friendLocations: List<FriendLocationUiModel> = emptyList(),
    val myLatitude: Double = 0.0,
    val myLongitude: Double = 0.0,
    val hasMyLocation: Boolean = false,
    val requestCameraMove: Boolean = false,
    /** Last-known camera position for instant map load (persisted across sessions). */
    val initialCameraLat: Double = 0.0,
    val initialCameraLng: Double = 0.0,
    val initialCameraZoom: Float = 2f,
    // Groups
    val groups: List<GroupFilter> = emptyList(),
    val directTargets: List<GroupFilter> = emptyList(),
    val activeGroupFilter: GroupFilter? = null,
    // Sharing
    val isSharing: Boolean = false,
    /** List of target ids the user is currently sharing with (group ids + "direct:{friendId}" entries). */
    val sharingTargetIds: List<String> = emptyList(),
    /** Per-target expiry map (epoch ms; Long.MAX_VALUE = "until you stop"). */
    val sharingTargetExpiries: Map<String, Long> = emptyMap(),
    /** Latest expiry across all targets (null = no session). */
    val sharingExpiresAt: Long? = null,
    /** Formatted countdown string for the latest-expiring target ("Xh Ym" or "Xm"). */
    val sharingCountdown: String? = null,
    // UI state
    val selectedFriend: FriendLocationUiModel? = null,
    val showFriendSheet: Boolean = false,
    val showGroupPicker: Boolean = false,
    val showShareSheet: Boolean = false,
    val showLocationOffDialog: Boolean = false,
    val myPhotoUrl: String? = null,
    val isLocationEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Whether the initial auto-zoom to fit all friends has already fired. */
    val hasAutoZoomed: Boolean = false,
    /**
     * True once [restoreLastCameraPosition] has finished reading DataStore.
     * The map waits for this before composing GoogleMap so the saved camera
     * position is the very first frame (no jump from (0,0) when location is off).
     */
    val cameraRestored: Boolean = false,
    /** Monotonic counter to break StateFlow structural equality on timeAgo refresh. */
    val timeAgoTick: Long = 0L,
    // ── Meetup destination ──────────────────────────────────────────────────
    /** Active destination for the currently-filtered group, if any. */
    val meetupDestination: MeetupDestination? = null,
    /** Pre-formatted distance from local user to destination ("0.4km away" / "120m away"). */
    val meetupDestinationDistanceText: String? = null,
    /** Pre-formatted ETA from local user to destination ("5 min" / "1h 12m"). */
    val meetupDestinationEtaText: String? = null,
    /** Long-press pick that hasn't been confirmed yet. Drives the bottom sheet. */
    val pendingDestinationPick: PendingDestinationPick? = null,
    /** Whether the "Set meetup point" sheet is visible. */
    val showSetDestinationSheet: Boolean = false,
    /**
     * Discoverable placement mode: when true the map shows a centered
     * crosshair and a bottom action bar instead of the regular FABs. Triggered
     * by the dedicated Meetup FAB; long-press picks a spot directly without
     * entering placement mode (preserves the power-user shortcut).
     */
    val isMeetupPlacementMode: Boolean = false,
    /** Resolved address for the spot the camera is currently centered on while in placement mode. */
    val placementAddress: String? = null,
    /** True while the geocoder is resolving [placementAddress]. */
    val isResolvingPlacementAddress: Boolean = false,
    /**
     * One-shot flag — set by [GlobalMapViewModel.requestDestinationFocus] so the
     * screen animates the camera onto the active destination pin. Cleared via
     * [onDestinationFocusConsumed] once the screen has handled it.
     */
    val requestDestinationFocus: Boolean = false,
    /** Whether the place-card sheet for the active destination is visible. */
    val showMeetupPlaceCard: Boolean = false,
    /**
     * Stable key (`"${groupId}_${destination.setAt}"`) of the destination the
     * local user has already authored a `MEETUP_ARRIVED` system message for.
     * Reset whenever the destination changes so a re-set destination triggers
     * a new arrival announcement.
     */
    val arrivedDestinationKey: String? = null
)

/**
 * A long-press pick on the global map that hasn't been confirmed yet.
 * Address may be null while the geocoder is still resolving (or if it failed).
 */
data class PendingDestinationPick(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val isResolvingAddress: Boolean = false
)
