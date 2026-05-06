package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.remote.chat.ConversationDto
import com.ovi.where.data.remote.chat.CreateDirectConversationRequest
import com.ovi.where.data.remote.chat.CreateGroupConversationRequest
import com.ovi.where.data.remote.chat.KtorApiClient
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.repository.ConversationRepository
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore
import com.ovi.where.core.constants.AppConstants
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ConversationRepository {

    private suspend fun getIdToken(): String {
        return firebaseAuth.currentUser?.getIdToken(false)?.await()?.token ?: ""
    }

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    override fun observeConversations(): Flow<List<Conversation>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        // Real-time Firestore listener for conversations
        val listener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
            .whereArrayContains("participantIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val conversations = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val unreadMap = doc.get("unreadCounts") as? Map<*, *>
                        val myUnread = (unreadMap?.get(uid) as? Long)?.toInt() ?: 0
                        Conversation(
                            id = doc.id,
                            type = if (doc.getString("type") == "group") ConversationType.GROUP
                                   else ConversationType.DIRECT,
                            participantIds = (doc.get("participantIds") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            groupId = doc.getString("groupId"),
                            name = doc.getString("name") ?: "",
                            photoUrl = doc.getString("photoUrl"),
                            lastMessageText = doc.getString("lastMessageText") ?: "",
                            lastMessageSenderId = doc.getString("lastMessageSenderId") ?: "",
                            lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                            unreadCounts = mapOf(uid to myUnread),
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    } catch (e: Exception) { null }
                }?.sortedByDescending { it.lastMessageTimestamp } ?: emptyList()
                trySend(conversations)
            }

        awaitClose { listener.remove() }
    }

    override fun observeConversation(conversationId: String): Flow<Conversation?> = callbackFlow {
        val uid = currentUid
        val listener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
            .document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val conv = snapshot?.let { doc ->
                    try {
                        Conversation(
                            id = doc.id,
                            type = if (doc.getString("type") == "group") ConversationType.GROUP
                                   else ConversationType.DIRECT,
                            participantIds = (doc.get("participantIds") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            groupId = doc.getString("groupId"),
                            name = doc.getString("name") ?: "",
                            photoUrl = doc.getString("photoUrl"),
                            lastMessageText = doc.getString("lastMessageText") ?: "",
                            lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
                trySend(conv)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getOrCreateDirectConversation(otherUserId: String): Resource<Conversation> {
        return try {
            val token = getIdToken()
            println("Creating conversation with otherUserId: $otherUserId")
            val dto = KtorApiClient.httpClient
                .post("${KtorApiClient.HTTP_BASE_URL}/api/conversations/direct") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(CreateDirectConversationRequest(otherUserId))
                }
                .body<ConversationDto>()
            println("Parsed DTO: $dto")
            Resource.Success(dto.toDomain())
        } catch (e: Exception) {
            println("Error: ${e.message}")
            Resource.Error(e.message ?: "Failed to get or create conversation")
        }
    }

    override suspend fun createGroupConversation(
        groupId: String, name: String, memberIds: List<String>
    ): Resource<Conversation> {
        return try {
            val token = getIdToken()
            val dto = KtorApiClient.httpClient
                .post("${KtorApiClient.HTTP_BASE_URL}/api/conversations/group") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(CreateGroupConversationRequest(groupId, name, memberIds))
                }
                .body<ConversationDto>()
            Resource.Success(dto.toDomain())
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create group conversation")
        }
    }

    override suspend fun markAsRead(conversationId: String, userId: String): Resource<Unit> {
        return try {
            val token = getIdToken()
            KtorApiClient.httpClient
                .patch("${KtorApiClient.HTTP_BASE_URL}/api/conversations/$conversationId/read") {
                    bearerAuth(token)
                }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to mark as read")
        }
    }

    override suspend fun updateLastMessage(
        conversationId: String, text: String, senderId: String
    ): Resource<Unit> = Resource.Success(Unit) // Updated server-side via message save

    private fun ConversationDto.toDomain() = Conversation(
        id = id, type = if (type == "group") ConversationType.GROUP else ConversationType.DIRECT,
        participantIds = participantIds, groupId = groupId, name = name, photoUrl = photoUrl,
        lastMessageText = lastMessageText, lastMessageSenderId = lastMessageSenderId,
        lastMessageTimestamp = lastMessageTimestamp, createdAt = createdAt
    )
}
