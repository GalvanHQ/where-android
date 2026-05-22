package com.ovi.where.data.remote.directions

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Server-side directions proxy.
 *
 * The route lives on **our** Cloud Run backend
 * (`POST /api/directions`) — not on Google directly.
 *
 * Why proxy:
 *  • Keeps the Routes API key on the server, never shipped in an APK.
 *  • Side-steps the "Requests from this Android client application
 *    <empty> are blocked" failure mode that hits when an Android-
 *    restricted Cloud key is used outside the Maps SDK.
 *  • Lets the server enforce auth, rate-limit, and (later) cache.
 *
 * Auth: same `Authorization: Bearer <id-token>` pattern the chat API
 * already uses — re-uses the existing Firebase ID-token flow.
 */
interface DirectionsApi {

    @POST("/api/directions")
    suspend fun getRoute(
        @Header("Authorization") token: String,
        @Body request: ServerDirectionsRequest
    ): ServerDirectionsResponse
}

@Serializable
data class ServerDirectionsRequest(
    val originLat: Double,
    val originLng: Double,
    val destLat: Double,
    val destLng: Double,
    /** "DRIVE" / "WALK" / "BICYCLE" / "TWO_WHEELER" / "TRANSIT". Server defaults to DRIVE. */
    val mode: String? = null
)

@Serializable
data class ServerDirectionsResponse(
    val distanceMeters: Long = 0,
    val durationSeconds: Long = 0,
    val encodedPolyline: String = ""
)
