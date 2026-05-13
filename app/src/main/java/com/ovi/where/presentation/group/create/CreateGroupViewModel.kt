package com.ovi.where.presentation.group.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.core.constants.AppConstants.MIN_SEARCH_QUERY_LENGTH
import com.ovi.where.core.constants.AppConstants.SEARCH_DEBOUNCE_MS
import com.ovi.where.core.utils.ImageUploadUtil
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.chat.CreateGroupConversationUseCase
import com.ovi.where.domain.usecase.group.CreateGroupUseCase
import com.ovi.where.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CreateGroupViewModel — handles group creation with member search, selection,
 * validation, avatar upload, and invite code display.
 *
 * - 300ms debounce on member search query (Requirement 10.2).
 * - Displays up to 20 results excluding already-selected members and current user.
 * - Add/remove members from chip row with consistency (Requirement 10.3).
 * - Validates group name length 3-50 characters (Requirement 10.1).
 * - Validates at least 1 member selected, max 50 (Requirement 10.4, 10.7).
 * - Uploads avatar if selected before creating group (Requirement 10.4).
 * - Displays invite code with share button on success (Requirement 10.6).
 * - Preserves form data on failure for retry (Requirement 10.5).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    application: Application,
    private val createGroupUseCase: CreateGroupUseCase,
    private val createGroupConversationUseCase: CreateGroupConversationUseCase,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val memberSearchQuery = MutableStateFlow("")

    private val currentUserId: String get() = firebaseAuth.currentUser?.uid ?: ""

    companion object {
        const val MIN_GROUP_NAME_LENGTH = 3
        const val MAX_GROUP_NAME_LENGTH = 50
        const val MIN_MEMBERS = 1
        const val MAX_MEMBERS = 50
    }

    init {
        // Debounced member search: 300ms debounce, minimum 2 characters
        viewModelScope.launch {
            memberSearchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .filter { it.length >= MIN_SEARCH_QUERY_LENGTH }
                .collect { query -> executeMemberSearch(query) }
        }
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null
        )
    }

    fun onDescriptionChange(desc: String) {
        _uiState.value = _uiState.value.copy(description = desc)
    }

    /**
     * Called when the user selects an avatar image URI.
     */
    fun onAvatarSelected(uri: Uri?) {
        _uiState.value = _uiState.value.copy(avatarUri = uri)
    }

    /**
     * Called when the member search query changes.
     * Triggers debounced search when query >= 2 characters.
     * Clears results when query is empty or too short.
     */
    fun onMemberSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(memberSearchQuery = query)
        memberSearchQuery.value = query
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                isSearching = false
            )
        }
    }

    /**
     * Executes the member search against the UserRepository.
     * Filters out already-selected members and the current user.
     * Limits results to 20.
     */
    private suspend fun executeMemberSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true)
        val selectedIds = _uiState.value.selectedMembers.map { it.id }.toSet()

        when (val result = userRepository.searchUsers(query)) {
            is Resource.Success -> {
                val users = result.data ?: emptyList()
                val filtered = users
                    .filter { it.id != currentUserId && it.id !in selectedIds }
                    .take(20)
                _uiState.value = _uiState.value.copy(
                    searchResults = filtered,
                    isSearching = false
                )
            }
            is Resource.Error -> {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = result.message
                )
            }
            else -> {}
        }
    }

    /**
     * Adds a user to the selected members chip row.
     * Removes them from search results to maintain consistency.
     */
    fun onMemberSelected(user: User) {
        val currentSelected = _uiState.value.selectedMembers.toMutableList()
        // Prevent duplicate selection
        if (currentSelected.any { it.id == user.id }) return

        currentSelected.add(user)
        _uiState.value = _uiState.value.copy(
            selectedMembers = currentSelected,
            searchResults = _uiState.value.searchResults.filter { it.id != user.id },
            membersError = null
        )
    }

    /**
     * Removes a user from the selected members chip row.
     * If the user matches the current search query, restores them to search results.
     */
    fun onMemberRemoved(user: User) {
        val currentSelected = _uiState.value.selectedMembers.toMutableList()
        currentSelected.removeAll { it.id == user.id }
        _uiState.value = _uiState.value.copy(selectedMembers = currentSelected)

        // If there's an active search query, re-run the search to restore the removed user
        // in results (if they match the query)
        val currentQuery = _uiState.value.memberSearchQuery
        if (currentQuery.length >= 2) {
            viewModelScope.launch { executeMemberSearch(currentQuery) }
        }
    }

    /**
     * Shares the invite code via system share sheet.
     */
    fun onShareInviteCode() {
        val inviteCode = _uiState.value.inviteCode
        if (inviteCode.isNotBlank()) {
            viewModelScope.launch {
                _uiEvent.send(
                    UiEvent.ShareContent(
                        title = "Join my group on Where!",
                        content = "Join my group using invite code: $inviteCode"
                    )
                )
            }
        }
    }

    /**
     * Navigates to the newly created group chat.
     */
    fun onNavigateToGroupChat() {
        val conversationId = _uiState.value.createdConversationId
        if (conversationId != null) {
            viewModelScope.launch {
                _uiEvent.send(UiEvent.Navigate(Screen.Chat.createRoute(conversationId)))
            }
        }
    }

    /**
     * Creates the group after validating inputs.
     * Uploads avatar if selected, then POSTs to /groups.
     * On success: displays invite code and navigates to group chat.
     * On failure: shows error and preserves form data for retry.
     */
    fun onCreateGroup() {
        val state = _uiState.value

        if (!validateInput(state)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Step 1: Upload avatar if selected
            var avatarUrl: String? = null
            val avatarUri = state.avatarUri
            if (avatarUri != null) {
                val uploadResult = ImageUploadUtil.uploadProfilePicture(
                    context = getApplication(),
                    imageUri = avatarUri
                )
                if (uploadResult.isFailure) {
                    // Avatar upload failed — show error, preserve form data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = getApplication<Application>().getString(R.string.error_group_avatar_upload_failed)
                    )
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString(
                                getApplication<Application>().getString(R.string.error_group_avatar_upload_failed)
                            )
                        )
                    )
                    return@launch
                }
                avatarUrl = uploadResult.getOrNull()
            }

            // Step 2: Create the group via REST API
            when (val result = createGroupUseCase(state.name, state.description, avatarUrl)) {
                is Resource.Success -> {
                    val group = result.data
                    if (group != null) {
                        // Step 3: Create the group conversation with selected members
                        val memberIds = state.selectedMembers.map { it.id } + currentUserId
                        val conversationResult = createGroupConversationUseCase(
                            groupId = group.id,
                            name = group.name,
                            memberIds = memberIds
                        )

                        val conversationId = when (conversationResult) {
                            is Resource.Success -> conversationResult.data?.id
                            else -> null
                        }

                        // Success: display invite code and prepare navigation
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            inviteCode = group.inviteCode,
                            createdGroupId = group.id,
                            createdConversationId = conversationId,
                            isGroupCreated = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = getApplication<Application>().getString(R.string.error_failed_create_group)
                        )
                    }
                }
                is Resource.Error -> {
                    // Failure: show error, preserve form data for retry
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString(
                                result.message ?: getApplication<Application>().getString(
                                    R.string.error_failed_create_group
                                )
                            )
                        )
                    )
                }
                is Resource.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    /**
     * Validates group creation inputs:
     * - Name length must be 3-50 characters (Requirement 10.1)
     * - At least 1 member must be selected (Requirement 10.7)
     * - Maximum 50 members (Requirement 10.4)
     */
    private fun validateInput(state: CreateGroupUiState): Boolean {
        val app = getApplication<Application>()

        if (state.name.isBlank()) {
            _uiState.value = _uiState.value.copy(
                nameError = app.getString(R.string.error_group_name_required)
            )
            return false
        }
        if (state.name.length < MIN_GROUP_NAME_LENGTH) {
            _uiState.value = _uiState.value.copy(
                nameError = app.getString(R.string.error_group_name_too_short)
            )
            return false
        }
        if (state.name.length > MAX_GROUP_NAME_LENGTH) {
            _uiState.value = _uiState.value.copy(
                nameError = app.getString(R.string.error_group_name_too_long)
            )
            return false
        }
        if (state.selectedMembers.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                membersError = app.getString(R.string.error_group_no_members)
            )
            return false
        }
        if (state.selectedMembers.size > MAX_MEMBERS) {
            _uiState.value = _uiState.value.copy(
                membersError = app.getString(R.string.error_group_too_many_members)
            )
            return false
        }
        return true
    }
}

data class CreateGroupUiState(
    val name: String = "",
    val description: String = "",
    val nameError: String? = null,
    val membersError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val inviteCode: String = "",
    // Avatar
    val avatarUri: Uri? = null,
    // Member search and selection state
    val memberSearchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val selectedMembers: List<User> = emptyList(),
    val isSearching: Boolean = false,
    // Post-creation state
    val isGroupCreated: Boolean = false,
    val createdGroupId: String? = null,
    val createdConversationId: String? = null
)
