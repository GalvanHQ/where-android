package com.ovi.where.presentation.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formats a millisecond timestamp for the conversation list (HH:mm / Yesterday / Day / dd/MM/yy). */
fun formatConversationTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = Date(timestamp) }

    // Today check: same year and same day-of-year
    val isSameDay = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    // Yesterday check: subtract 1 calendar day from now and compare year + day-of-year
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DATE, -1)
    }
    val isYesterday = yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) {
        return "Yesterday"
    }

    // Same calendar week check: same year and same week-of-year
    val isSameWeek = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.WEEK_OF_YEAR) == msgCal.get(Calendar.WEEK_OF_YEAR)
    if (isSameWeek) {
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
    }

    // Older dates: fall back to dd/MM/yy
    return SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
}

/** Formats a millisecond timestamp for message timestamps with date context.
 *  - Today: "HH:mm"
 *  - Within current week: "Mon 14:32" (abbreviated day name + time)
 *  - Older: "12 Jan 14:32" (day month + time)
 */
fun formatMessageTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = Date(timestamp) }
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    // Today check: same year and same day-of-year
    val isToday = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isToday) {
        return timeStr
    }

    // Same calendar week check: same year and same week-of-year
    val isSameWeek = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.WEEK_OF_YEAR) == msgCal.get(Calendar.WEEK_OF_YEAR)
    if (isSameWeek) {
        val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        return "$dayName $timeStr"
    }

    // Older messages: "12 Jan 14:32"
    val dateStr = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    return "$dateStr $timeStr"
}

/** Returns yyyy-MM-dd key for grouping messages by date. */
fun formatMessageDateKey(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

/**
 * Generates a human-readable date separator label for a given dateKey.
 * Returns "Today" for the current date, "Yesterday" for the previous date,
 * or a formatted date (e.g. "Jan 15, 2024") for older dates.
 *
 * Requirements: 10.3
 */
fun formatDateSeparatorLabel(dateKey: String, clock: () -> Long = { System.currentTimeMillis() }): String {
    val now = Calendar.getInstance().apply { timeInMillis = clock() }
    val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)

    val yesterday = Calendar.getInstance().apply {
        timeInMillis = clock()
        add(Calendar.DATE, -1)
    }
    val yesterdayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterday.time)

    return when (dateKey) {
        todayKey -> "Today"
        yesterdayKey -> "Yesterday"
        else -> {
            // Parse the dateKey and format as "MMM dd, yyyy"
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
            if (parsed != null) {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(parsed)
            } else {
                dateKey
            }
        }
    }
}
