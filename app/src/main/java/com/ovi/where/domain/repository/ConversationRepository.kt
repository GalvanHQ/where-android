package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Conversation
import kotlinx.coroutines.flow.Flow
import com.ovi.where.data.util.Resource as DataResource

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

    /**
     * Deletes a conversation from the local database.
     * Removes it from Room so it disappears from the conversation list.
     */
    suspend fun deleteConversation(conversationId: String): Resource<Unit>

    /**
     * Returns conversations using the NetworkBoundResource pattern:
     * 1. Serves Room cache immediately (Loading state with cached data)
     * 2. Checks staleness via CacheStalenessChecker
     * 3. Fetches from network if stale
     * 4. Saves to Room on success (Success state with fresh data)
     * 5. Serves stale cache on failure (Error state with cached data)
     *
     * Network requests are only triggered from ViewModel init or explicit user actions,
     * not from Compose recomposition.
     *
     * Requirements: 11.1, 11.2, 11.3, 11.4, 11.6
     */
    fun getConversationsResource(): Flow<DataResource<List<Conversation>>>

    /**
     * Forces a refresh of conversations from the network, bypassing staleness check.
     * Used for explicit user actions like pull-to-refresh.
     *
     * Requirements: 11.1, 11.2
     */
    fun refreshConversations(): Flow<DataResource<List<Conversation>>>
}
