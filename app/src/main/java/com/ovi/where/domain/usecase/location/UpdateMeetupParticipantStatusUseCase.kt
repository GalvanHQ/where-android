package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.MeetupParticipantStatus
import com.ovi.where.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Updates the calling user's meetup participation status. Used for the
 * arrival flag (`ARRIVED`) and the "I can't make it" opt-out (`CANT_MAKE_IT`).
 *
 * Each participant only ever flips their own entry — never anyone else's.
 * Firestore rules enforce that constraint at the data layer.
 */
class UpdateMeetupParticipantStatusUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(
        groupId: String,
        status: MeetupParticipantStatus
    ): Resource<Unit> {
        if (groupId.isBlank()) return Resource.Error("Group ID is required")
        return locationRepository.updateMeetupParticipantStatus(groupId, status)
    }
}
