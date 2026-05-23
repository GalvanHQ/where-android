package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.InteractionType
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.repository.InteractionRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.friend.AcceptFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.BlockUserUseCase
import com.ovi.where.domain.usecase.friend.CancelFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.DeclineFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.GetFriendshipStatusUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.UnblockUserUseCase
import com.ovi.where.presentation.model.OtherUserProfileUiModel
import com.ovi.where.presentation.model.ProfileFriendshipAction
import com.ovi.where.presentation.model.toOtherProfileUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the User Profile screen.
 *
 * Handles all friendship actions (send, cancel, accept, decline, unfriend,
 * block, unblock) with optimistic UI updates and error-code to snackbar mapping
 * per design §7.
 *
 * Injects [FirebaseAuth] to provide the caller uid for the
 * [ProfileFriendshipAction] mapper (needed to distinguish Blocked vs BlockedByThem
 * and RequestSent vs RequestReceived).
 */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val friendshipRepository: FriendshipRepository,
    private val interactionRepository: InteractionRepository,
    private val getFriendshipStatusUseCase: GetFriendshipStatusUseCase,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val cancelFriendRequestUseCase: CancelFriendRequestUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val acceptFriendRequestUseCase: AcceptFriendRequestUseCase,
    private val declineFriendRequestUseCase: DeclineFriendRequestUseCase,
    private val blockUserUseCase: BlockUserUseCase,
    private val unblockUserUseCase: UnblockUserUseCase,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val firebaseAuth: FirebaseAuth,
    private val locationRepository: com.ovi.where.domain.repository.LocationRepository,
    private val systemMessageWriter: com.ovi.where.data.repository.SystemMessageWriter,
    private val closeFriendsRepository: com.ovi.where.data.repository.CloseFriendsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _navigateToChat = MutableStateFlow<String?>(null)
    val navigateToChat: StateFlow<String?> = _navigateToChat.asStateFlow()

    /** One-shot snackbar events for error/success messages (design §7). */
    private val _snackbarEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    /** Ensures profile view interaction is recorded only once per ViewModel instance. */
    private var hasRecordedProfileView = false

    private val callerUid: String?
        get() = firebaseAuth.currentUser?.uid

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, notFound = false)
            when (val result = userRepository.getUser(userId)) {
                is Resource.Success -> {
                    val user = result.data
                    if (user == null) {
                        _uiState.value = _uiState.value.copy(isLoading = false, notFound = true)
                        return@launch
                    }
                    // Get the full friendship object to determine direction/blocker
                    val friendship = friendshipRepository.getFriendship(userId)
                    val uid = callerUid ?: ""
                    _uiState.value = _uiState.value.copy(
                        profile = user.toOtherProfileUiModel(
                            status = friendship?.status,
                            callerUid = uid,
                            requesterId = friendship?.requesterId
                        ),
                        isLoading = false
                    )
                    // Record profile view interaction once per ViewModel instance
                    if (!hasRecordedProfileView) {
                        hasRecordedProfileView = true
                        viewModelScope.launch {
                            interactionRepository.recordInteraction(
                                userId = userId,
                                displayName = user.displayName,
                                photoUrl = user.photoUrl,
                                type = InteractionType.PROFILE_VIEWED
                            )
                        }
                    }
                    // Resolve close-friend flag in parallel — the result
                    // populates the star toggle in the action row.
                    refreshCloseFriendStatus(userId)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
                else -> {}
            }
        }
    }

    fun sendFriendRequest(userId: String) {
        // Optimistic update to RequestSent
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile?.copy(
                friendshipAction = ProfileFriendshipAction.RequestSent
            )
        )
        viewModelScope.launch {
            val result = sendFriendRequestUseCase(userId)
            handleActionResult(result, userId, ProfileFriendshipAction.AddFriend)
        }
    }

    fun cancelFriendRequest(userId: String) {
        // Optimistic update to AddFriend
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile?.copy(
                friendshipAction = ProfileFriendshipAction.AddFriend
            )
        )
        viewModelScope.launch {
            val result = cancelFriendRequestUseCase(userId)
            handleActionResult(result, userId, ProfileFriendshipAction.RequestSent)
        }
    }

    fun acceptFriendRequest(userId: String) {
        // Optimistic update to AlreadyFriends
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile?.copy(
                friendshipAction = ProfileFriendshipAction.AlreadyFriends
            )
        )
        viewModelScope.launch {
            val result = acceptFriendRequestUseCase(userId)
            handleActionResult(result, userId, ProfileFriendshipAction.RequestReceived)
        }
    }

    fun declineFriendRequest(userId: String) {
        // Optimistic update to AddFriend
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile?.copy(
                friendshipAction = ProfileFriendshipAction.AddFriend
            )
        )
        viewModelScope.launch {
            val result = declineFriendRequestUseCase(userId)
            handleActionResult(result, userId, ProfileFriendshipAction.RequestReceived)
        }
    }

    fun removeFriend(userId: String) {
        // Optimistic update to AddFriend
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile?.copy(
                friendshipAction = ProfileFriendshipAction.AddFriend
            )
        )
        viewModelScope.launch {
            val result = removeFriendUseCase(userId)
            handleActionResult(result, userId, ProfileFriendshipAction.AlreadyFriends)
        }
    }

    fun blockUser(userId: String) {
        // Optimistic update to Blocked
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile?.copy(
                friendshipAction = ProfileFriendshipAction.Blocked
            )
        )
        viewModelScope.launch {
            val result = blockUserUseCase(userId)
            handleActionResult(result, userId, _uiState.value.profile?.friendshipAction ?: ProfileFriendshipAction.AddFriend)

            // On success, drop a USER_BLOCKED system message into the DM
            // timeline. The repo filters this out for non-target viewers, so
            // only the blocked user ever sees it. Keeps the DM honest about
            // what happened, exactly like Messenger.
            if (result is Resource.Success) {
                val convResult = getOrCreateDirectConversationUseCase(userId)
                val convId = (convResult as? Resource.Success)?.data?.id
                if (convId != null) {
                    val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                    systemMessageWriter.writeSystemMessage(
                        conversationId = convId,
                        eventType = com.ovi.where.domain.model.SystemEventType.USER_BLOCKED,
                        targetUserId = userId,
                        fallbackText = "$actor blocked you"
                    )
                }
            }
        }
    }

    fun unblockUser(userId: String) {
        // Optimistic update to AddFriend
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile?.copy(
                friendshipAction = ProfileFriendshipAction.AddFriend
            )
        )
        viewModelScope.launch {
            val result = unblockUserUseCase(userId)
            handleActionResult(result, userId, ProfileFriendshipAction.Blocked)
        }
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
                    mapErrorToSnackbar(result.message)
                }
                else -> {}
            }
        }
    }

    fun onChatNavigated() {
        _navigateToChat.value = null
    }

    /**
     * Starts sharing live location with this friend (direct share).
     * Uses 1 hour as default duration. The target is "direct:{friendId}".
     * The UI layer is responsible for starting the LocationTrackingService.
     */
    fun startLocationSharingWithFriend(friendId: String) {
        viewModelScope.launch {
            val targetId = "direct:$friendId"
            when (val result = locationRepository.startLocationSharing(listOf(targetId), 60L)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        locationSharingActive = true,
                        locationSharingTargetId = targetId
                    )
                }
                is Resource.Error -> {
                    mapErrorToSnackbar(result.message)
                }
                else -> {}
            }
        }
    }

    fun onRetry(userId: String) {
        loadUser(userId)
    }

    // ─── Error-code to snackbar mapping (design §7) ──────────────────

    /**
     * Handles the result of a friendship action. On error, reverts the optimistic
     * update and maps the error code to a snackbar message.
     *
     * @param result The result from the use case.
     * @param userId The target user id (for refresh on certain errors).
     * @param revertAction The [ProfileFriendshipAction] to revert to on failure.
     */
    private fun handleActionResult(
        result: Resource<Unit>,
        userId: String,
        revertAction: ProfileFriendshipAction
    ) {
        if (result is Resource.Error) {
            val errorMessage = result.message ?: ""
            when {
                errorMessage.contains("UNAVAILABLE", ignoreCase = true) -> {
                    revertOptimistic(revertAction)
                    _snackbarEvent.tryEmit("You're offline")
                }
                errorMessage.contains("DEADLINE_EXCEEDED", ignoreCase = true) -> {
                    revertOptimistic(revertAction)
                    _snackbarEvent.tryEmit("Couldn't complete action, try again")
                }
                errorMessage.contains("ALREADY_EXISTS", ignoreCase = true) ||
                errorMessage.contains("failed-precondition", ignoreCase = true) ||
                errorMessage.contains("FAILED_PRECONDITION", ignoreCase = true) -> {
                    // Silently refresh — the state has changed server-side
                    loadUser(userId)
                }
                errorMessage.contains("NOT_FOUND", ignoreCase = true) -> {
                    _snackbarEvent.tryEmit("Request is no longer available")
                    loadUser(userId)
                }
                errorMessage.contains("PERMISSION_DENIED", ignoreCase = true) -> {
                    revertOptimistic(revertAction)
                    _uiState.value = _uiState.value.copy(
                        error = "Permission denied"
                    )
                }
                else -> {
                    revertOptimistic(revertAction)
                    _snackbarEvent.tryEmit(errorMessage.ifEmpty { "Something went wrong" })
                }
            }
        }
    }

    private fun revertOptimistic(action: ProfileFriendshipAction) {
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile?.copy(friendshipAction = action)
        )
    }

    private fun mapErrorToSnackbar(message: String?) {
        val msg = message ?: "Something went wrong"
        when {
            msg.contains("UNAVAILABLE", ignoreCase = true) ->
                _snackbarEvent.tryEmit("You're offline")
            msg.contains("DEADLINE_EXCEEDED", ignoreCase = true) ->
                _snackbarEvent.tryEmit("Couldn't complete action, try again")
            else ->
                _snackbarEvent.tryEmit(msg)
        }
    }

    /**
     * Refreshes the close-friend flag for the currently-loaded profile.
     * Called from [loadUser] and after [toggleCloseFriend] writes complete.
     */
    fun refreshCloseFriendStatus(userId: String) {
        viewModelScope.launch {
            val isClose = closeFriendsRepository.isCloseFriend(userId)
            _uiState.update { it.copy(isCloseFriend = isClose) }
        }
    }

    /**
     * Adds or removes [userId] from the caller's close-friends set.
     * Optimistic UI: flag flips immediately, then the repo write happens.
     */
    fun toggleCloseFriend(userId: String) {
        val currentlyClose = _uiState.value.isCloseFriend
        _uiState.update { it.copy(isCloseFriend = !currentlyClose) }
        viewModelScope.launch {
            try {
                if (currentlyClose) {
                    closeFriendsRepository.remove(userId)
                } else {
                    closeFriendsRepository.add(userId)
                }
            } catch (e: Exception) {
                // Roll back the optimistic flip and surface a soft error.
                _uiState.update { it.copy(isCloseFriend = currentlyClose) }
                _snackbarEvent.tryEmit("Couldn't update close friends — try again")
            }
        }
    }
}

data class UserProfileUiState(
    val profile: OtherUserProfileUiModel? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val notFound: Boolean = false,
    /** Whether location sharing was just started with this friend. */
    val locationSharingActive: Boolean = false,
    /** The target ID for the active location sharing session (e.g., "direct:friendId"). */
    val locationSharingTargetId: String? = null,
    /** Whether this user is in the caller's "close friends" list — drives the star toggle. */
    val isCloseFriend: Boolean = false
)
