package com.ovi.where.data.remote.directions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Directions API client.
 *
 * Docs: https://developers.google.com/maps/documentation/directions/get-directions
 *
 * We only consume the encoded `overview_polyline` plus distance / duration
 * from the first leg of the first route — that's enough for in-app
 * "draw a line from me to the meetup point" navigation. Full step-by-step
 * is out of scope for v1 (would need voice TTS + on-route reroute).
 */
interface DirectionsApi {

    /**
     * Fetches a route between [origin] and [destination] (both in
     * `lat,lng` string form, e.g. `"37.7749,-122.4194"`).
     *
     * [mode] controls the routing mode. Defaults to `driving`; we'll let
     * the caller swap to `walking` if the user prefers that.
     */
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "driving"
    ): DirectionsResponse
}

@Serializable
data class DirectionsResponse(
    val status: String? = null,
    val routes: List<DirectionsRoute> = emptyList(),
    @SerialName("error_message")
    val errorMessage: String? = null
)

@Serializable
data class DirectionsRoute(
    @SerialName("overview_polyline")
    val overviewPolyline: DirectionsOverviewPolyline? = null,
    val legs: List<DirectionsLeg> = emptyList()
)

@Serializable
data class DirectionsOverviewPolyline(
    val points: String? = null
)

@Serializable
data class DirectionsLeg(
    val distance: DirectionsValue? = null,
    val duration: DirectionsValue? = null
)

@Serializable
data class DirectionsValue(
    val text: String? = null,
    /** Distance: meters. Duration: seconds. */
    val value: Long? = null
)
