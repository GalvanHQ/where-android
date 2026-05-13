package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a write operation queued for server sync while the device is offline.
 */
@Entity(tableName = "offline_operations")
data class OfflineOperationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = OperationStatus.PENDING.name,
    val retryCount: Int = 0
)

/**
 * Types of write operations that can be queued offline.
 */
enum class OperationType {
    SEND_MESSAGE,
    CREATE_GROUP,
    UPDATE_PROFILE,
    UPDATE_GROUP,
    DELETE_CONVERSATION,
    ADD_FRIEND,
    REMOVE_FRIEND
}

/**
 * Status of an offline operation in the queue.
 */
enum class OperationStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
    COMPLETED
}
