package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.VoiceMessageCacheEntity

@Dao
interface VoiceMessageCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voiceMessageCache: VoiceMessageCacheEntity)

    @Query("SELECT * FROM voice_message_cache WHERE messageId = :messageId")
    suspend fun getByMessageId(messageId: String): VoiceMessageCacheEntity?

    @Query("DELETE FROM voice_message_cache WHERE downloadedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
