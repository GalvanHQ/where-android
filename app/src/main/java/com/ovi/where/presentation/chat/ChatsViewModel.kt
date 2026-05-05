package com.ovi.where.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    init { loadConversations() }

    private fun loadConversations() {
        viewModelScope.launch {
            observeConversationsUseCase().collect { conversations ->
                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = false
                )
            }
        }
    }

    fun getOrCreateDirectChat(otherUserId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = getOrCreateDirectConversationUseCase(otherUserId)) {
                is Resource.Success -> onResult(result.data?.id)
                else -> onResult(null)
            }
        }
    }
}

data class ChatsUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true
)

fun formatConversationTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = Date(timestamp) }
    return when {
        now.get(Calendar.DATE) == msgCal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        now.get(Calendar.DATE) - msgCal.get(Calendar.DATE) == 1 -> "Yesterday"
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}
