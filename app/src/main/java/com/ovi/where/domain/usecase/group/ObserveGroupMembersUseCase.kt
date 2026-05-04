package com.ovi.where.domain.usecase.group

import com.ovi.where.domain.model.GroupMember
import com.ovi.where.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGroupMembersUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(groupId: String): Flow<List<GroupMember>> {
        return groupRepository.observeGroupMembers(groupId)
    }
}
