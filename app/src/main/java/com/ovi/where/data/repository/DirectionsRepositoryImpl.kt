package com.ovi.where.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.remote.directions.DirectionsApi
import com.ovi.where.data.remote.directions.PolylineDecoder
import com.ovi.where.data.remote.directions.ServerDirectionsRequest
import com.ovi.where.domain.model.Route
import com.ovi.where.domain.repository.DirectionsRepository
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [DirectionsRepository] — talks to our **own** Cloud Run
 * backend (`POST /api/directions`), which proxies the Routes API.
 *
 * Auth flow: pull a fresh Firebase ID token (cached internally by the
 * SDK), forward as a `Bearer` header. Same shape as the rest of the
 * REST surface, so 401s are handled the same way and the server gets
 * a verifiable user identity for rate-limiting.
 */
@Singleton
class DirectionsRepositoryImpl @Inject constructor(
    private val api: DirectionsApi,
    private val firebaseAuth: FirebaseAuth
) : DirectionsRepository {

    override suspend fun getRoute(origin: LatLng, destination: LatLng): Resource<Route> {
        val user = firebaseAuth.currentUser
            ?: return Resource.Error("Sign in to load directions.")
        return try {
            val tokenResult = user.getIdToken(false).await()
            val token = tokenResult.token
                ?: return Resource.Error("Sign in to load directions.")

            val response = api.getRoute(
                token = "Bearer $token",
                request = ServerDirectionsRequest(
                    originLat = origin.latitude,
                    originLng = origin.longitude,
                    destLat = destination.latitude,
                    destLng = destination.longitude
                )
            )

            val points = PolylineDecoder.decode(response.encodedPolyline)
            if (points.size < 2) {
                return Resource.Error("Route geometry was empty.")
            }
            Resource.Success(
                Route(
                    points = points,
                    distanceMeters = response.distanceMeters,
                    durationSeconds = response.durationSeconds
                )
            )
        } catch (e: HttpException) {
            // Server returns `{ error: "..." }` on 4xx/5xx. Surface
            // the upstream message to the UI so misconfiguration
            // (API not enabled, billing) is debuggable.
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            Timber.w("Directions HTTP ${e.code()}: $body")
            Resource.Error(extractErrorMessage(body) ?: e.message ?: "Could not load directions.")
        } catch (e: Exception) {
            Timber.e(e, "Directions request failed")
            Resource.Error(e.message ?: "Could not load directions.")
        }
    }

    /**
     * Pulls `error` (string) out of a JSON body without spinning up the
     * full kotlinx-serialization parser on the failure path. Returns
     * null on parse failure so the caller can fall back to a generic
     * message.
     */
    private fun extractErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val marker = "\"error\""
        val keyIdx = body.indexOf(marker).takeIf { it >= 0 } ?: return null
        val colon = body.indexOf(':', keyIdx).takeIf { it >= 0 } ?: return null
        val firstQuote = body.indexOf('"', colon).takeIf { it >= 0 } ?: return null
        val end = body.indexOf('"', firstQuote + 1).takeIf { it >= 0 } ?: return null
        return body.substring(firstQuote + 1, end).takeIf { it.isNotBlank() }
    }
}
