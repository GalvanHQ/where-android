package com.ovi.where.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val LAST_LOCATION_UPDATE = longPreferencesKey("last_location_update")
        val LOCATION_SHARING_ENABLED = booleanPreferencesKey("location_sharing_enabled")
        val BATTERY_SAVER_MODE = booleanPreferencesKey("battery_saver_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val LOCATION_OFF_DIALOG_SHOWN = booleanPreferencesKey("location_off_dialog_shown")
        // Sharing session persistence for service restart recovery
        val SHARING_TARGET_ID = stringPreferencesKey("sharing_target_id")
        val SHARING_EXPIRES_AT = longPreferencesKey("sharing_expires_at")
        // Last-used share target for quick re-share UX
        val LAST_SHARE_TARGET_ID = stringPreferencesKey("last_share_target_id")
        val LAST_SHARE_TARGET_NAME = stringPreferencesKey("last_share_target_name")
        val LAST_SHARE_DURATION = longPreferencesKey("last_share_duration")
        // Last-known user GPS coords — used to seed the map camera on cold start
        // so the map opens near the user even when location services are off.
        val LAST_KNOWN_LAT = stringPreferencesKey("last_known_lat")
        val LAST_KNOWN_LNG = stringPreferencesKey("last_known_lng")
    }

    val userId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.USER_ID]
    }

    val userName: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.USER_NAME]
    }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.IS_LOGGED_IN] ?: false
    }

    val isLocationSharingEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOCATION_SHARING_ENABLED] ?: true
    }

    val isBatterySaverMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BATTERY_SAVER_MODE] ?: false
    }

    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ONBOARDING_COMPLETE] ?: false
    }

    val isLocationOffDialogShown: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOCATION_OFF_DIALOG_SHOWN] ?: false
    }

    suspend fun saveUserId(userId: String) {
        dataStore.edit { preferences ->
            preferences[Keys.USER_ID] = userId
        }
    }

    suspend fun saveUserName(name: String) {
        dataStore.edit { preferences ->
            preferences[Keys.USER_NAME] = name
        }
    }

    suspend fun setLoggedIn(isLoggedIn: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.IS_LOGGED_IN] = isLoggedIn
        }
    }

    suspend fun setLastLocationUpdate(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_LOCATION_UPDATE] = timestamp
        }
    }

    suspend fun setLocationSharingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.LOCATION_SHARING_ENABLED] = enabled
        }
    }

    suspend fun setBatterySaverMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.BATTERY_SAVER_MODE] = enabled
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ONBOARDING_COMPLETE] = complete
        }
    }

    suspend fun setLocationOffDialogShown(shown: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.LOCATION_OFF_DIALOG_SHOWN] = shown
        }
    }

    // ── Sharing session persistence ───────────────────────────────────────────

    val sharingTargetId: Flow<String?> = dataStore.data.map { it[Keys.SHARING_TARGET_ID] }
    val sharingExpiresAt: Flow<Long?> = dataStore.data.map { it[Keys.SHARING_EXPIRES_AT] }

    suspend fun saveSharingSession(targetId: String, expiresAtMillis: Long?) {
        dataStore.edit { prefs ->
            prefs[Keys.SHARING_TARGET_ID] = targetId
            if (expiresAtMillis != null) {
                prefs[Keys.SHARING_EXPIRES_AT] = expiresAtMillis
            } else {
                prefs.remove(Keys.SHARING_EXPIRES_AT)
            }
        }
    }

    suspend fun clearSharingSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SHARING_TARGET_ID)
            prefs.remove(Keys.SHARING_EXPIRES_AT)
        }
    }

    // ── Last-used share target (quick re-share UX) ────────────────────────────

    val lastShareTargetId: Flow<String?> = dataStore.data.map { it[Keys.LAST_SHARE_TARGET_ID] }
    val lastShareTargetName: Flow<String?> = dataStore.data.map { it[Keys.LAST_SHARE_TARGET_NAME] }
    val lastShareDuration: Flow<Long?> = dataStore.data.map { it[Keys.LAST_SHARE_DURATION] }

    suspend fun saveLastShareTarget(targetId: String, targetName: String, durationMinutes: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SHARE_TARGET_ID] = targetId
            prefs[Keys.LAST_SHARE_TARGET_NAME] = targetName
            prefs[Keys.LAST_SHARE_DURATION] = durationMinutes
        }
    }

    // ── Last-known user location (seeds map camera on cold start) ────────────

    /** Returns (lat, lng) of the most recent successful GPS fix, or null if never saved. */
    suspend fun getLastKnownLocation(): Pair<Double, Double>? {
        val prefs = dataStore.data.map { p ->
            val lat = p[Keys.LAST_KNOWN_LAT]?.toDoubleOrNull()
            val lng = p[Keys.LAST_KNOWN_LNG]?.toDoubleOrNull()
            if (lat != null && lng != null) lat to lng else null
        }
        return prefs.first()
    }

    suspend fun saveLastKnownLocation(lat: Double, lng: Double) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_KNOWN_LAT] = lat.toString()
            prefs[Keys.LAST_KNOWN_LNG] = lng.toString()
        }
    }

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
