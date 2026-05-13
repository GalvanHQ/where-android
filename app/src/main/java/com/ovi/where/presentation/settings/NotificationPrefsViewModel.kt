package com.ovi.where.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.data.repository.NotificationPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Notification Preferences screen.
 *
 * Reads and writes per-channel notification enable/disable preferences
 * via [NotificationPreferencesRepository] backed by DataStore.
 *
 * Requirements: 8.1
 */
@HiltViewModel
class NotificationPrefsViewModel @Inject constructor(
    private val repository: NotificationPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPrefsUiState())
    val uiState: StateFlow<NotificationPrefsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.isChannelEnabled(NotificationPreferencesRepository.CHANNEL_ID_SOCIAL),
                repository.isChannelEnabled(NotificationPreferencesRepository.CHANNEL_ID_LOCATION_UPDATES),
                repository.isChannelEnabled(NotificationPreferencesRepository.CHANNEL_ID_GROUP_ACTIVITY),
                repository.isChannelEnabled(NotificationPreferencesRepository.CHANNEL_ID_MESSAGES)
            ) { friendRequests, locationUpdates, groupActivity, chatMessages ->
                NotificationPrefsUiState(
                    friendRequestsEnabled = friendRequests,
                    locationUpdatesEnabled = locationUpdates,
                    groupActivityEnabled = groupActivity,
                    chatMessagesEnabled = chatMessages,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setFriendRequestsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(friendRequestsEnabled = enabled) }
        viewModelScope.launch {
            repository.setChannelEnabled(NotificationPreferencesRepository.CHANNEL_ID_SOCIAL, enabled)
        }
    }

    fun setLocationUpdatesEnabled(enabled: Boolean) {
        _uiState.update { it.copy(locationUpdatesEnabled = enabled) }
        viewModelScope.launch {
            repository.setChannelEnabled(NotificationPreferencesRepository.CHANNEL_ID_LOCATION_UPDATES, enabled)
        }
    }

    fun setGroupActivityEnabled(enabled: Boolean) {
        _uiState.update { it.copy(groupActivityEnabled = enabled) }
        viewModelScope.launch {
            repository.setChannelEnabled(NotificationPreferencesRepository.CHANNEL_ID_GROUP_ACTIVITY, enabled)
        }
    }

    fun setChatMessagesEnabled(enabled: Boolean) {
        _uiState.update { it.copy(chatMessagesEnabled = enabled) }
        viewModelScope.launch {
            repository.setChannelEnabled(NotificationPreferencesRepository.CHANNEL_ID_MESSAGES, enabled)
        }
    }
}

data class NotificationPrefsUiState(
    val friendRequestsEnabled: Boolean = true,
    val locationUpdatesEnabled: Boolean = true,
    val groupActivityEnabled: Boolean = true,
    val chatMessagesEnabled: Boolean = true,
    val isLoading: Boolean = true
)
