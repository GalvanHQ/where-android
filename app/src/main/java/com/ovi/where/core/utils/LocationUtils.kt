package com.ovi.where.core.utils

import android.content.Context
import com.ovi.where.R
import kotlin.math.*

object LocationUtils {
    
    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val AVERAGE_WALKING_SPEED_MPS = 1.4
    private const val AVERAGE_DRIVING_SPEED_MPS = 13.9
    private const val AVERAGE_CYCLING_SPEED_MPS = 5.0
    
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
            distanceMeters < 1000 -> context.getString(R.string.distance_meters, distanceMeters.toInt())
            distanceMeters < 10000 -> context.getString(R.string.distance_km_decimal, distanceMeters / 1000)
            else -> context.getString(R.string.distance_km, (distanceMeters / 1000).toInt())
        }
    }
    
    fun formatDuration(context: Context, seconds: Long): String {
        return when {
            seconds < 60 -> context.getString(R.string.eta_less_than_one_min)
            seconds < 3600 -> context.getString(R.string.eta_minutes, seconds / 60)
            seconds < 7200 -> context.getString(R.string.eta_hours_minutes, seconds / 3600, (seconds % 3600) / 60)
            else -> context.getString(R.string.eta_hours, seconds / 3600)
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
