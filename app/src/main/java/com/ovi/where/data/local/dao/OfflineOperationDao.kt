package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.OfflineOperationEntity

/**
 * Data Access Object for offline operation queue management.
 * Supports ordered replay (createdAt ASC), batch processing (LIMIT 50),
 * and capacity enforcement (200 active operations max).
 */
@Dao
interface OfflineOperationDao {

    /**
     * Retrieves up to 50 pending operations ordered by creation time (oldest first)
     * for replay when connectivity is restored.
     */
    @Query("SELECT * FROM offline_operations WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 50")
    suspend fun getPendingOperations(): List<OfflineOperationEntity>

    /**
     * Inserts a new offline operation into the queue.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: OfflineOperationEntity)

    /**
     * Returns the count of operations that are either pending or in-progress.
     * Used to enforce the 200 operation capacity limit.
     */
    @Query("SELECT COUNT(*) FROM offline_operations WHERE status IN ('PENDING', 'IN_PROGRESS')")
    suspend fun getActiveCount(): Int

    /**
     * Updates the status of an operation by its ID.
     */
    @Query("UPDATE offline_operations SET status = :status WHERE id = :operationId")
    suspend fun updateStatus(operationId: String, status: String)

    /**
     * Updates both the status and retry count of an operation.
     */
    @Query("UPDATE offline_operations SET status = :status, retryCount = :retryCount WHERE id = :operationId")
    suspend fun updateStatusAndRetryCount(operationId: String, status: String, retryCount: Int)
}
