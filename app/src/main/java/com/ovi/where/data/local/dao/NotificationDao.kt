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
     * Returns every notification id currently in the local cache. Used by
     * the Firestore reconciler to figure out which rows to delete when an
     * entry disappears from the server-side map (e.g., another device
     * cleared it, or the prune job removed an expired entry).
     */
    @Query("SELECT id FROM notifications")
    suspend fun observeAllIds(): List<String>

    /** Reactive unread count for the badge on the bell chip. */
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    /** Marks one notification as read. Idempotent. */
    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

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
