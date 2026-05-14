package com.ovi.where.domain.model

data class Interaction(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val type: InteractionType,
    val timestamp: Long,
    val isOnline: Boolean = false
)

enum class InteractionType {
    MESSAGE_SENT,
    PROFILE_VIEWED
}
