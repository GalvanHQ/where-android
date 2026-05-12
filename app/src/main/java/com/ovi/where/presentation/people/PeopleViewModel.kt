package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.friend.ObserveSocialSummaryUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.domain.usecase.friend.BlockUserUseCase
import com.ovi.where.presentation.model.FriendUiModel
import com.ovi.where.presentation.model.toFriendUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PeopleViewModel — design §5.4.
 * Combines `observeFriends` + `observeSocialSummary` into a single UI state.
 * Exposes long-press actions (unfriend, block) and message navigation.
 */
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val observeSocialSummaryUseCase: ObserveSocialSummaryUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val blockUserUseCase: BlockUserUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState: StateFlow<PeopleUiState> = _uiState.asStateFlow()

    private val _navigateToChat = MutableStateFlow<String?>(null)
    val navigateToChat: StateFlow<String?> = _navigateToChat.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                observeFriendsUseCase(),
                observeSocialSummaryUseCase()
            ) { friends, summary ->
                PeopleUiState(
                    friends = friends
                        .map { it.toFriendUiModel() }
                        .sortedBy { it.displayName.lowercase() },
                    pendingRequestCount = summary.pendingIncomingCount,
                    isLoading = false,
                    error = null
                )
            }
                .catch { e ->
                    emit(
                        _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load friends"
                        )
                    )
                }
                .collect { _uiState.value = it }
        }
    }

    fun removeFriend(userId: String) {
        viewModelScope.launch { removeFriendUseCase(userId) }
    }

    fun blockUser(userId: String) {
        viewModelScope.launch { blockUserUseCase(userId) }
    }

    fun openOrCreateDm(userId: String) {
        viewModelScope.launch {
            when (val result = getOrCreateDirectConversationUseCase(userId)) {
                is Resource.Success -> {
                    result.data?.id?.let { conversationId ->
                        _navigateToChat.value = conversationId
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> { /* Loading */ }
            }
        }
    }

    fun onRetry() {
        _uiState.value = PeopleUiState() // reset to loading
        observeData()
    }

    fun onChatNavigated() {
        _navigateToChat.value = null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class PeopleUiState(
    val friends: List<FriendUiModel> = emptyList(),
    val pendingRequestCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)
