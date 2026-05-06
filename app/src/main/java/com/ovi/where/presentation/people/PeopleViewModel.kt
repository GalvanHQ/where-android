package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendRequestsUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.presentation.model.FriendUiModel
import com.ovi.where.presentation.model.toFriendUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val observeFriendRequestsUseCase: ObserveFriendRequestsUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState: StateFlow<PeopleUiState> = _uiState.asStateFlow()

    private val _navigateToChat = MutableStateFlow<String?>(null)
    val navigateToChat: StateFlow<String?> = _navigateToChat.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        observeFriends()
        observeRequests()
    }

    private fun observeFriends() {
        viewModelScope.launch {
            observeFriendsUseCase().collect { friends ->
                _uiState.value = _uiState.value.copy(
                    friends = friends
                        .map { it.toFriendUiModel() }
                        .sortedBy { it.displayName.lowercase() },
                    isLoading = false
                )
            }
        }
    }

    private fun observeRequests() {
        viewModelScope.launch {
            observeFriendRequestsUseCase().collect { requests ->
                _uiState.value = _uiState.value.copy(pendingRequestCount = requests.size)
            }
        }
    }

    fun removeFriend(userId: String) {
        viewModelScope.launch { removeFriendUseCase(userId) }
    }

    fun openOrCreateDm(userId: String) {
        viewModelScope.launch {
            println("Opening DM for userId: $userId")
            when (val result = getOrCreateDirectConversationUseCase(userId)) {
                is Resource.Success -> {
                    println("Conversation created: ${result.data}")
                    result.data?.id?.let { conversationId ->
                        _navigateToChat.value = conversationId
                    }
                }
                is Resource.Error -> {
                    println("Error: ${result.message}")
                    _error.value = result.message
                }
                else -> { /* Loading */ }
            }
        }
    }

    fun onChatNavigated() {
        _navigateToChat.value = null
    }

    fun clearError() {
        _error.value = null
    }
}

data class PeopleUiState(
    val friends: List<FriendUiModel> = emptyList(),
    val pendingRequestCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)
