package com.ovi.where.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for per-channel notification enable/disable preferences.
 *
 * Each notification channel (messages, social, location_updates, group_activity, general)
 * has a corresponding boolean preference. Channels are enabled by default.
 *
 * Requirements: 12.6
 */
@Singleton
class NotificationPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object Keys {
        val CHANNEL_MESSAGES = booleanPreferencesKey("notif_channel_messages_enabled")
        val CHANNEL_SOCIAL = booleanPreferencesKey("notif_channel_social_enabled")
        val CHANNEL_LOCATION_UPDATES = booleanPreferencesKey("notif_channel_location_updates_enabled")
        val CHANNEL_GROUP_ACTIVITY = booleanPreferencesKey("notif_channel_group_activity_enabled")
        val CHANNEL_GENERAL = booleanPreferencesKey("notif_channel_general_enabled")
    }

    /**
     * Maps channel IDs to their DataStore preference keys.
     */
    private val channelKeyMap: Map<String, Preferences.Key<Boolean>> = mapOf(
        CHANNEL_ID_MESSAGES to Keys.CHANNEL_MESSAGES,
        CHANNEL_ID_SOCIAL to Keys.CHANNEL_SOCIAL,
        CHANNEL_ID_LOCATION_UPDATES to Keys.CHANNEL_LOCATION_UPDATES,
        CHANNEL_ID_GROUP_ACTIVITY to Keys.CHANNEL_GROUP_ACTIVITY,
        CHANNEL_ID_GENERAL to Keys.CHANNEL_GENERAL
    )

    /**
     * Returns a [Flow] indicating whether the given channel is enabled.
     * Defaults to true (enabled) if no preference has been set.
     */
    fun isChannelEnabled(channelId: String): Flow<Boolean> {
        val key = channelKeyMap[channelId] ?: return kotlinx.coroutines.flow.flowOf(true)
        return dataStore.data.map { preferences ->
            preferences[key] ?: true
        }
    }

    /**
     * Checks synchronously (suspending) whether the given channel is enabled.
     * Defaults to true (enabled) if no preference has been set.
     */
    suspend fun isChannelEnabledSync(channelId: String): Boolean {
        val key = channelKeyMap[channelId] ?: return true
        val preferences = dataStore.data.first()
        return preferences[key] ?: true
    }

    /**
     * Sets the enable/disable state for a notification channel.
     */
    suspend fun setChannelEnabled(channelId: String, enabled: Boolean) {
        val key = channelKeyMap[channelId] ?: return
        dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }

    companion object {
        const val CHANNEL_ID_MESSAGES = "messages"
        const val CHANNEL_ID_SOCIAL = "social"
        const val CHANNEL_ID_LOCATION_UPDATES = "location_updates"
        const val CHANNEL_ID_GROUP_ACTIVITY = "group_activity"
        const val CHANNEL_ID_GENERAL = "general"
    }
}
