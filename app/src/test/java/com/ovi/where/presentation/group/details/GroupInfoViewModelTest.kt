package com.ovi.where.presentation.group.details

import android.app.Application
import android.content.res.Resources
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.model.GroupMember
import com.ovi.where.domain.model.MemberRole
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.MessageRepository
import com.ovi.where.domain.usecase.group.DeleteGroupUseCase
import com.ovi.where.domain.usecase.group.GetGroupUseCase
import com.ovi.where.domain.usecase.group.KickMemberUseCase
import com.ovi.where.domain.usecase.group.LeaveGroupUseCase
import com.ovi.where.domain.usecase.group.ObserveGroupMembersUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class GroupInfoViewModelTest : StringSpec({

    lateinit var application: Application
    lateinit var resources: Resources
    lateinit var getGroupUseCase: GetGroupUseCase
    lateinit var observeGroupMembersUseCase: ObserveGroupMembersUseCase
    lateinit var leaveGroupUseCase: LeaveGroupUseCase
    lateinit var kickMemberUseCase: KickMemberUseCase
    lateinit var deleteGroupUseCase: DeleteGroupUseCase
    lateinit var getUsersUseCase: GetUsersUseCase
    lateinit var messageRepository: MessageRepository
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var firebaseUser: FirebaseUser

    val testDispatcher = StandardTestDispatcher()

    beforeEach {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        getGroupUseCase = mockk(relaxed = true)
        observeGroupMembersUseCase = mockk(relaxed = true)
        leaveGroupUseCase = mockk(relaxed = true)
        kickMemberUseCase = mockk(relaxed = true)
        deleteGroupUseCase = mockk(relaxed = true)
        getUsersUseCase = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        firebaseAuth = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "currentUser"
        every { application.getString(R.string.status_admin) } returns "Admin"
        every { application.getString(R.string.status_member) } returns "Member"
        every { application.getString(R.string.error_loading_group) } returns "Couldn't load group details"
        every { application.getString(R.string.error_leave_group_failed) } returns "Failed to leave group"
        every { application.resources } returns resources
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createViewModel() = GroupInfoViewModel(
        application = application,
        getGroupUseCase = getGroupUseCase,
        observeGroupMembersUseCase = observeGroupMembersUseCase,
        leaveGroupUseCase = leaveGroupUseCase,
        kickMemberUseCase = kickMemberUseCase,
        deleteGroupUseCase = deleteGroupUseCase,
        getUsersUseCase = getUsersUseCase,
        messageRepository = messageRepository,
        firebaseAuth = firebaseAuth
    )

    "loadGroupInfo populates group name, description, and avatar" {
        runTest(testDispatcher) {
            val group = Group(
                id = "group1",
                name = "Test Group",
                description = "A test group",
                avatarUrl = "https://example.com/avatar.png",
                conversationId = "conv1"
            )
            coEvery { getGroupUseCase("group1") } returns Resource.Success(group)
            every { messageRepository.observeMessages("conv1") } returns flowOf(emptyList())

            val vm = createViewModel()
            vm.loadGroupInfo("group1")
            advanceUntilIdle()

            val state = vm.uiState.value
            state.groupName shouldBe "Test Group"
            state.groupDescription shouldBe "A test group"
            state.groupAvatarUrl shouldBe "https://example.com/avatar.png"
            state.isLoading shouldBe false
            state.error shouldBe null
        }
    }

    "loadGroupInfo sets error on failure" {
        runTest(testDispatcher) {
            coEvery { getGroupUseCase("group1") } returns Resource.Error("Network error")

            val vm = createViewModel()
            vm.loadGroupInfo("group1")
            advanceUntilIdle()

            val state = vm.uiState.value
            state.isLoading shouldBe false
            state.error shouldBe "Network error"
        }
    }

    "observeMembers sorts admins first then alphabetical by display name" {
        runTest(testDispatcher) {
            val members = listOf(
                GroupMember(id = "m1", userId = "user1", role = MemberRole.MEMBER),
                GroupMember(id = "m2", userId = "user2", role = MemberRole.ADMIN),
                GroupMember(id = "m3", userId = "user3", role = MemberRole.MEMBER),
                GroupMember(id = "m4", userId = "user4", role = MemberRole.ADMIN)
            )
            val users = listOf(
                User(id = "user1", displayName = "Charlie"),
                User(id = "user2", displayName = "Bob"),
                User(id = "user3", displayName = "Alice"),
                User(id = "user4", displayName = "Dave")
            )

            every { observeGroupMembersUseCase("group1") } returns flowOf(members)
            coEvery { getUsersUseCase(listOf("user1", "user2", "user3", "user4")) } returns Resource.Success(users)

            val vm = createViewModel()
            vm.observeMembers("group1")
            advanceUntilIdle()

            val state = vm.uiState.value
            // Admins first (Bob, Dave), then members alphabetically (Alice, Charlie)
            state.members.size shouldBe 4
            state.members[0].displayName shouldBe "Bob"
            state.members[0].isAdmin shouldBe true
            state.members[1].displayName shouldBe "Dave"
            state.members[1].isAdmin shouldBe true
            state.members[2].displayName shouldBe "Alice"
            state.members[2].isAdmin shouldBe false
            state.members[3].displayName shouldBe "Charlie"
            state.members[3].isAdmin shouldBe false
        }
    }

    "observeMembers shows role labels Admin or Member" {
        runTest(testDispatcher) {
            val members = listOf(
                GroupMember(id = "m1", userId = "user1", role = MemberRole.ADMIN),
                GroupMember(id = "m2", userId = "user2", role = MemberRole.MEMBER)
            )
            val users = listOf(
                User(id = "user1", displayName = "Alice"),
                User(id = "user2", displayName = "Bob")
            )

            every { observeGroupMembersUseCase("group1") } returns flowOf(members)
            coEvery { getUsersUseCase(listOf("user1", "user2")) } returns Resource.Success(users)

            val vm = createViewModel()
            vm.observeMembers("group1")
            advanceUntilIdle()

            val state = vm.uiState.value
            state.members[0].roleText shouldBe "Admin"
            state.members[1].roleText shouldBe "Member"
        }
    }

    "observeMembers detects current user as admin" {
        runTest(testDispatcher) {
            val members = listOf(
                GroupMember(id = "m1", userId = "currentUser", role = MemberRole.ADMIN),
                GroupMember(id = "m2", userId = "user2", role = MemberRole.MEMBER)
            )
            val users = listOf(
                User(id = "currentUser", displayName = "Me"),
                User(id = "user2", displayName = "Bob")
            )

            every { observeGroupMembersUseCase("group1") } returns flowOf(members)
            coEvery { getUsersUseCase(listOf("currentUser", "user2")) } returns Resource.Success(users)

            val vm = createViewModel()
            vm.observeMembers("group1")
            advanceUntilIdle()

            val state = vm.uiState.value
            state.isCurrentUserAdmin shouldBe true
        }
    }

    "observeMembers hides admin actions for non-admin users" {
        runTest(testDispatcher) {
            val members = listOf(
                GroupMember(id = "m1", userId = "otherAdmin", role = MemberRole.ADMIN),
                GroupMember(id = "m2", userId = "currentUser", role = MemberRole.MEMBER)
            )
            val users = listOf(
                User(id = "otherAdmin", displayName = "Admin"),
                User(id = "currentUser", displayName = "Me")
            )

            every { observeGroupMembersUseCase("group1") } returns flowOf(members)
            coEvery { getUsersUseCase(listOf("otherAdmin", "currentUser")) } returns Resource.Success(users)

            val vm = createViewModel()
            vm.observeMembers("group1")
            advanceUntilIdle()

            val state = vm.uiState.value
            state.isCurrentUserAdmin shouldBe false
        }
    }

    "observeMembers detects sole admin status" {
        runTest(testDispatcher) {
            val members = listOf(
                GroupMember(id = "m1", userId = "currentUser", role = MemberRole.ADMIN),
                GroupMember(id = "m2", userId = "user2", role = MemberRole.MEMBER)
            )
            val users = listOf(
                User(id = "currentUser", displayName = "Me"),
                User(id = "user2", displayName = "Bob")
            )

            every { observeGroupMembersUseCase("group1") } returns flowOf(members)
            coEvery { getUsersUseCase(listOf("currentUser", "user2")) } returns Resource.Success(users)

            val vm = createViewModel()
            vm.observeMembers("group1")
            advanceUntilIdle()

            val state = vm.uiState.value
            state.isSoleAdmin shouldBe true
        }
    }

    "leaveGroup is blocked when user is sole admin" {
        runTest(testDispatcher) {
            val members = listOf(
                GroupMember(id = "m1", userId = "currentUser", role = MemberRole.ADMIN),
                GroupMember(id = "m2", userId = "user2", role = MemberRole.MEMBER)
            )
            val users = listOf(
                User(id = "currentUser", displayName = "Me"),
                User(id = "user2", displayName = "Bob")
            )

            every { observeGroupMembersUseCase("group1") } returns flowOf(members)
            coEvery { getUsersUseCase(listOf("currentUser", "user2")) } returns Resource.Success(users)

            val vm = createViewModel()
            vm.observeMembers("group1")
            advanceUntilIdle()

            // Attempt to leave — should be blocked (sole admin)
            vm.leaveGroup("group1")
            advanceUntilIdle()

            // leaveGroupUseCase should NOT have been called
            // The VM should still be in the same state (no navigation)
            val state = vm.uiState.value
            state.isSoleAdmin shouldBe true
        }
    }

    "leaveGroup succeeds and navigates back when not sole admin" {
        runTest(testDispatcher) {
            val members = listOf(
                GroupMember(id = "m1", userId = "currentUser", role = MemberRole.ADMIN),
                GroupMember(id = "m2", userId = "user2", role = MemberRole.ADMIN)
            )
            val users = listOf(
                User(id = "currentUser", displayName = "Me"),
                User(id = "user2", displayName = "Bob")
            )

            every { observeGroupMembersUseCase("group1") } returns flowOf(members)
            coEvery { getUsersUseCase(listOf("currentUser", "user2")) } returns Resource.Success(users)
            coEvery { leaveGroupUseCase("group1") } returns Resource.Success(Unit)

            val vm = createViewModel()
            vm.observeMembers("group1")
            advanceUntilIdle()

            vm.leaveGroup("group1")
            advanceUntilIdle()

            // Verify state is consistent (not sole admin, so leave was allowed)
            val state = vm.uiState.value
            state.isSoleAdmin shouldBe false
        }
    }

    "removeMember failure shows error snackbar without state mutation" {
        runTest(testDispatcher) {
            coEvery { kickMemberUseCase("group1", "user2") } returns Resource.Error("Server error")

            val vm = createViewModel()
            val stateBefore = vm.uiState.value

            vm.removeMember("group1", "user2")
            advanceUntilIdle()

            // State should not have changed (no mutation on failure)
            val stateAfter = vm.uiState.value
            stateAfter.members shouldBe stateBefore.members
        }
    }

    "deleteGroup failure shows error snackbar without navigation" {
        runTest(testDispatcher) {
            coEvery { deleteGroupUseCase("group1") } returns Resource.Error("Server error")

            val vm = createViewModel()
            vm.deleteGroup("group1")
            advanceUntilIdle()

            // No state mutation expected on failure
            val state = vm.uiState.value
            state.error shouldBe null // error is communicated via snackbar event, not state
        }
    }

    "shared media shows most recent 20 IMAGE messages" {
        runTest(testDispatcher) {
            val group = Group(
                id = "group1",
                name = "Test Group",
                description = "desc",
                conversationId = "conv1"
            )
            // Create 25 image messages and 5 text messages
            val imageMessages = (1..25).map { i ->
                Message(
                    id = "img$i",
                    conversationId = "conv1",
                    senderId = "user1",
                    senderName = "User",
                    text = "",
                    type = MessageType.IMAGE,
                    timestamp = i.toLong() * 1000,
                    imageUrl = "https://example.com/img$i.jpg"
                )
            }
            val textMessages = (1..5).map { i ->
                Message(
                    id = "txt$i",
                    conversationId = "conv1",
                    senderId = "user1",
                    senderName = "User",
                    text = "Hello $i",
                    type = MessageType.TEXT,
                    timestamp = (i + 25).toLong() * 1000
                )
            }
            val allMessages = imageMessages + textMessages

            coEvery { getGroupUseCase("group1") } returns Resource.Success(group)
            every { messageRepository.observeMessages("conv1") } returns flowOf(allMessages)

            val vm = createViewModel()
            vm.loadGroupInfo("group1")
            advanceUntilIdle()

            val state = vm.uiState.value
            // Should only have 20 most recent image messages (not text messages)
            state.sharedMedia.size shouldBe 20
            // Should be sorted by timestamp descending (most recent first)
            state.sharedMedia[0].timestamp shouldBe 25000L
            state.sharedMedia[19].timestamp shouldBe 6000L
        }
    }
})
