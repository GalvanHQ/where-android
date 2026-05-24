package com.ovi.where.presentation.model

/**
 * UI state for the Conversation Info screen (direct message details).
 * Displays user profile, online status, shared media, and conversation settings.
 */
data class ConversationInfoUiState(
    val conversationTitle: String = "",
    val photoUrl: String? = null,
    val isOnline: Boolean = false,
    val lastActiveTime: String? = null, // "Active Xh ago"
    val otherUserId: String? = null,
    val sharedMedia: List<MediaThumbnail> = emptyList(),
    val isMuted: Boolean = false,
    /**
     * True when the local user has blocked the other party. Drives the
     * "Block" / "Unblock" affordance on the conversation info + chat
     * header surfaces. Updated reactively via FriendshipRepository's
     * blocked-users listener so a block from any other surface
     * (UserProfile, People tab) flows back here within one snapshot.
     */
    val isBlocked: Boolean = false,
    val isLoading: Boolean = true,
    // Customization
    val themeColor: String? = null,
    val emojiShortcut: String? = null,
    val nicknames: Map<String, String> = emptyMap()
)
