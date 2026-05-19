package com.ovi.where.presentation.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_DAY
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_HOUR
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_MINUTE
import com.ovi.where.data.location.LocationManager
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.location.ObserveActiveLocationsUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import com.ovi.where.presentation.model.MemberLocationUiModel
import com.ovi.where.presentation.model.toUiModel
import com.ovi.where.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    application: Application,
    private val observeActiveLocationsUseCase: ObserveActiveLocationsUseCase,
    private val startLocationSharingUseCase: StartLocationSharingUseCase,
    private val stopLocationSharingUseCase: StopLocationSharingUseCase,
    private val locationManager: LocationManager,
    private val locationRepository: LocationRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private var currentGroupId: String? = null
    private var locationObserverJob: Job? = null

    fun observeLocations(groupId: String) {
        currentGroupId = groupId

        // Restore sharing state from Firestore
        viewModelScope.launch {
            val isSharing = locationRepository.checkSharingStatus(groupId)
            _uiState.value = _uiState.value.copy(isSharing = isSharing)
        }

        // Observe meetup destination
        viewModelScope.launch {
            locationRepository.observeMeetupDestination(groupId).collect { destination ->
                _uiState.value = _uiState.value.copy(meetupDestination = destination)
                updateDistanceToDestination()
            }
        }

        // Use consolidated listener and filter client-side by targetId.
        // This eliminates per-group Firestore subcollection listeners.
        // Firebase read optimization: single listener shared across all groups,
        // client-side filtering costs 0 additional reads.
        locationObserverJob?.cancel()
        locationObserverJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                observeActiveLocationsUseCase()
                    .distinctUntilChanged()
                    .collect { allLocations ->
                        // Client-side filter: only show locations targeting this group
                        val locations = allLocations.filter { loc ->
                            loc.targetId == groupId && loc.isSharingActive &&
                                (loc.sharingExpiresAt == 0L ||
                                 loc.sharingExpiresAt == Long.MAX_VALUE ||
                                 System.currentTimeMillis() < loc.sharingExpiresAt)
                        }

                        _uiState.value = _uiState.value.copy(
                            locations = locations.map { sharedLocation ->
                                // Use denormalized displayName/photoUrl from the location doc
                                // No separate user profile reads needed
                                val displayName = sharedLocation.displayName.ifEmpty { sharedLocation.userId }
                                val photoUrl = sharedLocation.photoUrl
                                val timeAgoText = formatTimeAgo(sharedLocation.timestamp)

                                // Compute distance and ETA from my location
                                val myLat = _uiState.value.myLatitude
                                val myLng = _uiState.value.myLongitude
                                val hasMy = _uiState.value.hasMyLocation
                                val hasValid = sharedLocation.latitude != 0.0 && sharedLocation.longitude != 0.0

                                val distance = if (hasMy && hasValid && myLat != 0.0 && myLng != 0.0) {
                                    com.ovi.where.core.utils.LocationUtils.calculateDistance(
                                        myLat, myLng,
                                        sharedLocation.latitude, sharedLocation.longitude
                                    )
                                } else null

                                val eta = if (distance != null && distance > 50.0) {
                                    val speedMps = if (sharedLocation.speed > 1f) {
                                        sharedLocation.speed.toDouble()
                                    } else {
                                        13.9 // Default driving speed ~50 km/h
                                    }
                                    val etaSeconds = com.ovi.where.core.utils.LocationUtils.calculateETA(distance, speedMps)
                                    if (etaSeconds < 3600L) {
                                        "${etaSeconds / 60} min"
                                    } else {
                                        "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
                                    }
                                } else null

                                sharedLocation.toUiModel(
                                    displayName = displayName,
                                    timeAgoText = timeAgoText,
                                    photoUrl = photoUrl,
                                    distanceMeters = distance,
                                    etaText = eta
                                )
                            },
                            isLoading = false,
                            isEmpty = locations.isEmpty()
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to observe locations"
                )
            }
        }
    }

    fun onStartSharing(groupId: String, durationMinutes: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = startLocationSharingUseCase(groupId, durationMinutes)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isSharing = true, isLoading = false)
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

    fun onStopSharing(groupId: String) {
        viewModelScope.launch {
            when (val result = stopLocationSharingUseCase(groupId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isSharing = false)
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
                updateDistanceToDestination()
            }
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

    // ── Meetup Destination Actions ────────────────────────────────────────────

    /**
     * Sets a meetup destination for the current group.
     * Called when the user long-presses on the map or uses the destination picker.
     */
    fun setMeetupDestination(latitude: Double, longitude: Double, name: String, address: String = "") {
        val groupId = currentGroupId ?: return
        viewModelScope.launch {
            locationRepository.setMeetupDestination(groupId, latitude, longitude, name, address)
        }
    }

    /** Clears the active meetup destination. */
    fun clearMeetupDestination() {
        val groupId = currentGroupId ?: return
        viewModelScope.launch {
            locationRepository.clearMeetupDestination(groupId)
        }
    }

    /** Recomputes distance and ETA from my location to the meetup destination. */
    private fun updateDistanceToDestination() {
        val state = _uiState.value
        val dest = state.meetupDestination
        if (dest == null || !dest.hasValidLocation || !state.hasMyLocation ||
            state.myLatitude == 0.0 || state.myLongitude == 0.0
        ) {
            _uiState.value = state.copy(myDistanceToDestination = null, myEtaToDestination = null)
            return
        }

        val distance = com.ovi.where.core.utils.LocationUtils.calculateDistance(
            state.myLatitude, state.myLongitude,
            dest.latitude, dest.longitude
        )
        val etaSeconds = com.ovi.where.core.utils.LocationUtils.calculateETA(distance)
        val etaText = when {
            etaSeconds < 60 -> "< 1 min"
            etaSeconds < 3600 -> "${etaSeconds / 60} min"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }
        _uiState.value = state.copy(
            myDistanceToDestination = distance,
            myEtaToDestination = etaText
        )
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val ctx = getApplication<Application>()
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < MILLIS_PER_MINUTE  -> ctx.getString(R.string.time_just_now)
            diff < MILLIS_PER_HOUR    -> ctx.getString(R.string.time_minutes_ago, diff / MILLIS_PER_MINUTE)
            diff < MILLIS_PER_DAY     -> ctx.getString(R.string.time_hours_ago, diff / MILLIS_PER_HOUR)
            else                      -> ctx.getString(R.string.time_days_ago, diff / MILLIS_PER_DAY)
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationObserverJob?.cancel()
    }
}

data class MapUiState(
    val locations: List<MemberLocationUiModel> = emptyList(),
    val myLatitude: Double = 0.0,
    val myLongitude: Double = 0.0,
    val hasMyLocation: Boolean = false,
    val isSharing: Boolean = false,
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
    val error: String? = null,
    /** Active meetup destination for this group (null if none set). */
    val meetupDestination: com.ovi.where.domain.model.MeetupDestination? = null,
    /** Distance from my location to the meetup destination in meters. */
    val myDistanceToDestination: Double? = null,
    /** Formatted ETA from my location to the meetup destination. */
    val myEtaToDestination: String? = null
)
