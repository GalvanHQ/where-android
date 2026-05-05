package com.ovi.where.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.formatConversationTimestamp
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    private val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    init { loadConversations() }

    private fun loadConversations() {
        viewModelScope.launch {
            observeConversationsUseCase().collect { conversations ->
                val uid = currentUserId ?: ""
                _uiState.value = _uiState.value.copy(
                    conversations = conversations.map { it.toUiModel(uid, ::formatConversationTimestamp) },
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
    val conversations: List<ConversationUiModel> = emptyList(),
    val isLoading: Boolean = true
)
