package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists user online/offline status to Room for offline access.
 * Updated from Socket.IO presence frames (Requirement 6.3).
 */
@Entity(tableName = "online_status")
data class OnlineStatusEntity(
    @PrimaryKey
    val userId: String,
    val isOnline: Boolean,
    val lastUpdatedAt: Long
)
