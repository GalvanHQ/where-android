package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(conversationId: String): Flow<Conversation?>
    suspend fun getOrCreateDirectConversation(otherUserId: String): Resource<Conversation>
    suspend fun createGroupConversation(groupId: String, name: String, memberIds: List<String>): Resource<Conversation>
    suspend fun markAsRead(conversationId: String, userId: String): Resource<Unit>
    suspend fun updateLastMessage(conversationId: String, text: String, senderId: String): Resource<Unit>
}
