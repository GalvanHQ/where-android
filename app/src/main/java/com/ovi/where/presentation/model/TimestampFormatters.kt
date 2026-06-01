package com.ovi.where.presentation.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formats a millisecond timestamp for the conversation list row.
 *
 * Professional Messenger/WhatsApp-style formatting:
 * - Today → short time "2:32 PM" (no leading zero)
 * - Yesterday → "Yesterday"
 * - Same week → abbreviated day name "Mon", "Tue", etc.
 * - Same year → short date "Jan 15"
 * - Older → "Jan 15, 2024"
 */
fun formatConversationTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = Date(timestamp) }

    // Today → short time (no leading zero on hour)
    val isSameDay = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    // Yesterday
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DATE, -1)
    }
    val isYesterday = yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) {
        return "Yesterday"
    }

    // Within last 7 days → abbreviated day name (Mon, Tue, Wed...)
    val diffDays = (now.timeInMillis - msgCal.timeInMillis) / (24 * 60 * 60 * 1000)
    if (diffDays < 7 && now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) {
        return SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
    }

    // Same year → "Jan 15"
    if (now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) {
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }

    // Older → "Jan 15, 2024"
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}

/** Formats a millisecond timestamp for message bubbles (Messenger style).
 *  Shows relative date + time:
 *  - Today → "2:32 PM"
 *  - Yesterday → "Yesterday 9:15 AM"
 *  - Same week → "Monday 4:10 PM"
 *  - Older → "Jan 15 2:32 PM"
 */
fun formatMessageTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = Date(timestamp) }
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

    val isSameDay = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) return "$timeStr"

    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DATE, -1)
    }
    val isYesterday = yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Yesterday $timeStr"

    val isSameWeek = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.WEEK_OF_YEAR) == msgCal.get(Calendar.WEEK_OF_YEAR)
    if (isSameWeek) {
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        return "$dayName $timeStr"
    }

    val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
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
