package com.ovi.where.domain.usecase.group

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.repository.GroupRepository
import javax.inject.Inject

class GetUserGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(): Resource<List<Group>> {
        return groupRepository.getUserGroups()
    }
}
