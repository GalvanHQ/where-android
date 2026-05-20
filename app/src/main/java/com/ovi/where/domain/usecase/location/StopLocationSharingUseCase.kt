package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.LocationRepository
import javax.inject.Inject

class StopLocationSharingUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    /** Stops the entire active sharing session. */
    suspend operator fun invoke(): Resource<Unit> = locationRepository.stopLocationSharing()

    /**
     * Removes a single target from the active session. If it's the last target,
     * the session is stopped entirely.
     */
    suspend operator fun invoke(targetId: String): Resource<Unit> =
        locationRepository.removeSharingTarget(targetId)
}
