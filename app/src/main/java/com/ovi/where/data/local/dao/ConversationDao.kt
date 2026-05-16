package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getById(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun observeById(conversationId: String): Flow<ConversationEntity?>

    @Query("UPDATE conversations SET unreadCount = :unreadCount WHERE id = :conversationId")
    suspend fun updateUnreadCount(conversationId: String, unreadCount: Int)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteById(conversationId: String)

    @Query("SELECT id FROM conversations")
    suspend fun getAllIds(): List<String>

    @Query("SELECT documentUpdateTime FROM conversations WHERE id = :conversationId")
    suspend fun getDocumentUpdateTime(conversationId: String): Long?

    @Query("SELECT lastSyncTimestamp FROM conversations WHERE id = :conversationId")
    suspend fun getLastSyncTimestamp(conversationId: String): Long?
}
