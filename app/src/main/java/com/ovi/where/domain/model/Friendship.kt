package com.ovi.where.domain.model

data class Friendship(
    val id: String = "",
    val requesterId: String = "",
    val receiverId: String = "",
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class FriendshipStatus { PENDING, ACCEPTED, BLOCKED }
