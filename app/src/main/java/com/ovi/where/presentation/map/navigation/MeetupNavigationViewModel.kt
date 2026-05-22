package com.ovi.where.presentation.map.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.ovi.where.core.common.Resource
import com.ovi.where.data.location.LocationManager
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.Route
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.location.GetMeetupRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for [MeetupNavigationScreen].
 *
 * Holds the user's current location, the active meetup destination, the
 * computed route polyline plus distance / duration, and a transient error
 * message used to surface fetch failures.
 */
data class MeetupNavigationUiState(
    val isLoading: Boolean = true,
    val origin: LatLng? = null,
    val destination: MeetupDestination? = null,
    val route: Route? = null,
    /** Live distance from the user's current position to the destination, meters. */
    val remainingMeters: Float? = null,
    /** Human-readable distance label e.g. "1.2km". */
    val distanceLabel: String? = null,
    /** Human-readable ETA label e.g. "8 min". */
    val etaLabel: String? = null,
    val errorMessage: String? = null
)

/**
 * Backs the in-app turn-by-turn-style navigation screen.
 *
 * Responsibilities:
 *  • Observe the meetup destination for [groupId] in real time.
 *  • Stream the user's location from [LocationManager.getLocationUpdates].
 *  • Fetch the route on first GPS fix and re-fetch when the user drifts
 *    >250m off the cached polyline (avoids re-billing the Directions API
 *    on every GPS tick — Google's free tier is generous, but not infinite).
 */
@HiltViewModel
class MeetupNavigationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository,
    private val locationManager: LocationManager,
    private val getMeetupRouteUseCase: GetMeetupRouteUseCase
) : ViewModel() {

    /** Group whose active meetup we're navigating to. */
    val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    private val _uiState = MutableStateFlow(MeetupNavigationUiState())
    val uiState: StateFlow<MeetupNavigationUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var destinationJob: Job? = null
    private var routeJob: Job? = null

    /** Lat/lng of the destination at the time of the last successful route fetch. */
    private var routedDestination: LatLng? = null

    init {
        observeDestination()
        observeLocation()
    }

    private fun observeDestination() {
        if (groupId.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Missing group reference."
            )
            return
        }
        destinationJob?.cancel()
        destinationJob = viewModelScope.launch {
            locationRepository.observeMeetupDestination(groupId)
                .distinctUntilChanged()
                .collect { destination ->
                    if (destination == null || !destination.hasValidLocation) {
                        // Destination cleared — let the UI navigate back via
                        // a sentinel state. The screen pops itself when it
                        // sees `destination == null` after the first load.
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            destination = null,
                            route = null,
                            errorMessage = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(destination = destination)
                        maybeRefreshRoute(force = false)
                    }
                }
        }
    }

    private fun observeLocation() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            // Prime from last known so we don't sit on a blank map waiting
            // for the next GPS tick.
            locationManager.getCurrentLocation()?.let { last ->
                onLocationUpdate(LatLng(last.latitude, last.longitude))
            }
            locationManager.getLocationUpdates()
                .collect { loc -> onLocationUpdate(LatLng(loc.latitude, loc.longitude)) }
        }
    }

    private fun onLocationUpdate(origin: LatLng) {
        val current = _uiState.value
        val newRemaining = current.destination?.let { dest ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                origin.latitude, origin.longitude,
                dest.latitude, dest.longitude,
                results
            )
            results[0]
        }
        _uiState.value = current.copy(
            origin = origin,
            remainingMeters = newRemaining,
            distanceLabel = newRemaining?.let { formatDistance(it) }
                ?: current.distanceLabel,
            etaLabel = current.etaLabel
        )
        maybeRefreshRoute(force = false)
    }

    /** Public hook for the recenter / retry button. Forces a fresh route. */
    fun refreshRoute() {
        maybeRefreshRoute(force = true)
    }

    private fun maybeRefreshRoute(force: Boolean) {
        val state = _uiState.value
        val origin = state.origin ?: return
        val dest = state.destination ?: return
        if (!dest.hasValidLocation) return

        val destLatLng = LatLng(dest.latitude, dest.longitude)
        val needsFetch = when {
            force -> true
            state.route == null -> true
            routedDestination == null -> true
            // Destination moved — re-route.
            routedDestination != destLatLng &&
                distanceMetersBetween(routedDestination!!, destLatLng) > 50f -> true
            // User drifted off the polyline. Cheap heuristic: distance from
            // current position to the nearest cached point > 250m.
            offRouteDistance(origin, state.route!!.points) > OFF_ROUTE_THRESHOLD_METERS -> true
            else -> false
        }
        if (!needsFetch) return
        if (routeJob?.isActive == true) return

        routeJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = state.route == null)
            when (val result = getMeetupRouteUseCase(origin, destLatLng)) {
                is Resource.Success -> {
                    val route = result.data
                    routedDestination = destLatLng
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        route = route,
                        distanceLabel = route?.let { formatDistance(it.distanceMeters.toFloat()) }
                            ?: _uiState.value.distanceLabel,
                        etaLabel = route?.let { formatEta(it.durationSeconds) },
                        errorMessage = null
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        destinationJob?.cancel()
        routeJob?.cancel()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun formatDistance(meters: Float): String =
        if (meters < 1000f) "${meters.toInt()}m"
        else String.format("%.1fkm", meters / 1000f)

    private fun formatEta(seconds: Long): String? {
        if (seconds <= 0) return null
        return when {
            seconds < 60 -> "< 1 min"
            seconds < 3600 -> "${seconds / 60} min"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    private fun distanceMetersBetween(a: LatLng, b: LatLng): Float {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude, a.longitude, b.latitude, b.longitude, out
        )
        return out[0]
    }

    private fun offRouteDistance(origin: LatLng, points: List<LatLng>): Float {
        if (points.isEmpty()) return Float.MAX_VALUE
        var minDist = Float.MAX_VALUE
        // Sample every ~5th point — cheap "am I anywhere near the line"
        // check. Good enough for a "should I refetch" gate.
        val stride = (points.size / 50).coerceAtLeast(1)
        var i = 0
        while (i < points.size) {
            val d = distanceMetersBetween(origin, points[i])
            if (d < minDist) minDist = d
            i += stride
        }
        return minDist
    }

    companion object {
        private const val OFF_ROUTE_THRESHOLD_METERS = 250f
    }
}
