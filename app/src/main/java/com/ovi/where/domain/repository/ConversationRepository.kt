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

    /**
     * Syncs unread counts from the server via a single REST API call.
     * Called on app foreground (Requirement 12.5).
     * On failure/timeout: retains Room-cached counts and returns a recoverable error (Requirement 12.6).
     */
    suspend fun syncUnreadCounts(): Resource<Unit>

    /**
     * Fetches the initial conversation list from REST and persists to Room.
     * Called on first launch when Room has no records (Requirement 12.7).
     * Must complete before the Firestore listener starts incremental updates.
     */
    suspend fun fetchInitialConversationsIfNeeded(): Resource<Unit>
}
