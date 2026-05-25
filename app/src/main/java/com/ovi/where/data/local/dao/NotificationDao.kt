package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    /** Inserts (or replaces) a single notification. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    /** Newest-first stream of every notification — drives the inbox UI. */
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    /**
     * One-shot snapshot of every cached row. Used by the Firestore
     * reconciler to diff incoming entries against what we already have
     * — anything byte-equal is skipped on the upsert path so we don't
     * churn Room writes (and the bound Flow emissions) on every doc
     * snapshot. Inbox is small (≤ 200 rows by FIFO cap), so loading the
     * whole table is cheap.
     */
    @Query("SELECT * FROM notifications")
    suspend fun snapshotAll(): List<NotificationEntity>

    /** Reactive unread count for the badge on the bell chip. */
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    /** Marks every notification as read (called from "mark all read" action). */
    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    /** Removes a single notification (swipe-to-delete in the inbox). */
    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun delete(id: String)

    /** Wipes the entire inbox. */
    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    /** Deletes notifications older than [thresholdMs]. */
    @Query("DELETE FROM notifications WHERE timestamp < :thresholdMs")
    suspend fun pruneOlderThan(thresholdMs: Long)
}
