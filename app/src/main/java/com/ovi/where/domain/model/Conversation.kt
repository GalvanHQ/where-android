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
    val unreadCounts: Map<String, Int> = emptyMap(),
    val createdAt: Long = 0L,
    // Presence
    val onlineMembers: Set<String> = emptySet(),
    // Typing: userId -> userName
    val typingMembers: Map<String, String> = emptyMap(),
    // Mute/Pin
    val mutedBy: List<String> = emptyList(),
    val pinnedBy: List<String> = emptyList()
)

enum class ConversationType { DIRECT, GROUP }
