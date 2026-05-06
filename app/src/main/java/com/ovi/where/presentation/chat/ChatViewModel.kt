package com.ovi.where.presentation.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.location.LocationManager
import com.ovi.where.data.remote.chat.ChatWebSocketClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.data.repository.MessageRepositoryImpl
import com.ovi.where.domain.usecase.chat.MarkConversationReadUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.chat.ObserveMessagesUseCase
import com.ovi.where.domain.usecase.chat.SendLocationMessageUseCase
import com.ovi.where.domain.usecase.chat.SendMessageUseCase
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.MessageUiModel
import com.ovi.where.presentation.model.formatConversationTimestamp
import com.ovi.where.presentation.model.formatMessageDateKey
import com.ovi.where.presentation.model.formatMessageTime
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendLocationMessageUseCase: SendLocationMessageUseCase,
    private val markConversationReadUseCase: MarkConversationReadUseCase,
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val wsClient: ChatWebSocketClient,
    private val messageRepositoryImpl: MessageRepositoryImpl,
    private val firebaseAuth: FirebaseAuth,
    private val locationManager: LocationManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    fun init(conversationId: String) {
        loadConversation(conversationId)
        loadMessages(conversationId)
        connectWebSocket(conversationId)
        observeTyping()
    }

    private fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val uid = currentUserId ?: ""
            observeConversationsUseCase().collect { conversations ->
                val conv = conversations.firstOrNull { it.id == conversationId }
                if (conv != null) {
                    _uiState.value = _uiState.value.copy(
                        conversation = conv.toUiModel(uid, ::formatConversationTimestamp)
                    )
                }
            }
        }
    }

    private fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            messageRepositoryImpl.loadHistory(conversationId)
            val uid = currentUserId ?: ""
            observeMessagesUseCase(conversationId).collect { messages ->
                _uiState.value = _uiState.value.copy(
                    messages = messages.map {
                        it.toUiModel(
                            currentUserId   = uid,
                            timeFormatter   = ::formatMessageTime,
                            dateKeyFormatter = ::formatMessageDateKey
                        )
                    },
                    isLoading = false
                )
            }
        }
    }

    private fun connectWebSocket(conversationId: String) {
        viewModelScope.launch {
            val token = firebaseAuth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
            wsClient.connect(conversationId, token)
            _uiState.value = _uiState.value.copy(conversationId = conversationId)
        }
    }

    private fun observeTyping() {
        viewModelScope.launch {
            wsClient.incomingFrames.collect { frame ->
                if (frame is ServerFrame.UserTyping) {
                    _uiState.value = _uiState.value.copy(
                        typingUserId   = if (frame.isTyping) frame.userId   else null,
                        typingUserName = if (frame.isTyping) frame.userName else null
                    )
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        viewModelScope.launch { wsClient.sendTyping(text.isNotEmpty()) }
    }

    fun sendMessage() {
        val text  = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val convId = _uiState.value.conversationId ?: return
        _uiState.value = _uiState.value.copy(inputText = "", isSending = false)
        viewModelScope.launch { sendMessageUseCase(convId, text) }
    }

    fun sendLocation(latitude: Double, longitude: Double) {
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch { sendLocationMessageUseCase(convId, latitude, longitude) }
    }

    @Suppress("MissingPermission")
    fun requestCurrentLocationAndSend() {
        viewModelScope.launch {
            val location = locationManager.getCurrentLocation()
            if (location != null) {
                sendLocation(location.latitude, location.longitude)
            }
        }
    }

    fun markRead() {
        val convId = _uiState.value.conversationId ?: return
        val uid    = currentUserId ?: return
        viewModelScope.launch {
            markConversationReadUseCase(convId, uid)
            wsClient.sendRead()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.disconnect()
    }
}

data class ChatUiState(
    val conversationId: String? = null,
    val conversation: ConversationUiModel? = null,
    val messages: List<MessageUiModel> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    val typingUserId: String? = null,
    val typingUserName: String? = null
)
