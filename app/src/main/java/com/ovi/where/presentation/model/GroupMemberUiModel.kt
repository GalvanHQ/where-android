package com.ovi.where.presentation.model

import com.ovi.where.domain.model.GroupMember
import com.ovi.where.domain.model.MemberRole

data class GroupMemberUiModel(
    val id: String,
    val userId: String,
    val displayName: String,
    val roleText: String,
    val isAdmin: Boolean,
    val isSharingLocation: Boolean
)

fun GroupMember.toUiModel(
    displayName: String = userId,
    adminText: String,
    memberText: String
): GroupMemberUiModel {
    return GroupMemberUiModel(
        id = id,
        userId = userId,
        displayName = displayName,
        roleText = if (role == MemberRole.ADMIN) adminText else memberText,
        isAdmin = role == MemberRole.ADMIN,
        isSharingLocation = isSharingLocation
    )
}
