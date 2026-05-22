package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.MeetupDestinationEntity
import kotlinx.coroutines.flow.Flow

/**
 * SSOT for the meetup destination per group.
 *
 * The repository keeps this table in sync with the Firestore snapshot;
 * the UI never reads Firestore directly for the meetup. One row per
 * group; absence of a row = "no active meetup for this group".
 */
@Dao
interface MeetupDestinationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(destination: MeetupDestinationEntity)

    @Query("DELETE FROM meetup_destination WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: String)

    @Query("DELETE FROM meetup_destination")
    suspend fun deleteAll()

    @Query("SELECT * FROM meetup_destination WHERE groupId = :groupId LIMIT 1")
    fun observeByGroup(groupId: String): Flow<MeetupDestinationEntity?>

    @Query("SELECT * FROM meetup_destination WHERE isActive = 1")
    fun observeAllActive(): Flow<List<MeetupDestinationEntity>>
}
