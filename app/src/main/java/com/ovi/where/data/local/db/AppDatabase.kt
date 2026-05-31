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
import com.ovi.where.data.local.dao.MeetupDestinationDao
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.dao.NotificationDao
import com.ovi.where.data.local.dao.OfflineOperationDao
import com.ovi.where.data.local.dao.OnlineStatusDao
import com.ovi.where.data.local.dao.VoiceMessageCacheDao
import com.ovi.where.data.local.entity.CacheMetadataEntity
import com.ovi.where.data.local.entity.ConversationEntity
import com.ovi.where.data.local.entity.InteractionEntity
import com.ovi.where.data.local.entity.LinkPreviewCacheEntity
import com.ovi.where.data.local.entity.MeetupDestinationEntity
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.local.entity.NotificationEntity
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
        OnlineStatusEntity::class,
        MeetupDestinationEntity::class,
        NotificationEntity::class,
        com.ovi.where.data.local.entity.UserCacheEntity::class
    ],
    version = 20,
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
    abstract fun meetupDestinationDao(): MeetupDestinationDao
    abstract fun notificationDao(): NotificationDao
    abstract fun userCacheDao(): com.ovi.where.data.local.dao.UserCacheDao

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

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add customization columns to conversations table (theme color, emoji shortcut, nicknames)
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `themeColor` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `emojiShortcut` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `nicknamesJson` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Track lastSeen alongside online status so we can render
                // Messenger-style "Active 5m ago" subtitles when a user is offline.
                db.execSQL("ALTER TABLE `online_status` ADD COLUMN `lastSeen` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add denormalized profile and targeting fields to shared_location table
                // for cache-first display without additional lookups (Req 10.1, 10.4)
                db.execSQL("ALTER TABLE `shared_location` ADD COLUMN `photoUrl` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `shared_location` ADD COLUMN `targetType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shared_location` ADD COLUMN `targetId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shared_location` ADD COLUMN `visibleTo` TEXT DEFAULT NULL")
            }
        }

        /**
         * Adds three nullable columns to `messages` for system events
         * (Messenger-style "info messages"). Additive, defaults to NULL,
         * so older code paths read existing rows unchanged.
         *
         * See spec: `.kiro/specs/group-system-messages/`.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `systemEventType` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `systemEventPayload` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `targetUserId` TEXT DEFAULT NULL")
            }
        }

        /**
         * Adds the `meetup_destination` table — Room becomes the SSOT for the
         * meetup feature. The repository keeps it in sync with the Firestore
         * snapshot; the UI flows directly off this table.
         *
         * Participants are serialized to a single JSON column to keep the
         * schema simple — they're always read together with the destination.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `meetup_destination` (
                        `groupId` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `name` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `setBy` TEXT NOT NULL,
                        `setAt` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `participantsJson` TEXT NOT NULL,
                        PRIMARY KEY(`groupId`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Adds the `notifications` table - durable inbox for the in-app
         * notification list. Mirrors the FCM payload plus an `isRead` flag and
         * the resolved deep-link route. Old rows are pruned by the repo.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        `deepLinkRoute` TEXT,
                        `conversationId` TEXT,
                        `groupId` TEXT,
                        `userId` TEXT,
                        `destinationName` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Adds the `user_cache` table — persistent per-uid mirror of user
         * profile data. Exists so VM-scoped `userCache` maps survive
         * process death and the UI doesn't render "Member" / placeholder
         * names + null avatars while Firestore re-resolves on cold start.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_cache` (
                        `id` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `bio` TEXT NOT NULL,
                        `photoUrl` TEXT,
                        `isOnline` INTEGER NOT NULL,
                        `lastSeen` INTEGER NOT NULL,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Adds `targetIdsJson` to the `shared_location` table.
         *
         * Multi-target shares (one user fanning out to several groups +
         * direct friends at once) carry an empty legacy `targetId` and
         * stash the full membership in `targetIds`. Without persisting
         * this list, the chat header's "who's actively sharing" filter
         * was failing to match multi-target rows after they passed
         * through Room — the header avatar row never rendered.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `shared_location` ADD COLUMN `targetIdsJson` TEXT DEFAULT NULL"
                )
            }
        }

        /**
         * Adds home + social columns to `user_cache` and the denormalized
         * `isAtHome` flag to `shared_location`.
         *
         * The home/social columns let the profile screens render a cached
         * user's Home row + social links without waiting on Firestore, and
         * `isAtHome` carries the sharer's "at home" state to viewers so the
         * map pin can show an "At Home" badge. All additive with safe
         * defaults so existing rows read unchanged.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_cache` ADD COLUMN `homeLatitude` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `user_cache` ADD COLUMN `homeLongitude` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `user_cache` ADD COLUMN `homeLabel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `user_cache` ADD COLUMN `facebookUrl` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `user_cache` ADD COLUMN `instagramUrl` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `user_cache` ADD COLUMN `linkedinUrl` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shared_location` ADD COLUMN `isAtHome` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
