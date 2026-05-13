package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.CacheMetadataEntity

@Dao
interface CacheMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: CacheMetadataEntity)

    @Query("SELECT * FROM cache_metadata WHERE `key` = :key")
    suspend fun getByKey(key: String): CacheMetadataEntity?

    @Query("DELETE FROM cache_metadata WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}
