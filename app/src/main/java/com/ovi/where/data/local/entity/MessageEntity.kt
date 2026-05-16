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
    val readByJson: String,
    // Voice message
    val voiceUrl: String? = null,
    val voiceDurationMs: Long? = null,
    // Link preview
    val linkPreviewTitle: String? = null,
    val linkPreviewDescription: String? = null,
    val linkPreviewImageUrl: String? = null,
    val linkPreviewDomain: String? = null,
    val linkPreviewUrl: String? = null,
    // Mentions (stored as JSON array of user IDs)
    val mentionedUserIdsJson: String? = null,
    // Live location sharing
    val locationSharingSessionId: String? = null,
    val locationSharingDurationMinutes: Long? = null,
    // Forward
    val forwardedFrom: String? = null
)
