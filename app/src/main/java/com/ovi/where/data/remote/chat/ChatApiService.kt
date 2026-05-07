package com.ovi.where.data.remote.chat

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Header

interface ChatApiService {
    @GET("/api/conversations/{conversationId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String
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
}
