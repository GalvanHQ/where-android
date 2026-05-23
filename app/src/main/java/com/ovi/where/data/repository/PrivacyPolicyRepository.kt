package com.ovi.where.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to the current user's privacy preferences.
 *
 * The preferences themselves are written by
 * [com.ovi.where.presentation.settings.PrivacyViewModel] (which owns the
 * UI side: DataStore + Firestore mirror). This repository exists so the
 * data layer can read them without depending on the presentation module —
 * `LocationRepositoryImpl` and `UserRepositoryImpl` consume it on the
 * hot path.
 *
 * Why DataStore (not Firestore) for reads? The local copy is the
 * authoritative source for *the current user's own* policy (these
 * settings only restrict the calling user's actions). Firestore is the
 * sync mirror across devices. Reading from DataStore keeps start-sharing
 * checks instant + offline-safe.
 */
@Singleton
class PrivacyPolicyRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /** Reactive view of the current user's location-sharing mode. */
    fun observeLocationSharingMode(): Flow<LocationSharingMode> =
        dataStore.data.map { LocationSharingMode.fromKey(it[LOCATION_SHARING_KEY]) }

    /** Reactive view of the current user's profile-visibility mode. */
    fun observeProfileVisibility(): Flow<ProfileVisibility> =
        dataStore.data.map { ProfileVisibility.fromKey(it[PROFILE_VISIBILITY_KEY]) }

    /** Suspend-style snapshot. Used by the location repo on the start-share hot path. */
    suspend fun currentLocationSharingMode(): LocationSharingMode =
        observeLocationSharingMode().first()

    /** Suspend-style snapshot. Used by the user-search repo to filter results. */
    suspend fun currentProfileVisibility(): ProfileVisibility =
        observeProfileVisibility().first()

    enum class LocationSharingMode(val key: String) {
        /** No client-side restriction — the only check is the existing target list. */
        ALWAYS("always"),
        /** Only allow direct shares with friends; group shares only when every member is a friend. */
        FRIENDS("friends"),
        /** Hard kill switch — every start/add call is rejected. */
        NEVER("never");

        companion object {
            fun fromKey(key: String?): LocationSharingMode =
                entries.firstOrNull { it.key == key } ?: FRIENDS
        }
    }

    enum class ProfileVisibility(val key: String) {
        EVERYONE("everyone"),
        FRIENDS("friends"),
        HIDDEN("hidden");

        companion object {
            fun fromKey(key: String?): ProfileVisibility =
                entries.firstOrNull { it.key == key } ?: EVERYONE
        }
    }

    companion object {
        // Mirrors PrivacyViewModel's keys. Kept in sync via a code review
        // checklist — both files have the same comment about it.
        val LOCATION_SHARING_KEY = stringPreferencesKey("pref_location_sharing")
        val PROFILE_VISIBILITY_KEY = stringPreferencesKey("pref_profile_visibility")
    }
}
