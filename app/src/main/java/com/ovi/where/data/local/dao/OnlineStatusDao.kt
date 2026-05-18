package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.OnlineStatusEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for persisting user online/offline status from Socket.IO presence frames.
 * Provides offline access to last-known presence state (Requirement 6.3).
 */
@Dao
interface OnlineStatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: OnlineStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(statuses: List<OnlineStatusEntity>)

    @Query("SELECT * FROM online_status WHERE isOnline = 1")
    suspend fun getOnlineUsers(): List<OnlineStatusEntity>

    @Query("SELECT * FROM online_status WHERE isOnline = 1")
    fun observeOnlineUsers(): Flow<List<OnlineStatusEntity>>

    @Query("SELECT isOnline FROM online_status WHERE userId = :userId")
    suspend fun isUserOnline(userId: String): Boolean?

    @Query("SELECT * FROM online_status WHERE userId = :userId")
    suspend fun getStatus(userId: String): OnlineStatusEntity?

    @Query("DELETE FROM online_status")
    suspend fun clearAll()
}
