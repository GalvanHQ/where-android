package com.ovi.where.core.event

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-screen request bus for "show a user's home on the map".
 *
 * Used when a user taps the **Home** section on a profile screen (their own
 * [com.ovi.where.presentation.profile.ProfileScreen] or another user's
 * [com.ovi.where.presentation.people.UserProfileScreen]): the screen routes
 * to the Map tab and publishes a [HomePinRequest] here, which
 * [com.ovi.where.presentation.map.GlobalMapViewModel] consumes — it drops a
 * temporary "home" pin at the coordinates, animates the camera to it, and
 * renders a status bubble ("This is <name>'s home").
 *
 * Why a bus (not a nav argument): the GoogleMap is hoisted *outside* the
 * NavHost as a persistent backdrop, so the Map destination can't receive
 * route arguments the usual way. The same singleton-bus pattern already
 * powers [MeetupPlaceCardEventBus].
 *
 * Replay-only-latest: there is no queue. A [request] of `null` clears any
 * currently-shown home pin. [tick] is a monotonic counter so the consumer
 * reacts to each publish exactly once, surviving recompositions and tab
 * switches.
 */
@Singleton
class HomePinEventBus @Inject constructor() {

    /** A request to show (or clear, when null) a home pin on the map. */
    data class HomePinRequest(
        val userId: String,
        val displayName: String,
        val photoUrl: String?,
        val latitude: Double,
        val longitude: Double,
        val label: String,
        val tick: Long
    )

    private val _request = MutableStateFlow<HomePinRequest?>(null)
    val request: StateFlow<HomePinRequest?> = _request.asStateFlow()

    private var counter = 0L

    /** Publish a request to show [displayName]'s home at the given coords. */
    fun requestShow(
        userId: String,
        displayName: String,
        photoUrl: String?,
        latitude: Double,
        longitude: Double,
        label: String
    ) {
        counter += 1L
        _request.value = HomePinRequest(
            userId = userId,
            displayName = displayName,
            photoUrl = photoUrl,
            latitude = latitude,
            longitude = longitude,
            label = label,
            tick = counter
        )
    }

    /** Clear the currently-shown home pin (e.g. user dismissed it). */
    fun clear() {
        _request.value = null
    }
}
