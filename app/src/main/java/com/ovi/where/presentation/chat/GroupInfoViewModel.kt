package com.ovi.where.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.MemberRole
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.repository.GroupRepository
import com.ovi.where.domain.repository.MessageRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.presentation.model.GroupInfoUiState
import com.ovi.where.presentation.model.GroupMemberUiModel
import com.ovi.where.presentation.model.MediaThumbnail
import com.ovi.where.presentation.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ViewModel for the Messenger-style Group Info Screen.
 *
 * Loads group details, member list with online status, shared media, and invite link.
 * Exposes [GroupInfoUiState] as StateFlow.
 * Implements admin actions: make admin, remove member, delete group.
 * Handles error states with retry capability.
 *
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11
 */
@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val conversationRepository: com.ovi.where.domain.repository.ConversationRepository,
    private val firebaseAuth: FirebaseAuth,
    private val friendshipRepository: com.ovi.where.domain.repository.FriendshipRepository,
    private val systemMessageWriter: com.ovi.where.data.repository.SystemMessageWriter
) : ViewModel() {

    private val groupId: String = savedStateHandle["groupId"] ?: ""

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private val _currentUserId: String? get() = firebaseAuth.currentUser?.uid
    val currentUserId: String? get() = _currentUserId

    /** Cached conversationId for loading shared media */
    private var conversationId: String? = null

    /** Resolves and caches the conversationId for this group; returns null if unavailable. */
    private suspend fun ensureConversationId(): String? {
        if (conversationId == null) {
            conversationId = conversationRepository.getConversationIdByGroupId(groupId)
        }
        return conversationId
    }

    /** Display name lookup for a target user from the cached members list. */
    private fun resolveTargetName(userId: String): String =
        _uiState.value.members.firstOrNull { it.userId == userId }?.displayName ?: "Someone"

    init {
        if (groupId.isNotBlank()) {
            loadGroupInfo()
        }
    }

    /**
     * Loads all group info: details, members, invite link, and shared media.
     * Called on init and on retry after error.
     */
    fun loadGroupInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = groupRepository.getGroup(groupId)) {
                is Resource.Success -> {
                    val group = result.data
                    if (group != null) {
                        conversationId = group.conversationId
                        // If no conversationId on the group, resolve it via Room + Firestore
                        if (conversationId == null) {
                            conversationId = conversationRepository.getConversationIdByGroupId(groupId)
                        }
                        _uiState.update {
                            it.copy(
                                groupName = group.name,
                                groupDescription = group.description,
                                groupPhotoUrl = group.avatarUrl,
                                memberCount = group.memberCount,
                                isLoading = false,
                                error = null
                            )
                        }
                        // Sync group photo to conversation if needed
                        if (!group.avatarUrl.isNullOrBlank()) {
                            syncGroupPhotoToConversation(group.avatarUrl)
                        }
                        // Load additional data in parallel
                        loadMembers()
                        loadInviteLink()
                        conversationId?.let {
                            loadSharedMedia(it)
                            loadNicknames(it)
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Group not found"
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message ?: "Failed to load group"
                        )
                    }
                }
                is Resource.Loading -> {
                    // Already showing loading state
                }
            }
        }
    }

    /**
     * Retry loading group info after an error.
     */
    fun retry() {
        loadGroupInfo()
    }

    /**
     * Observes group members and enriches with user details (display name, photo, online status).
     * Sorts admins first, then alphabetical by display name.
     * Requirement 9.3, 9.11
     */
    private fun loadMembers() {
        viewModelScope.launch {
            groupRepository.observeGroupMembers(groupId).collect { members ->
                val userIds = members.map { it.userId }
                val users = when (val usersResult = userRepository.getUsers(userIds)) {
                    is Resource.Success -> usersResult.data ?: emptyList()
                    else -> emptyList()
                }
                val userMap = users.associateBy { it.id }
                val uid = currentUserId

                val memberUiModels = members.map { member ->
                    val user = userMap[member.userId]
                    GroupMemberUiModel(
                        id = member.id,
                        userId = member.userId,
                        displayName = user?.displayName?.ifBlank { member.userId } ?: member.userId,
                        username = user?.username ?: "",
                        photoUrl = user?.photoUrl,
                        isOnline = user?.isOnline ?: false,
                        roleText = if (member.role == MemberRole.ADMIN) "Admin" else "Member",
                        isAdmin = member.role == MemberRole.ADMIN,
                        isSharingLocation = member.isSharingLocation
                    )
                }

                // Sort: admins first, then alphabetical by display name
                val sortedMembers = memberUiModels.sortedWith(
                    compareByDescending<GroupMemberUiModel> { it.isAdmin }
                        .thenBy { it.displayName.lowercase() }
                )

                val isCurrentUserAdmin = members.any {
                    it.userId == uid && it.role == MemberRole.ADMIN
                }

                _uiState.update {
                    it.copy(
                        members = sortedMembers,
                        memberCount = sortedMembers.size,
                        isCurrentUserAdmin = isCurrentUserAdmin
                    )
                }
            }
        }
    }

    /**
     * Loads the group invite link (admin only).
     * Requirement 9.10
     */
    private fun loadInviteLink() {
        viewModelScope.launch {
            when (val result = groupRepository.getInviteLink(groupId)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(inviteLink = result.data) }
                }
                is Resource.Error -> {
                    // Invite link failure is non-critical, don't show error
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Resolves the conversationId by looking up conversations with this groupId.
     * Uses Room first, then Firestore direct query as fallback.
     */
    private fun resolveConversationId(groupId: String) {
        viewModelScope.launch {
            val resolved = conversationRepository.getConversationIdByGroupId(groupId)
            if (resolved != null && conversationId == null) {
                conversationId = resolved
                loadSharedMedia(resolved)
                loadNicknames(resolved)
            }
        }
    }

    /**
     * Ensures the conversation document's photoUrl matches the group's avatarUrl.
     * This handles the case where the group was created with a photo but the
     * conversation document was never updated (legacy data or server didn't sync).
     */
    private fun syncGroupPhotoToConversation(avatarUrl: String) {
        viewModelScope.launch {
            conversationRepository.updateConversationPhotoUrlByGroupId(groupId, avatarUrl)
        }
    }

    /**
     * Loads nicknames from the conversation document.
     */
    private fun loadNicknames(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.observeConversation(conversationId).collect { conversation ->
                if (conversation != null) {
                    _uiState.update {
                        it.copy(
                            nicknames = conversation.nicknames,
                            themeColor = conversation.themeColor,
                            emojiShortcut = conversation.emojiShortcut
                        )
                    }
                }
            }
        }
    }

    /**
     * Loads shared media thumbnails from the conversation's messages.
     */
    private fun loadSharedMedia(conversationId: String) {
        viewModelScope.launch {
            messageRepository.observeMessages(conversationId).collect { messages ->
                val mediaThumbnails = messages
                    .filter { it.type == MessageType.IMAGE && it.imageUrl != null }
                    .sortedByDescending { it.timestamp }
                    .take(SHARED_MEDIA_LIMIT)
                    .map { message ->
                        MediaThumbnail(
                            id = message.id,
                            thumbnailUrl = message.imageUrl ?: "",
                            type = MediaType.IMAGE
                        )
                    }
                _uiState.update { it.copy(sharedMedia = mediaThumbnails) }
            }
        }
    }

    // ── Admin Actions ───────────────────────────────────────────────────────

    /**
     * Makes a member an admin (optimistic update).
     * Requirement 9.4
     */
    fun makeAdmin(userId: String) {
        val previousMembers = _uiState.value.members

        // Optimistic update
        _uiState.update { state ->
            state.copy(
                members = state.members.map { member ->
                    if (member.userId == userId) {
                        member.copy(isAdmin = true, roleText = "Admin")
                    } else member
                }
            )
        }

        viewModelScope.launch {
            when (groupRepository.updateMemberRole(groupId, userId, MemberRole.ADMIN)) {
                is Resource.Success -> {
                    val convId = ensureConversationId()
                    if (convId != null) {
                        val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                        val target = resolveTargetName(userId)
                        systemMessageWriter.writeSystemMessage(
                            conversationId = convId,
                            eventType = com.ovi.where.domain.model.SystemEventType.MEMBER_PROMOTED,
                            targetUserId = userId,
                            fallbackText = "$actor made $target an admin"
                        )
                    }
                }
                is Resource.Error -> {
                    // Revert optimistic update
                    _uiState.update { it.copy(members = previousMembers) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Demotes an admin back to a regular member (optimistic update). Mirrors
     * [makeAdmin] but flips the role the other direction. Emits a
     * MEMBER_DEMOTED system message into the group's chat.
     */
    fun demoteAdmin(userId: String) {
        val previousMembers = _uiState.value.members

        _uiState.update { state ->
            state.copy(
                members = state.members.map { member ->
                    if (member.userId == userId) {
                        member.copy(isAdmin = false, roleText = "Member")
                    } else member
                }
            )
        }

        viewModelScope.launch {
            when (groupRepository.updateMemberRole(groupId, userId, MemberRole.MEMBER)) {
                is Resource.Success -> {
                    val convId = ensureConversationId()
                    if (convId != null) {
                        val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                        val target = resolveTargetName(userId)
                        systemMessageWriter.writeSystemMessage(
                            conversationId = convId,
                            eventType = com.ovi.where.domain.model.SystemEventType.MEMBER_DEMOTED,
                            targetUserId = userId,
                            fallbackText = "$actor removed $target as admin"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(members = previousMembers) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Removes a member from the group (optimistic update).
     * Requirement 9.4
     */
    fun removeMember(userId: String) {
        val previousMembers = _uiState.value.members
        val previousMemberCount = _uiState.value.memberCount

        // Optimistic update
        _uiState.update { state ->
            val updatedMembers = state.members.filter { it.userId != userId }
            state.copy(
                members = updatedMembers,
                memberCount = updatedMembers.size
            )
        }

        viewModelScope.launch {
            // Capture target name BEFORE optimistic state was mutated, since
            // the member may have already been removed from the cached list.
            val targetName = previousMembers.firstOrNull { it.userId == userId }?.displayName ?: "Someone"
            when (groupRepository.removeMember(groupId, userId)) {
                is Resource.Success -> {
                    val convId = ensureConversationId()
                    if (convId != null) {
                        val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                        systemMessageWriter.writeSystemMessage(
                            conversationId = convId,
                            eventType = com.ovi.where.domain.model.SystemEventType.MEMBER_REMOVED,
                            targetUserId = userId,
                            fallbackText = "$actor removed $targetName"
                        )
                    }
                }
                is Resource.Error -> {
                    // Revert optimistic update
                    _uiState.update {
                        it.copy(
                            members = previousMembers,
                            memberCount = previousMemberCount
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Deletes the group entirely (admin action).
     * Requirement 9.9
     */
    fun deleteGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (groupRepository.deleteGroup(groupId)) {
                is Resource.Success -> {
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(error = "Failed to delete group")
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Leaves the group.
     * Requirement 9.8
     */
    fun leaveGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            // Resolve conversationId BEFORE leaving so the doc read is still
            // permitted. We need to write the system message before our own
            // removal closes the door on writes.
            val convId = ensureConversationId()
            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
            val actorId = firebaseAuth.currentUser?.uid

            when (groupRepository.leaveGroup(groupId)) {
                is Resource.Success -> {
                    if (convId != null && actorId != null) {
                        systemMessageWriter.writeSystemMessage(
                            conversationId = convId,
                            eventType = com.ovi.where.domain.model.SystemEventType.MEMBER_LEFT,
                            targetUserId = actorId,
                            fallbackText = "$actor left the group"
                        )
                    }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(error = "Failed to leave group")
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Toggles mute state for the group.
     * Requirement 9.2
     */
    fun toggleMute() {
        val currentMuted = _uiState.value.isMuted
        _uiState.update { it.copy(isMuted = !currentMuted) }
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Updates the group's name and description together. Optimistic update +
     * Firestore persist with rollback on error. Either field can stay the
     * same — pass the existing value to avoid blanking it out.
     */
    fun updateGroupDetails(newName: String, newDescription: String) {
        if (newName.isBlank() || newName.length < 3) return
        val previousName = _uiState.value.groupName
        val previousDescription = _uiState.value.groupDescription
        if (newName == previousName && newDescription == previousDescription) return

        _uiState.update { it.copy(groupName = newName, groupDescription = newDescription) }

        viewModelScope.launch {
            when (groupRepository.updateGroup(groupId, newName, newDescription)) {
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(groupName = previousName, groupDescription = previousDescription)
                    }
                }
                else -> {
                    // Emit one system message per changed field. Cloud Functions
                    // would normally do this server-side; here we author from
                    // the client because the project uses Cloud Run + WS, not
                    // Firestore-triggered Functions. Idempotent ids in
                    // SystemMessageWriter make duplicate-emit harmless.
                    val convId = ensureConversationId()
                    if (convId != null) {
                        val actorName = firebaseAuth.currentUser?.displayName ?: "Someone"
                        if (newName != previousName) {
                            systemMessageWriter.writeSystemMessage(
                                conversationId = convId,
                                eventType = com.ovi.where.domain.model.SystemEventType.GROUP_RENAMED,
                                payload = mapOf(
                                    "oldName" to previousName,
                                    "newName" to newName
                                ),
                                fallbackText = "$actorName renamed the group to \"$newName\""
                            )
                        }
                        if (newDescription != previousDescription) {
                            systemMessageWriter.writeSystemMessage(
                                conversationId = convId,
                                eventType = com.ovi.where.domain.model.SystemEventType.GROUP_DESCRIPTION_CHANGED,
                                payload = mapOf(
                                    "oldDescription" to previousDescription,
                                    "newDescription" to newDescription
                                ),
                                fallbackText = "$actorName updated the group description"
                            )
                        }
                    }
                }
            }
        }
    }

    /** Backwards-compatible name-only update. Delegates to [updateGroupDetails]. */
    fun updateGroupName(newName: String) {
        updateGroupDetails(newName, _uiState.value.groupDescription)
    }

    /**
     * Adds multiple members to the group.
     */
    fun addMembers(userIds: List<String>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            var allSuccess = true
            val convId = ensureConversationId()
            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
            for (userId in userIds) {
                when (groupRepository.addMember(groupId, userId)) {
                    is Resource.Success -> {
                        if (convId != null) {
                            // Resolve target name from the User repo since the
                            // member isn't in the cached list yet.
                            val targetName = (userRepository.getUser(userId) as? Resource.Success)
                                ?.data?.displayName ?: "Someone"
                            systemMessageWriter.writeSystemMessage(
                                conversationId = convId,
                                eventType = com.ovi.where.domain.model.SystemEventType.MEMBER_ADDED,
                                targetUserId = userId,
                                fallbackText = "$actor added $targetName"
                            )
                        }
                    }
                    is Resource.Error -> { allSuccess = false }
                    else -> {}
                }
            }
            if (allSuccess) onSuccess()
            else _uiState.update { it.copy(error = "Failed to add some members") }
        }
    }

    /**
     * Loads the current user's friends list for the Add Members screen.
     */
    fun loadFriendsForAdding(onResult: (List<com.ovi.where.domain.model.FriendEntry>) -> Unit) {
        viewModelScope.launch {
            friendshipRepository.observeFriends().collect { friends ->
                onResult(friends)
            }
        }
    }

    /**
     * Updates the group photo URL in Firestore and the linked conversation.
     * Uses groupId-based Room update for immediate UI feedback on ChatsScreen/ChatScreen.
     */
    fun updateGroupPhoto(photoUrl: String) {
        _uiState.update { it.copy(groupPhotoUrl = photoUrl) }
        viewModelScope.launch {
            try {
                timber.log.Timber.d("GroupInfoVM: updateGroupPhoto called, groupId=$groupId, url=$photoUrl")

                // 1. Persist avatarUrl to the group Firestore document
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("groups")
                    .document(groupId)
                    .update("avatarUrl", photoUrl)
                    .await()
                timber.log.Timber.d("GroupInfoVM: group avatarUrl updated in Firestore")

                // 2. Update conversation photo by groupId (Room + Firestore)
                val result = conversationRepository.updateConversationPhotoUrlByGroupId(groupId, photoUrl)
                timber.log.Timber.d("GroupInfoVM: updateConversationPhotoUrlByGroupId result=$result")

                // 3. Also try by conversationId as fallback
                if (conversationId == null) {
                    conversationId = conversationRepository.getConversationIdByGroupId(groupId)
                }
                timber.log.Timber.d("GroupInfoVM: resolved conversationId=$conversationId")
                conversationId?.let { convId ->
                    conversationRepository.updateConversationPhotoUrl(convId, photoUrl)
                    timber.log.Timber.d("GroupInfoVM: also updated by conversationId=$convId")
                }

                // 4. Emit a system message line for the timeline.
                conversationId?.let { convId ->
                    val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                    systemMessageWriter.writeSystemMessage(
                        conversationId = convId,
                        eventType = com.ovi.where.domain.model.SystemEventType.GROUP_PHOTO_CHANGED,
                        payload = mapOf("newPhotoUrl" to photoUrl),
                        fallbackText = "$actor changed the group photo"
                    )
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "GroupInfoVM: updateGroupPhoto failed")
                _uiState.update { it.copy(groupPhotoUrl = null) }
            }
        }
    }

    /**
     * Updates a nickname for a group member.
     */
    fun updateNickname(userId: String, nickname: String) {
        viewModelScope.launch {
            // Ensure conversationId is resolved
            if (conversationId == null) {
                conversationId = conversationRepository.getConversationIdByGroupId(groupId)
            }
            val convId = conversationId
            timber.log.Timber.d("GroupInfoVM: updateNickname userId=$userId, nickname=$nickname, convId=$convId, groupId=$groupId")
            if (convId == null) {
                timber.log.Timber.w("GroupInfoVM: updateNickname SKIPPED — conversationId is null")
                return@launch
            }

            val previous = _uiState.value.nicknames[userId].orEmpty()
            val newNick = nickname.trim()
            if (previous == newNick) return@launch

            val currentNicknames = _uiState.value.nicknames.toMutableMap()
            if (newNick.isBlank()) {
                currentNicknames.remove(userId)
            } else {
                currentNicknames[userId] = newNick
            }
            _uiState.update { it.copy(nicknames = currentNicknames) }
            val result = conversationRepository.updateNicknames(convId, currentNicknames)
            timber.log.Timber.d("GroupInfoVM: updateNicknames result=$result")

            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
            val target = resolveTargetName(userId)
            val text = if (newNick.isBlank()) "$actor cleared $target's nickname"
                else "$actor set $target's nickname to \"$newNick\""
            systemMessageWriter.writeSystemMessage(
                conversationId = convId,
                eventType = com.ovi.where.domain.model.SystemEventType.NICKNAME_CHANGED,
                targetUserId = userId,
                payload = mapOf(
                    "oldNickname" to previous,
                    "newNickname" to newNick
                ),
                fallbackText = text
            )
        }
    }

    /**
     * Updates the theme color for the group's conversation.
     */
    fun updateThemeColor(colorHex: String?) {
        val previous = _uiState.value.themeColor
        if (previous == colorHex) return
        _uiState.update { it.copy(themeColor = colorHex) }
        viewModelScope.launch {
            // Ensure conversationId is resolved
            if (conversationId == null) {
                conversationId = conversationRepository.getConversationIdByGroupId(groupId)
            }
            val convId = conversationId ?: return@launch
            conversationRepository.updateThemeColor(convId, colorHex)
            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
            systemMessageWriter.writeSystemMessage(
                conversationId = convId,
                eventType = com.ovi.where.domain.model.SystemEventType.THEME_COLOR_CHANGED,
                payload = buildMap {
                    if (previous != null) put("oldColor", previous)
                    if (colorHex != null) put("newColor", colorHex)
                },
                fallbackText = "$actor changed the chat color"
            )
        }
    }

    /**
     * Updates the emoji shortcut for the group's conversation.
     */
    fun updateEmojiShortcut(emoji: String?) {
        val previous = _uiState.value.emojiShortcut
        if (previous == emoji) return
        _uiState.update { it.copy(emojiShortcut = emoji) }
        viewModelScope.launch {
            if (conversationId == null) {
                conversationId = conversationRepository.getConversationIdByGroupId(groupId)
            }
            val convId = conversationId ?: return@launch
            conversationRepository.updateEmojiShortcut(convId, emoji)
            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
            val text = if (emoji.isNullOrBlank()) "$actor changed the emoji shortcut"
                else "$actor set the emoji shortcut to $emoji"
            systemMessageWriter.writeSystemMessage(
                conversationId = convId,
                eventType = com.ovi.where.domain.model.SystemEventType.EMOJI_SHORTCUT_CHANGED,
                payload = buildMap {
                    if (previous != null) put("oldEmoji", previous)
                    if (emoji != null) put("newEmoji", emoji)
                },
                fallbackText = text
            )
        }
    }

    companion object {
        private const val SHARED_MEDIA_LIMIT = 20
    }
}
