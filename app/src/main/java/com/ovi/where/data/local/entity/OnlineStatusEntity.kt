package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists user online/offline status to Room for offline access.
 * Updated from Socket.IO presence frames (Requirement 6.3).
 *
 * @property lastSeen Last known active timestamp (epoch millis). For online users this
 *   tracks the moment we observed them online; for offline users this is the moment they
 *   went offline. Used to render Messenger-style "Active 5m ago" / "Active 2h ago" subtitles.
 */
@Entity(tableName = "online_status")
data class OnlineStatusEntity(
    @PrimaryKey
    val userId: String,
    val isOnline: Boolean,
    val lastUpdatedAt: Long,
    val lastSeen: Long = 0L
)
