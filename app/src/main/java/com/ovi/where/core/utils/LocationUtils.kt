package com.ovi.where.core.utils

import android.content.Context
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants.DISTANCE_KM_THRESHOLD_METERS
import com.ovi.where.core.constants.AppConstants.DISTANCE_ROUNDED_KM_THRESHOLD_METERS
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object LocationUtils {
    
    private const val EARTH_RADIUS_METERS = 6371000.0
    
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS_METERS * c
    }

    fun formatDistance(context: Context, distanceMeters: Double): String {
        return when {
            distanceMeters < DISTANCE_KM_THRESHOLD_METERS -> context.getString(R.string.distance_meters, distanceMeters.toInt())
            distanceMeters < DISTANCE_ROUNDED_KM_THRESHOLD_METERS -> context.getString(R.string.distance_km_decimal, distanceMeters / DISTANCE_KM_THRESHOLD_METERS)
            else -> context.getString(R.string.distance_km, (distanceMeters / DISTANCE_KM_THRESHOLD_METERS).toInt())
        }
    }

}
