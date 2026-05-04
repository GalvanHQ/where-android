package com.ovi.where.presentation.model

import com.ovi.where.domain.model.Group

data class GroupUiModel(
    val id: String,
    val name: String,
    val description: String,
    val inviteCode: String,
    val memberCountText: String,
    val memberCount: Int,
    val createdBy: String
)

fun Group.toUiModel(memberCountText: String): GroupUiModel {
    return GroupUiModel(
        id = id,
        name = name,
        description = description,
        inviteCode = inviteCode,
        memberCountText = memberCountText,
        memberCount = memberCount,
        createdBy = createdBy
    )
}
