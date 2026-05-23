package com.ovi.where.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quiet-hours preferences.
 *
 * The user picks a daily window (e.g. 22:00–07:00) during which
 * notifications are silenced or fully suppressed. Stored as minute-of-day
 * integers (0–1440) so we don't have to deal with timezone strings —
 * [shouldMuteNow] resolves the current minute in the device's local
 * timezone every time it's called.
 *
 * This is independent from per-conversation mute. Quiet hours apply
 * globally; per-conversation mute applies to one chat. Both check
 * "close friends" — entries flagged as close-friend bypass quiet hours so
 * urgent contacts always get through.
 */
@Singleton
class QuietHoursRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val START_MIN = intPreferencesKey("quiet_hours_start_minute")
        val END_MIN = intPreferencesKey("quiet_hours_end_minute")
        /**
         * When true, quiet hours fully drop notifications. When false they
         * post silently (no sound / vibration but still visible in shade).
         */
        val FULL_BLOCK = booleanPreferencesKey("quiet_hours_full_block")
    }

    fun observe(): Flow<QuietHoursSettings> = dataStore.data.map { prefs ->
        QuietHoursSettings(
            enabled = prefs[Keys.ENABLED] ?: false,
            startMinuteOfDay = prefs[Keys.START_MIN] ?: DEFAULT_START_MIN,
            endMinuteOfDay = prefs[Keys.END_MIN] ?: DEFAULT_END_MIN,
            fullBlock = prefs[Keys.FULL_BLOCK] ?: false
        )
    }

    /**
     * Returns true when the *current* local time falls inside the user's
     * quiet-hours window. Handles wrap-around windows like 22:00–07:00
     * naturally (the end-minute is interpreted as "next day" when smaller
     * than the start).
     */
    suspend fun shouldMuteNow(): Boolean {
        val settings = observe().first()
        if (!settings.enabled) return false
        val nowMin = Calendar.getInstance().let {
            it[Calendar.HOUR_OF_DAY] * 60 + it[Calendar.MINUTE]
        }
        return isWithinWindow(nowMin, settings.startMinuteOfDay, settings.endMinuteOfDay)
    }

    /**
     * Returns the [QuietHoursSettings.fullBlock] flag, used by the helper
     * to decide between drop-notification vs post-silently when
     * [shouldMuteNow] returns true.
     */
    suspend fun isFullBlock(): Boolean = observe().first().fullBlock

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setWindow(startMinute: Int, endMinute: Int) {
        require(startMinute in 0..1439 && endMinute in 0..1439) {
            "Quiet-hours minute must be in 0..1439"
        }
        dataStore.edit {
            it[Keys.START_MIN] = startMinute
            it[Keys.END_MIN] = endMinute
        }
    }

    suspend fun setFullBlock(full: Boolean) {
        dataStore.edit { it[Keys.FULL_BLOCK] = full }
    }

    /**
     * Internal helper used by tests + `shouldMuteNow`. Returns true when
     * `nowMin` is within `[startMin, endMin)`, handling wrap-around windows
     * (e.g. start=22*60, end=7*60 means quiet hours run overnight).
     */
    fun isWithinWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean {
        if (startMin == endMin) return false // empty window
        return if (startMin < endMin) {
            nowMin in startMin until endMin
        } else {
            // Wrap-around: [start..midnight) ∪ [midnight..end)
            nowMin >= startMin || nowMin < endMin
        }
    }

    companion object {
        /** Default quiet hours window — 22:00 → 07:00 local time. */
        const val DEFAULT_START_MIN = 22 * 60
        const val DEFAULT_END_MIN = 7 * 60
    }
}

data class QuietHoursSettings(
    val enabled: Boolean,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val fullBlock: Boolean
)
