package com.ovi.where.core.utils

import android.content.Context
import com.google.firebase.Timestamp
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_DAY
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_HOUR
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_MINUTE
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_WEEK
import com.ovi.where.core.constants.AppConstants.MINUTES_PER_DAY
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    
    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
    
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }
    
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }
    
    fun formatTimeAgo(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < MILLIS_PER_MINUTE -> context.getString(R.string.time_just_now)
            diff < MILLIS_PER_HOUR -> {
                val minutes = (diff / MILLIS_PER_MINUTE).toInt()
                context.resources.getQuantityString(R.plurals.time_ago_minutes, minutes, minutes)
            }
            diff < MILLIS_PER_DAY -> {
                val hours = (diff / MILLIS_PER_HOUR).toInt()
                context.resources.getQuantityString(R.plurals.time_ago_hours, hours, hours)
            }
            diff < MILLIS_PER_WEEK -> {
                val days = (diff / MILLIS_PER_DAY).toInt()
                context.resources.getQuantityString(R.plurals.time_ago_days, days, days)
            }
            else -> formatDate(timestamp)
        }
    }
    
    fun formatTimeRemaining(context: Context, expiryTimestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = expiryTimestamp - now
        
        if (diff <= 0) return context.getString(R.string.time_expired)
        
        return when {
            diff < MILLIS_PER_MINUTE -> {
                val seconds = diff / 1000
                context.getString(R.string.time_seconds_remaining, seconds)
            }
            diff < MILLIS_PER_HOUR -> {
                val minutes = diff / MILLIS_PER_MINUTE
                val remainingSeconds = (diff % MILLIS_PER_MINUTE) / 1000
                if (remainingSeconds == 0L) {
                    context.getString(R.string.time_minutes_remaining, minutes)
                } else {
                    context.getString(R.string.time_minutes_seconds_remaining, minutes, remainingSeconds)
                }
            }
            diff < MILLIS_PER_DAY -> {
                val hours = diff / MILLIS_PER_HOUR
                val minutes = (diff % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
                if (minutes == 0L) {
                    context.getString(R.string.time_hours_remaining, hours)
                } else {
                    context.getString(R.string.time_hours_minutes_remaining, hours, minutes)
                }
            }
            else -> {
                val days = diff / MILLIS_PER_DAY
                val hours = (diff % MILLIS_PER_DAY) / MILLIS_PER_HOUR
                if (hours == 0L) {
                    context.getString(R.string.time_days_remaining, days)
                } else {
                    context.getString(R.string.time_days_hours_remaining, days, hours)
                }
            }
        }
    }
    
    fun isExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() > timestamp
    }
    
    fun isExpiringSoon(timestamp: Long, thresholdMs: Long = MILLIS_PER_MINUTE): Boolean {
        val now = System.currentTimeMillis()
        return (timestamp - now) in 1..thresholdMs
    }
    
    fun formatDurationMinutes(context: Context, minutes: Long): String {
        return when {
            minutes < SECONDS_PER_MINUTE -> {
                context.resources.getQuantityString(R.plurals.duration_format_minutes, minutes.toInt(), minutes.toInt())
            }
            minutes < MINUTES_PER_DAY -> {
                val hours = minutes / SECONDS_PER_MINUTE
                val remainingMinutes = minutes % SECONDS_PER_MINUTE
                if (remainingMinutes == 0L) {
                    context.resources.getQuantityString(R.plurals.duration_format_hours, hours.toInt(), hours.toInt())
                } else {
                    val hourText = context.resources.getQuantityString(R.plurals.duration_format_hours, hours.toInt(), hours.toInt())
                    val minText = context.resources.getQuantityString(R.plurals.duration_format_minutes, remainingMinutes.toInt(), remainingMinutes.toInt())
                    "$hourText $minText"
                }
            }
            else -> {
                val days = minutes / MINUTES_PER_DAY
                context.resources.getQuantityString(R.plurals.duration_format_days, days.toInt(), days.toInt())
            }
        }
    }
    
    fun timestampToDate(timestamp: Timestamp): Date {
        return timestamp.toDate()
    }
    
    fun now(): Long = System.currentTimeMillis()

    /** Minutes per hour (used for duration formatting). */
    private const val SECONDS_PER_MINUTE = 60L
}
