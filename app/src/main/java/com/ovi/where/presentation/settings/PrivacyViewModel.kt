package com.ovi.where.presentation.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.constants.AppConstants.STATE_FLOW_SUBSCRIBE_TIMEOUT_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Location sharing options for the Privacy screen.
 */
enum class LocationSharingMode(val key: String, val displayName: String, val description: String) {
    ALWAYS("always", "Always Share", "Your location is visible to all friends at all times"),
    FRIENDS("friends", "Friends Only", "Only friends can see your location when you share"),
    NEVER("never", "Never Share", "Your location is never shared with anyone");

    companion object {
        fun fromKey(key: String?): LocationSharingMode =
            entries.firstOrNull { it.key == key } ?: FRIENDS
    }
}

/**
 * Profile visibility options for the Privacy screen.
 */
enum class ProfileVisibility(val key: String, val displayName: String, val description: String) {
    EVERYONE("everyone", "Everyone", "Anyone can find and view your profile"),
    FRIENDS("friends", "Friends Only", "Only your friends can view your profile"),
    HIDDEN("hidden", "Hidden", "Your profile is not visible to anyone");

    companion object {
        fun fromKey(key: String?): ProfileVisibility =
            entries.firstOrNull { it.key == key } ?: EVERYONE
    }
}

/**
 * ViewModel for the Privacy settings screen.
 *
 * Reads and writes location sharing and profile visibility preferences to DataStore.
 *
 * Requirements: 8.8
 */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val locationSharing: StateFlow<LocationSharingMode> = dataStore.data
        .map { preferences ->
            LocationSharingMode.fromKey(preferences[LOCATION_SHARING_KEY])
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = LocationSharingMode.FRIENDS
        )

    val profileVisibility: StateFlow<ProfileVisibility> = dataStore.data
        .map { preferences ->
            ProfileVisibility.fromKey(preferences[PROFILE_VISIBILITY_KEY])
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = ProfileVisibility.EVERYONE
        )

    fun setLocationSharing(mode: LocationSharingMode) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[LOCATION_SHARING_KEY] = mode.key
            }
        }
    }

    fun setProfileVisibility(visibility: ProfileVisibility) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PROFILE_VISIBILITY_KEY] = visibility.key
            }
        }
    }

    companion object {
        val LOCATION_SHARING_KEY = stringPreferencesKey("pref_location_sharing")
        val PROFILE_VISIBILITY_KEY = stringPreferencesKey("pref_profile_visibility")
    }
}
