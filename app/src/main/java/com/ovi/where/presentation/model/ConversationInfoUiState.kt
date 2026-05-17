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
    val sharedMedia: List<MediaThumbnail> = emptyList(),
    val isMuted: Boolean = false,
    val isLoading: Boolean = true
)
