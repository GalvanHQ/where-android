package com.ovi.where.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-channel notification enable/disable preferences, backed by DataStore.
 *
 * Channels:
 *  • messages          — chat messages and @mentions
 *  • social            — friend requests / accepted
 *  • location_updates  — live location started / stopped + arrival pings
 *  • group_activity    — member joined / left, group renamed, etc.
 *  • meetup            — meetup destination set / cleared / member arrived
 *  • general           — fallback for unrecognized FCM types
 *
 * Channels are enabled by default. The user toggles them from
 * [com.ovi.where.presentation.settings.NotificationPreferencesScreen].
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
        val CHANNEL_MEETUP = booleanPreferencesKey("notif_channel_meetup_enabled")
        val CHANNEL_GENERAL = booleanPreferencesKey("notif_channel_general_enabled")
    }

    private val channelKeyMap: Map<String, Preferences.Key<Boolean>> = mapOf(
        CHANNEL_ID_MESSAGES to Keys.CHANNEL_MESSAGES,
        CHANNEL_ID_SOCIAL to Keys.CHANNEL_SOCIAL,
        CHANNEL_ID_LOCATION_UPDATES to Keys.CHANNEL_LOCATION_UPDATES,
        CHANNEL_ID_GROUP_ACTIVITY to Keys.CHANNEL_GROUP_ACTIVITY,
        CHANNEL_ID_MEETUP to Keys.CHANNEL_MEETUP,
        CHANNEL_ID_GENERAL to Keys.CHANNEL_GENERAL
    )

    /**
     * Reactive view of whether the given channel is enabled. Defaults to
     * `true` (enabled) when no value has been written yet.
     */
    fun isChannelEnabled(channelId: String): Flow<Boolean> {
        val key = channelKeyMap[channelId] ?: return flowOf(true)
        return dataStore.data.map { it[key] ?: true }
    }

    /** Suspend-style read; safe to call from FCM handlers. */
    suspend fun isChannelEnabledSync(channelId: String): Boolean {
        val key = channelKeyMap[channelId] ?: return true
        return dataStore.data.first()[key] ?: true
    }

    /** Persists the enable/disable state for a channel. */
    suspend fun setChannelEnabled(channelId: String, enabled: Boolean) {
        val key = channelKeyMap[channelId] ?: return
        dataStore.edit { it[key] = enabled }
    }

    companion object {
        const val CHANNEL_ID_MESSAGES = "messages"
        const val CHANNEL_ID_SOCIAL = "social"
        const val CHANNEL_ID_LOCATION_UPDATES = "location_updates"
        const val CHANNEL_ID_GROUP_ACTIVITY = "group_activity"
        const val CHANNEL_ID_MEETUP = "meetup"
        const val CHANNEL_ID_GENERAL = "general"
    }
}
