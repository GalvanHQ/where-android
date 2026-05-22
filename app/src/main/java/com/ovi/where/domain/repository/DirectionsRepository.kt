package com.ovi.where.domain.repository

import com.google.android.gms.maps.model.LatLng
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Route

/**
 * Wrapper around the Google Directions API.
 *
 * Exposed as a domain interface so the navigation feature can pull
 * routes without caring about the HTTP/serialization shape.
 */
interface DirectionsRepository {

    /**
     * Fetches a driving route from [origin] to [destination].
     *
     * Returns:
     *  • [Resource.Success] with a decoded [Route] (≥2 points + distance/duration)
     *  • [Resource.Error]   when the API rejects, returns no routes, or the
     *                       polyline is empty/un-decodable.
     */
    suspend fun getRoute(origin: LatLng, destination: LatLng): Resource<Route>
}
