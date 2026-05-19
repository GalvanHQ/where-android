package com.ovi.where.domain.model

/**
 * Socket.IO payload for real-time location updates between clients.
 *
 * This frame is emitted via Socket.IO on each GPS fix and relayed by the server
 * to all clients in the same location room. The server validates all fields
 * before broadcasting.
 *
 * @property userId Firebase UID of the sharing user
 * @property latitude Latitude in degrees, range -90 to 90
 * @property longitude Longitude in degrees, range -180 to 180
 * @property accuracy Location accuracy in meters, must be >= 0
 * @property speed Speed in meters per second, must be >= 0
 * @property bearing Bearing in degrees, range 0 to 360
 * @property timestamp Unix epoch milliseconds
 */
data class LocationUpdateFrame(
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long
) {
    /**
     * Validates that all fields are within their specified ranges.
     *
     * Validation rules:
     * - userId must not be blank
     * - latitude must be in range -90..90
     * - longitude must be in range -180..180
     * - accuracy must be >= 0
     * - speed must be >= 0
     * - bearing must be in range 0..360
     * - timestamp must be > 0
     *
     * @return true if all fields are valid, false otherwise
     */
    fun isValid(): Boolean {
        return userId.isNotBlank() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            accuracy >= 0f &&
            speed >= 0f &&
            bearing in 0f..360f &&
            timestamp > 0L
    }
}
