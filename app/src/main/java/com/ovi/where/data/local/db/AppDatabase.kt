package com.ovi.where.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ovi.where.data.local.dao.CacheMetadataDao
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.local.dao.LocationDao
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.dao.OfflineOperationDao
import com.ovi.where.data.local.entity.CacheMetadataEntity
import com.ovi.where.data.local.entity.ConversationEntity
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.local.entity.OfflineOperationEntity
import com.ovi.where.data.local.entity.SharedLocationEntity

@Database(
    entities = [
        SharedLocationEntity::class,
        MessageEntity::class,
        ConversationEntity::class,
        CacheMetadataEntity::class,
        OfflineOperationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
    abstract fun offlineOperationDao(): OfflineOperationDao
}
