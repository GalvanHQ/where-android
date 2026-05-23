package com.ovi.where.presentation.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.core.constants.AppConstants.STATE_FLOW_SUBSCRIBE_TIMEOUT_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
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
 * Persists location-sharing and profile-visibility choices to:
 *  • DataStore — fast local read on the hot path (start-sharing checks).
 *  • Firestore (`users/{uid}` doc) — cross-device sync + the field the
 *    user-search query consults to enforce profile visibility from other
 *    users' searches.
 *
 * The two stores are kept in sync best-effort. On boot the local copy is
 * authoritative if Firestore is unreachable; on every set we write both.
 *
 * Requirements: 8.8
 */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
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
            dataStore.edit { it[LOCATION_SHARING_KEY] = mode.key }
            // Mirror to Firestore so the field is available cross-device.
            // Search queries don't read it (locationSharingMode only governs
            // outgoing share starts), but cross-device sync keeps the user's
            // selection consistent if they sign in on a tablet.
            persistUserField("locationSharingMode", mode.key)
        }
    }

    fun setProfileVisibility(visibility: ProfileVisibility) {
        viewModelScope.launch {
            dataStore.edit { it[PROFILE_VISIBILITY_KEY] = visibility.key }
            // Mirror to Firestore so other users' search queries can filter
            // out hidden / friends-only profiles when running their searches.
            persistUserField("profileVisibility", visibility.key)
        }
    }

    /**
     * Writes a single field on the current user's profile doc. Best-effort —
     * we don't want a transient network failure to block the local UI flip
     * since the DataStore write already succeeded. Worst case the
     * cross-device mirror is one re-launch behind.
     */
    private suspend fun persistUserField(field: String, value: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(uid)
                .update(field, value)
                .await()
        } catch (e: Exception) {
            Timber.w(e, "Failed to mirror $field=$value to Firestore")
        }
    }

    companion object {
        val LOCATION_SHARING_KEY = stringPreferencesKey("pref_location_sharing")
        val PROFILE_VISIBILITY_KEY = stringPreferencesKey("pref_profile_visibility")
    }
}
