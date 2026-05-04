package com.ovi.where.core.utils

import android.content.Context
import com.google.firebase.Timestamp
import com.ovi.where.R
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
            diff < 60_000 -> context.getString(R.string.time_just_now)
            diff < 3600_000 -> {
                val minutes = (diff / 60_000).toInt()
                context.resources.getQuantityString(R.plurals.time_ago_minutes, minutes, minutes)
            }
            diff < 86_400_000 -> {
                val hours = (diff / 3_600_000).toInt()
                context.resources.getQuantityString(R.plurals.time_ago_hours, hours, hours)
            }
            diff < 604_800_000 -> {
                val days = (diff / 86_400_000).toInt()
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
            diff < 60_000 -> {
                val seconds = diff / 1000
                context.getString(R.string.time_seconds_remaining, seconds)
            }
            diff < 3_600_000 -> {
                val minutes = diff / 60_000
                val remainingSeconds = (diff % 60_000) / 1000
                if (remainingSeconds == 0L) {
                    context.getString(R.string.time_minutes_remaining, minutes)
                } else {
                    context.getString(R.string.time_minutes_seconds_remaining, minutes, remainingSeconds)
                }
            }
            diff < 86_400_000 -> {
                val hours = diff / 3_600_000
                val minutes = (diff % 3_600_000) / 60_000
                if (minutes == 0L) {
                    context.getString(R.string.time_hours_remaining, hours)
                } else {
                    context.getString(R.string.time_hours_minutes_remaining, hours, minutes)
                }
            }
            else -> {
                val days = diff / 86_400_000
                val hours = (diff % 86_400_000) / 3_600_000
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
    
    fun isExpiringSoon(timestamp: Long, thresholdMs: Long = 60_000): Boolean {
        val now = System.currentTimeMillis()
        return (timestamp - now) in 1..thresholdMs
    }
    
    fun formatDurationMinutes(context: Context, minutes: Long): String {
        return when {
            minutes < 60 -> {
                context.resources.getQuantityString(R.plurals.duration_format_minutes, minutes.toInt(), minutes.toInt())
            }
            minutes < 1440 -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0L) {
                    context.resources.getQuantityString(R.plurals.duration_format_hours, hours.toInt(), hours.toInt())
                } else {
                    val hourText = context.resources.getQuantityString(R.plurals.duration_format_hours, hours.toInt(), hours.toInt())
                    val minText = context.resources.getQuantityString(R.plurals.duration_format_minutes, remainingMinutes.toInt(), remainingMinutes.toInt())
                    "$hourText $minText"
                }
            }
            else -> {
                val days = minutes / 1440
                context.resources.getQuantityString(R.plurals.duration_format_days, days.toInt(), days.toInt())
            }
        }
    }
    
    fun timestampToDate(timestamp: Timestamp): Date {
        return timestamp.toDate()
    }
    
    fun now(): Long = System.currentTimeMillis()
}
