package com.ovi.where.data.remote.chat

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatSocketIoClient"

@Singleton
class ChatSocketIoClient @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _incomingFrames = MutableSharedFlow<ServerFrame>(extraBufferCapacity = 64)
    val incomingFrames: SharedFlow<ServerFrame> = _incomingFrames.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var socket: Socket? = null
    private var currentConversationId: String? = null
    private var currentToken: String? = null

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    fun connect(conversationId: String, firebaseToken: String) {
        if (currentConversationId == conversationId &&
            (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING)) {
            return
        }

        disconnect()

        currentConversationId = conversationId
        currentToken = firebaseToken
        
        _connectionState.value = ConnectionState.CONNECTING

        try {
            val options = IO.Options.builder()
                .setQuery("conversationId=$conversationId&token=$firebaseToken")
                .setTransports(arrayOf(io.socket.engineio.client.transports.WebSocket.NAME))
                .build()

            socket = IO.socket(ChatApiClient.WS_BASE_URL, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket.IO connected")
                _connectionState.value = ConnectionState.CONNECTED
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket.IO disconnected")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket.IO connect error: ${args.firstOrNull()}")
                _connectionState.value = ConnectionState.ERROR
            }

            socket?.on("connected") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.Connected>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (connected): ${e.message}")
                }
            }

            socket?.on("message") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.MessageDelivered>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (message): ${e.message}")
                }
            }

            socket?.on("ack") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.MessageAck>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (ack): ${e.message}")
                }
            }

            socket?.on("typing") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.UserTyping>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (typing): ${e.message}")
                }
            }

            socket?.on("error") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.Error>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (error): ${e.message}")
                }
            }

            socket?.connect()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        currentConversationId = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    suspend fun sendText(text: String, tempId: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = JSONObject().apply {
            put("tempId", tempId)
            put("text", text)
        }
        socket?.emit("message", payload)
    }

    suspend fun sendLocation(latitude: Double, longitude: Double, tempId: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = JSONObject().apply {
            put("tempId", tempId)
            put("latitude", latitude)
            put("longitude", longitude)
        }
        socket?.emit("location_message", payload)
    }

    suspend fun sendTyping(isTyping: Boolean) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = JSONObject().apply {
            put("isTyping", isTyping)
        }
        socket?.emit("typing", payload)
    }

    suspend fun sendRead() {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        socket?.emit("read")
    }
}
