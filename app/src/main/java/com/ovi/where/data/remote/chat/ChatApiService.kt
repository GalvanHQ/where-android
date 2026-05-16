package com.ovi.where.data.remote.chat

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header
import retrofit2.http.Url

interface ChatApiService {
    @GET("/api/conversations/{conversationId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String
    ): List<MessageDto>

    @GET("/api/conversations/{conversationId}/messages")
    suspend fun getMessagesPaginated(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 30
    ): MessagePageDto

    /**
     * Fetches messages after a given timestamp for catching up on missed messages.
     * Used on reconnection to fetch messages received while disconnected (Requirement 13.5).
     */
    @GET("/api/conversations/{conversationId}/messages")
    suspend fun getMessagesSince(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String,
        @Query("after") after: String,
        @Query("limit") limit: Int = 100
    ): List<MessageDto>

    @POST("/api/conversations/direct")
    suspend fun getOrCreateDirectConversation(
        @Header("Authorization") token: String,
        @Body request: CreateDirectConversationRequest
    ): ConversationDto

    @POST("/api/conversations/group")
    suspend fun createGroupConversation(
        @Header("Authorization") token: String,
        @Body request: CreateGroupConversationRequest
    ): ConversationDto

    @PATCH("/api/conversations/{conversationId}/read")
    suspend fun markAsRead(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String
    )

    /**
     * Fetches all conversation unread counts in a single response.
     * Used for foreground sync (Requirement 12.5).
     */
    @GET("/api/conversations/unread-counts")
    suspend fun getUnreadCounts(
        @Header("Authorization") token: String
    ): List<UnreadCountDto>

    /**
     * Fetches the full conversation list from REST.
     * Used for initial load when Room has no records (Requirement 12.7).
     */
    @GET("/api/conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String
    ): List<ConversationDto>

    /**
     * Deletes a message sent by the current user.
     * Used by the context menu "Delete" action (Requirement 9.7).
     */
    @DELETE("/api/conversations/{conversationId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String
    )

    /**
     * Fetches Open Graph metadata for a given URL from the server-side link preview API.
     * Used to generate rich link previews in chat messages.
     *
     * Requirement 12.2: Fetch OG metadata (title, description, image URL) from server-side API.
     * Requirement 12.3: Caller enforces 5-second timeout.
     */
    @GET("/api/link-preview")
    suspend fun fetchLinkPreview(
        @Query("url") url: String
    ): LinkPreviewDto
}
