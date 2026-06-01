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
        val BATTERY_SAVER_MODE = booleanPreferencesKey("battery_saver_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val LOCATION_OFF_DIALOG_SHOWN = booleanPreferencesKey("location_off_dialog_shown")
        val PERMISSION_ONBOARDING_SHOWN = booleanPreferencesKey("permission_onboarding_shown")
        // Sharing session persistence for service restart recovery
        val SHARING_TARGET_ID = stringPreferencesKey("sharing_target_id")
        val SHARING_EXPIRES_AT = longPreferencesKey("sharing_expires_at")
        // Last-known user GPS coords — used to seed the map camera on cold start
        // so the map opens near the user even when location services are off.
        val LAST_KNOWN_LAT = stringPreferencesKey("last_known_lat")
        val LAST_KNOWN_LNG = stringPreferencesKey("last_known_lng")
    }

    val userId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.USER_ID]
    }


    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ONBOARDING_COMPLETE] ?: false
    }

    val isLocationOffDialogShown: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOCATION_OFF_DIALOG_SHOWN] ?: false
    }

    /**
     * Whether the first-run [com.ovi.where.presentation.permission.PermissionOnboardingSheet]
     * has been shown and dismissed. Tracked separately from the legacy
     * [isLocationOffDialogShown] flag so we can keep the old behavior for
     * users who haven't seen the new sheet yet.
     */
    val isPermissionOnboardingShown: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.PERMISSION_ONBOARDING_SHOWN] ?: false
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

    suspend fun setPermissionOnboardingShown(shown: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.PERMISSION_ONBOARDING_SHOWN] = shown
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

    /**
     * Clears every user-scoped key while preserving device-level UI
     * state. Use this on sign out and on "Clear cache" — anywhere we
     * want to wipe the previous account without resetting first-run
     * UX (permission onboarding, location-off coachmark, etc.).
     *
     * Device-level keys we deliberately keep:
     *  • [Keys.PERMISSION_ONBOARDING_SHOWN] — permissions are per-install,
     *    not per-account. Resetting this would re-show the onboarding
     *    sheet (and trigger system permission prompts) immediately on
     *    sign out, on top of the auth screen. That was the reported bug.
     *  • [Keys.LOCATION_OFF_DIALOG_SHOWN] — same reasoning, device-level.
     *  • [Keys.BATTERY_SAVER_MODE] — a device preference the user set
     *    intentionally; should survive account swaps.
     *
     * Everything else is user-scoped: USER_ID, USER_NAME, IS_LOGGED_IN,
     * sharing session, last share target, last known GPS, onboarding
     * complete (the welcome screens), location sharing toggle.
     */
    suspend fun clearUserScoped() {
        dataStore.edit { preferences ->
            // Copy the device-level values we want to keep.
            val keepPermissionOnboarding = preferences[Keys.PERMISSION_ONBOARDING_SHOWN]
            val keepLocationOffDialog = preferences[Keys.LOCATION_OFF_DIALOG_SHOWN]
            val keepBatterySaver = preferences[Keys.BATTERY_SAVER_MODE]

            preferences.clear()

            if (keepPermissionOnboarding != null) {
                preferences[Keys.PERMISSION_ONBOARDING_SHOWN] = keepPermissionOnboarding
            }
            if (keepLocationOffDialog != null) {
                preferences[Keys.LOCATION_OFF_DIALOG_SHOWN] = keepLocationOffDialog
            }
            if (keepBatterySaver != null) {
                preferences[Keys.BATTERY_SAVER_MODE] = keepBatterySaver
            }
        }
    }
}
