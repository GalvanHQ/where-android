package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ovi.where.data.local.entity.InteractionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionDao {

    @Upsert
    suspend fun upsert(entity: InteractionEntity)

    @Query("SELECT * FROM interactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<InteractionEntity>>

    @Query("DELETE FROM interactions")
    suspend fun clearAll()
}
