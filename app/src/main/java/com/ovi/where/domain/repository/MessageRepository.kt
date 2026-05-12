package com.ovi.where.domain.repository

import android.net.Uri
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessagePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MessageRepository {
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, text: String, replyToId: String? = null): Resource<Message>
    suspend fun sendLocationMessage(conversationId: String, latitude: Double?, longitude: Double?): Resource<Message>
    suspend fun sendImageMessage(conversationId: String, imageUri: Uri): Resource<Message>
    suspend fun loadHistory(conversationId: String)
    suspend fun loadOlderMessages(conversationId: String, beforeCursor: String?, limit: Int = 30): MessagePage
    suspend fun reactToMessage(conversationId: String, messageId: String, emoji: String): Resource<Unit>
    suspend fun removeReaction(conversationId: String, messageId: String, emoji: String): Resource<Unit>
    suspend fun markRead(conversationId: String, userId: String)
    suspend fun retryMessage(messageId: String): Resource<Message>

    /**
     * Fetches messages missed during disconnection using the timestamp of the last
     * locally cached message for the given conversation.
     * Requirement 13.5, 13.6
     */
    suspend fun fetchMissedMessages(conversationId: String): Resource<Unit>

    /** Returns the current offline queue size */
    val offlineQueueSize: Int

    /** Observes upload progress for a given message tempId (0-100) */
    fun observeUploadProgress(tempId: String): StateFlow<Int>?
}
