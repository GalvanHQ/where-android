package com.ovi.where.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentSearchesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val MAX_ENTRIES = 15
        private val PEOPLE_RECENT_SEARCHES = stringPreferencesKey("recent_searches_people")
        private val CHATS_RECENT_SEARCHES = stringPreferencesKey("recent_searches_chats")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun keyForScreen(screenKey: String): Preferences.Key<String> {
        return when (screenKey) {
            "people" -> PEOPLE_RECENT_SEARCHES
            "chats" -> CHATS_RECENT_SEARCHES
            else -> stringPreferencesKey("recent_searches_$screenKey")
        }
    }

    fun getRecentSearches(screenKey: String): Flow<List<String>> {
        val key = keyForScreen(screenKey)
        return dataStore.data
            .map { preferences ->
                val raw = preferences[key] ?: return@map emptyList()
                try {
                    json.decodeFromString<List<String>>(raw)
                } catch (e: Exception) {
                    Timber.w(e, "Malformed JSON in recent searches for screen '$screenKey', clearing entry")
                    // Clear the corrupted entry
                    dataStore.edit { it.remove(key) }
                    emptyList()
                }
            }
            .catch { e ->
                Timber.e(e, "Error reading recent searches for screen '$screenKey'")
                emit(emptyList())
            }
    }

    suspend fun addSearch(screenKey: String, query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val key = keyForScreen(screenKey)
        dataStore.edit { preferences ->
            val current = try {
                val raw = preferences[key]
                if (raw != null) json.decodeFromString<List<String>>(raw) else emptyList()
            } catch (e: Exception) {
                Timber.w(e, "Malformed JSON in recent searches for screen '$screenKey', resetting")
                emptyList()
            }

            // Remove duplicate if exists, then prepend
            val updated = (listOf(trimmed) + current.filter { it != trimmed })
                .take(MAX_ENTRIES)

            preferences[key] = json.encodeToString(updated)
        }
    }

    suspend fun removeSearch(screenKey: String, query: String) {
        val key = keyForScreen(screenKey)
        dataStore.edit { preferences ->
            val current = try {
                val raw = preferences[key]
                if (raw != null) json.decodeFromString<List<String>>(raw) else emptyList()
            } catch (e: Exception) {
                Timber.w(e, "Malformed JSON in recent searches for screen '$screenKey', resetting")
                emptyList()
            }

            val updated = current.filter { it != query }
            preferences[key] = json.encodeToString(updated)
        }
    }

    suspend fun clearAll(screenKey: String) {
        val key = keyForScreen(screenKey)
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }
}
