package com.ovi.where.domain.model

import com.google.firebase.firestore.PropertyName

data class User(
    val id: String = "",
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val phoneNumber: String? = null,
    @PropertyName("isOnline")
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val createdAt: Long = 0L,
    val fcmToken: String? = null
)
