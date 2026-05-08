package com.ovi.where.domain.usecase.location

import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveActiveLocationsUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    operator fun invoke(): Flow<List<SharedLocation>> {
        return locationRepository.observeActiveLocations()
    }
}
