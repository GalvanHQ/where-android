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
}
