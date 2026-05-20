package com.ovi.where.domain.model

import com.google.firebase.firestore.PropertyName

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
    val targetType: String = "group",  // "group" or "direct" or "multi"
    val targetId: String = "",         // legacy: single groupId or "direct:friendId"
    val targetIds: List<String> = emptyList(), // multi-recipient: list of target ids
    val visibleTo: List<String> = emptyList(),
    // Display info for live location bubble rendering
    val displayName: String = "",
    val photoUrl: String? = null,
    val sharingStartedAt: Long = 0L
) {
    /**
     * Secondary constructor for Firestore deserialization.
     * Firestore stores all numbers as Double/Long, so Float fields need conversion.
     * This constructor accepts Double for accuracy/speed/bearing and converts to Float.
     */
    @Suppress("unused")
    constructor() : this(
        id = "",
        userId = "",
        groupId = "",
        latitude = 0.0,
        longitude = 0.0,
        accuracy = 0f,
        speed = 0f,
        bearing = 0f,
        timestamp = 0L,
        isSharingActive = false,
        sharingExpiresAt = 0L,
        targetType = "group",
        targetId = "",
        targetIds = emptyList(),
        visibleTo = emptyList(),
        displayName = "",
        photoUrl = null,
        sharingStartedAt = 0L
    )
}
