package com.ovi.where.domain.model

import com.google.android.gms.maps.model.LatLng

/**
 * A computed driving route between two points, decoded from the Google
 * Directions API response.
 *
 * Used by the in-app meetup navigation screen to draw the path on the
 * map and show distance/ETA in the status bar.
 */
data class Route(
    /** Decoded polyline points, ordered origin → destination. */
    val points: List<LatLng>,
    /** Total distance along the route, in meters. */
    val distanceMeters: Long,
    /** Estimated duration along the route, in seconds (driving). */
    val durationSeconds: Long
) {
    val isValid: Boolean
        get() = points.size >= 2
}
