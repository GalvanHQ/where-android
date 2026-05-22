package com.ovi.where.data.remote.directions

import com.google.android.gms.maps.model.LatLng

/**
 * Decoder for Google's encoded-polyline format.
 *
 * Algorithm reference:
 *   https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 *
 * The Directions API returns the route geometry as a single string of
 * variable-length-delta-encoded lat/lng pairs. We decode it on-device
 * instead of pulling in `com.google.maps.android:android-maps-utils`
 * just for one function — it's ~30 lines and has no transitive cost.
 */
internal object PolylineDecoder {

    /**
     * Decodes a Google-encoded polyline string into a list of `LatLng`s.
     * Returns an empty list on a null/blank input or any parse error.
     */
    fun decode(encoded: String?): List<LatLng> {
        if (encoded.isNullOrBlank()) return emptyList()

        val length = encoded.length
        val out = ArrayList<LatLng>(length / 4)
        var index = 0
        var lat = 0
        var lng = 0

        try {
            while (index < length) {
                lat += decodeNext(encoded, index).also { index = it.nextIndex }.value
                lng += decodeNext(encoded, index).also { index = it.nextIndex }.value
                out.add(LatLng(lat * 1e-5, lng * 1e-5))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }

    /** Decode a single variable-length signed integer starting at [start]. */
    private fun decodeNext(s: String, start: Int): DecodeResult {
        var index = start
        var shift = 0
        var result = 0
        while (true) {
            val b = s[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
            if (b < 0x20) break
        }
        // ZigZag decode
        val value = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        return DecodeResult(value, index)
    }

    private data class DecodeResult(val value: Int, val nextIndex: Int)
}
