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
    val lastMessageType: String = "TEXT",
    val lastMessageStatus: String = "SENT",
    val unreadCount: Int,
    val memberIdsJson: String,
    val mutedByJson: String = "[]",
    val pinnedByJson: String = "[]",
    val lastSyncTimestamp: Long,
    val documentUpdateTime: Long = 0L,
    val participantNamesJson: String? = null,
    val participantPhotosJson: String? = null,
    val themeColor: String? = null,
    val emojiShortcut: String? = null,
    val nicknamesJson: String? = null
)
