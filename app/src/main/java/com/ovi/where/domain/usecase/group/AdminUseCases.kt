package com.ovi.where.domain.usecase.group

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.GroupRepository
import com.ovi.where.domain.model.MemberRole
import javax.inject.Inject

class KickMemberUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String, userId: String): Resource<Unit> {
        return groupRepository.removeMember(groupId, userId)
    }
}

class PromoteMemberUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String, userId: String): Resource<Unit> {
        return groupRepository.updateMemberRole(groupId, userId, MemberRole.ADMIN)
    }
}

class DeleteGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String): Resource<Unit> {
        return groupRepository.deleteGroup(groupId)
    }
}

class UpdateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String, name: String, description: String) =
        groupRepository.updateGroup(groupId, name, description)
}
