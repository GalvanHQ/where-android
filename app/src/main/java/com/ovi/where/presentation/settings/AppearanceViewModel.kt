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
 * Represents the available theme modes for the app.
 */
enum class ThemeMode(val key: String, val displayName: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System default");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * ViewModel for the Appearance settings screen.
 *
 * Reads and writes the user's theme preference to DataStore.
 * The selected theme is exposed as a [StateFlow] so that both the
 * AppearanceScreen and the root composable (MainActivity) can observe it.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val selectedTheme: StateFlow<ThemeMode> = dataStore.data
        .map { preferences ->
            ThemeMode.fromKey(preferences[THEME_MODE_KEY])
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = ThemeMode.SYSTEM
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[THEME_MODE_KEY] = mode.key
            }
        }
    }

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("pref_theme_mode")
    }
}
