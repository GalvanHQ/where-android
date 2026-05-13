package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey
    val key: String,
    val lastFetchedAt: Long,
    val eTag: String?
)
