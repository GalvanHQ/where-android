package com.ovi.where.server.service

import com.ovi.where.server.model.ServerFrame
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** Holds the active WebSocket session for one participant in one conversation. */
data class ChatSession(
    val userId: String,
    val userName: String,
    val conversationId: String,
    val session: DefaultWebSocketServerSession
)

/**
 * Manages all live WebSocket sessions grouped by conversationId.
 * Thread-safe via ConcurrentHashMap.
 */
object ChatSessionManager {

    // conversationId → list of active sessions
    private val rooms = ConcurrentHashMap<String, MutableList<ChatSession>>()

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun join(chatSession: ChatSession) {
        rooms.getOrPut(chatSession.conversationId) {
            java.util.Collections.synchronizedList(mutableListOf())
        }.add(chatSession)
    }

    fun leave(chatSession: ChatSession) {
        rooms[chatSession.conversationId]?.remove(chatSession)
        if (rooms[chatSession.conversationId]?.isEmpty() == true) {
            rooms.remove(chatSession.conversationId)
        }
    }

    /**
     * Broadcast a [ServerFrame] to all active sessions in a conversation,
     * optionally excluding the sender (when [excludeUserId] is set).
     */
    suspend fun broadcast(
        conversationId: String,
        frame: ServerFrame,
        excludeUserId: String? = null
    ) {
        val encoded = json.encodeToString(frame)
        val sessions = rooms[conversationId]?.toList() ?: return
        for (cs in sessions) {
            if (cs.userId == excludeUserId) continue
            if (cs.session.isActive) {
                try {
                    cs.session.send(Frame.Text(encoded))
                } catch (e: Exception) {
                    // Session died — remove it
                    leave(cs)
                }
            }
        }
    }

    /** Send a frame only to the originating session. */
    suspend fun sendTo(chatSession: ChatSession, frame: ServerFrame) {
        if (!chatSession.session.isActive) return
        val encoded = json.encodeToString(frame)
        try {
            chatSession.session.send(Frame.Text(encoded))
        } catch (e: Exception) {
            leave(chatSession)
        }
    }

    fun getActiveUserIds(conversationId: String): List<String> =
        rooms[conversationId]?.map { it.userId } ?: emptyList()
}
