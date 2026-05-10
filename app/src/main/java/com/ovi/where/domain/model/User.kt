package com.ovi.where.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class User(
    val id: String = "",
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    @PropertyName("isOnline")
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val createdAt: Long = 0L,
    val fcmToken: String? = null,
    @PropertyName("isEmailVerified")
    val isEmailVerified: Boolean = false
) {
    /** Profile is complete when the user has chosen a username. */
    @get:Exclude
    val isProfileComplete: Boolean
        get() = username.isNotBlank()
}
