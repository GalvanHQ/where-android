package com.ovi.where.presentation

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.ovi.where.core.common.Resource
import com.ovi.where.data.location.LocationManager
import com.ovi.where.data.network.ConnectivityObserver
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.repository.MessageRepositoryImpl
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.model.Interaction
import com.ovi.where.domain.model.InteractionType
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessagePage
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.repository.InteractionRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.chat.MarkConversationReadUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.chat.ObserveMessagesUseCase
import com.ovi.where.domain.usecase.chat.SendLocationMessageUseCase
import com.ovi.where.domain.usecase.chat.SendMessageUseCase
import com.ovi.where.domain.usecase.friend.AcceptFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.BlockUserUseCase
import com.ovi.where.domain.usecase.friend.CancelFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.DeclineFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.GetFriendshipStatusUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.UnblockUserUseCase
import com.ovi.where.presentation.chat.ChatViewModel
import com.ovi.where.presentation.people.UserProfileViewModel
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Fake InteractionRepository that records all calls to `recordInteraction` for assertion.
 */
class FakeInteractionRepository : InteractionRepository {
    data class RecordedCall(
        val userId: String,
        val displayName: String,
        val photoUrl: String?,
        val type: InteractionType
    )

    val recordedCalls = mutableListOf<RecordedCall>()

    override fun getRecentInteractions(limit: Int): Flow<List<Interaction>> = flowOf(emptyList())

    override suspend fun recordInteraction(
        userId: String,
        displayName: String,
        photoUrl: String?,
        type: InteractionType
    ) {
        recordedCalls.add(RecordedCall(userId, displayName, photoUrl, type))
    }

    override suspend fun clearAll() {
        recordedCalls.clear()
    }
}

