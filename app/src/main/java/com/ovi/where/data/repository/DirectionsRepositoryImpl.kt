package com.ovi.where.data.repository

import com.google.android.gms.maps.model.LatLng
import com.ovi.where.BuildConfig
import com.ovi.where.core.common.Resource
import com.ovi.where.data.remote.directions.DirectionsApi
import com.ovi.where.data.remote.directions.PolylineDecoder
import com.ovi.where.domain.model.Route
import com.ovi.where.domain.repository.DirectionsRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default Directions repository.
 *
 * Hits Google's REST endpoint with the same API key the rest of the
 * app already uses for Maps SDK / Places. Decodes the encoded
 * polyline locally so the UI can render it as a `Polyline` over our
 * own GoogleMap composable.
 */
@Singleton
class DirectionsRepositoryImpl @Inject constructor(
    private val api: DirectionsApi
) : DirectionsRepository {

    override suspend fun getRoute(origin: LatLng, destination: LatLng): Resource<Route> {
        val key = BuildConfig.MAPS_API_KEY
        if (key.isBlank()) {
            return Resource.Error("Maps API key missing — can't fetch directions.")
        }
        return try {
            val response = api.getDirections(
                origin = "${origin.latitude},${origin.longitude}",
                destination = "${destination.latitude},${destination.longitude}",
                apiKey = key
            )
            val status = response.status?.uppercase()
            if (status != "OK") {
                Timber.w("Directions API status=$status err=${response.errorMessage}")
                return Resource.Error(
                    response.errorMessage
                        ?: "No route available (status=$status)."
                )
            }
            val firstRoute = response.routes.firstOrNull()
                ?: return Resource.Error("No route returned.")

            val points = PolylineDecoder.decode(firstRoute.overviewPolyline?.points)
            if (points.size < 2) {
                return Resource.Error("Route geometry was empty.")
            }
            val leg = firstRoute.legs.firstOrNull()
            val distance = leg?.distance?.value ?: 0L
            val duration = leg?.duration?.value ?: 0L

            Resource.Success(
                Route(
                    points = points,
                    distanceMeters = distance,
                    durationSeconds = duration
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Directions request failed")
            Resource.Error(e.message ?: "Could not load directions.")
        }
    }
}
