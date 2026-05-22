package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Clears the active meetup destination for a group.
 *
 * Symmetric with [SetMeetupDestinationUseCase]. Validates the group id then
 * delegates to the repository, which sets the `meetupDestination.isActive`
 * field to `false` and zeroes out the coordinates so observers (map, chat,
 * group info) all transition to the "no destination" state in one snapshot.
 */
class ClearMeetupDestinationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(groupId: String): Resource<Unit> {
        if (groupId.isBlank()) {
            return Resource.Error("Group ID is required")
        }
        return locationRepository.clearMeetupDestination(groupId)
    }
}
