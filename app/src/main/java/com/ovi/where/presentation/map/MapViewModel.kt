package com.ovi.where.presentation.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.data.location.LocationManager
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.location.ObserveGroupLocationsUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import com.ovi.where.presentation.model.MemberLocationUiModel
import com.ovi.where.presentation.model.toUiModel
import com.ovi.where.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val observeGroupLocationsUseCase: ObserveGroupLocationsUseCase,
    private val startLocationSharingUseCase: StartLocationSharingUseCase,
    private val stopLocationSharingUseCase: StopLocationSharingUseCase,
    private val locationManager: LocationManager,
    private val getUsersUseCase: GetUsersUseCase,
    private val locationRepository: LocationRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private var currentGroupId: String? = null
    private val userDisplayNames = mutableMapOf<String, String>()
    private val userPhotoUrls = mutableMapOf<String, String?>()

    fun observeLocations(groupId: String) {
        currentGroupId = groupId

        // Restore sharing state from Firestore
        viewModelScope.launch {
            val isSharing = locationRepository.checkSharingStatus(groupId)
            _uiState.value = _uiState.value.copy(isSharing = isSharing)
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            observeGroupLocationsUseCase(groupId)
                .distinctUntilChanged()
                .collect { locations ->
                    val unknownIds = locations.map { it.userId }
                        .filter { !userDisplayNames.containsKey(it) }
                    if (unknownIds.isNotEmpty()) fetchDisplayNames(unknownIds)

                    _uiState.value = _uiState.value.copy(
                        locations = locations.map { sharedLocation ->
                            sharedLocation.toUiModel(
                                displayName = userDisplayNames[sharedLocation.userId] ?: sharedLocation.userId,
                                timeAgoText = formatTimeAgo(sharedLocation.timestamp),
                                photoUrl = userPhotoUrls[sharedLocation.userId]
                            )
                        },
                        isLoading = false,
                        isEmpty = locations.isEmpty()
                    )
                }
        }
    }

    private suspend fun fetchDisplayNames(userIds: List<String>) {
        when (val result = getUsersUseCase(userIds)) {
            is Resource.Success -> result.data?.forEach { user ->
                userDisplayNames[user.id] = user.displayName
                userPhotoUrls[user.id] = user.photoUrl
            }
            else -> {}
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

    private fun formatTimeAgo(timestamp: Long): String {
        val ctx = getApplication<Application>()
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000     -> ctx.getString(R.string.time_just_now)
            diff < 3_600_000  -> ctx.getString(R.string.time_minutes_ago, diff / 60_000)
            diff < 86_400_000 -> ctx.getString(R.string.time_hours_ago, diff / 3_600_000)
            else              -> ctx.getString(R.string.time_days_ago, diff / 86_400_000)
        }
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
    val error: String? = null
)
