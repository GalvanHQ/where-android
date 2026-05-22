package com.ovi.where.domain.usecase.location

import com.google.android.gms.maps.model.LatLng
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Route
import com.ovi.where.domain.repository.DirectionsRepository
import javax.inject.Inject

/**
 * Fetches a driving route between the user's current location and the
 * meetup destination.
 *
 * Wraps [DirectionsRepository.getRoute] so the UI layer doesn't depend
 * on the data layer directly — keeps the existing clean-arch boundary.
 */
class GetMeetupRouteUseCase @Inject constructor(
    private val repository: DirectionsRepository
) {
    suspend operator fun invoke(origin: LatLng, destination: LatLng): Resource<Route> =
        repository.getRoute(origin, destination)
}
