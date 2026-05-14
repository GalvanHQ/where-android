package com.ovi.where.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ovi.where.data.local.dao.CacheMetadataDao
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.local.dao.InteractionDao
import com.ovi.where.data.local.dao.LocationDao
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.dao.OfflineOperationDao
import com.ovi.where.data.local.entity.CacheMetadataEntity
import com.ovi.where.data.local.entity.ConversationEntity
import com.ovi.where.data.local.entity.InteractionEntity
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.local.entity.OfflineOperationEntity
import com.ovi.where.data.local.entity.SharedLocationEntity

@Database(
    entities = [
        SharedLocationEntity::class,
        MessageEntity::class,
        ConversationEntity::class,
        CacheMetadataEntity::class,
        OfflineOperationEntity::class,
        InteractionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
    abstract fun offlineOperationDao(): OfflineOperationDao
    abstract fun interactionDao(): InteractionDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interactions` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `photoUrl` TEXT,
                        `type` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
