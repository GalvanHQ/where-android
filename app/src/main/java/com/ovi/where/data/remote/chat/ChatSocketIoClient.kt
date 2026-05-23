package com.ovi.where.data.remote.chat

import android.util.Log
import com.ovi.where.data.remote.chat.ChatSocketIoClient.Companion.FOREGROUND_RECONNECT_TIMEOUT_MS
import io.socket.client.IO
import io.socket.client.Socket
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatSocketIoClient"

@Singleton
class ChatSocketIoClient @Inject constructor() {

    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _incomingFrames = MutableSharedFlow<ServerFrame>(extraBufferCapacity = 64)
    val incomingFrames: SharedFlow<ServerFrame> = _incomingFrames.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var socket: Socket? = null
    private var currentConversationId: String? = null
    private var currentToken: String? = null

    // Reconnection state
    private var reconnectionJob: Job? = null
    private var reconnectAttempt = 0
    private var isManualDisconnect = false

    // Typing indicator debounce manager
    val typingIndicatorManager = TypingIndicatorManager(
        scope = scope,
        sendTyping = { isTyping -> sendTyping(isTyping) }
    )

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    fun connect(conversationId: String, firebaseToken: String) {
        if (currentConversationId == conversationId &&
            (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING)) {
            return
        }

        disconnect()

        currentConversationId = conversationId
        currentToken = firebaseToken
        isManualDisconnect = false

        _connectionState.value = ConnectionState.CONNECTING

        try {
            val options = IO.Options.builder()
                .setQuery("conversationId=$conversationId&token=$firebaseToken")
                .setTransports(arrayOf(io.socket.engineio.client.transports.WebSocket.NAME))
                .build()

            socket = IO.socket(ChatApiClient.WS_BASE_URL, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Timber.i("Socket.IO connected")
                _connectionState.value = ConnectionState.CONNECTED
                resetReconnectionState()
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Timber.i("Socket.IO disconnected")
                _connectionState.value = ConnectionState.DISCONNECTED
                if (!isManualDisconnect) {
                    startReconnection()
                }
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket.IO connect error: ${args.firstOrNull()}")
                _connectionState.value = ConnectionState.ERROR
                if (!isManualDisconnect) {
                    startReconnection()
                }
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

            socket?.on("reaction_update") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.ReactionUpdate>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (reaction_update): ${e.message}")
                }
            }

            socket?.on("read_receipt") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.ReadReceipt>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (read_receipt): ${e.message}")
                }
            }

            socket?.on("presence") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.Presence>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (presence): ${e.message}")
                }
            }

            socket?.on("location_update") { args ->
                val data = args[0] as? JSONObject ?: return@on
                try {
                    val frame = json.decodeFromString<ServerFrame.LocationUpdate>(data.toString())
                    scope.launch { _incomingFrames.emit(frame) }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (location_update): ${e.message}")
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
        isManualDisconnect = true
        reconnectionJob?.cancel()
        reconnectionJob = null
        resetReconnectionState()
        typingIndicatorManager.reset()
        socket?.disconnect()
        socket?.off()
        socket = null
        currentConversationId = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendText(
        text: String,
        tempId: String,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToSenderName: String? = null,
        mentionedUserIds: List<String> = emptyList()
    ) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = JSONObject().apply {
            put("tempId", tempId)
            put("text", text)
            if (replyToId != null) put("replyToId", replyToId)
            if (replyToText != null) put("replyToText", replyToText)
            if (replyToSenderName != null) put("replyToSenderName", replyToSenderName)
            // Carry @mention metadata so the server can route a separate
            // notification type for mentioned users — bypasses mute lists,
            // and lights up a different channel on the client.
            if (mentionedUserIds.isNotEmpty()) {
                val arr = org.json.JSONArray()
                mentionedUserIds.forEach { arr.put(it) }
                put("mentionedUserIds", arr)
            }
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

    suspend fun sendImage(imageUrl: String, tempId: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = JSONObject().apply {
            put("tempId", tempId)
            put("imageUrl", imageUrl)
        }
        socket?.emit("image_message", payload)
    }

    suspend fun sendVoiceMessage(conversationId: String, tempId: String, voiceUrl: String, durationMs: Long) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = JSONObject().apply {
            put("tempId", tempId)
            put("voiceUrl", voiceUrl)
            put("durationMs", durationMs)
        }
        socket?.emit("voice_message", payload)
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

    suspend fun sendReaction(messageId: String, emoji: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("emoji", emoji)
        }
        socket?.emit("reaction", payload)
    }

    suspend fun removeReaction(messageId: String, emoji: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("emoji", emoji)
        }
        socket?.emit("remove_reaction", payload)
    }

    /**
     * Emits a location_update event via Socket.IO for real-time peer location sharing.
     * Called by LocationRepository on each GPS fix (throttled by speed state).
     * Fire-and-forget: failures are logged by the caller.
     */
    fun emitLocationUpdate(payload: JSONObject) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        socket?.emit("location_update", payload)
    }

    // --- Reconnection Logic ---

    /**
     * Calculates the backoff delay for a given attempt number.
     * Follows exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s -> capped at 30s.
     */
    internal fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = INITIAL_BACKOFF_MS * (1L shl attempt.coerceAtMost(30))
        return exponentialDelay.coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun startReconnection() {
        // Don't start if already reconnecting or if manually disconnected
        if (reconnectionJob?.isActive == true) return
        if (isManualDisconnect) return

        reconnectionJob = scope.launch {
            while (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                val delayMs = calculateBackoffDelay(reconnectAttempt)
                Timber.i("Reconnection attempt ${reconnectAttempt + 1}/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")

                delay(delayMs)

                // Check if we were manually disconnected during the delay
                if (isManualDisconnect) return@launch

                reconnectAttempt++
                _connectionState.value = ConnectionState.CONNECTING

                attemptReconnect()

                // If connected successfully, the EVENT_CONNECT handler will reset state
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    return@launch
                }
            }

            // Exhausted all attempts - stop automatic reconnection
            Log.w(TAG, "Exhausted all $MAX_RECONNECT_ATTEMPTS reconnection attempts")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun attemptReconnect() {
        val convId = currentConversationId ?: return
        val token = currentToken ?: return

        try {
            // Disconnect existing socket without triggering reconnection
            socket?.disconnect()
            socket?.off()
            socket = null

            val options = IO.Options.builder()
                .setQuery("conversationId=$convId&token=$token")
                .setTransports(arrayOf(io.socket.engineio.client.transports.WebSocket.NAME))
                .build()

            socket = IO.socket(ChatApiClient.WS_BASE_URL, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Timber.i("Socket.IO reconnected")
                _connectionState.value = ConnectionState.CONNECTED
                resetReconnectionState()
                reconnectionJob?.cancel()
                reconnectionJob = null
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Timber.w("Socket.IO disconnected during reconnection")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket.IO reconnect error: ${args.firstOrNull()}")
                _connectionState.value = ConnectionState.ERROR
            }

            // Re-register all event listeners
            registerEventListeners()

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Reconnection attempt failed: ${e.message}")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun registerEventListeners() {
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

        socket?.on("reaction_update") { args ->
            val data = args[0] as? JSONObject ?: return@on
            try {
                val frame = json.decodeFromString<ServerFrame.ReactionUpdate>(data.toString())
                scope.launch { _incomingFrames.emit(frame) }
            } catch (e: Exception) {
                Log.e(TAG, "Parse error (reaction_update): ${e.message}")
            }
        }

        socket?.on("read_receipt") { args ->
            val data = args[0] as? JSONObject ?: return@on
            try {
                val frame = json.decodeFromString<ServerFrame.ReadReceipt>(data.toString())
                scope.launch { _incomingFrames.emit(frame) }
            } catch (e: Exception) {
                Log.e(TAG, "Parse error (read_receipt): ${e.message}")
            }
        }

        socket?.on("presence") { args ->
            val data = args[0] as? JSONObject ?: return@on
            try {
                val frame = json.decodeFromString<ServerFrame.Presence>(data.toString())
                scope.launch { _incomingFrames.emit(frame) }
            } catch (e: Exception) {
                Log.e(TAG, "Parse error (presence): ${e.message}")
            }
        }

        socket?.on("location_update") { args ->
            val data = args[0] as? JSONObject ?: return@on
            try {
                val frame = json.decodeFromString<ServerFrame.LocationUpdate>(data.toString())
                scope.launch { _incomingFrames.emit(frame) }
            } catch (e: Exception) {
                Log.e(TAG, "Parse error (location_update): ${e.message}")
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
    }

    private fun resetReconnectionState() {
        reconnectAttempt = 0
    }

    /**
     * Manually trigger a reconnection attempt. Used when the user taps "Retry"
     * after automatic reconnection has been exhausted.
     */
    fun manualReconnect() {
        val convId = currentConversationId ?: return
        val token = currentToken ?: return
        resetReconnectionState()
        isManualDisconnect = false
        reconnectionJob?.cancel()
        reconnectionJob = null
        connect(convId, token)
    }

    // ─── Lifecycle Management (Requirement 21) ────────────────────────────────

    /**
     * Called on user logout. Cancels the SupervisorJob-based CoroutineScope,
     * disconnects the socket, cancels any active reconnection job, and resets
     * the TypingIndicatorManager. Leaves zero active coroutines and zero open
     * socket connections owned by this client instance.
     *
     * After calling this method, the scope is recreated so the client can be
     * reused after re-authentication.
     *
     * Requirement 21.2: On logout, cancel scope, disconnect, cancel reconnection,
     * reset TypingIndicatorManager.
     */
    fun logout() {
        Timber.i("ChatSocketIoClient logout: cancelling scope and disconnecting")
        // Cancel all active coroutines in the scope (including reconnection job)
        supervisorJob.cancel()
        reconnectionJob = null

        // Disconnect socket and release reference
        isManualDisconnect = true
        typingIndicatorManager.reset()
        socket?.disconnect()
        socket?.off()
        socket = null
        currentConversationId = null
        currentToken = null
        _connectionState.value = ConnectionState.DISCONNECTED

        // Recreate scope for potential reuse after re-authentication
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    }

    /**
     * Called when the Application process is destroyed. Cancels the SupervisorJob-based
     * CoroutineScope and releases the socket reference, ensuring no background threads
     * or socket connections persist beyond process termination.
     *
     * Requirement 21.3: On process destroy, cancel scope and release socket reference.
     */
    fun onProcessDestroy() {
        Timber.i("ChatSocketIoClient onProcessDestroy: releasing all resources")
        supervisorJob.cancel()
        reconnectionJob = null
        typingIndicatorManager.reset()
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Disconnects the socket for background transition. Does not cancel the scope
     * so that the client can reconnect when the app returns to foreground.
     * Sets isManualDisconnect to prevent automatic reconnection.
     *
     * Requirement 21.4: On background (onStop, !isChangingConfigurations),
     * disconnect socket within 5s.
     */
    fun disconnectForBackground() {
        Timber.i("ChatSocketIoClient disconnectForBackground: disconnecting socket")
        isManualDisconnect = true
        reconnectionJob?.cancel()
        reconnectionJob = null
        typingIndicatorManager.reset()
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Reconnects the socket after returning from background. Attempts to reach
     * CONNECTED state within [FOREGROUND_RECONNECT_TIMEOUT_MS]. If the connection
     * is not established within that time, continues with exponential backoff
     * without crashing or leaving the connection in an indeterminate state.
     *
     * Requirement 21.5: On foreground (onStart), reconnect socket within 2s.
     * Requirement 21.6: If reconnection doesn't reach CONNECTED within 2s,
     * continue with exponential backoff.
     */
    fun reconnectFromForeground() {
        val convId = currentConversationId ?: return
        val token = currentToken ?: return

        Timber.i("ChatSocketIoClient reconnectFromForeground: attempting reconnection")
        isManualDisconnect = false
        resetReconnectionState()

        // Attempt connection — if it doesn't reach CONNECTED within 2s,
        // the normal reconnection logic (exponential backoff) will handle it
        // via the EVENT_CONNECT_ERROR and EVENT_DISCONNECT handlers.
        connect(convId, token)

        // Start a timeout job: if not connected within 2s, ensure backoff continues
        scope.launch {
            delay(FOREGROUND_RECONNECT_TIMEOUT_MS)
            if (_connectionState.value != ConnectionState.CONNECTED) {
                Timber.i("Foreground reconnect did not reach CONNECTED within ${FOREGROUND_RECONNECT_TIMEOUT_MS}ms, continuing with backoff")
                // The reconnection logic is already running via startReconnection()
                // triggered by EVENT_CONNECT_ERROR or EVENT_DISCONNECT handlers.
                // If it hasn't started yet (e.g., connect() is still pending), start it.
                if (reconnectionJob == null || reconnectionJob?.isActive != true) {
                    startReconnection()
                }
            }
        }
    }

    companion object {
        internal const val INITIAL_BACKOFF_MS = 1000L
        internal const val MAX_BACKOFF_MS = 30_000L
        internal const val MAX_RECONNECT_ATTEMPTS = 10
        /** Timeout for foreground reconnection before falling back to backoff (Requirement 21.6). */
        internal const val FOREGROUND_RECONNECT_TIMEOUT_MS = 2000L
    }
}
