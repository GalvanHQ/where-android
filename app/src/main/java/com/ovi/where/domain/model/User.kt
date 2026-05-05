package com.ovi.where.domain.model

data class User(
    val id: String = "",
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val phoneNumber: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val createdAt: Long = 0L,
    val fcmToken: String? = null
)
