package com.ovi.where.core.notification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the global map screen is currently in the foreground.
 *
 * Live-location and meetup-destination notifications are most useful when
 * the user is *not* already on the map. When the map screen is visible,
 * the events are reflected on screen in real time, so we suppress the
 * system tray push (still persisting to the in-app inbox).
 *
 * Set/cleared by [com.ovi.where.presentation.map.GlobalMapScreen] via a
 * `DisposableEffect`.
 */
@Singleton
class ActiveMapTracker @Inject constructor() {

    @Volatile
    private var mapVisible: Boolean = false

    /** Marks the map screen visible (call from `onResume`). */
    fun setMapVisible(visible: Boolean) {
        mapVisible = visible
    }

    /** True while the global map is the top destination. */
    fun isMapVisible(): Boolean = mapVisible
}
