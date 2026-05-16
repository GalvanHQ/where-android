package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.LocationRepository
import javax.inject.Inject

class StartLocationSharingUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(groupId: String?, durationMinutes: Long): Resource<Unit> {
        if (groupId.isNullOrBlank()) {
            return Resource.Error("Group ID is required to start location sharing")
        }
        return locationRepository.startLocationSharing(groupId, durationMinutes)
    }
}
