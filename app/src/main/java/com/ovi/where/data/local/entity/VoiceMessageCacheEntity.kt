package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_message_cache")
data class VoiceMessageCacheEntity(
    @PrimaryKey
    val messageId: String,
    val localFilePath: String,
    val durationMs: Long,
    val downloadedAt: Long
)
