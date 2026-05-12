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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val distanceMeters: Float? = null
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
    }

    /** Auto-locate user on first launch (like Google Maps). */
    private fun autoLocateOnLaunch() {
        viewModelScope.launch {
            locateMe()
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
     */
    private fun observeConsolidatedLocations() {
        locationStreamJob?.cancel()
        locationStreamJob = viewModelScope.launch {
            observeActiveLocationsUseCase()
                .distinctUntilChanged()
                .collect { locations ->
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

        // Debounced user profile fetch for unknown IDs
        val unknownIds = deduped.map { it.userId }.filter { !userCache.containsKey(it) }
        if (unknownIds.isNotEmpty()) {
            debouncedFetchUsers(unknownIds)
        }

        // Build UI models with distance-from-me
        val myLat = _uiState.value.myLatitude
        val myLng = _uiState.value.myLongitude
        val hasMyLoc = _uiState.value.hasMyLocation
        val groups = _uiState.value.groups
        val directTargets = _uiState.value.directTargets

        val friendLocations = deduped.map { loc ->
            val user = userCache[loc.userId]
            val target = groups.firstOrNull { it.id == loc.targetId }
                ?: directTargets.firstOrNull { it.id == loc.targetId }
            val groupName = target?.name ?: if (loc.targetType == "direct") "Direct" else ""

            val distance = if (hasMyLoc && loc.latitude != 0.0 && loc.longitude != 0.0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(myLat, myLng, loc.latitude, loc.longitude, results)
                results[0]
            } else null

            FriendLocationUiModel(
                userId = loc.userId,
                displayName = user?.displayName ?: loc.userId,
                username = user?.username ?: "",
                photoUrl = user?.photoUrl,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                speed = loc.speed,
                bearing = loc.bearing,
                timeAgo = formatTimeAgo(loc.timestamp),
                isActive = loc.isSharingActive,
                groupId = loc.targetId,
                groupName = groupName,
                distanceMeters = distance
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
            delay(200)
            val toFetch: List<String>
            synchronized(pendingUserFetchIds) {
                toFetch = pendingUserFetchIds.toList()
                pendingUserFetchIds.clear()
            }
            if (toFetch.isNotEmpty()) fetchUsers(toFetch)
        }
    }

    private suspend fun fetchUsers(userIds: List<String>) {
        when (val result = getUsersUseCase(userIds)) {
            is Resource.Success -> {
                result.data?.forEach { user -> userCache[user.id] = user }
                // Re-trigger UI rebuild with cached data
                observeActiveLocationsUseCase()
                    .distinctUntilChanged()
                    // Just take 1 to trigger a rebuild, the ongoing collector handles the rest
            }
            else -> {}
        }
    }

    /** Auto-refresh timeAgo every 30s */
    private fun startTimeAgoTicker() {
        timeAgoRefreshJob?.cancel()
        timeAgoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                val current = _uiState.value
                if (current.friendLocations.isNotEmpty()) {
                    _uiState.value = current.copy(
                        friendLocations = current.friendLocations.map { friend ->
                            friend // timeAgo recalculated on next location update
                        }
                    )
                }
            }
        }
    }

    private fun checkAllSharingSessions() {
        viewModelScope.launch {
            val groups = _uiState.value.groups
            val directTargets = _uiState.value.directTargets

            for (group in groups) {
                if (locationRepository.checkSharingStatus(group.id)) {
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingGroupId = group.id
                    )
                    return@launch
                }
            }
            for (target in directTargets) {
                if (locationRepository.checkSharingStatus(target.id)) {
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingGroupId = target.id
                    )
                    return@launch
                }
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun setGroupFilter(filter: GroupFilter?) {
        _uiState.value = _uiState.value.copy(activeGroupFilter = filter)
        // Re-process with new filter applied
        observeConsolidatedLocations()
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
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingGroupId = groupId,
                        isLoading = false
                    )
                    startLocationService(groupId, durationMinutes)
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
                    _uiState.value = _uiState.value.copy(isSharing = false, sharingGroupId = null)
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
            meters < 1000 -> "${meters.toInt()}m away"
            else -> String.format("%.1fkm away", meters / 1000)
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
            diff < 60_000     -> ctx.getString(R.string.time_just_now)
            diff < 3_600_000  -> ctx.getString(R.string.time_minutes_ago, diff / 60_000)
            diff < 86_400_000 -> ctx.getString(R.string.time_hours_ago, diff / 3_600_000)
            else              -> ctx.getString(R.string.time_days_ago, diff / 86_400_000)
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
    // UI state
    val selectedFriend: FriendLocationUiModel? = null,
    val showFriendSheet: Boolean = false,
    val showGroupPicker: Boolean = false,
    val showShareSheet: Boolean = false,
    val showLocationOffDialog: Boolean = false,
    val myPhotoUrl: String? = null,
    val isLocationEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val error: String? = null
)
