package com.ovi.where.presentation.group.create

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.chat.CreateGroupConversationUseCase
import com.ovi.where.domain.usecase.group.CreateGroupUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateGroupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var createGroupUseCase: CreateGroupUseCase
    private lateinit var createGroupConversationUseCase: CreateGroupConversationUseCase
    private lateinit var userRepository: UserRepository
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseUser: FirebaseUser
    private lateinit var viewModel: CreateGroupViewModel

    private val currentUserId = "current-user-id"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        createGroupUseCase = mockk()
        createGroupConversationUseCase = mockk()
        userRepository = mockk()
        firebaseAuth = mockk()
        firebaseUser = mockk()

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns currentUserId
        every { application.getString(R.string.error_group_name_required) } returns "Group name is required"
        every { application.getString(R.string.error_group_name_too_short) } returns "Name must be at least 3 characters"
        every { application.getString(R.string.error_group_name_too_long) } returns "Name must be at most 50 characters"
        every { application.getString(R.string.error_group_no_members) } returns "At least 1 member must be selected"
        every { application.getString(R.string.error_group_too_many_members) } returns "Maximum 50 members allowed"
        every { application.getString(R.string.error_failed_create_group) } returns "Failed to create group"
        every { application.getString(R.string.error_group_avatar_upload_failed) } returns "Failed to upload group avatar"

        viewModel = CreateGroupViewModel(
            application = application,
            createGroupUseCase = createGroupUseCase,
            createGroupConversationUseCase = createGroupConversationUseCase,
            userRepository = userRepository,
            firebaseAuth = firebaseAuth
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Member Search Tests ---

    @Test
    fun `search triggers after 300ms debounce when query is at least 2 chars`() = runTest {
        val users = listOf(
            User(id = "user1", displayName = "Alice", username = "alice"),
            User(id = "user2", displayName = "Bob", username = "bob")
        )
        coEvery { userRepository.searchUsers("al") } returns Resource.Success(users)

        viewModel.onMemberSearchQueryChange("al")

        // Before debounce: no results
        advanceTimeBy(200)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())

        // After debounce: results appear
        advanceTimeBy(150)
        assertEquals(2, viewModel.uiState.value.searchResults.size)
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `search does not trigger when query is less than 2 chars`() = runTest {
        viewModel.onMemberSearchQueryChange("a")

        advanceTimeBy(500)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
    }

    @Test
    fun `search results exclude current user`() = runTest {
        val users = listOf(
            User(id = currentUserId, displayName = "Me", username = "me"),
            User(id = "user1", displayName = "Alice", username = "alice")
        )
        coEvery { userRepository.searchUsers("al") } returns Resource.Success(users)

        viewModel.onMemberSearchQueryChange("al")
        advanceTimeBy(350)

        assertEquals(1, viewModel.uiState.value.searchResults.size)
        assertEquals("user1", viewModel.uiState.value.searchResults[0].id)
    }

    @Test
    fun `search results exclude already-selected members`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        val bob = User(id = "user2", displayName = "Bob", username = "bob")
        val users = listOf(alice, bob)
        coEvery { userRepository.searchUsers("al") } returns Resource.Success(users)

        // Select Alice first
        viewModel.onMemberSelected(alice)

        // Now search
        viewModel.onMemberSearchQueryChange("al")
        advanceTimeBy(350)

        // Alice should be excluded from results
        assertEquals(1, viewModel.uiState.value.searchResults.size)
        assertEquals("user2", viewModel.uiState.value.searchResults[0].id)
    }

    @Test
    fun `search results limited to 20`() = runTest {
        val users = (1..30).map { i ->
            User(id = "user$i", displayName = "User $i", username = "user$i")
        }
        coEvery { userRepository.searchUsers("us") } returns Resource.Success(users)

        viewModel.onMemberSearchQueryChange("us")
        advanceTimeBy(350)

        assertEquals(20, viewModel.uiState.value.searchResults.size)
    }

    @Test
    fun `selecting a member adds to chip row and removes from search results`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        val bob = User(id = "user2", displayName = "Bob", username = "bob")
        coEvery { userRepository.searchUsers("al") } returns Resource.Success(listOf(alice, bob))

        viewModel.onMemberSearchQueryChange("al")
        advanceTimeBy(350)

        // Select Alice
        viewModel.onMemberSelected(alice)

        // Alice should be in selected members
        assertEquals(1, viewModel.uiState.value.selectedMembers.size)
        assertEquals("user1", viewModel.uiState.value.selectedMembers[0].id)

        // Alice should be removed from search results
        assertEquals(1, viewModel.uiState.value.searchResults.size)
        assertEquals("user2", viewModel.uiState.value.searchResults[0].id)
    }

    @Test
    fun `removing a member from chip row triggers re-search to restore them in results`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        val bob = User(id = "user2", displayName = "Bob", username = "bob")
        coEvery { userRepository.searchUsers("al") } returns Resource.Success(listOf(alice, bob))

        // Search and select Alice
        viewModel.onMemberSearchQueryChange("al")
        advanceTimeBy(350)
        viewModel.onMemberSelected(alice)

        // Remove Alice
        viewModel.onMemberRemoved(alice)

        // Selected members should be empty
        assertTrue(viewModel.uiState.value.selectedMembers.isEmpty())

        // After re-search completes, Alice should be back in results
        advanceTimeBy(50) // executeMemberSearch is called directly, not debounced
        assertEquals(2, viewModel.uiState.value.searchResults.size)
    }

    @Test
    fun `duplicate selection is prevented`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")

        viewModel.onMemberSelected(alice)
        viewModel.onMemberSelected(alice)

        assertEquals(1, viewModel.uiState.value.selectedMembers.size)
    }

    @Test
    fun `clearing search query clears results`() = runTest {
        val users = listOf(User(id = "user1", displayName = "Alice", username = "alice"))
        coEvery { userRepository.searchUsers("al") } returns Resource.Success(users)

        viewModel.onMemberSearchQueryChange("al")
        advanceTimeBy(350)
        assertEquals(1, viewModel.uiState.value.searchResults.size)

        // Clear query
        viewModel.onMemberSearchQueryChange("")
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
    }

    @Test
    fun `search error updates error state`() = runTest {
        coEvery { userRepository.searchUsers("al") } returns Resource.Error("Network error")

        viewModel.onMemberSearchQueryChange("al")
        advanceTimeBy(350)

        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals("Network error", viewModel.uiState.value.error)
    }

    // --- Validation Tests ---

    @Test
    fun `validation fails when name is blank`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        viewModel.onNameChange("")

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        assertEquals("Group name is required", viewModel.uiState.value.nameError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `validation fails when name is shorter than 3 characters`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        viewModel.onNameChange("AB")

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        assertEquals("Name must be at least 3 characters", viewModel.uiState.value.nameError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `validation fails when name is longer than 50 characters`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        viewModel.onNameChange("A".repeat(51))

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        assertEquals("Name must be at most 50 characters", viewModel.uiState.value.nameError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `validation passes when name is exactly 3 characters`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        viewModel.onNameChange("ABC")

        val group = Group(id = "g1", name = "ABC", inviteCode = "INV123")
        val conversation = Conversation(
            id = "conv1", name = "ABC", type = ConversationType.GROUP, participantIds = listOf("user1", currentUserId)
        )
        coEvery { createGroupUseCase("ABC", "", null) } returns Resource.Success(group)
        coEvery { createGroupConversationUseCase(any(), any(), any()) } returns Resource.Success(conversation)

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        assertNull(viewModel.uiState.value.nameError)
        assertTrue(viewModel.uiState.value.isGroupCreated)
    }

    @Test
    fun `validation passes when name is exactly 50 characters`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        val name50 = "A".repeat(50)
        viewModel.onNameChange(name50)

        val group = Group(id = "g1", name = name50, inviteCode = "INV123")
        val conversation = Conversation(
            id = "conv1", name = name50, type = ConversationType.GROUP, participantIds = listOf("user1", currentUserId)
        )
        coEvery { createGroupUseCase(name50, "", null) } returns Resource.Success(group)
        coEvery { createGroupConversationUseCase(any(), any(), any()) } returns Resource.Success(conversation)

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        assertNull(viewModel.uiState.value.nameError)
        assertTrue(viewModel.uiState.value.isGroupCreated)
    }

    @Test
    fun `validation fails when no members selected`() = runTest {
        viewModel.onNameChange("Valid Group")

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        assertEquals("At least 1 member must be selected", viewModel.uiState.value.membersError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `validation fails when more than 50 members selected`() = runTest {
        viewModel.onNameChange("Valid Group")

        // Select 51 members
        (1..51).forEach { i ->
            viewModel.onMemberSelected(User(id = "user$i", displayName = "User $i", username = "user$i"))
        }

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        assertEquals("Maximum 50 members allowed", viewModel.uiState.value.membersError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `selecting a member clears membersError`() = runTest {
        viewModel.onNameChange("Valid Group")
        viewModel.onCreateGroup()
        advanceTimeBy(100)
        assertEquals("At least 1 member must be selected", viewModel.uiState.value.membersError)

        // Now select a member
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)

        assertNull(viewModel.uiState.value.membersError)
    }

    // --- Group Creation Success Tests ---

    @Test
    fun `successful group creation displays invite code`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        viewModel.onNameChange("My Group")

        val group = Group(id = "g1", name = "My Group", inviteCode = "ABC123")
        val conversation = Conversation(
            id = "conv1", name = "My Group", type = ConversationType.GROUP, participantIds = listOf("user1", currentUserId)
        )
        coEvery { createGroupUseCase("My Group", "", null) } returns Resource.Success(group)
        coEvery { createGroupConversationUseCase("g1", "My Group", listOf("user1", currentUserId)) } returns Resource.Success(conversation)

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        val state = viewModel.uiState.value
        assertEquals("ABC123", state.inviteCode)
        assertTrue(state.isGroupCreated)
        assertEquals("g1", state.createdGroupId)
        assertEquals("conv1", state.createdConversationId)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // --- Group Creation Failure Tests ---

    @Test
    fun `failed group creation shows error and preserves form data`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        viewModel.onNameChange("My Group")
        viewModel.onDescriptionChange("A description")

        coEvery { createGroupUseCase("My Group", "A description", null) } returns Resource.Error("Server error")

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        val state = viewModel.uiState.value
        // Error is shown
        assertEquals("Server error", state.error)
        assertFalse(state.isLoading)
        // Form data is preserved
        assertEquals("My Group", state.name)
        assertEquals("A description", state.description)
        assertEquals(1, state.selectedMembers.size)
        assertEquals("user1", state.selectedMembers[0].id)
        assertFalse(state.isGroupCreated)
    }

    @Test
    fun `name change clears nameError`() = runTest {
        viewModel.onNameChange("AB")
        viewModel.onMemberSelected(User(id = "user1", displayName = "Alice", username = "alice"))
        viewModel.onCreateGroup()
        advanceTimeBy(100)
        assertEquals("Name must be at least 3 characters", viewModel.uiState.value.nameError)

        // Changing name clears the error
        viewModel.onNameChange("ABC")
        assertNull(viewModel.uiState.value.nameError)
    }

    // --- Share Invite Code Tests ---

    @Test
    fun `share invite code emits ShareContent event`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        viewModel.onNameChange("My Group")

        val group = Group(id = "g1", name = "My Group", inviteCode = "ABC123")
        val conversation = Conversation(
            id = "conv1", name = "My Group", type = ConversationType.GROUP, participantIds = listOf("user1", currentUserId)
        )
        coEvery { createGroupUseCase("My Group", "", null) } returns Resource.Success(group)
        coEvery { createGroupConversationUseCase("g1", "My Group", listOf("user1", currentUserId)) } returns Resource.Success(conversation)

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        // Now share the invite code
        viewModel.onShareInviteCode()
        advanceTimeBy(100)

        val event = viewModel.uiEvent.first()
        assertTrue(event is UiEvent.ShareContent)
        val shareEvent = event as UiEvent.ShareContent
        assertTrue(shareEvent.content.contains("ABC123"))
    }

    // --- Navigate to Group Chat Tests ---

    @Test
    fun `navigate to group chat emits Navigate event with correct route`() = runTest {
        val alice = User(id = "user1", displayName = "Alice", username = "alice")
        viewModel.onMemberSelected(alice)
        viewModel.onNameChange("My Group")

        val group = Group(id = "g1", name = "My Group", inviteCode = "ABC123")
        val conversation = Conversation(
            id = "conv1", name = "My Group", type = ConversationType.GROUP, participantIds = listOf("user1", currentUserId)
        )
        coEvery { createGroupUseCase("My Group", "", null) } returns Resource.Success(group)
        coEvery { createGroupConversationUseCase("g1", "My Group", listOf("user1", currentUserId)) } returns Resource.Success(conversation)

        viewModel.onCreateGroup()
        advanceTimeBy(100)

        // Navigate to the group chat
        viewModel.onNavigateToGroupChat()
        advanceTimeBy(100)

        val event = viewModel.uiEvent.first()
        assertTrue(event is UiEvent.Navigate)
        val navEvent = event as UiEvent.Navigate
        assertEquals("chat/conv1", navEvent.route)
    }
}
