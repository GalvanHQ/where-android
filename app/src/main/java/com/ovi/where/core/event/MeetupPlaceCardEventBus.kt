package com.ovi.where.core.event

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-screen request bus for "open the meetup place-card sheet on the map".
 *
 * Used when a user taps the "Meet at …" pill inside the chat screen's
 * [com.ovi.where.presentation.chat.components.LiveMeetupSheet]: the chat sheet
 * is dismissed, the Map tab is selected, and this bus carries the request to
 * [com.ovi.where.presentation.map.GlobalMapViewModel], which opens the
 * place-card sheet (destination, ETA, participants, directions).
 *
 * Implementation notes:
 * - Singleton so any feature can publish a request and any consumer can
 *   observe it without piping callbacks through the navigation graph.
 * - Uses a [StateFlow] of monotonically-increasing tick values. Consumers
 *   remember the last tick they handled and react only when a strictly
 *   greater tick arrives. This survives Compose recompositions and tab
 *   switches (no replay-storms, no missed events when the destination
 *   ViewModel isn't yet alive — it'll see the latest tick on first
 *   collection).
 * - Replay-only-latest: there is no event queue. If two requests fire
 *   back-to-back the consumer collapses them into a single "open" — the
 *   user almost never wants two place-card sheets stacked anyway.
 */
@Singleton
class MeetupPlaceCardEventBus @Inject constructor() {
    private val _requestTick = MutableStateFlow(0L)
    val requestTick: StateFlow<Long> = _requestTick.asStateFlow()

    /**
     * Publish a new request. The current value is incremented (timestamps would
     * be ambiguous if two requests landed in the same millisecond on a fast
     * device, so we use a monotonic counter instead).
     */
    fun requestOpen() {
        _requestTick.value = _requestTick.value + 1L
    }
}
