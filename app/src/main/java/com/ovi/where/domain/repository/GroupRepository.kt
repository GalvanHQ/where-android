package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.model.GroupMember
import kotlinx.coroutines.flow.Flow
import com.ovi.where.core.common.DataResource

interface GroupRepository {
    suspend fun createGroup(name: String, description: String, avatarUrl: String? = null): Resource<Group>
    suspend fun getGroup(groupId: String): Resource<Group>
    suspend fun getUserGroups(): Resource<List<Group>>
    suspend fun joinGroupWithCode(inviteCode: String): Resource<Group>
    suspend fun leaveGroup(groupId: String): Resource<Unit>
    suspend fun deleteGroup(groupId: String): Resource<Unit>
    suspend fun updateGroup(groupId: String, name: String, description: String): Resource<Group>

    /**
     * Persists the per-group conversation id back onto the group document.
     * Called by the group-creation flow after the chat conversation is
     * created server-side. Idempotent.
     */
    suspend fun setGroupConversationId(groupId: String, conversationId: String): Resource<Unit>

    /**
     * Updates the avatar URL on a group document. The avatar is uploaded
     * separately (e.g. to Cloud Storage); this call only writes the
     * resolved URL.
     */
    suspend fun setGroupAvatar(groupId: String, avatarUrl: String): Resource<Unit>

    fun observeGroupMembers(groupId: String): Flow<List<GroupMember>>
    suspend fun addMember(groupId: String, userId: String): Resource<Unit>
    suspend fun removeMember(groupId: String, userId: String): Resource<Unit>
    suspend fun updateMemberRole(groupId: String, userId: String, role: com.ovi.where.domain.model.MemberRole): Resource<Unit>

    /**
     * Returns group members using the NetworkBoundResource pattern:
     * 1. Serves cached members immediately (Loading state)
     * 2. Checks staleness via CacheStalenessChecker
     * 3. Fetches from Firestore if stale
     * 4. Saves to local cache on success (Success state)
     * 5. Serves stale cache on failure (Error state with cached data)
     *
     * Requirements: 11.1, 11.2, 11.3, 11.6
     */
    fun getGroupMembersResource(groupId: String): Flow<DataResource<List<GroupMember>>>

    /**
     * Mutes a member in a group. The muted member will not be able to send messages
     * in the group until unmuted by an admin.
     *
     * Requirement 15.2, 15.3
     */
    suspend fun muteMember(groupId: String, userId: String): Resource<Unit>

    /**
     * Retrieves the invite link for a group.
     *
     * Requirement 15.5, 15.6
     */
    suspend fun getInviteLink(groupId: String): Resource<String>
}
