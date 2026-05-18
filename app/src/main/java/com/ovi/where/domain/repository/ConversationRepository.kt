package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Conversation
import kotlinx.coroutines.flow.Flow
import com.ovi.where.core.common.DataResource

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

    /**
     * Fetches conversations from Firestore in batches of 30 IDs using `whereIn` queries.
     * Executes at most one batch query set per foreground sync cycle.
     * Skips Firestore re-read for conversations whose lastSyncTimestamp is less than 5 minutes old.
     * Skips Room write for documents whose stored documentUpdateTime matches the incoming snapshot version.
     *
     * Requirements: 5.1, 5.2, 5.7, 7.4
     */
    suspend fun batchFetchConversations(conversationIds: List<String>): Resource<Unit>

    /**
     * Pins a conversation for the current user.
     * Returns error if the user already has 3 pinned conversations (Req 24.4).
     */
    suspend fun pinConversation(conversationId: String): Resource<Unit>

    /**
     * Unpins a conversation for the current user.
     */
    suspend fun unpinConversation(conversationId: String): Resource<Unit>

    /**
     * Mutes a conversation for the current user (Req 24.5).
     */
    suspend fun muteConversation(conversationId: String): Resource<Unit>

    /**
     * Unmutes a conversation for the current user.
     */
    suspend fun unmuteConversation(conversationId: String): Resource<Unit>

    /**
     * Archives a conversation for the current user (Req 24.3).
     */
    suspend fun archiveConversation(conversationId: String): Resource<Unit>

    /**
     * Soft-deletes a conversation for the current user (Req 1.7, 2.7).
     * Adds the current user's ID to the Firestore `deletedBy` array field,
     * and also removes from local Room for immediate UI feedback.
     * The conversation will not reappear on sync because the Firestore listener
     * filters out conversations where the current user is in `deletedBy`.
     */
    suspend fun softDeleteConversation(conversationId: String): Resource<Unit>

    /**
     * Updates the theme color for a conversation.
     * Stored as a hex string (e.g., "#5170FF").
     */
    suspend fun updateThemeColor(conversationId: String, color: String?): Resource<Unit>

    /**
     * Updates the emoji shortcut for a conversation.
     * Stored as a single emoji character (e.g., "👍").
     */
    suspend fun updateEmojiShortcut(conversationId: String, emoji: String?): Resource<Unit>

    /**
     * Updates nicknames for participants in a conversation.
     * Stored as a map of userId -> nickname.
     */
    suspend fun updateNicknames(conversationId: String, nicknames: Map<String, String>): Resource<Unit>

    /**
     * Resolves the conversationId for a given groupId.
     * First checks Room, then queries Firestore directly as fallback.
     */
    suspend fun getConversationIdByGroupId(groupId: String): String?

    /**
     * Updates the photo URL for a conversation.
     * Writes to Room first (SSOT) then Firestore.
     */
    suspend fun updateConversationPhotoUrl(conversationId: String, photoUrl: String?): Resource<Unit>

    /**
     * Updates the photo URL for a conversation identified by its groupId.
     * Writes to Room first (SSOT) then resolves conversationId for Firestore.
     */
    suspend fun updateConversationPhotoUrlByGroupId(groupId: String, photoUrl: String?): Resource<Unit>
}
