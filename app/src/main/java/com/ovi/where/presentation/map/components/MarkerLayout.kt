package com.ovi.where.presentation.map.components

import com.google.android.gms.maps.model.LatLng

/** Two markers within this distance (meters) are considered overlapping. */
const val CLUSTER_RADIUS_METERS = 15f

/** How far (meters) to spread markers from the centroid when they overlap. */
const val FAN_OUT_RADIUS_METERS = 3.0

/**
 * When multiple markers sit within [CLUSTER_RADIUS_METERS] of each other,
 * fans them out in a circle around their centroid so all avatars are visible.
 * Markers that are far enough apart keep their original positions.
 *
 * Used by both `GlobalMapScreen` and the chat `LiveMeetupSheet` so overlapping
 * avatars never stack on top of each other.
 *
 * @param markers list of (id → LatLng) pairs for all markers on the map.
 * @return map of id → adjusted LatLng.
 */
fun fanOutOverlappingMarkers(
    markers: List<Pair<String, LatLng>>
): Map<String, LatLng> {
    if (markers.size <= 1) return markers.toMap()

    val result = mutableMapOf<String, LatLng>()
    val assigned = mutableSetOf<String>()

    for (i in markers.indices) {
        if (markers[i].first in assigned) continue

        val cluster = mutableListOf(markers[i])
        assigned.add(markers[i].first)

        for (j in i + 1 until markers.size) {
            if (markers[j].first in assigned) continue
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(
                markers[i].second.latitude, markers[i].second.longitude,
                markers[j].second.latitude, markers[j].second.longitude,
                dist
            )
            if (dist[0] < CLUSTER_RADIUS_METERS) {
                cluster.add(markers[j])
                assigned.add(markers[j].first)
            }
        }

        if (cluster.size == 1) {
            result[cluster[0].first] = cluster[0].second
        } else {
            val centroidLat = cluster.sumOf { it.second.latitude } / cluster.size
            val centroidLng = cluster.sumOf { it.second.longitude } / cluster.size
            val angleStep = 360.0 / cluster.size
            cluster.forEachIndexed { index, (id, _) ->
                val angleDeg = angleStep * index
                val angleRad = Math.toRadians(angleDeg)
                // 1 degree latitude ≈ 111,320 meters
                val latOffset = (FAN_OUT_RADIUS_METERS * kotlin.math.cos(angleRad)) / 111_320.0
                val lngOffset = (FAN_OUT_RADIUS_METERS * kotlin.math.sin(angleRad)) /
                    (111_320.0 * kotlin.math.cos(Math.toRadians(centroidLat)))
                result[id] = LatLng(centroidLat + latOffset, centroidLng + lngOffset)
            }
        }
    }
    return result
}