/**
 * Unit tests verifying that ChatViewModel and UserProfileViewModel correctly
 * invoke `InteractionRepository.recordInteraction` with the expected InteractionType.
 *
 * Uses a FakeInteractionRepository that records all calls for assertion.
 *
 * Validates: Requirements 8.3, 8.4
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InteractionRecordingTest : StringSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeEach {
        Dispatchers.setMain(testDispatcher)
    }

    afterEach {
        Dispatchers.resetMain()
    }

    // ─── ChatViewModel: recordInteraction with MESSAGE_SENT (Requirement 8.3) ─

    "ChatViewModel calls recordInteraction with MESSAGE_SENT after successful send in 1:1 conversation" {
        runTest {
            val fakeInteractionRepo = FakeInteractionRepository()

            // Mock dependencies
            val application: Application = mockk(relaxed = true)
            val observeMessagesUseCase: ObserveMessagesUseCase = mockk(relaxed = true)
            val sendMessageUseCase: SendMessageUseCase = mockk(relaxed = true)
            val sendLocationMessageUseCase: SendLocationMessageUseCase = mockk(relaxed = true)
            val markConversationReadUseCase: MarkConversationReadUseCase = mockk(relaxed = true)
            val observeConversationsUseCase: ObserveConversationsUseCase = mockk(relaxed = true)
            val wsClient: ChatSocketIoClient = mockk(relaxed = true)
            val messageRepositoryImpl: MessageRepositoryImpl = mockk(relaxed = true)
            val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
            val firebaseUser: FirebaseUser = mockk(relaxed = true)
            val locationManager: LocationManager = mockk(relaxed = true)
            val friendshipRepository: FriendshipRepository = mockk(relaxed = true)

            every { firebaseAuth.currentUser } returns firebaseUser
            every { firebaseUser.uid } returns "user123"
            every { firebaseUser.displayName } returns "Test User"
            every { firebaseUser.photoUrl } returns null

            val tokenResult = mockk<GetTokenResult>()
            every { tokenResult.token } returns "test-token"
            val tokenTask = mockk<com.google.android.gms.tasks.Task<GetTokenResult>>(relaxed = true)
            every { tokenTask.isComplete } returns true
            every { tokenTask.isCanceled } returns false
            every { tokenTask.exception } returns null
            every { tokenTask.result } returns tokenResult
            every { firebaseUser.getIdToken(any()) } returns tokenTask

            every { wsClient.incomingFrames } returns MutableSharedFlow(extraBufferCapacity = 64)
            every { wsClient.connectionState } returns MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)

            every { observeMessagesUseCase(any()) } returns flowOf(emptyList())

            // Set up a 1:1 conversation so recordMessageInteraction fires
            val directConversation = Conversation(
                id = "conv1",
                name = "Alice",
                type = ConversationType.DIRECT,
                participantIds = listOf("user123", "alice456"),
                lastMessageText = "",
                lastMessageTimestamp = 0L,
                unreadCounts = emptyMap(),
                photoUrl = "https://example.com/alice.jpg",
                groupId = null,
                onlineMembers = emptySet()
            )
            every { observeConversationsUseCase() } returns flowOf(listOf(directConversation))

            coEvery { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) } returns
                MessagePage(emptyList(), null, false)
            every { messageRepositoryImpl.offlineQueueSize } returns 0

            val dummyMessage = Message(
                id = "temp1", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", text = "Hello Alice", type = MessageType.TEXT,
                timestamp = 1000L, status = MessageStatus.PENDING
            )
            coEvery { messageRepositoryImpl.sendMessage("conv1", "Hello Alice", null) } returns
                Resource.Success(dummyMessage)

            val locationRepository: LocationRepository = mockk(relaxed = true)
            val connectivityObserver: ConnectivityObserver = mockk(relaxed = true)
            every { connectivityObserver.isConnected } returns MutableStateFlow(true)
            every { locationRepository.observeCachedLocations() } returns flowOf(emptyList())
            every { locationRepository.observeLocationsWithCacheFallback(any()) } returns flowOf(emptyList())

            val handle = SavedStateHandle(mapOf("conversationId" to "conv1"))
            val vm = ChatViewModel(
                application, handle, observeMessagesUseCase, sendMessageUseCase,
                sendLocationMessageUseCase, markConversationReadUseCase,
                observeConversationsUseCase, dagger.Lazy { wsClient }, messageRepositoryImpl,
                firebaseAuth, locationManager, friendshipRepository,
                fakeInteractionRepo, mockk(relaxed = true), locationRepository, connectivityObserver,
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            // Type and send a message
            vm.onInputChange("Hello Alice")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            // Verify recordInteraction was called with MESSAGE_SENT
            fakeInteractionRepo.recordedCalls shouldHaveSize 1
            fakeInteractionRepo.recordedCalls[0].userId shouldBe "alice456"
            fakeInteractionRepo.recordedCalls[0].displayName shouldBe "Alice"
            fakeInteractionRepo.recordedCalls[0].photoUrl shouldBe "https://example.com/alice.jpg"
            fakeInteractionRepo.recordedCalls[0].type shouldBe InteractionType.MESSAGE_SENT
        }
    }

    "ChatViewModel does NOT call recordInteraction for group conversations" {
        runTest {
            val fakeInteractionRepo = FakeInteractionRepository()

            val application: Application = mockk(relaxed = true)
            val observeMessagesUseCase: ObserveMessagesUseCase = mockk(relaxed = true)
            val sendMessageUseCase: SendMessageUseCase = mockk(relaxed = true)
            val sendLocationMessageUseCase: SendLocationMessageUseCase = mockk(relaxed = true)
            val markConversationReadUseCase: MarkConversationReadUseCase = mockk(relaxed = true)
            val observeConversationsUseCase: ObserveConversationsUseCase = mockk(relaxed = true)
            val wsClient: ChatSocketIoClient = mockk(relaxed = true)
            val messageRepositoryImpl: MessageRepositoryImpl = mockk(relaxed = true)
            val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
            val firebaseUser: FirebaseUser = mockk(relaxed = true)
            val locationManager: LocationManager = mockk(relaxed = true)
            val friendshipRepository: FriendshipRepository = mockk(relaxed = true)

            every { firebaseAuth.currentUser } returns firebaseUser
            every { firebaseUser.uid } returns "user123"
            every { firebaseUser.displayName } returns "Test User"
            every { firebaseUser.photoUrl } returns null

            val tokenResult = mockk<GetTokenResult>()
            every { tokenResult.token } returns "test-token"
            val tokenTask = mockk<com.google.android.gms.tasks.Task<GetTokenResult>>(relaxed = true)
            every { tokenTask.isComplete } returns true
            every { tokenTask.isCanceled } returns false
            every { tokenTask.exception } returns null
            every { tokenTask.result } returns tokenResult
            every { firebaseUser.getIdToken(any()) } returns tokenTask

            every { wsClient.incomingFrames } returns MutableSharedFlow(extraBufferCapacity = 64)
            every { wsClient.connectionState } returns MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)

            every { observeMessagesUseCase(any()) } returns flowOf(emptyList())

            // Set up a GROUP conversation
            val groupConversation = Conversation(
                id = "conv-group",
                name = "Team Chat",
                type = ConversationType.GROUP,
                participantIds = listOf("user123", "alice456", "bob789"),
                lastMessageText = "",
                lastMessageTimestamp = 0L,
                unreadCounts = emptyMap(),
                photoUrl = null,
                groupId = "group1",
                onlineMembers = emptySet()
            )
            every { observeConversationsUseCase() } returns flowOf(listOf(groupConversation))

            coEvery { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) } returns
                MessagePage(emptyList(), null, false)
            every { messageRepositoryImpl.offlineQueueSize } returns 0

            val dummyMessage = Message(
                id = "temp1", conversationId = "conv-group", senderId = "user123",
                senderName = "Test User", text = "Hello team", type = MessageType.TEXT,
                timestamp = 1000L, status = MessageStatus.PENDING
            )
            coEvery { messageRepositoryImpl.sendMessage("conv-group", "Hello team", null) } returns
                Resource.Success(dummyMessage)

            val locationRepository: LocationRepository = mockk(relaxed = true)
            val connectivityObserver: ConnectivityObserver = mockk(relaxed = true)
            every { connectivityObserver.isConnected } returns MutableStateFlow(true)
            every { locationRepository.observeCachedLocations() } returns flowOf(emptyList())
            every { locationRepository.observeLocationsWithCacheFallback(any()) } returns flowOf(emptyList())

            val handle = SavedStateHandle(mapOf("conversationId" to "conv-group"))
            val vm = ChatViewModel(
                application, handle, observeMessagesUseCase, sendMessageUseCase,
                sendLocationMessageUseCase, markConversationReadUseCase,
                observeConversationsUseCase, dagger.Lazy { wsClient }, messageRepositoryImpl,
                firebaseAuth, locationManager, friendshipRepository,
                fakeInteractionRepo, mockk(relaxed = true), locationRepository, connectivityObserver,
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            // Type and send a message in group
            vm.onInputChange("Hello team")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            // Verify recordInteraction was NOT called for group conversations
            fakeInteractionRepo.recordedCalls.shouldBeEmpty()
        }
    }

    "ChatViewModel does NOT call recordInteraction on failed send" {
        runTest {
            val fakeInteractionRepo = FakeInteractionRepository()

            val application: Application = mockk(relaxed = true)
            val observeMessagesUseCase: ObserveMessagesUseCase = mockk(relaxed = true)
            val sendMessageUseCase: SendMessageUseCase = mockk(relaxed = true)
            val sendLocationMessageUseCase: SendLocationMessageUseCase = mockk(relaxed = true)
            val markConversationReadUseCase: MarkConversationReadUseCase = mockk(relaxed = true)
            val observeConversationsUseCase: ObserveConversationsUseCase = mockk(relaxed = true)
            val wsClient: ChatSocketIoClient = mockk(relaxed = true)
            val messageRepositoryImpl: MessageRepositoryImpl = mockk(relaxed = true)
            val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
            val firebaseUser: FirebaseUser = mockk(relaxed = true)
            val locationManager: LocationManager = mockk(relaxed = true)
            val friendshipRepository: FriendshipRepository = mockk(relaxed = true)

            every { firebaseAuth.currentUser } returns firebaseUser
            every { firebaseUser.uid } returns "user123"
            every { firebaseUser.displayName } returns "Test User"
            every { firebaseUser.photoUrl } returns null

            val tokenResult = mockk<GetTokenResult>()
            every { tokenResult.token } returns "test-token"
            val tokenTask = mockk<com.google.android.gms.tasks.Task<GetTokenResult>>(relaxed = true)
            every { tokenTask.isComplete } returns true
            every { tokenTask.isCanceled } returns false
            every { tokenTask.exception } returns null
            every { tokenTask.result } returns tokenResult
            every { firebaseUser.getIdToken(any()) } returns tokenTask

            every { wsClient.incomingFrames } returns MutableSharedFlow(extraBufferCapacity = 64)
            every { wsClient.connectionState } returns MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)

            every { observeMessagesUseCase(any()) } returns flowOf(emptyList())

            // 1:1 conversation
            val directConversation = Conversation(
                id = "conv1",
                name = "Alice",
                type = ConversationType.DIRECT,
                participantIds = listOf("user123", "alice456"),
                lastMessageText = "",
                lastMessageTimestamp = 0L,
                unreadCounts = emptyMap(),
                photoUrl = null,
                groupId = null,
                onlineMembers = emptySet()
            )
            every { observeConversationsUseCase() } returns flowOf(listOf(directConversation))

            coEvery { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) } returns
                MessagePage(emptyList(), null, false)
            every { messageRepositoryImpl.offlineQueueSize } returns 0

            // Send fails
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns
                Resource.Error("Network error")

            val locationRepository: LocationRepository = mockk(relaxed = true)
            val connectivityObserver: ConnectivityObserver = mockk(relaxed = true)
            every { connectivityObserver.isConnected } returns MutableStateFlow(true)
            every { locationRepository.observeCachedLocations() } returns flowOf(emptyList())
            every { locationRepository.observeLocationsWithCacheFallback(any()) } returns flowOf(emptyList())

            val handle = SavedStateHandle(mapOf("conversationId" to "conv1"))
            val vm = ChatViewModel(
                application, handle, observeMessagesUseCase, sendMessageUseCase,
                sendLocationMessageUseCase, markConversationReadUseCase,
                observeConversationsUseCase, dagger.Lazy { wsClient }, messageRepositoryImpl,
                firebaseAuth, locationManager, friendshipRepository,
                fakeInteractionRepo, mockk(relaxed = true), locationRepository, connectivityObserver,
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.onInputChange("Hello")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            // Verify recordInteraction was NOT called on failure
            fakeInteractionRepo.recordedCalls.shouldBeEmpty()
        }
    }

    // ─── UserProfileViewModel: recordInteraction with PROFILE_VIEWED (Requirement 8.4) ─

    "UserProfileViewModel calls recordInteraction with PROFILE_VIEWED when profile is loaded" {
        runTest {
            val fakeInteractionRepo = FakeInteractionRepository()

            val userRepository: UserRepository = mockk(relaxed = true)
            val friendshipRepository: FriendshipRepository = mockk(relaxed = true)
            val getFriendshipStatusUseCase: GetFriendshipStatusUseCase = mockk(relaxed = true)
            val sendFriendRequestUseCase: SendFriendRequestUseCase = mockk(relaxed = true)
            val cancelFriendRequestUseCase: CancelFriendRequestUseCase = mockk(relaxed = true)
            val removeFriendUseCase: RemoveFriendUseCase = mockk(relaxed = true)
            val acceptFriendRequestUseCase: AcceptFriendRequestUseCase = mockk(relaxed = true)
            val declineFriendRequestUseCase: DeclineFriendRequestUseCase = mockk(relaxed = true)
            val blockUserUseCase: BlockUserUseCase = mockk(relaxed = true)
            val unblockUserUseCase: UnblockUserUseCase = mockk(relaxed = true)
            val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase = mockk(relaxed = true)
            val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
            val firebaseUser: FirebaseUser = mockk(relaxed = true)

            every { firebaseAuth.currentUser } returns firebaseUser
            every { firebaseUser.uid } returns "currentUser123"

            val targetUser = User(
                id = "targetUser456",
                displayName = "Bob Smith",
                username = "bobsmith",
                photoUrl = "https://example.com/bob.jpg",
                email = "bob@example.com"
            )
            coEvery { userRepository.getUser("targetUser456") } returns Resource.Success(targetUser)
            coEvery { friendshipRepository.getFriendship("targetUser456") } returns null

            val vm = UserProfileViewModel(
                userRepository,
                friendshipRepository,
                fakeInteractionRepo,
                getFriendshipStatusUseCase,
                sendFriendRequestUseCase,
                cancelFriendRequestUseCase,
                removeFriendUseCase,
                acceptFriendRequestUseCase,
                declineFriendRequestUseCase,
                blockUserUseCase,
                unblockUserUseCase,
                getOrCreateDirectConversationUseCase,
                firebaseAuth
            )

            vm.loadUser("targetUser456")
            advanceUntilIdle()

            // Verify recordInteraction was called with PROFILE_VIEWED
            fakeInteractionRepo.recordedCalls shouldHaveSize 1
            fakeInteractionRepo.recordedCalls[0].userId shouldBe "targetUser456"
            fakeInteractionRepo.recordedCalls[0].displayName shouldBe "Bob Smith"
            fakeInteractionRepo.recordedCalls[0].photoUrl shouldBe "https://example.com/bob.jpg"
            fakeInteractionRepo.recordedCalls[0].type shouldBe InteractionType.PROFILE_VIEWED
        }
    }

    "UserProfileViewModel records PROFILE_VIEWED only once per ViewModel instance" {
        runTest {
            val fakeInteractionRepo = FakeInteractionRepository()

            val userRepository: UserRepository = mockk(relaxed = true)
            val friendshipRepository: FriendshipRepository = mockk(relaxed = true)
            val getFriendshipStatusUseCase: GetFriendshipStatusUseCase = mockk(relaxed = true)
            val sendFriendRequestUseCase: SendFriendRequestUseCase = mockk(relaxed = true)
            val cancelFriendRequestUseCase: CancelFriendRequestUseCase = mockk(relaxed = true)
            val removeFriendUseCase: RemoveFriendUseCase = mockk(relaxed = true)
            val acceptFriendRequestUseCase: AcceptFriendRequestUseCase = mockk(relaxed = true)
            val declineFriendRequestUseCase: DeclineFriendRequestUseCase = mockk(relaxed = true)
            val blockUserUseCase: BlockUserUseCase = mockk(relaxed = true)
            val unblockUserUseCase: UnblockUserUseCase = mockk(relaxed = true)
            val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase = mockk(relaxed = true)
            val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
            val firebaseUser: FirebaseUser = mockk(relaxed = true)

            every { firebaseAuth.currentUser } returns firebaseUser
            every { firebaseUser.uid } returns "currentUser123"

            val targetUser = User(
                id = "targetUser456",
                displayName = "Bob Smith",
                username = "bobsmith",
                photoUrl = "https://example.com/bob.jpg",
                email = "bob@example.com"
            )
            coEvery { userRepository.getUser("targetUser456") } returns Resource.Success(targetUser)
            coEvery { friendshipRepository.getFriendship("targetUser456") } returns null

            val vm = UserProfileViewModel(
                userRepository,
                friendshipRepository,
                fakeInteractionRepo,
                getFriendshipStatusUseCase,
                sendFriendRequestUseCase,
                cancelFriendRequestUseCase,
                removeFriendUseCase,
                acceptFriendRequestUseCase,
                declineFriendRequestUseCase,
                blockUserUseCase,
                unblockUserUseCase,
                getOrCreateDirectConversationUseCase,
                firebaseAuth
            )

            // Load the same user twice
            vm.loadUser("targetUser456")
            advanceUntilIdle()
            vm.loadUser("targetUser456")
            advanceUntilIdle()

            // Verify recordInteraction was called only ONCE
            fakeInteractionRepo.recordedCalls shouldHaveSize 1
            fakeInteractionRepo.recordedCalls[0].type shouldBe InteractionType.PROFILE_VIEWED
        }
    }

    "UserProfileViewModel does NOT call recordInteraction when user is not found" {
        runTest {
            val fakeInteractionRepo = FakeInteractionRepository()

            val userRepository: UserRepository = mockk(relaxed = true)
            val friendshipRepository: FriendshipRepository = mockk(relaxed = true)
            val getFriendshipStatusUseCase: GetFriendshipStatusUseCase = mockk(relaxed = true)
            val sendFriendRequestUseCase: SendFriendRequestUseCase = mockk(relaxed = true)
            val cancelFriendRequestUseCase: CancelFriendRequestUseCase = mockk(relaxed = true)
            val removeFriendUseCase: RemoveFriendUseCase = mockk(relaxed = true)
            val acceptFriendRequestUseCase: AcceptFriendRequestUseCase = mockk(relaxed = true)
            val declineFriendRequestUseCase: DeclineFriendRequestUseCase = mockk(relaxed = true)
            val blockUserUseCase: BlockUserUseCase = mockk(relaxed = true)
            val unblockUserUseCase: UnblockUserUseCase = mockk(relaxed = true)
            val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase = mockk(relaxed = true)
            val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
            val firebaseUser: FirebaseUser = mockk(relaxed = true)

            every { firebaseAuth.currentUser } returns firebaseUser
            every { firebaseUser.uid } returns "currentUser123"

            // User not found
            coEvery { userRepository.getUser("nonexistent") } returns Resource.Error("User not found")

            val vm = UserProfileViewModel(
                userRepository,
                friendshipRepository,
                fakeInteractionRepo,
                getFriendshipStatusUseCase,
                sendFriendRequestUseCase,
                cancelFriendRequestUseCase,
                removeFriendUseCase,
                acceptFriendRequestUseCase,
                declineFriendRequestUseCase,
                blockUserUseCase,
                unblockUserUseCase,
                getOrCreateDirectConversationUseCase,
                firebaseAuth
            )

            vm.loadUser("nonexistent")
            advanceUntilIdle()

            // Verify recordInteraction was NOT called
            fakeInteractionRepo.recordedCalls.shouldBeEmpty()
        }
    }
})

