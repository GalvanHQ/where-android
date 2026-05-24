package com.ovi.where.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.presentation.model.ConversationInfoUiState
import com.ovi.where.presentation.model.MediaThumbnail
import com.ovi.where.presentation.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Conversation Info screen (direct message details).
 *
 * Loads conversation details, other user's online status, last active time,
 * shared media thumbnails, and mute state. Exposes UI state as a StateFlow
 * and supports retry on error.
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8
 */
@HiltViewModel
class ConversationInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val friendshipRepository: com.ovi.where.domain.repository.FriendshipRepository,
    private val messageDao: MessageDao,
    private val firebaseAuth: FirebaseAuth,
    private val userCache: com.ovi.where.data.cache.UserCache,
) : ViewModel() {

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""

    private val _uiState = MutableStateFlow(ConversationInfoUiState())
    val uiState: StateFlow<ConversationInfoUiState> = _uiState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    init {
        loadConversationInfo()
        observeBlockedState()
    }

    /**
     * Observes the local user's `users/{uid}/blocks` subcollection so
     * the Block / Unblock affordance flips reactively whenever this
     * conversation's other party is added to or removed from the block
     * list — even when the change originated from another surface like
     * the People tab or User Profile screen.
     *
     * Implementation note: we **do not** read `_uiState.value.otherUserId`
     * inside the collect block. That uid is resolved asynchronously by
     * [loadConversationDetails], so on init we'd race the conversation
     * load and compute `isBlocked = false` against a still-`null`
     * otherUserId — and never re-evaluate. Instead we cache every
     * snapshot's blocked-uid set on the VM and recompute the flag
     * whenever either side changes (the block listener emits a new set
     * OR loadConversationDetails resolves the otherUserId).
     */
    @Volatile
    private var lastBlockedUids: Set<String> = emptySet()

    private fun observeBlockedState() {
        viewModelScope.launch {
            friendshipRepository.observeBlockedUsers()
                .map { blocks -> blocks.map { it.blockedUid }.toSet() }
                .distinctUntilChanged()
                .collect { uids ->
                    lastBlockedUids = uids
                    val otherUid = _uiState.value.otherUserId
                    val isBlocked = otherUid != null && otherUid in uids
                    if (_uiState.value.isBlocked != isBlocked) {
                        _uiState.update { it.copy(isBlocked = isBlocked) }
                    }
                }
        }
    }

    /**
     * Recomputes the blocked flag against the cached uid set. Called
     * from [loadConversationDetails] every time a fresh `otherUserId`
     * is resolved so the very first conversation snapshot picks up the
     * already-known block state instead of waiting for the next
     * blocked-users emission.
     */
    private fun recomputeIsBlocked(otherUid: String?) {
        val isBlocked = otherUid != null && otherUid in lastBlockedUids
        if (_uiState.value.isBlocked != isBlocked) {
            _uiState.update { it.copy(isBlocked = isBlocked) }
        }
    }

    /**
     * Loads all conversation info data: conversation details, user profile,
     * online status, last active time, shared media, and mute state.
     */
    fun loadConversationInfo() {
        if (conversationId.isBlank()) {
            _errorState.value = "Conversation not found"
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        _errorState.value = null

        viewModelScope.launch {
            try {
                loadConversationDetails()
            } catch (e: Exception) {
                _errorState.value = e.message ?: "Failed to load conversation info"
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Retry loading conversation info after an error.
     */
    fun retry() {
        loadConversationInfo()
    }

    private suspend fun loadConversationDetails() {
        val uid = currentUserId ?: run {
            _errorState.value = "User not authenticated"
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        // Observe conversation to get participant info and mute state
        conversationRepository.observeConversation(conversationId).collect { conversation ->
            if (conversation == null) {
                _errorState.value = "Conversation not found"
                _uiState.update { it.copy(isLoading = false) }
                return@collect
            }

            // Determine the other user's ID in a direct message
            val otherUserId = conversation.participantIds.firstOrNull { it != uid }

            // Recompute blocked state now that the otherUid is known.
            // observeBlockedState() may have already received the latest
            // blocks snapshot before this conversation load completed —
            // pull from the cached set so the UI flips Block/Unblock
            // immediately on screen open.
            recomputeIsBlocked(otherUserId)

            // Check mute state
            val isMuted = conversation.mutedBy.contains(uid)

            // Load customization fields
            val themeColor = conversation.themeColor
            val emojiShortcut = conversation.emojiShortcut
            val nicknames = conversation.nicknames

            if (conversation.type == ConversationType.DIRECT && otherUserId != null) {
                // Load other user's profile via the persistent UserCache so
                // names + avatars survive ViewModel recreation. Try cache
                // first (instant + offline-friendly), then warm if missing.
                val cached = userCache.getCached(otherUserId)
                val effectiveUser = if (cached != null) {
                    // Refresh in background so the UI gets fresh data once
                    // Firestore catches up. UserRepositoryImpl warm-writes
                    // back into the cache; we just don't await it here.
                    viewModelScope.launch { userCache.warmUp(otherUserId) }
                    cached
                } else {
                    userCache.warmUp(otherUserId)
                    userCache.getCached(otherUserId)
                }

                if (effectiveUser != null) {
                    val user = effectiveUser
                    val lastActive = formatLastActiveTime(user.lastSeen, user.isOnline)
                    val sharedMedia = loadSharedMedia()
                    _uiState.update {
                        it.copy(
                            conversationTitle = user.displayName.ifBlank { "Unknown User" },
                            photoUrl = user.photoUrl,
                            isOnline = user.isOnline,
                            lastActiveTime = lastActive,
                            otherUserId = otherUserId,
                            sharedMedia = sharedMedia,
                            isMuted = isMuted,
                            themeColor = themeColor,
                            emojiShortcut = emojiShortcut,
                            nicknames = nicknames,
                            isLoading = false
                        )
                    }
                    _errorState.value = null
                } else {
                    // Fall back to conversation name if user fetch failed.
                    val sharedMedia = loadSharedMedia()
                    _uiState.update {
                        it.copy(
                            conversationTitle = conversation.name.ifBlank { "Unknown User" },
                            photoUrl = conversation.photoUrl,
                            isOnline = false,
                            lastActiveTime = null,
                            otherUserId = otherUserId,
                            sharedMedia = sharedMedia,
                            isMuted = isMuted,
                            themeColor = themeColor,
                            emojiShortcut = emojiShortcut,
                            nicknames = nicknames,
                            isLoading = false
                        )
                    }
                    _errorState.value = null
                }
            } else {
                // Group conversation fallback (shouldn't normally reach here,
                // but handle gracefully)
                val sharedMedia = loadSharedMedia()
                _uiState.update {
                    it.copy(
                        conversationTitle = conversation.name.ifBlank { "Unnamed Group" },
                        photoUrl = conversation.photoUrl,
                        isOnline = false,
                        lastActiveTime = null,
                        sharedMedia = sharedMedia,
                        isMuted = isMuted,
                        themeColor = themeColor,
                        emojiShortcut = emojiShortcut,
                        nicknames = nicknames,
                        isLoading = false
                    )
                }
                _errorState.value = null
            }
        }
    }

    /**
     * Loads the most recent shared media thumbnails for the conversation.
     * Returns up to 6 items for the horizontal scrollable preview row.
     */
    private suspend fun loadSharedMedia(): List<MediaThumbnail> {
        return try {
            val mediaEntities = messageDao.getMediaMessages(conversationId, SHARED_MEDIA_LIMIT, 0)
            mediaEntities.map { entity ->
                MediaThumbnail(
                    id = entity.id,
                    thumbnailUrl = entity.thumbnailUrl ?: entity.imageUrl ?: "",
                    type = when (entity.type.uppercase()) {
                        "VIDEO" -> MediaType.VIDEO
                        else -> MediaType.IMAGE
                    }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Formats the last active time for display.
     * Returns "Active now" if online, "Active Xh ago" based on lastSeen timestamp,
     * or null if the time cannot be determined.
     */
    private fun formatLastActiveTime(lastSeen: Long, isOnline: Boolean): String? {
        if (isOnline) return "Active now"
        if (lastSeen <= 0L) return null

        val now = System.currentTimeMillis()
        val diffMs = now - lastSeen
        val diffMinutes = diffMs / (1000 * 60)
        val diffHours = diffMs / (1000 * 60 * 60)
        val diffDays = diffMs / (1000 * 60 * 60 * 24)

        return when {
            diffMinutes < 1 -> "Active now"
            diffMinutes < 60 -> "Active ${diffMinutes}m ago"
            diffHours < 24 -> "Active ${diffHours}h ago"
            diffDays < 7 -> "Active ${diffDays}d ago"
            else -> null
        }
    }

    /**
     * Toggles the mute state for this conversation.
     */
    fun toggleMute() {
        viewModelScope.launch {
            val currentMuted = _uiState.value.isMuted
            val result = if (currentMuted) {
                conversationRepository.unmuteConversation(conversationId)
            } else {
                conversationRepository.muteConversation(conversationId)
            }
            if (result is Resource.Success) {
                _uiState.update { it.copy(isMuted = !currentMuted) }
            }
        }
    }

    /**
     * Mutes this conversation for a specific [option] duration.
     *
     * Writes the per-user expiry to `conversations/{id}.mutedUntil[uid]`
     * (server respects this when fanning out FCM) and flips the local
     * `isMuted` flag for immediate UI feedback. Mentions still bypass the
     * mute on the server.
     */
    fun muteFor(option: com.ovi.where.domain.model.MuteOption) {
        viewModelScope.launch {
            val result = conversationRepository.muteConversationFor(conversationId, option)
            if (result is Resource.Success) {
                _uiState.update { it.copy(isMuted = true) }
            }
        }
    }

    /**
     * Blocks the other user in this DM.
     *
     * The optimistic UI flip happens immediately so the action feels
     * responsive. Server-side, `FriendshipRepository.blockUser` calls the
     * Cloud Function which writes to `users/{uid}/blocks/{blockedUid}`,
     * which our `observeBlockedUsers` subscription will pick up and
     * confirm. On failure we revert and surface the message.
     *
     * Caller is expected to feed a snackbar event from this VM's
     * existing error path.
     */
    fun blockUser() {
        val otherUid = _uiState.value.otherUserId ?: return
        if (_uiState.value.isBlocked) return
        // Optimistic
        _uiState.update { it.copy(isBlocked = true) }
        viewModelScope.launch {
            val result = friendshipRepository.blockUser(otherUid)
            if (result is Resource.Error) {
                _uiState.update { it.copy(isBlocked = false) }
                _errorState.value = result.message ?: "Failed to block user"
            }
        }
    }

    /**
     * Unblocks the other user in this DM. Symmetric to [blockUser] —
     * optimistic flip, server confirms, revert on failure.
     */
    fun unblockUser() {
        val otherUid = _uiState.value.otherUserId ?: return
        if (!_uiState.value.isBlocked) return
        _uiState.update { it.copy(isBlocked = false) }
        viewModelScope.launch {
            val result = friendshipRepository.unblockUser(otherUid)
            if (result is Resource.Error) {
                _uiState.update { it.copy(isBlocked = true) }
                _errorState.value = result.message ?: "Failed to unblock user"
            }
        }
    }

    /**
     * Updates the theme color for this conversation.
     * Writes to Room first (SSOT), then Firestore. The Room Flow auto-updates the UI.
     */
    fun updateThemeColor(colorHex: String?) {
        viewModelScope.launch {
            conversationRepository.updateThemeColor(conversationId, colorHex)
        }
    }

    /**
     * Updates the emoji shortcut for this conversation.
     * Writes to Room first (SSOT), then Firestore. The Room Flow auto-updates the UI.
     */
    fun updateEmojiShortcut(emoji: String?) {
        viewModelScope.launch {
            conversationRepository.updateEmojiShortcut(conversationId, emoji)
        }
    }

    /**
     * Updates a nickname for a participant in this conversation.
     * Writes to Room first (SSOT), then Firestore. The Room Flow auto-updates the UI.
     */
    fun updateNickname(userId: String, nickname: String) {
        viewModelScope.launch {
            val currentNicknames = _uiState.value.nicknames.toMutableMap()
            if (nickname.isBlank()) {
                currentNicknames.remove(userId)
            } else {
                currentNicknames[userId] = nickname
            }
            conversationRepository.updateNicknames(conversationId, currentNicknames)
        }
    }

    companion object {
        private const val SHARED_MEDIA_LIMIT = 6
    }
}
