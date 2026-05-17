package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestMessages(conversationId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByConversationPaginated(conversationId: String, beforeTimestamp: Long, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("UPDATE messages SET id = :newId, status = :status, timestamp = :timestamp WHERE id = :oldId")
    suspend fun updateIdAndStatus(oldId: String, newId: String, status: String, timestamp: Long)

    @Query("UPDATE messages SET reactionsJson = :reactionsJson WHERE id = :messageId")
    suspend fun updateReactions(messageId: String, reactionsJson: String)

    @Query("UPDATE messages SET readByJson = :readByJson WHERE id = :messageId")
    suspend fun updateReadBy(messageId: String, readByJson: String)

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getById(messageId: String): MessageEntity?

    @Query("UPDATE messages SET imageUrl = :imageUrl WHERE id = :messageId")
    suspend fun updateImageUrl(messageId: String, imageUrl: String)

    @Query("UPDATE messages SET voiceUrl = :voiceUrl WHERE id = :messageId")
    suspend fun updateVoiceUrl(messageId: String, voiceUrl: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND text LIKE :query COLLATE NOCASE ORDER BY timestamp ASC")
    suspend fun searchMessages(conversationId: String, query: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND (type = 'IMAGE' OR type = 'VIDEO') ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMediaMessages(conversationId: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND (type = 'IMAGE' OR type = 'VIDEO')")
    suspend fun getMediaMessageCount(conversationId: String): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND type = 'DOCUMENT' ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getDocumentMessages(conversationId: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND type = 'DOCUMENT'")
    suspend fun getDocumentMessageCount(conversationId: String): Int

    /**
     * Finds a potential duplicate message by matching conversationId, senderId, text,
     * and timestamp within a 1-second tolerance window.
     * Used as a secondary deduplication safeguard (Requirement 1.13, 2.13).
     */
    @Query(
        """
        SELECT * FROM messages 
        WHERE conversationId = :conversationId 
          AND senderId = :senderId 
          AND text = :text 
          AND timestamp BETWEEN :timestampLow AND :timestampHigh 
        LIMIT 1
        """
    )
    suspend fun findDuplicate(
        conversationId: String,
        senderId: String,
        text: String,
        timestampLow: Long,
        timestampHigh: Long
    ): MessageEntity?
}
