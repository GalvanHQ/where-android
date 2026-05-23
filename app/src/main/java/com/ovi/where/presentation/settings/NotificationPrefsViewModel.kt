package com.ovi.where.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.constants.AppConstants.STATE_FLOW_SUBSCRIBE_TIMEOUT_MS
import com.ovi.where.core.notification.NotificationHelper
import com.ovi.where.data.repository.NotificationPreferencesRepository
import com.ovi.where.data.repository.QuietHoursRepository
import com.ovi.where.data.repository.QuietHoursSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [NotificationPreferencesScreen].
 *
 * Owns two independent surfaces:
 *  • Per-channel toggles (chat, social, location, etc.) — backed by
 *    [NotificationPreferencesRepository]. Channel ids come from
 *    [NotificationHelper] which is the canonical owner.
 *  • Quiet hours — backed by [QuietHoursRepository], a separate DataStore
 *    namespace because the schema (window minutes + full-block flag) is
 *    different and we want to evolve them independently.
 *
 * Optimistic UI: we update the local state immediately on tap, then write
 * to DataStore. The flow combine reads the canonical state back so any
 * conflict resolves to the persisted value within ~one frame.
 */
@HiltViewModel
class NotificationPrefsViewModel @Inject constructor(
    private val repository: NotificationPreferencesRepository,
    private val quietHoursRepository: QuietHoursRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPrefsUiState())
    val uiState: StateFlow<NotificationPrefsUiState> = _uiState.asStateFlow()

    /** Reactive view of the user's quiet-hours config. */
    val quietHours: StateFlow<QuietHoursSettings> = quietHoursRepository.observe()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS),
            QuietHoursSettings(
                enabled = false,
                startMinuteOfDay = QuietHoursRepository.DEFAULT_START_MIN,
                endMinuteOfDay = QuietHoursRepository.DEFAULT_END_MIN,
                fullBlock = false
            )
        )

    init {
        viewModelScope.launch {
            combine(
                repository.isChannelEnabled(NotificationHelper.CHANNEL_SOCIAL),
                repository.isChannelEnabled(NotificationHelper.CHANNEL_LOCATION_UPDATES),
                repository.isChannelEnabled(NotificationHelper.CHANNEL_GROUP_ACTIVITY),
                repository.isChannelEnabled(NotificationHelper.CHANNEL_MESSAGES),
                repository.isChannelEnabled(NotificationHelper.CHANNEL_MEETUP)
            ) { values ->
                NotificationPrefsUiState(
                    friendRequestsEnabled = values[0],
                    locationUpdatesEnabled = values[1],
                    groupActivityEnabled = values[2],
                    chatMessagesEnabled = values[3],
                    meetupEnabled = values[4],
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setFriendRequestsEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_SOCIAL, enabled
    ) { it.copy(friendRequestsEnabled = enabled) }

    fun setLocationUpdatesEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_LOCATION_UPDATES, enabled
    ) { it.copy(locationUpdatesEnabled = enabled) }

    fun setGroupActivityEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_GROUP_ACTIVITY, enabled
    ) { it.copy(groupActivityEnabled = enabled) }

    fun setChatMessagesEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_MESSAGES, enabled
    ) { it.copy(chatMessagesEnabled = enabled) }

    fun setMeetupEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_MEETUP, enabled
    ) { it.copy(meetupEnabled = enabled) }

    // ── Quiet hours ────────────────────────────────────────────────────

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { quietHoursRepository.setEnabled(enabled) }
    }

    fun setQuietHoursWindow(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        viewModelScope.launch {
            quietHoursRepository.setWindow(startMinuteOfDay, endMinuteOfDay)
        }
    }

    fun setQuietHoursFullBlock(full: Boolean) {
        viewModelScope.launch { quietHoursRepository.setFullBlock(full) }
    }

    private fun persist(
        channelId: String,
        enabled: Boolean,
        optimistic: (NotificationPrefsUiState) -> NotificationPrefsUiState
    ) {
        _uiState.update(optimistic)
        viewModelScope.launch { repository.setChannelEnabled(channelId, enabled) }
    }
}

data class NotificationPrefsUiState(
    val friendRequestsEnabled: Boolean = true,
    val locationUpdatesEnabled: Boolean = true,
    val groupActivityEnabled: Boolean = true,
    val chatMessagesEnabled: Boolean = true,
    val meetupEnabled: Boolean = true,
    val isLoading: Boolean = true
)
