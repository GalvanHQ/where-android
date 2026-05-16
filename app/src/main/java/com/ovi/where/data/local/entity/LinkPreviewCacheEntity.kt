package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "link_preview_cache")
data class LinkPreviewCacheEntity(
    @PrimaryKey
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val domain: String,
    val fetchedAt: Long
)
