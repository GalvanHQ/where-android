package com.ovi.where.domain.model

data class GroupMember(
    val id: String = "",
    val userId: String = "",
    val groupId: String = "",
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = 0L,
    val isSharingLocation: Boolean = false,
    val sharingExpiresAt: Long = 0L
)

enum class MemberRole {
    ADMIN,
    MEMBER
}
