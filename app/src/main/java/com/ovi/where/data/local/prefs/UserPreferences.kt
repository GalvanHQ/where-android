package com.ovi.where.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
