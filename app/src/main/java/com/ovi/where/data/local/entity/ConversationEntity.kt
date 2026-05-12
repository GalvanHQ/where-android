package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val photoUrl: String?,
    val groupId: String?,
    val lastMessageText: String,
    val lastMessageTimestamp: Long,
    val lastMessageSenderId: String,
    val unreadCount: Int,
    val memberIdsJson: String,
    val lastSyncTimestamp: Long
)
