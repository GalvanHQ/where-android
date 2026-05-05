package com.ovi.where.domain.model

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val type: MessageType = MessageType.TEXT,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = 0L,
    val readBy: List<String> = emptyList()
)

enum class MessageType { TEXT, LOCATION }
