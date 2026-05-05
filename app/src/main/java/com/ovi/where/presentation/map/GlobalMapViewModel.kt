package com.ovi.where.presentation.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.data.location.LocationManager
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
import com.ovi.where.domain.usecase.location.ObserveGroupLocationsUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
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
    val name: String
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
    private val firebaseAuth: FirebaseAuth
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GlobalMapUiState())
    val uiState: StateFlow<GlobalMapUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    // Per-group raw locations (groupId → list of SharedLocation)
    private val rawLocations = MutableStateFlow<Map<String, List<SharedLocation>>>(emptyMap())

    // User profile cache (userId → User)
    private val userCache = mutableMapOf<String, User>()

    // Per-group Firestore listener jobs
    private val groupJobs = mutableMapOf<String, Job>()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    init {
        loadGroupsAndObserve()
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
                    checkAllSharingSessions(groups)
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
            val groupName = groups.firstOrNull { it.id == groupId }?.name ?: ""
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
    private fun checkAllSharingSessions(groups: List<Group>) {
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
                    startLocationService(groupId)
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

    private fun startLocationService(groupId: String) {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(LocationTrackingService.createStartIntent(ctx, groupId))
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

    override fun onCleared() {
        super.onCleared()
        groupJobs.values.forEach { it.cancel() }
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
    val activeGroupFilter: GroupFilter? = null,    // null = show all groups
    // Sharing
    val isSharing: Boolean = false,
    val sharingGroupId: String? = null,
    // UI state
    val selectedFriend: FriendLocationUiModel? = null,
    val showFriendSheet: Boolean = false,
    val showGroupPicker: Boolean = false,
    val showShareSheet: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)
