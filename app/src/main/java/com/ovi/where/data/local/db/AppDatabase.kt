package com.ovi.where.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ovi.where.data.local.dao.CacheMetadataDao
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.local.dao.InteractionDao
import com.ovi.where.data.local.dao.LinkPreviewCacheDao
import com.ovi.where.data.local.dao.LocationDao
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.dao.OfflineOperationDao
import com.ovi.where.data.local.dao.OnlineStatusDao
import com.ovi.where.data.local.dao.VoiceMessageCacheDao
import com.ovi.where.data.local.entity.CacheMetadataEntity
import com.ovi.where.data.local.entity.ConversationEntity
import com.ovi.where.data.local.entity.InteractionEntity
import com.ovi.where.data.local.entity.LinkPreviewCacheEntity
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.local.entity.OfflineOperationEntity
import com.ovi.where.data.local.entity.OnlineStatusEntity
import com.ovi.where.data.local.entity.SharedLocationEntity
import com.ovi.where.data.local.entity.VoiceMessageCacheEntity

@Database(
    entities = [
        SharedLocationEntity::class,
        MessageEntity::class,
        ConversationEntity::class,
        CacheMetadataEntity::class,
        OfflineOperationEntity::class,
        InteractionEntity::class,
        VoiceMessageCacheEntity::class,
        LinkPreviewCacheEntity::class,
        OnlineStatusEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
    abstract fun offlineOperationDao(): OfflineOperationDao
    abstract fun interactionDao(): InteractionDao
    abstract fun voiceMessageCacheDao(): VoiceMessageCacheDao
    abstract fun linkPreviewCacheDao(): LinkPreviewCacheDao
    abstract fun onlineStatusDao(): OnlineStatusDao

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new tables
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `voice_message_cache` (
                        `messageId` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `downloadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `link_preview_cache` (
                        `url` TEXT NOT NULL,
                        `title` TEXT,
                        `description` TEXT,
                        `imageUrl` TEXT,
                        `domain` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`url`)
                    )
                    """.trimIndent()
                )

                // Add new columns to messages table
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `voiceUrl` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `voiceDurationMs` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `linkPreviewTitle` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `linkPreviewDescription` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `linkPreviewImageUrl` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `linkPreviewDomain` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `linkPreviewUrl` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `mentionedUserIdsJson` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `locationSharingSessionId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `locationSharingDurationMinutes` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `forwardedFrom` TEXT DEFAULT NULL")

                // Add new columns to shared_location table
                db.execSQL("ALTER TABLE `shared_location` ADD COLUMN `displayName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shared_location` ADD COLUMN `sharingStartedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add documentUpdateTime column to conversations table for version-based skip
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `documentUpdateTime` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create online_status table for persisting presence state from Socket.IO (Req 6.3)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `online_status` (
                        `userId` TEXT NOT NULL,
                        `isOnline` INTEGER NOT NULL,
                        `lastUpdatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add lastMessageType and lastMessageStatus columns to conversations table (Req 24.1, 24.2)
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `lastMessageType` TEXT NOT NULL DEFAULT 'TEXT'")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `lastMessageStatus` TEXT NOT NULL DEFAULT 'SENT'")
                // Add mutedByJson and pinnedByJson columns to conversations table (Req 24.4, 24.5)
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `mutedByJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `pinnedByJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add participantNamesJson and participantPhotosJson columns to conversations table
                // for persistent storage of resolved participant metadata (Req 1.8, 1.9, 2.8, 2.9)
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `participantNamesJson` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `participantPhotosJson` TEXT DEFAULT NULL")
            }
        }
    }
}
