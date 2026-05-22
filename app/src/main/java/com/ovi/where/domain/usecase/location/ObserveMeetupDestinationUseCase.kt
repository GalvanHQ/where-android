package com.ovi.where.domain.usecase.location

import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/**
 * Streams the active meetup destination for a group.
 *
 * Returns an empty flow when [groupId] is blank so callers don't need to
 * branch — they can always `collect` the result.
 */
class ObserveMeetupDestinationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    operator fun invoke(groupId: String): Flow<MeetupDestination?> {
        if (groupId.isBlank()) return emptyFlow()
        return locationRepository.observeMeetupDestination(groupId)
    }
}
