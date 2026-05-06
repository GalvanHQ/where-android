package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.remote.chat.ChatWebSocketClient
import com.ovi.where.data.remote.chat.KtorApiClient
import com.ovi.where.data.remote.chat.MessageDto
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.repository.MessageRepository
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val wsClient: ChatWebSocketClient,
    private val firebaseAuth: FirebaseAuth
) : MessageRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // Per-conversation message cache (flow of accumulated messages)
    private val messageCache = mutableMapOf<String, MutableStateFlow<List<Message>>>()

    private fun cacheFor(conversationId: String): MutableStateFlow<List<Message>> =
        messageCache.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    private suspend fun getIdToken(): String =
        firebaseAuth.currentUser?.getIdToken(false)?.await()?.token ?: ""

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        val cache = cacheFor(conversationId)

        // Collect incoming WebSocket frames and append to cache
        return wsClient.incomingFrames
            .transform { frame ->
                if (frame is ServerFrame.MessageDelivered &&
                    frame.conversationId == conversationId) {
                    val msg = frame.toDomain()
                    val current = cache.value.toMutableList()
                    // Avoid duplicates
                    if (current.none { it.id == msg.id }) {
                        current.add(msg)
                        current.sortBy { it.timestamp }
                        cache.value = current
                    }
                }
                emit(cache.value)
            }
            .onStart { emit(cache.value) }
    }

    override suspend fun sendMessage(conversationId: String, text: String): Resource<Message> {
        return try {
            val tempId = UUID.randomUUID().toString()

            // Optimistically add to local cache
            val optimistic = Message(
                id = tempId,
                conversationId = conversationId,
                senderId = firebaseAuth.currentUser?.uid ?: "",
                senderName = firebaseAuth.currentUser?.displayName ?: "",
                text = text,
                type = MessageType.TEXT,
                timestamp = System.currentTimeMillis()
            )
            val cache = cacheFor(conversationId)
            cache.value = cache.value + optimistic

            wsClient.sendText(text, tempId)
            Resource.Success(optimistic)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send message")
        }
    }

    override suspend fun sendLocationMessage(
        conversationId: String,
        latitude: Double,
        longitude: Double
    ): Resource<Message> {
        return try {
            val tempId = UUID.randomUUID().toString()

            val optimistic = Message(
                id = tempId,
                conversationId = conversationId,
                senderId = firebaseAuth.currentUser?.uid ?: "",
                senderName = firebaseAuth.currentUser?.displayName ?: "",
                text = "📍 Location",
                type = MessageType.LOCATION,
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis()
            )
            val cache = cacheFor(conversationId)
            cache.value = cache.value + optimistic

            wsClient.sendLocation(latitude, longitude, tempId)
            Resource.Success(optimistic)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send location")
        }
    }

    /** Load message history from the server and seed the local cache. */
    override suspend fun loadHistory(conversationId: String) {
        try {
            val token = getIdToken()
            val messages = KtorApiClient.httpClient
                .get("${KtorApiClient.HTTP_BASE_URL}/api/conversations/$conversationId/messages") {
                    bearerAuth(token)
                }
                .body<List<MessageDto>>()
                .map { it.toDomain() }

            val cache = cacheFor(conversationId)
            // Merge: history + any already-optimistic messages
            val optimistic = cache.value.filter { it.id.length == 36 && messages.none { m -> m.id == it.id } }
            cache.value = (messages + optimistic).sortedBy { it.timestamp }
        } catch (e: Exception) {
            // Keep cache as-is if history load fails
        }
    }

    private fun ServerFrame.MessageDelivered.toDomain() = Message(
        id = id, conversationId = conversationId, senderId = senderId,
        senderName = senderName, senderPhotoUrl = senderPhotoUrl, text = text,
        type = if (messageType == "LOCATION") MessageType.LOCATION else MessageType.TEXT,
        latitude = latitude, longitude = longitude, timestamp = timestamp, readBy = readBy
    )

    private fun MessageDto.toDomain() = Message(
        id = id, conversationId = conversationId, senderId = senderId,
        senderName = senderName, senderPhotoUrl = senderPhotoUrl, text = text,
        type = if (messageType == "LOCATION") MessageType.LOCATION else MessageType.TEXT,
        latitude = latitude, longitude = longitude, timestamp = timestamp, readBy = readBy
    )
}
