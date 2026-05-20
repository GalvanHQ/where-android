package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Starts a location sharing session with one or more targets.
 * Targets can be a mix of group ids and "direct:{friendId}" entries.
 */
class StartLocationSharingUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(targetIds: List<String>, durationMinutes: Long): Resource<Unit> {
        val sanitized = targetIds.filter { it.isNotBlank() }.distinct()
        if (sanitized.isEmpty()) {
            return Resource.Error("Pick at least one friend or group to share with")
        }
        return locationRepository.startLocationSharing(sanitized, durationMinutes)
    }

    /** Convenience overload for single-target sharing. */
    suspend operator fun invoke(targetId: String?, durationMinutes: Long): Resource<Unit> {
        if (targetId.isNullOrBlank()) {
            return Resource.Error("Target ID is required to start location sharing")
        }
        return invoke(listOf(targetId), durationMinutes)
    }
}
