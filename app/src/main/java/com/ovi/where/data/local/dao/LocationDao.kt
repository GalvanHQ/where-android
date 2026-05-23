package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.SharedLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SharedLocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<SharedLocationEntity>)

    @Query("SELECT * FROM shared_location WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getLocationsByGroup(groupId: String): Flow<List<SharedLocationEntity>>

    @Query("SELECT * FROM shared_location WHERE userId = :userId AND groupId = :groupId")
    fun getUserLocation(userId: String, groupId: String): Flow<SharedLocationEntity?>

    @Query("DELETE FROM shared_location WHERE groupId = :groupId")
    suspend fun deleteLocationsByGroup(groupId: String)

    @Query("DELETE FROM shared_location WHERE timestamp < :expiryTime")
    suspend fun deleteExpiredLocations(expiryTime: Long)

    /** Delete a single row by primary key (the doc id, which equals userId
     *  for activeLocations rows). Used by the listener to reconcile when a
     *  sharer drops off the visibleTo list entirely. */
    @Query("DELETE FROM shared_location WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Delete rows matching a (userId, target) pair. Used when a sharer
     *  stops their session — Firestore writes `isSharingActive=false` and
     *  the listener uses this to clean up Room so the cached-locations
     *  Flow stops replaying the stale "Sharing" badge. */
    @Query("DELETE FROM shared_location WHERE userId = :userId AND groupId = :targetId")
    suspend fun deleteByUserAndTarget(userId: String, targetId: String)

    /**
     * Observes all active location sharing sessions from Room cache.
     * Used for aggressive local caching — serves cached positions within 100ms of screen open.
     * Requirement 7.2: Cache last known location per sharer in Room.
     */
    @Query("SELECT * FROM shared_location WHERE isSharingActive = 1 ORDER BY timestamp DESC")
    fun observeAllActive(): Flow<List<SharedLocationEntity>>

    /**
     * Returns all active locations synchronously for immediate cache reads.
     * Requirement 7.2: Serve cached positions within 100ms.
     */
    @Query("SELECT * FROM shared_location WHERE isSharingActive = 1")
    suspend fun getAllActive(): List<SharedLocationEntity>

    /**
     * Returns cached location for a specific user, regardless of active status.
     * Used for displaying last known position even after sharing ends.
     */
    @Query("SELECT * FROM shared_location WHERE userId = :userId LIMIT 1")
    suspend fun getCachedLocationForUser(userId: String): SharedLocationEntity?

    /**
     * Observes cached locations filtered by targetId for group-specific views.
     * Eliminates the need for per-group Firestore listeners.
     * Returns only active locations ordered by most recent first.
     */
    @Query("SELECT * FROM shared_location WHERE targetId = :targetId AND isSharingActive = 1 ORDER BY timestamp DESC")
    fun observeByTargetId(targetId: String): Flow<List<SharedLocationEntity>>
}
