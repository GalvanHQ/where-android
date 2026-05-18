package com.ovi.where.domain.model

data class Conversation(
    val id: String = "",
    val type: ConversationType = ConversationType.DIRECT,
    val participantIds: List<String> = emptyList(),
    val groupId: String? = null,
    val name: String = "",
    val photoUrl: String? = null,
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageType: MessageType = MessageType.TEXT,
    val lastMessageStatus: MessageStatus = MessageStatus.SENT,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val createdAt: Long = 0L,
    // Presence
    val onlineMembers: Set<String> = emptySet(),
    // Typing: userId -> userName
    val typingMembers: Map<String, String> = emptyMap(),
    // Mute/Pin
    val mutedBy: List<String> = emptyList(),
    val pinnedBy: List<String> = emptyList(),
    // Customization
    val themeColor: String? = null,
    val emojiShortcut: String? = null,
    val nicknames: Map<String, String> = emptyMap(),
    // Participant metadata (resolved from user profiles, cached in Room)
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String?> = emptyMap()
)

enum class ConversationType { DIRECT, GROUP }
