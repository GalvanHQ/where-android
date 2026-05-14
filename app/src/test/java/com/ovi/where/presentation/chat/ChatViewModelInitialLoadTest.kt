package com.ovi.where.presentation.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.ovi.where.data.location.LocationManager
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.repository.MessageRepositoryImpl
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessagePage
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.usecase.chat.MarkConversationReadUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.chat.ObserveMessagesUseCase
import com.ovi.where.domain.usecase.chat.SendLocationMessageUseCase
import com.ovi.where.domain.usecase.chat.SendMessageUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Unit tests for ChatViewModel task 6.1:
 * - Observe messages flow from MessageRepository
 * - Load initial 30 messages from Room, then sync with server
 * - Maintain messages sorted by timestamp ascending, use message ID as secondary sort key
 * - Expose UI state with messages, isLoading, hasMoreMessages, paginationCursor
 *
 * Requirements: 2.1, 14.1, 14.5
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelInitialLoadTest : StringSpec({

    lateinit var application: Application
    lateinit var savedStateHandle: SavedStateHandle
    lateinit var observeMessagesUseCase: ObserveMessagesUseCase
    lateinit var sendMessageUseCase: SendMessageUseCase
    lateinit var sendLocationMessageUseCase: SendLocationMessageUseCase
    lateinit var markConversationReadUseCase: MarkConversationReadUseCase
    lateinit var observeConversationsUseCase: ObserveConversationsUseCase
    lateinit var wsClient: ChatSocketIoClient
    lateinit var messageRepositoryImpl: MessageRepositoryImpl
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var firebaseUser: FirebaseUser
    lateinit var locationManager: LocationManager
    lateinit var friendshipRepository: com.ovi.where.domain.repository.FriendshipRepository
    lateinit var interactionRepository: com.ovi.where.domain.repository.InteractionRepository

    val testDispatcher = StandardTestDispatcher()

    beforeEach {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        observeMessagesUseCase = mockk(relaxed = true)
        sendMessageUseCase = mockk(relaxed = true)
        sendLocationMessageUseCase = mockk(relaxed = true)
        markConversationReadUseCase = mockk(relaxed = true)
        observeConversationsUseCase = mockk(relaxed = true)
        wsClient = mockk(relaxed = true)
        messageRepositoryImpl = mockk(relaxed = true)
        firebaseAuth = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)
        locationManager = mockk(relaxed = true)
        friendshipRepository = mockk(relaxed = true)
        interactionRepository = mockk(relaxed = true)

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "user123"
        every { firebaseUser.displayName } returns "Test User"

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

        every { observeConversationsUseCase() } returns flowOf(emptyList())
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createViewModel(conversationId: String? = null): ChatViewModel {
        savedStateHandle = SavedStateHandle().apply {
            if (conversationId != null) {
                set("conversationId", conversationId)
            }
        }
        return ChatViewModel(
            application,
            savedStateHandle,
            observeMessagesUseCase,
            sendMessageUseCase,
            sendLocationMessageUseCase,
            markConversationReadUseCase,
            observeConversationsUseCase,
            wsClient,
            messageRepositoryImpl,
            firebaseAuth,
            locationManager,
            friendshipRepository,
            interactionRepository
        )
    }

    fun makeMessage(id: String, timestamp: Long, conversationId: String = "conv1") = Message(
        id = id,
        conversationId = conversationId,
        senderId = "user123",
        senderName = "Test User",
        text = "Message $id",
        type = MessageType.TEXT,
        timestamp = timestamp,
        status = MessageStatus.SENT
    )

    "initial state has isLoading true" {
        runTest {
            every { observeMessagesUseCase(any()) } returns flowOf(emptyList())
            coEvery { messageRepositoryImpl.loadOlderMessages(any(), null, 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel("conv1")

            // Before advancing, isLoading should be true
            vm.uiState.value.isLoading shouldBe true
        }
    }

    "loads initial messages and sets isLoading to false" {
        runTest {
            val messages = listOf(
                makeMessage("msg1", 1000L),
                makeMessage("msg2", 2000L),
                makeMessage("msg3", 3000L)
            )
            every { observeMessagesUseCase("conv1") } returns flowOf(messages)
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(messages, "1000", true)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            vm.uiState.value.isLoading shouldBe false
            vm.uiState.value.messages.size shouldBe 3
        }
    }

    "messages are sorted by timestamp ascending" {
        runTest {
            // Provide messages in random order
            val messages = listOf(
                makeMessage("msg3", 3000L),
                makeMessage("msg1", 1000L),
                makeMessage("msg2", 2000L)
            )
            every { observeMessagesUseCase("conv1") } returns flowOf(messages)
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(messages, "1000", true)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            val uiMessages = vm.uiState.value.messages
            uiMessages[0].id shouldBe "msg1"
            uiMessages[1].id shouldBe "msg2"
            uiMessages[2].id shouldBe "msg3"
        }
    }

    "messages with same timestamp are sorted by ID as secondary key" {
        runTest {
            // Messages with same timestamp but different IDs
            val messages = listOf(
                makeMessage("c_msg", 1000L),
                makeMessage("a_msg", 1000L),
                makeMessage("b_msg", 1000L)
            )
            every { observeMessagesUseCase("conv1") } returns flowOf(messages)
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(messages, "1000", true)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            val uiMessages = vm.uiState.value.messages
            // Sorted by ID when timestamps are equal
            uiMessages[0].id shouldBe "a_msg"
            uiMessages[1].id shouldBe "b_msg"
            uiMessages[2].id shouldBe "c_msg"
        }
    }

    "exposes hasMoreMessages from initial load response" {
        runTest {
            every { observeMessagesUseCase("conv1") } returns flowOf(emptyList())
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), "1000", true)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            vm.uiState.value.hasMoreMessages shouldBe true
        }
    }

    "exposes hasMoreMessages as false when no more pages" {
        runTest {
            every { observeMessagesUseCase("conv1") } returns flowOf(emptyList())
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            vm.uiState.value.hasMoreMessages shouldBe false
        }
    }

    "exposes paginationCursor from initial load response" {
        runTest {
            every { observeMessagesUseCase("conv1") } returns flowOf(emptyList())
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), "cursor_123", true)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            vm.uiState.value.paginationCursor shouldBe "cursor_123"
        }
    }

    "conversationId is set from SavedStateHandle" {
        runTest {
            every { observeMessagesUseCase("conv1") } returns flowOf(emptyList())
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            vm.uiState.value.conversationId shouldBe "conv1"
        }
    }

    "calls loadOlderMessages with null cursor for initial load" {
        runTest {
            every { observeMessagesUseCase("conv1") } returns flowOf(emptyList())
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            coVerify(exactly = 1) { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) }
        }
    }

    "observes messages flow from repository" {
        runTest {
            val messagesFlow = MutableStateFlow(emptyList<Message>())
            every { observeMessagesUseCase("conv1") } returns messagesFlow
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            vm.uiState.value.messages.size shouldBe 0

            // Emit new messages
            messagesFlow.value = listOf(
                makeMessage("msg1", 1000L),
                makeMessage("msg2", 2000L)
            )
            advanceUntilIdle()

            vm.uiState.value.messages.size shouldBe 2
        }
    }

    "does not auto-init when conversationId is not in SavedStateHandle" {
        runTest {
            val vm = createViewModel(null)
            advanceUntilIdle()

            vm.uiState.value.conversationId shouldBe null
            vm.uiState.value.isLoading shouldBe true

            // Should not have called any repository methods
            coVerify(exactly = 0) { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) }
        }
    }

    "init method works for backward compatibility" {
        runTest {
            every { observeMessagesUseCase("conv1") } returns flowOf(emptyList())
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel(null)
            vm.init("conv1")
            advanceUntilIdle()

            vm.uiState.value.conversationId shouldBe "conv1"
            vm.uiState.value.isLoading shouldBe false
        }
    }

    "init method is idempotent for same conversationId" {
        runTest {
            every { observeMessagesUseCase("conv1") } returns flowOf(emptyList())
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel("conv1")
            advanceUntilIdle()

            // Calling init again with same conversationId should be a no-op
            vm.init("conv1")
            advanceUntilIdle()

            // Should only have been called once
            coVerify(exactly = 1) { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) }
        }
    }
})
