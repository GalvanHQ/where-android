package com.ovi.where.presentation.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formats a millisecond timestamp for the conversation list (HH:mm / Yesterday / dd/MM/yy). */
fun formatConversationTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = Date(timestamp) }
    return when {
        now.get(Calendar.DATE) == msgCal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        now.get(Calendar.DATE) - msgCal.get(Calendar.DATE) == 1 -> "Yesterday"
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** Formats a millisecond timestamp as HH:mm for message timestamps. */
fun formatMessageTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

/** Returns yyyy-MM-dd key for grouping messages by date. */
fun formatMessageDateKey(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
