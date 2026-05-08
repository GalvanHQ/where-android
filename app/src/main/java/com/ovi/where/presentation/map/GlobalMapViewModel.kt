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
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
import com.ovi.where.domain.usecase.location.ObserveGroupLocationsUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
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
    val timeAgo: String,
    val isActive: Boolean,         // isSharingActive
    val groupId: String,           // which group this location came from
    val groupName: String
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
    private val observeGroupLocationsUseCase: ObserveGroupLocationsUseCase,
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

    // Per-group raw locations (groupId → list of SharedLocation)
    private val rawLocations = MutableStateFlow<Map<String, List<SharedLocation>>>(emptyMap())

    // User profile cache (userId → User)
    private val userCache = mutableMapOf<String, User>()

    // Per-group Firestore listener jobs
    private val groupJobs = mutableMapOf<String, Job>()
    private var directLocationJob: Job? = null
    private var friendsJob: Job? = null

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    private val locationProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SystemLocationManager.PROVIDERS_CHANGED_ACTION) {
                checkGpsEnabled()
            }
        }
    }

    init {
        loadGroupsAndObserve()
        observeFriendsAndDirectLocations()
        observeLocationOffDialogPref()
        observeCurrentUser()
        checkGpsEnabled()
        registerLocationProviderReceiver()
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

    // ── Initialisation ────────────────────────────────────────────────────────

    private fun loadGroupsAndObserve() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = getUserGroupsUseCase()) {
                is Resource.Success -> {
                    val groups = result.data ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        groups = groups.map { GroupFilter(it.id, it.name) }
                    )
                    groups.forEach { group -> observeGroupLocations(group) }
                    checkAllSharingSessions(groups, _uiState.value.directTargets)
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

    private fun observeGroupLocations(group: Group) {
        val job = viewModelScope.launch {
            observeGroupLocationsUseCase(group.id).collect { locations ->
                val current = rawLocations.value.toMutableMap()
                current[group.id] = locations
                rawLocations.value = current
                rebuildFriendLocations()
            }
        }
        groupJobs[group.id]?.cancel()
        groupJobs[group.id] = job
    }

    /** Aggregate raw locations from all groups into a deduplicated friend list. */
    private fun rebuildFriendLocations() {
        val uid = currentUserId ?: return
        val activeFilter = _uiState.value.activeGroupFilter
        val groupsToShow = if (activeFilter != null) {
            rawLocations.value.filterKeys { it == activeFilter.id }
        } else {
            rawLocations.value
        }

        // Flatten all locations across groups
        val allLocations = mutableListOf<Triple<SharedLocation, String, String>>() // loc, groupId, groupName
        val groups = _uiState.value.groups
        for ((groupId, locations) in groupsToShow) {
            val target = groups.firstOrNull { it.id == groupId } ?: _uiState.value.directTargets.firstOrNull { it.id == groupId }
            val groupName = target?.name ?: if (groupId.startsWith("direct:")) "Direct" else ""
            locations
                .filter { it.userId != uid && it.isSharingActive }
                .forEach { allLocations.add(Triple(it, groupId, groupName)) }
        }

        // Deduplicate by userId — keep the most recent location per user
        val deduped = allLocations
            .groupBy { it.first.userId }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.first.timestamp }!! }
            .values.toList()

        // Fetch any unknown display names
        val unknownIds = deduped.map { it.first.userId }.filter { !userCache.containsKey(it) }
        if (unknownIds.isNotEmpty()) {
            viewModelScope.launch { fetchUsers(unknownIds) }
        }

        val friendLocations = deduped.map { (loc, groupId, groupName) ->
            val user = userCache[loc.userId]
            FriendLocationUiModel(
                userId = loc.userId,
                displayName = user?.displayName ?: loc.userId,
                username = user?.username ?: "",
                photoUrl = user?.photoUrl,
                latitude = loc.latitude,
                longitude = loc.longitude,
                timeAgo = formatTimeAgo(loc.timestamp),
                isActive = loc.isSharingActive,
                groupId = groupId,
                groupName = groupName
            )
        }

        _uiState.value = _uiState.value.copy(
            friendLocations = friendLocations,
            isLoading = false
        )
    }

    private suspend fun fetchUsers(userIds: List<String>) {
        when (val result = getUsersUseCase(userIds)) {
            is Resource.Success -> {
                result.data?.forEach { user -> userCache[user.id] = user }
                rebuildFriendLocations()
            }
            else -> {}
        }
    }

    /** Check all groups for an active sharing session to restore isSharing state. */
    private fun observeFriendsAndDirectLocations() {
        friendsJob?.cancel()
        friendsJob = viewModelScope.launch {
            observeFriendsUseCase().collect { friends ->
                friends.forEach { userCache[it.id] = it }
                val directTargets = friends.map {
                    GroupFilter(
                        id = "direct:${it.id}",
                        name = it.displayName,
                        isDirect = true,
                        photoUrl = it.photoUrl
                    )
                }
                _uiState.value = _uiState.value.copy(directTargets = directTargets)
                observeDirectLocations(friends.map { it.id })
                checkAllSharingSessions(emptyList(), directTargets)
            }
        }
    }

    private fun observeDirectLocations(friendIds: List<String>) {
        directLocationJob?.cancel()
        directLocationJob = viewModelScope.launch {
            locationRepository.observeDirectLocationShares(friendIds).collect { locations ->
                val current = rawLocations.value.toMutableMap()
                current.keys.filter { it.startsWith("direct:") }.forEach { current.remove(it) }
                locations.forEach { loc ->
                    current["direct:${loc.userId}"] = listOf(loc)
                }
                rawLocations.value = current
                rebuildFriendLocations()
            }
        }
    }

    private fun checkAllSharingSessions(groups: List<Group>, directTargets: List<GroupFilter>) {
        viewModelScope.launch {
            for (group in groups) {
                if (locationRepository.checkSharingStatus(group.id)) {
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingGroupId = group.id
                    )
                    break // Only one group can be active at a time
                }
            }
            if (!_uiState.value.isSharing) {
                for (target in directTargets) {
                    if (locationRepository.checkSharingStatus(target.id)) {
                        _uiState.value = _uiState.value.copy(
                            isSharing = true,
                            sharingGroupId = target.id
                        )
                        break
                    }
                }
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun setGroupFilter(filter: GroupFilter?) {
        _uiState.value = _uiState.value.copy(activeGroupFilter = filter)
        rebuildFriendLocations()
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
                    hasMyLocation = true
                )
            }
        }
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
        groupJobs.values.forEach { it.cancel() }
        unregisterLocationProviderReceiver()
    }
}

data class GlobalMapUiState(
    // Map data
    val friendLocations: List<FriendLocationUiModel> = emptyList(),
    val myLatitude: Double = 0.0,
    val myLongitude: Double = 0.0,
    val hasMyLocation: Boolean = false,
    // Groups
    val groups: List<GroupFilter> = emptyList(),
    val directTargets: List<GroupFilter> = emptyList(),
    val activeGroupFilter: GroupFilter? = null,    // null = show all groups
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
