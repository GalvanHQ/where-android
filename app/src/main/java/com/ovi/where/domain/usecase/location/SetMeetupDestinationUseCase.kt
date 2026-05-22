package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Sets a meetup destination for a group.
 *
 * When a destination is active, all group members see:
 * - A destination pin on the map
 * - Their distance to the destination
 * - Estimated time of arrival
 *
 * This eliminates the "where are we meeting?" question entirely.
 */
class SetMeetupDestinationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(
        groupId: String,
        latitude: Double,
        longitude: Double,
        name: String,
        address: String = "",
        memberIds: List<String>
    ): Resource<Unit> {
        if (groupId.isBlank()) {
            return Resource.Error("Group ID is required")
        }
        if (latitude == 0.0 && longitude == 0.0) {
            return Resource.Error("Invalid destination coordinates")
        }
        return locationRepository.setMeetupDestination(
            groupId = groupId,
            latitude = latitude,
            longitude = longitude,
            name = name,
            address = address,
            memberIds = memberIds
        )
    }
}
