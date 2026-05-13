package com.ovi.where.core.utils

import android.content.Context
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants.DISTANCE_KM_THRESHOLD_METERS
import com.ovi.where.core.constants.AppConstants.DISTANCE_ROUNDED_KM_THRESHOLD_METERS
import com.ovi.where.core.constants.AppConstants.SECONDS_PER_HOUR
import com.ovi.where.core.constants.AppConstants.SECONDS_PER_TWO_HOURS
import kotlin.math.*

object LocationUtils {
    
    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val AVERAGE_WALKING_SPEED_MPS = 1.4
    private const val AVERAGE_DRIVING_SPEED_MPS = 13.9
    private const val AVERAGE_CYCLING_SPEED_MPS = 5.0
    private const val SECONDS_PER_MINUTE = 60L
    
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
    
    fun calculateETA(
        distanceMeters: Double,
        speedMps: Double = AVERAGE_DRIVING_SPEED_MPS
    ): Long {
        if (speedMps <= 0) return Long.MAX_VALUE
        return (distanceMeters / speedMps).toLong()
    }
    
    fun formatDistance(context: Context, distanceMeters: Double): String {
        return when {
            distanceMeters < DISTANCE_KM_THRESHOLD_METERS -> context.getString(R.string.distance_meters, distanceMeters.toInt())
            distanceMeters < DISTANCE_ROUNDED_KM_THRESHOLD_METERS -> context.getString(R.string.distance_km_decimal, distanceMeters / DISTANCE_KM_THRESHOLD_METERS)
            else -> context.getString(R.string.distance_km, (distanceMeters / DISTANCE_KM_THRESHOLD_METERS).toInt())
        }
    }
    
    fun formatDuration(context: Context, seconds: Long): String {
        return when {
            seconds < SECONDS_PER_MINUTE -> context.getString(R.string.eta_less_than_one_min)
            seconds < SECONDS_PER_HOUR -> context.getString(R.string.eta_minutes, seconds / SECONDS_PER_MINUTE)
            seconds < SECONDS_PER_TWO_HOURS -> context.getString(R.string.eta_hours_minutes, seconds / SECONDS_PER_HOUR, (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)
            else -> context.getString(R.string.eta_hours, seconds / SECONDS_PER_HOUR)
        }
    }
    
    fun formatETA(context: Context, distanceMeters: Double, speedMps: Double = AVERAGE_DRIVING_SPEED_MPS): String {
        val etaSeconds = calculateETA(distanceMeters, speedMps)
        return formatDuration(context, etaSeconds)
    }
    
    fun calculateBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        
        val y = sin(deltaLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
    
    fun getCardinalDirection(bearing: Double): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((bearing + 22.5) / 45).toInt() % 8
        return directions[index]
    }
}
