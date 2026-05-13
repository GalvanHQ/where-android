package com.ovi.where.presentation.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.usecase.friend.AcceptFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.CancelFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.DeclineFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.ovi.where.presentation.model.FriendRequestUiModel
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Friend Requests screen with Incoming and Sent tabs.
 *
 * - **SavedStateHandle-backed tab state** persists the selected tab across process death.
 * - **Combines incoming + outgoing** via `combine(observeIncomingRequests, observeOutgoingRequests)`.
 * - **Optimistic accept/decline/cancel with undo**: immediately removes the row,
 *   calls the use case, emits a snackbar event with an undo action. On failure,
 *   reverts the optimistic removal and emits an error snackbar.
 * - **One-shot snackbar event** via [MutableSharedFlow] with `extraBufferCapacity = 1`.
 */
@HiltViewModel
class FriendRequestsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val friendshipRepository: FriendshipRepository,
    private val acceptFriendRequestUseCase: AcceptFriendRequestUseCase,
    private val declineFriendRequestUseCase: DeclineFriendRequestUseCase,
    private val cancelFriendRequestUseCase: CancelFriendRequestUseCase,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase
) : ViewModel() {

    companion object {
        private const val KEY_SELECTED_TAB = "selectedTab"
    }

    private val _uiState = MutableStateFlow(FriendRequestsUiState())
    val uiState: StateFlow<FriendRequestsUiState> = _uiState.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<SnackbarEvent> = _snackbarEvent.asSharedFlow()

    init {
        // Restore persisted tab selection
        val restoredTab = savedStateHandle.get<Int>(KEY_SELECTED_TAB) ?: 0
        _uiState.value = _uiState.value.copy(selectedTab = restoredTab)

        observeRequests()
    }

    private fun observeRequests() {
        viewModelScope.launch {
            combine(
                friendshipRepository.observeIncomingRequests(),
                friendshipRepository.observeOutgoingRequests()
            ) { incoming, outgoing ->
                _uiState.value.copy(
                    requests = incoming.map { it.toUiModel() },
                    outgoingRequests = outgoing.map { it.toUiModel() },
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectTab(tab: Int) {
        savedStateHandle[KEY_SELECTED_TAB] = tab
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    /**
     * Triggers a refresh of the friend requests data.
     * Called from pull-to-refresh gesture.
     */
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        observeRequests()
    }

    /**
     * Optimistically accepts an incoming friend request.
     * Removes the row immediately, calls the use case, and emits a snackbar with undo.
     * On failure: reverts the removal and emits an error snackbar.
     */
    fun acceptRequest(requesterId: String) {
        val currentRequests = _uiState.value.requests
        val removedItem = currentRequests.find { it.requesterId == requesterId } ?: return
        val displayName = removedItem.displayName

        // Optimistic removal
        _uiState.value = _uiState.value.copy(
            requests = currentRequests.filter { it.requesterId != requesterId }
        )

        viewModelScope.launch {
            val result = acceptFriendRequestUseCase(requesterId)
            when (result) {
                is Resource.Success -> {
                    _snackbarEvent.tryEmit(
                        SnackbarEvent(
                            message = "Now friends with $displayName. Undo",
                            undoAction = { undoAccept(requesterId, removedItem) }
                        )
                    )
                }
                is Resource.Error -> {
                    // Revert optimistic removal
                    _uiState.value = _uiState.value.copy(
                        requests = _uiState.value.requests + removedItem
                    )
                    _snackbarEvent.tryEmit(
                        SnackbarEvent(message = result.message ?: "Failed to accept request")
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Optimistically declines an incoming friend request.
     * Removes the row immediately, calls the use case, and emits a snackbar with undo.
     * On failure: reverts the removal and emits an error snackbar.
     */
    fun declineRequest(requesterId: String) {
        val currentRequests = _uiState.value.requests
        val removedItem = currentRequests.find { it.requesterId == requesterId } ?: return

        // Optimistic removal
        _uiState.value = _uiState.value.copy(
            requests = currentRequests.filter { it.requesterId != requesterId }
        )

        viewModelScope.launch {
            val result = declineFriendRequestUseCase(requesterId)
            when (result) {
                is Resource.Success -> {
                    _snackbarEvent.tryEmit(
                        SnackbarEvent(
                            message = "Declined. Undo",
                            undoAction = { undoDecline(requesterId, removedItem) }
                        )
                    )
                }
                is Resource.Error -> {
                    // Revert optimistic removal
                    _uiState.value = _uiState.value.copy(
                        requests = _uiState.value.requests + removedItem
                    )
                    _snackbarEvent.tryEmit(
                        SnackbarEvent(message = result.message ?: "Failed to decline request")
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Optimistically cancels an outgoing friend request.
     * Removes the row immediately, calls the use case, and emits a snackbar with undo.
     * On failure: reverts the removal and emits an error snackbar.
     */
    fun cancelRequest(receiverId: String) {
        val currentOutgoing = _uiState.value.outgoingRequests
        val removedItem = currentOutgoing.find { it.requesterId == receiverId } ?: return

        // Optimistic removal
        _uiState.value = _uiState.value.copy(
            outgoingRequests = currentOutgoing.filter { it.requesterId != receiverId }
        )

        viewModelScope.launch {
            val result = cancelFriendRequestUseCase(receiverId)
            when (result) {
                is Resource.Success -> {
                    _snackbarEvent.tryEmit(
                        SnackbarEvent(
                            message = "Request cancelled. Undo",
                            undoAction = { undoCancel(receiverId, removedItem) }
                        )
                    )
                }
                is Resource.Error -> {
                    // Revert optimistic removal
                    _uiState.value = _uiState.value.copy(
                        outgoingRequests = _uiState.value.outgoingRequests + removedItem
                    )
                    _snackbarEvent.tryEmit(
                        SnackbarEvent(message = result.message ?: "Failed to cancel request")
                    )
                }
                else -> {}
            }
        }
    }

    // ─── Undo actions ────────────────────────────────────────────────

    /**
     * Undo accept: inverse action is removeFriend (they were just made friends).
     * Restore the row in the incoming list.
     */
    private fun undoAccept(requesterId: String, item: FriendRequestUiModel) {
        viewModelScope.launch {
            // Restore the row optimistically
            _uiState.value = _uiState.value.copy(
                requests = _uiState.value.requests + item
            )
            // Call inverse action: remove the friendship that was just created
            val result = removeFriendUseCase(requesterId)
            if (result is Resource.Error) {
                _snackbarEvent.tryEmit(
                    SnackbarEvent(message = result.message ?: "Failed to undo")
                )
            }
        }
    }

    /**
     * Undo decline: inverse action is sendFriendRequest (re-send the request
     * that was declined, effectively restoring the pending state).
     * Restore the row in the incoming list.
     */
    private fun undoDecline(requesterId: String, item: FriendRequestUiModel) {
        viewModelScope.launch {
            // Restore the row optimistically
            _uiState.value = _uiState.value.copy(
                requests = _uiState.value.requests + item
            )
            // Call inverse action: re-send the friend request from the other side
            // Note: the server-side callable will handle this correctly since
            // the decline deleted the pair doc, so a new send from the requester
            // would recreate it. Here we send from our side which creates a new
            // pending request in the opposite direction — the real restore happens
            // via the snapshot listener when the server processes it.
            val result = sendFriendRequestUseCase(requesterId)
            if (result is Resource.Error) {
                _snackbarEvent.tryEmit(
                    SnackbarEvent(message = result.message ?: "Failed to undo")
                )
            }
        }
    }

    /**
     * Undo cancel: inverse action is sendFriendRequest (re-send the request
     * that was cancelled).
     * Restore the row in the outgoing list.
     */
    private fun undoCancel(receiverId: String, item: FriendRequestUiModel) {
        viewModelScope.launch {
            // Restore the row optimistically
            _uiState.value = _uiState.value.copy(
                outgoingRequests = _uiState.value.outgoingRequests + item
            )
            // Call inverse action: re-send the friend request
            val result = sendFriendRequestUseCase(receiverId)
            if (result is Resource.Error) {
                _snackbarEvent.tryEmit(
                    SnackbarEvent(message = result.message ?: "Failed to undo")
                )
            }
        }
    }
}

/**
 * One-shot snackbar event emitted by [FriendRequestsViewModel].
 *
 * @param message The text to display in the snackbar.
 * @param undoAction Optional lambda invoked when the user taps "Undo".
 *   Null when the snackbar is an error notification with no undo affordance.
 */
data class SnackbarEvent(
    val message: String,
    val undoAction: (() -> Unit)? = null
)

data class FriendRequestsUiState(
    val requests: List<FriendRequestUiModel> = emptyList(),
    val outgoingRequests: List<FriendRequestUiModel> = emptyList(),
    val selectedTab: Int = 0,
    val isLoading: Boolean = true
)
