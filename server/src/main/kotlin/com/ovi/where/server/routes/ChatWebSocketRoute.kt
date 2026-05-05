package com.ovi.where.server.routes

import com.ovi.where.server.model.MessageDto
import com.ovi.where.server.model.ServerFrame
import com.ovi.where.server.model.WsFrame
import com.ovi.where.server.service.ChatSession
import com.ovi.where.server.service.ChatSessionManager
import com.ovi.where.server.service.FirebaseAdminService
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * WebSocket endpoint:
 *   ws://server/ws/chat/{conversationId}?token={firebaseIdToken}
 */
fun Route.chatWebSocketRoute() {
    webSocket("/ws/chat/{conversationId}") {
        val conversationId = call.parameters["conversationId"]
            ?: return@webSocket close(io.ktor.websocket.CloseReason(
                io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Missing conversationId"
            ))

        // Verify token from query param
        val token = call.request.queryParameters["token"]
            ?: return@webSocket close(io.ktor.websocket.CloseReason(
                io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Missing token"
            ))

        val firebaseToken = FirebaseAdminService.verifyToken(token)
            ?: return@webSocket close(io.ktor.websocket.CloseReason(
                io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Invalid token"
            ))

        val userId = firebaseToken.uid
        val userName = firebaseToken.name ?: "User"

        // Verify participant
        if (!FirebaseAdminService.isParticipant(conversationId, userId)) {
            return@webSocket close(io.ktor.websocket.CloseReason(
                io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Not a participant"
            ))
        }

        val chatSession = ChatSession(userId, userName, conversationId, this)
        ChatSessionManager.join(chatSession)

        // Confirm connection
        ChatSessionManager.sendTo(chatSession, ServerFrame.Connected(conversationId, userId))

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    handleIncomingFrame(
                        raw = frame.readText(),
                        chatSession = chatSession,
                        conversationId = conversationId
                    )
                }
            }
        } catch (e: Exception) {
            // Connection closed or error
        } finally {
            ChatSessionManager.leave(chatSession)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.handleIncomingFrame(
    raw: String,
    chatSession: ChatSession,
    conversationId: String
) {
    val parsed = try {
        Json.parseToJsonElement(raw).jsonObject
    } catch (e: Exception) {
        ChatSessionManager.sendTo(chatSession, ServerFrame.Error("Invalid JSON"))
        return
    }

    when (val type = parsed["type"]?.jsonPrimitive?.content) {
        "message" -> handleTextMessage(parsed, chatSession, conversationId)
        "location_message" -> handleLocationMessage(parsed, chatSession, conversationId)
        "typing" -> handleTyping(parsed, chatSession, conversationId)
        "read" -> handleRead(chatSession, conversationId)
        else -> ChatSessionManager.sendTo(chatSession, ServerFrame.Error("Unknown frame type: $type"))
    }
}

private suspend fun handleTextMessage(
    data: JsonObject,
    chatSession: ChatSession,
    conversationId: String
) {
    val tempId = data["tempId"]?.jsonPrimitive?.content ?: ""
    val text = data["text"]?.jsonPrimitive?.content?.trim() ?: ""
    if (text.isEmpty()) return

    val msgId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()

    val msgDto = MessageDto(
        id = msgId,
        conversationId = conversationId,
        senderId = chatSession.userId,
        senderName = chatSession.userName,
        text = text,
        messageType = "TEXT",
        timestamp = now
    )

    // Persist to Firestore
    FirebaseAdminService.saveMessage(msgDto)

    val serverFrame = ServerFrame.MessageDelivered(
        id = msgId,
        conversationId = conversationId,
        senderId = chatSession.userId,
        senderName = chatSession.userName,
        text = text,
        messageType = "TEXT",
        timestamp = now
    )

    // Broadcast to all room members (including sender)
    ChatSessionManager.broadcast(conversationId, serverFrame)

    // Send ACK to sender with tempId mapping
    ChatSessionManager.sendTo(chatSession, ServerFrame.MessageAck(tempId, msgId, now))
}

private suspend fun handleLocationMessage(
    data: JsonObject,
    chatSession: ChatSession,
    conversationId: String
) {
    val tempId = data["tempId"]?.jsonPrimitive?.content ?: ""
    val lat = data["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return
    val lng = data["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return

    val msgId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()

    val msgDto = MessageDto(
        id = msgId,
        conversationId = conversationId,
        senderId = chatSession.userId,
        senderName = chatSession.userName,
        text = "📍 Location",
        messageType = "LOCATION",
        latitude = lat,
        longitude = lng,
        timestamp = now
    )

    FirebaseAdminService.saveMessage(msgDto)

    val serverFrame = ServerFrame.MessageDelivered(
        id = msgId,
        conversationId = conversationId,
        senderId = chatSession.userId,
        senderName = chatSession.userName,
        text = "📍 Location",
        messageType = "LOCATION",
        latitude = lat,
        longitude = lng,
        timestamp = now
    )

    ChatSessionManager.broadcast(conversationId, serverFrame)
    ChatSessionManager.sendTo(chatSession, ServerFrame.MessageAck(tempId, msgId, now))
}

private suspend fun handleTyping(
    data: JsonObject,
    chatSession: ChatSession,
    conversationId: String
) {
    val isTyping = data["isTyping"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
    ChatSessionManager.broadcast(
        conversationId,
        ServerFrame.UserTyping(chatSession.userId, chatSession.userName, isTyping),
        excludeUserId = chatSession.userId
    )
}

private suspend fun handleRead(chatSession: ChatSession, conversationId: String) {
    FirebaseAdminService.markConversationRead(conversationId, chatSession.userId)
}
