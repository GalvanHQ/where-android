package com.ovi.where.data.remote.chat

import android.util.Log
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatWebSocketClient"
private const val RECONNECT_DELAY_MS = 3_000L
private const val MAX_RECONNECT_ATTEMPTS = 5

@Singleton
class ChatWebSocketClient @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json  = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _incomingFrames = MutableSharedFlow<ServerFrame>(extraBufferCapacity = 64)
    val incomingFrames: SharedFlow<ServerFrame> = _incomingFrames.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var wsSession: io.ktor.websocket.DefaultWebSocketSession? = null
    private var connectionJob: Job? = null
    private var currentConversationId: String? = null
    private var currentToken: String? = null
    private var reconnectAttempts = 0

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    /** Connect to a conversation room. Call this when entering a ChatScreen. */
    fun connect(conversationId: String, firebaseToken: String) {
        if (currentConversationId == conversationId &&
            _connectionState.value == ConnectionState.CONNECTED) return

        currentConversationId = conversationId
        currentToken = firebaseToken
        reconnectAttempts = 0
        startConnection()
    }

    /** Disconnect from the current room. Call when leaving ChatScreen. */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        currentConversationId = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /** Send a text message. */
    suspend fun sendText(text: String, tempId: String) {
        val payload = """{"type":"message","tempId":"$tempId","text":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(text))},"messageType":"TEXT"}"""
        sendRaw(payload)
    }

    /** Send a location message. */
    suspend fun sendLocation(latitude: Double, longitude: Double, tempId: String) {
        val payload = """{"type":"location_message","tempId":"$tempId","latitude":$latitude,"longitude":$longitude}"""
        sendRaw(payload)
    }

    /** Send typing indicator. */
    suspend fun sendTyping(isTyping: Boolean) {
        sendRaw("""{"type":"typing","isTyping":$isTyping}""")
    }

    /** Mark conversation as read. */
    suspend fun sendRead() {
        sendRaw("""{"type":"read"}""")
    }

    private suspend fun sendRaw(payload: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        try {
            wsSession?.send(Frame.Text(payload))
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
        }
    }

    private fun startConnection() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            while (isActive && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                _connectionState.value = ConnectionState.CONNECTING
                try {
                    val convId = currentConversationId ?: break
                    val token  = currentToken ?: break
                    val wsUrl  = KtorApiClient.WS_BASE_URL

                    KtorApiClient.wsClient.webSocket(
                        urlString = "$wsUrl/ws/chat/$convId?token=$token"
                    ) {
                        wsSession = this
                        _connectionState.value = ConnectionState.CONNECTED
                        reconnectAttempts = 0

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                parseAndEmit(frame.readText())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket error: ${e.message}")
                    _connectionState.value = ConnectionState.ERROR
                }

                reconnectAttempts++
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    delay(RECONNECT_DELAY_MS * reconnectAttempts)
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }
    }

    private fun parseAndEmit(raw: String) {
        try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return
            val frame: ServerFrame = when (type) {
                "message" -> json.decodeFromString<ServerFrame.MessageDelivered>(raw)
                "ack"     -> json.decodeFromString<ServerFrame.MessageAck>(raw)
                "typing"  -> json.decodeFromString<ServerFrame.UserTyping>(raw)
                "error"   -> json.decodeFromString<ServerFrame.Error>(raw)
                "connected" -> json.decodeFromString<ServerFrame.Connected>(raw)
                else -> return
            }
            scope.launch { _incomingFrames.emit(frame) }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }
}
