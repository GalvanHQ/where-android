package com.ovi.where.domain.usecase.group

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.GroupRepository
import javax.inject.Inject

/**
 * Mutes a member in a group conversation.
 *
 * Called by the admin from the GroupChatHeader overflow menu.
 * On success, the member is muted and cannot send messages until unmuted.
 * On failure, the caller should display an error snackbar (Requirement 15.3).
 *
 * Requirements: 15.2, 15.3
 */
class MuteGroupMemberUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String, memberId: String): Resource<Unit> {
        if (groupId.isBlank() || memberId.isBlank()) {
            return Resource.Error("Invalid group or member ID")
        }
        return groupRepository.muteMember(groupId, memberId)
    }
}
