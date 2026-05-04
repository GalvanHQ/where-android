package com.ovi.where.domain.usecase.group

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.GroupRepository
import javax.inject.Inject

class LeaveGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String): Resource<Unit> {
        return groupRepository.leaveGroup(groupId)
    }
}
