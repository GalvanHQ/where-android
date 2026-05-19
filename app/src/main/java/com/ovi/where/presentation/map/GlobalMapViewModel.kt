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
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
import com.ovi.where.domain.usecase.location.ObserveActiveLocationsUseCase
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

/** Simple group representation for the filter pill / sheet. */
data class GroupFilter(
    val id: String,
    val name: String,
    val isDirect: Boolean = false,
    val photoUrl: String? = null
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
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase
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
    }

    /** Auto-locate user on first launch (like Google Maps). */
    private fun autoLocateOnLaunch() {
        viewModelScope.launch {
            locateMe()
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
                val savedTargetId = userPreferences.sharingTargetId.first()
                val savedExpiry = userPreferences.sharingExpiresAt.first()

                if (savedTargetId.isNullOrEmpty()) return@launch

                val now = System.currentTimeMillis()

                // Check if session already expired
                if (savedExpiry != null && now >= savedExpiry) {
                    // Expired — clean up
                    Timber.d("restoreSharingState: persisted session expired, cleaning up")
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingGroupId = null,
                        sharingExpiresAt = null,
                        sharingCountdown = null
                    )
                    userPreferences.clearSharingSession()
                    stopLocationService()
                    return@launch
                }

                // Restore active session
                _uiState.value = _uiState.value.copy(
                    isSharing = true,
                    sharingGroupId = savedTargetId,
                    sharingExpiresAt = savedExpiry,
                    sharingCountdown = formatCountdown(savedExpiry)
                )
                startCountdownTicker()
            } catch (e: Exception) {
                Timber.e(e, "restoreSharingState failed")
            }
        }
    }

    /**
     * Starts a countdown ticker that updates the sharing countdown string every 60s.
     * On session expiry, updates state to inactive and stops the tracking service.
     *
     * Requirement 12.1: Format as "Xh Ym" when ≥ 60 min, "Xm" when < 60 min.
     * Requirement 12.4: On expiry, stop service within 5 seconds.
     */
    private fun startCountdownTicker() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(60_000L) // Update every 60 seconds
                val expiresAt = _uiState.value.sharingExpiresAt ?: break // Continuous — no countdown
                val now = System.currentTimeMillis()

                if (now >= expiresAt) {
                    // Session expired — clean up
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingGroupId = null,
                        sharingExpiresAt = null,
                        sharingCountdown = null
                    )
                    stopLocationService()
                    userPreferences.clearSharingSession()
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.toast_sharing_stopped)))
                    break
                }

                _uiState.value = _uiState.value.copy(
                    sharingCountdown = formatCountdown(expiresAt)
                )
            }
        }
    }

    /**
     * Formats the remaining time until expiry as a countdown string.
     * Returns "Xh Ym" when ≥ 60 minutes remain, "Xm" when < 60 minutes.
     * Returns null for continuous sessions (no expiry).
     */
    private fun formatCountdown(expiresAt: Long?): String? {
        if (expiresAt == null || expiresAt == Long.MAX_VALUE) return null
        val remainingMs = expiresAt - System.currentTimeMillis()
        if (remainingMs <= 0) return null
        val minutes = remainingMs / MILLIS_PER_MINUTE
        return when {
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes}m"
        }
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
                        groups = groups.map { GroupFilter(it.id, it.name) }
                    )
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

        // Client-side filter by group if filter is active
        val filtered = if (activeFilter != null) {
            locations.filter { it.targetId == activeFilter.id }
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
     * `activeLocations/{uid}` document, instead of N reads per group + N reads per friend.
     *
     * Firebase read optimization: Reduces from (groups.size + directTargets.size) × 2 reads
     * down to exactly 1 read. Each call previously triggered 2 reads per target (consolidated + legacy).
     */
    private fun checkAllSharingSessions() {
        viewModelScope.launch {
            try {
                val uid = currentUserId ?: return@launch
                // Single read: check consolidated activeLocations doc for current user
                val isActive = locationRepository.checkSharingStatus(
                    _uiState.value.sharingGroupId ?: ""
                )
                if (isActive && _uiState.value.sharingGroupId != null) {
                    _uiState.value = _uiState.value.copy(
                        isSharing = true
                    )
                    return@launch
                }

                // Fallback: check in-memory sessions first (0 reads)
                val groups = _uiState.value.groups
                val directTargets = _uiState.value.directTargets
                val allTargets = groups + directTargets
                for (target in allTargets) {
                    if (locationRepository.isSharingLocation(target.id)) {
                        _uiState.value = _uiState.value.copy(
                            isSharing = true,
                            sharingGroupId = target.id
                        )
                        return@launch
                    }
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

    fun startSharing(groupId: String, durationMinutes: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, showShareSheet = false)
            when (val result = startLocationSharingUseCase(groupId, durationMinutes)) {
                is Resource.Success -> {
                    val expiresAt = if (durationMinutes > 0) {
                        System.currentTimeMillis() + durationMinutes * MILLIS_PER_MINUTE
                    } else null
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingGroupId = groupId,
                        sharingExpiresAt = expiresAt,
                        sharingCountdown = formatCountdown(expiresAt),
                        isLoading = false
                    )
                    startLocationService(groupId, durationMinutes)
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

    fun stopSharing() {
        val groupId = _uiState.value.sharingGroupId ?: return
        viewModelScope.launch {
            when (val result = stopLocationSharingUseCase(groupId)) {
                is Resource.Success -> {
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingGroupId = null,
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

    @Suppress("MissingPermission")
    fun locateMe() {
        viewModelScope.launch {
            locationManager.getCurrentLocation()?.let { loc ->
                _uiState.value = _uiState.value.copy(
                    myLatitude = loc.latitude,
                    myLongitude = loc.longitude,
                    hasMyLocation = true,
                    requestCameraMove = true
                )
            }
        }
    }

    fun onCameraMoveConsumed() {
        _uiState.value = _uiState.value.copy(requestCameraMove = false)
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

    private fun startLocationService(groupId: String, durationMinutes: Long) {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(LocationTrackingService.createStartIntent(ctx, groupId, durationMinutes))
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
    // Groups
    val groups: List<GroupFilter> = emptyList(),
    val directTargets: List<GroupFilter> = emptyList(),
    val activeGroupFilter: GroupFilter? = null,
    // Sharing
    val isSharing: Boolean = false,
    val sharingGroupId: String? = null,
    /** Epoch millis when the current sharing session expires (null = continuous). */
    val sharingExpiresAt: Long? = null,
    /** Formatted countdown string: "Xh Ym" or "Xm". Null for continuous sessions. */
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
    /** Monotonic counter to break StateFlow structural equality on timeAgo refresh. */
    val timeAgoTick: Long = 0L
)
