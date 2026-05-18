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
    private val messageDao: MessageDao,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""

    private val _uiState = MutableStateFlow(ConversationInfoUiState())
    val uiState: StateFlow<ConversationInfoUiState> = _uiState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    init {
        loadConversationInfo()
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

            // Check mute state
            val isMuted = conversation.mutedBy.contains(uid)

            // Load customization fields
            val themeColor = conversation.themeColor
            val emojiShortcut = conversation.emojiShortcut
            val nicknames = conversation.nicknames

            if (conversation.type == ConversationType.DIRECT && otherUserId != null) {
                // Load other user's profile for DM conversations
                when (val userResult = userRepository.getUser(otherUserId)) {
                    is Resource.Success -> {
                        val user = userResult.data!!
                        val lastActive = formatLastActiveTime(user.lastSeen, user.isOnline)

                        // Load shared media thumbnails
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
                    }
                    is Resource.Error -> {
                        // Fall back to conversation name if user fetch fails
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
                    is Resource.Loading -> {
                        // Keep loading state
                    }
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
     * Updates the theme color for this conversation.
     * @param colorHex Hex color string (e.g., "#5170FF") or null to reset.
     */
    fun updateThemeColor(colorHex: String?) {
        viewModelScope.launch {
            val result = conversationRepository.updateThemeColor(conversationId, colorHex)
            if (result is Resource.Success) {
                _uiState.update { it.copy(themeColor = colorHex) }
            }
        }
    }

    /**
     * Updates the emoji shortcut for this conversation.
     * @param emoji Single emoji character or null to reset.
     */
    fun updateEmojiShortcut(emoji: String?) {
        viewModelScope.launch {
            val result = conversationRepository.updateEmojiShortcut(conversationId, emoji)
            if (result is Resource.Success) {
                _uiState.update { it.copy(emojiShortcut = emoji) }
            }
        }
    }

    /**
     * Updates a nickname for a participant in this conversation.
     * @param userId The user whose nickname to set.
     * @param nickname The new nickname, or empty string to remove.
     */
    fun updateNickname(userId: String, nickname: String) {
        viewModelScope.launch {
            val currentNicknames = _uiState.value.nicknames.toMutableMap()
            if (nickname.isBlank()) {
                currentNicknames.remove(userId)
            } else {
                currentNicknames[userId] = nickname
            }
            val result = conversationRepository.updateNicknames(conversationId, currentNicknames)
            if (result is Resource.Success) {
                _uiState.update { it.copy(nicknames = currentNicknames) }
            }
        }
    }

    companion object {
        private const val SHARED_MEDIA_LIMIT = 6
    }
}
