package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.LinkPreviewCacheEntity

@Dao
interface LinkPreviewCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(linkPreviewCache: LinkPreviewCacheEntity)

    @Query("SELECT * FROM link_preview_cache WHERE url = :url")
    suspend fun getByUrl(url: String): LinkPreviewCacheEntity?

    @Query("DELETE FROM link_preview_cache WHERE fetchedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
