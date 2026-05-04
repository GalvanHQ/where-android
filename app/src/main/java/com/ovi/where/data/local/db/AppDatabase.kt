package com.ovi.where.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ovi.where.data.local.dao.LocationDao
import com.ovi.where.data.local.entity.SharedLocationEntity

@Database(
    entities = [SharedLocationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
}
