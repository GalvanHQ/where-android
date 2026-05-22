package com.ovi.where.presentation.model

/**
 * UI state for the Group Info screen.
 * Displays group details, member list, shared media, and group management actions.
 */
data class GroupInfoUiState(
    val groupName: String = "",
    val groupDescription: String = "",
    val groupPhotoUrl: String? = null,
    val memberCount: Int = 0,
    val members: List<GroupMemberUiModel> = emptyList(),
    val inviteLink: String? = null,
    val isCurrentUserAdmin: Boolean = false,
    val sharedMedia: List<MediaThumbnail> = emptyList(),
    val nicknames: Map<String, String> = emptyMap(),
    val themeColor: String? = null,
    val emojiShortcut: String? = null,
    val isMuted: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)
