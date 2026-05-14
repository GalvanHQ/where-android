package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey
    val id: String, // "{userId}_{type}" for upsert behavior
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val type: String, // "MESSAGE_SENT" or "PROFILE_VIEWED"
    val timestamp: Long
)
