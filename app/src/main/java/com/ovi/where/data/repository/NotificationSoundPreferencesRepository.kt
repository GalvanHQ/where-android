package com.ovi.where.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ovi.where.core.notification.NotificationHelper
import com.ovi.where.core.notification.NotificationSound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-channel notification *sound* preferences.
 *
 * Why a separate repository from [NotificationPreferencesRepository]?
 *  • Different schema (string ids vs booleans).
 *  • Different write semantics — each sound change has to bump a
 *    *channel version* counter so the system rebuilds the channel with
 *    the new sound. Channel sound is immutable after creation on Android
 *    O+, so we re-create the channel under a versioned id (e.g.
 *    `messages_v3`) every time the user picks a new sound.
 *  • Keeps the boolean enable/disable repo flat and easy to reason about.
 *
 * The schema:
 *   `notif_sound_<channelId>`        — id of the current [NotificationSound]
 *   `notif_channel_version_<base>`   — int incremented on each sound change
 */
@Singleton
class NotificationSoundPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val baseChannels = listOf(
        NotificationHelper.CHANNEL_MESSAGES,
        NotificationHelper.CHANNEL_SOCIAL,
        NotificationHelper.CHANNEL_LOCATION_UPDATES,
        NotificationHelper.CHANNEL_GROUP_ACTIVITY,
        NotificationHelper.CHANNEL_MEETUP,
        NotificationHelper.CHANNEL_GENERAL,
    )

    private fun soundKey(baseChannelId: String) =
        stringPreferencesKey("notif_sound_$baseChannelId")

    private fun versionKey(baseChannelId: String) =
        intPreferencesKey("notif_channel_version_$baseChannelId")

    /** Reactive sound for the given base channel. */
    fun observeSound(baseChannelId: String): Flow<NotificationSound> =
        dataStore.data.map { prefs ->
            NotificationSound.fromId(
                prefs[soundKey(baseChannelId)]
                    ?: NotificationSound.defaultFor(baseChannelId).id
            )
        }

    /** Suspend read; safe from FCM dispatch. */
    suspend fun getSound(baseChannelId: String): NotificationSound {
        val prefs = dataStore.data.first()
        val id = prefs[soundKey(baseChannelId)]
            ?: NotificationSound.defaultFor(baseChannelId).id
        return NotificationSound.fromId(id)
    }

    /** Suspend read of the channel version (for resolving the active channel id). */
    suspend fun getChannelVersion(baseChannelId: String): Int {
        return dataStore.data.first()[versionKey(baseChannelId)] ?: 0
    }

    /**
     * Persist a new sound for [baseChannelId] and bump the channel version
     * so the next call to [NotificationHelper.createChannels] rebuilds the
     * channel with the chosen sound. The previous (stale) channel id is
     * deleted by the helper to avoid clutter in the system settings UI.
     */
    suspend fun setSound(baseChannelId: String, sound: NotificationSound) {
        if (baseChannelId !in baseChannels) return
        dataStore.edit { prefs ->
            val currentSoundId = prefs[soundKey(baseChannelId)]
                ?: NotificationSound.defaultFor(baseChannelId).id
            prefs[soundKey(baseChannelId)] = sound.id
            // Only bump version when the sound actually changed — picking
            // the same option a second time should be a no-op.
            if (currentSoundId != sound.id) {
                val current = prefs[versionKey(baseChannelId)] ?: 0
                prefs[versionKey(baseChannelId)] = current + 1
            }
        }
    }

    /**
     * Snapshot read of every (channel, sound, version) triple. Used by
     * [NotificationHelper.createChannels] to rebuild the system channels
     * on app start.
     */
    suspend fun snapshot(): List<ChannelSoundConfig> {
        val prefs = dataStore.data.first()
        return baseChannels.map { id ->
            val sound = NotificationSound.fromId(
                prefs[soundKey(id)] ?: NotificationSound.defaultFor(id).id
            )
            val version = prefs[versionKey(id)] ?: 0
            ChannelSoundConfig(baseChannelId = id, sound = sound, version = version)
        }
    }
}

/**
 * Resolved channel configuration for a single base channel id.
 *
 * `versionedChannelId` is what gets passed to `NotificationCompat.Builder`
 * — it's the actual id Android knows about. The base id is the stable
 * key we display in the UI ("Messages", "Social"...) and persist against.
 */
data class ChannelSoundConfig(
    val baseChannelId: String,
    val sound: NotificationSound,
    val version: Int
) {
    val versionedChannelId: String
        get() = if (version == 0) baseChannelId else "${baseChannelId}_v$version"
}
