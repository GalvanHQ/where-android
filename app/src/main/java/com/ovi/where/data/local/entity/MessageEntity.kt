package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val text: String,
    val type: String,
    val timestamp: Long,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val imageUrl: String?,
    val thumbnailUrl: String?,
    val replyToId: String?,
    val replyToText: String?,
    val replyToSenderName: String?,
    val reactionsJson: String,
    val readByJson: String
)
