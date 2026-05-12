package com.ovi.where.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.local.dao.LocationDao
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.entity.ConversationEntity
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.local.entity.SharedLocationEntity

@Database(
    entities = [
        SharedLocationEntity::class,
        MessageEntity::class,
        ConversationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
}
