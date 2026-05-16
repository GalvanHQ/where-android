package com.ovi.where.domain.model

data class SharedLocation(
    val id: String = "",
    val userId: String = "",
    val groupId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val timestamp: Long = 0L,
    val isSharingActive: Boolean = false,
    val sharingExpiresAt: Long = 0L,
    // Consolidated location fields
    val targetType: String = "group",  // "group" or "direct"
    val targetId: String = "",         // groupId or "direct:friendId"
    val visibleTo: List<String> = emptyList(),
    // Display info for live location bubble rendering
    val displayName: String = "",
    val sharingStartedAt: Long = 0L
)
