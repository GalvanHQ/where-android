package com.ovi.where.domain.usecase.location

import com.ovi.where.core.constants.AppConstants
import com.ovi.where.core.utils.LocationUtils
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.SharedLocation
import javax.inject.Inject

/**
 * Detects when group members arrive at the meetup destination.
 *
 * A member is considered "arrived" when their distance to the destination
 * is less than [AppConstants.ARRIVAL_RADIUS_METERS] (100m).
 *
 * This is a lightweight "geofence-lite" approach that doesn't require
 * the Geofencing API (which has battery and quota limitations). Instead,
 * it checks distances on each location update received via the existing
 * Firestore/Socket.IO location stream.
 */
class DetectArrivalUseCase @Inject constructor() {

    /**
     * Checks which members have arrived at the destination.
     *
     * @param locations Current locations of all sharing members.
     * @param destination The active meetup destination.
     * @return List of user IDs that are within the arrival radius.
     */
    operator fun invoke(
        locations: List<SharedLocation>,
        destination: MeetupDestination
    ): List<String> {
        if (!destination.hasValidLocation || !destination.isActive) return emptyList()

        return locations.filter { location ->
            if (location.latitude == 0.0 && location.longitude == 0.0) return@filter false
            if (!location.isSharingActive) return@filter false

            val distance = LocationUtils.calculateDistance(
                location.latitude, location.longitude,
                destination.latitude, destination.longitude
            )
            distance <= AppConstants.ARRIVAL_RADIUS_METERS
        }.map { it.userId }
    }

    /**
     * Checks if a single user has arrived at the destination.
     */
    fun hasArrived(
        latitude: Double,
        longitude: Double,
        destination: MeetupDestination
    ): Boolean {
        if (!destination.hasValidLocation) return false
        val distance = LocationUtils.calculateDistance(
            latitude, longitude,
            destination.latitude, destination.longitude
        )
        return distance <= AppConstants.ARRIVAL_RADIUS_METERS
    }
}
