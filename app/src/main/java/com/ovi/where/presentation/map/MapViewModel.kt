package com.ovi.where.presentation.map

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.data.location.LocationManager
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
    private val getUsersUseCase: GetUsersUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private var currentGroupId: String? = null
    private val userDisplayNames = mutableMapOf<String, String>()

    fun observeLocations(groupId: String) {
        currentGroupId = groupId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            observeGroupLocationsUseCase(groupId).collect { locations ->
                // Fetch display names for any new user IDs
                val unknownIds = locations.map { it.userId }
                    .filter { !userDisplayNames.containsKey(it) }
                if (unknownIds.isNotEmpty()) {
                    fetchDisplayNames(unknownIds)
                }
                _uiState.value = _uiState.value.copy(
                    locations = locations.map { sharedLocation ->
                        sharedLocation.toUiModel(
                            displayName = userDisplayNames[sharedLocation.userId] ?: sharedLocation.userId,
                            timeAgoText = formatTimeAgo(sharedLocation.timestamp)
                        )
                    },
                    isLoading = false
                )
            }
        }
    }

    private suspend fun fetchDisplayNames(userIds: List<String>) {
        when (val result = getUsersUseCase(userIds)) {
            is Resource.Success -> {
                result.data?.forEach { user ->
                    userDisplayNames[user.id] = user.displayName
                }
            }
            else -> {}
        }
    }

    fun onStartSharing(groupId: String, durationMinutes: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = startLocationSharingUseCase(groupId, durationMinutes)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        isLoading = false
                    )
                    startLocationService(groupId)
                    _uiEvent.send(UiEvent.ShowSnackbar(
                        UiText.StringResource(R.string.toast_sharing_started)
                    ))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(
                        UiText.StringResource(R.string.error_failed_start_sharing)
                    ))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun onStopSharing(groupId: String) {
        viewModelScope.launch {
            when (val result = stopLocationSharingUseCase(groupId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isSharing = false)
                    stopLocationService()
                    _uiEvent.send(UiEvent.ShowSnackbar(
                        UiText.StringResource(R.string.toast_sharing_stopped)
                    ))
                }
                is Resource.Error -> {
                    _uiEvent.send(UiEvent.ShowSnackbar(
                        UiText.StringResource(R.string.error_failed_stop_sharing)
                    ))
                }
                is Resource.Loading -> {}
            }
        }
    }

    @Suppress("MissingPermission")
    fun locateMe() {
        viewModelScope.launch {
            val location = locationManager.getCurrentLocation()
            location?.let {
                _uiState.value = _uiState.value.copy(
                    myLatitude = it.latitude,
                    myLongitude = it.longitude,
                    hasMyLocation = true
                )
            }
        }
    }

    private fun startLocationService(groupId: String) {
        val context = getApplication<Application>()
        val intent = LocationTrackingService.createStartIntent(context, groupId)
        context.startForegroundService(intent)
    }

    private fun stopLocationService() {
        val context = getApplication<Application>()
        val intent = LocationTrackingService.createStopIntent(context)
        context.startService(intent)
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val context = getApplication<Application>()
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> context.getString(R.string.time_just_now)
            diff < 3_600_000 -> context.getString(R.string.time_minutes_ago, diff / 60_000)
            diff < 86_400_000 -> context.getString(R.string.time_hours_ago, diff / 3_600_000)
            else -> context.getString(R.string.time_days_ago, diff / 86_400_000)
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
    val error: String? = null
)
