package com.ovi.where.core.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt-provided wrapper around Firebase Remote Config for feature flags.
 *
 * - `useNewFriendshipModel`: when true, the app reads from the new denormalized
 *   data model (users/{uid}/friends, inbox, outbox, summary). When false, it
 *   falls back to the legacy `friendships/{uuid}` collection.
 *
 * Default: `false` (legacy model). Flipped to `true` during the staged rollout.
 * Min fetch interval: 30 min in release, 0 s in debug (for testing).
 */
@Singleton
class FeatureFlags @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) {
    val useNewFriendshipModel: Boolean
        get() = remoteConfig.getBoolean(KEY_USE_NEW_FRIENDSHIP_MODEL)

    companion object {
        const val KEY_USE_NEW_FRIENDSHIP_MODEL = "useNewFriendshipModel"
    }
}
