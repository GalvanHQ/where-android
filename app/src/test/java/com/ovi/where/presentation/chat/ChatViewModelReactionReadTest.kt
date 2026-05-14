package com.ovi.where.presentation.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.ovi.where.core.common.Resource
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
 * Unit tests for ChatViewModel task 6.4:
 * - Toggle reaction via MessageRepository.reactToMessage
 * - Manage replyingToMessage state (set on long-press reply action, clear on dismiss/send)
 * - Emit read event on conversation open (covering all unread messages)
 * - Defer read event if disconnected
 *
 * Requirements: 3.3, 4.1, 4.2, 5.1, 5.5
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelReactionReadTest : StringSpec({

    lateinit var application: Application
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
    lateinit var connectionState: MutableStateFlow<ChatSocketIoClient.ConnectionState>
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

        connectionState = MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "user123"

        val tokenResult = mockk<GetTokenResult>()
        every { tokenResult.token } returns "test-token"
        val tokenTask = mockk<com.google.android.gms.tasks.Task<GetTokenResult>>(relaxed = true)
        every { tokenTask.isComplete } returns true
        every { tokenTask.isCanceled } returns false
        every { tokenTask.exception } returns null
        every { tokenTask.result } returns tokenResult
        every { firebaseUser.getIdToken(any()) } returns tokenTask

        every { wsClient.incomingFrames } returns MutableSharedFlow(extraBufferCapacity = 64)
        every { wsClient.connectionState } returns connectionState

        every { observeMessagesUseCase(any()) } returns flowOf(emptyList())
        every { observeConversationsUseCase() } returns flowOf(emptyList())
        coEvery { messageRepositoryImpl.loadOlderMessages(any(), null, 30) } returns
            MessagePage(emptyList(), null, false)
        coEvery { markConversationReadUseCase(any(), any()) } returns Resource.Success(Unit)
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createViewModel(convId: String? = "conv1"): ChatViewModel {
        val handle = if (convId != null) SavedStateHandle(mapOf("conversationId" to convId))
                     else SavedStateHandle()
        return ChatViewModel(
            application,
            handle,
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

    // ─── Reaction Toggle Tests (Requirement 3.3) ─────────────────────────────

    "toggleReaction calls reactToMessage on repository with correct params" {
        runTest {
            coEvery { messageRepositoryImpl.reactToMessage(any(), any(), any()) } returns Resource.Success(Unit)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.toggleReaction("msg1", "👍")
            advanceUntilIdle()

            coVerify(exactly = 1) { messageRepositoryImpl.reactToMessage("conv1", "msg1", "👍") }
        }
    }

    "toggleReaction does nothing when conversationId is null" {
        runTest {
            val vm = createViewModel(null)
            advanceUntilIdle()

            vm.toggleReaction("msg1", "👍")
            advanceUntilIdle()

            coVerify(exactly = 0) { messageRepositoryImpl.reactToMessage(any(), any(), any()) }
        }
    }

    // ─── Reply State Management Tests (Requirements 4.1, 4.2) ────────────────

    "setReplyingTo sets the replyingToMessage in UI state" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            val mockMessage = mockk<com.ovi.where.presentation.model.MessageUiModel>(relaxed = true)
            every { mockMessage.id } returns "msg1"

            vm.setReplyingTo(mockMessage)

            vm.uiState.value.replyingToMessage shouldBe mockMessage
        }
    }

    "clearReply clears the replyingToMessage without affecting input text" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            val mockMessage = mockk<com.ovi.where.presentation.model.MessageUiModel>(relaxed = true)
            vm.setReplyingTo(mockMessage)
            vm.onInputChange("some text")

            vm.clearReply()

            vm.uiState.value.replyingToMessage shouldBe null
            vm.uiState.value.inputText shouldBe "some text"
        }
    }

    "sendMessage clears replyingToMessage on send" {
        runTest {
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns
                Resource.Success(mockk(relaxed = true))

            val vm = createViewModel()
            advanceUntilIdle()

            val mockMessage = mockk<com.ovi.where.presentation.model.MessageUiModel>(relaxed = true)
            every { mockMessage.id } returns "reply-target-id"
            vm.setReplyingTo(mockMessage)
            vm.onInputChange("reply text")

            vm.sendMessage()
            advanceUntilIdle()

            vm.uiState.value.replyingToMessage shouldBe null
            vm.uiState.value.inputText shouldBe ""
        }
    }

    "sendMessage passes replyToId from replyingToMessage to repository" {
        runTest {
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns
                Resource.Success(mockk(relaxed = true))

            val vm = createViewModel()
            advanceUntilIdle()

            val mockMessage = mockk<com.ovi.where.presentation.model.MessageUiModel>(relaxed = true)
            every { mockMessage.id } returns "reply-target-id"
            vm.setReplyingTo(mockMessage)
            vm.onInputChange("reply text")

            vm.sendMessage()
            advanceUntilIdle()

            coVerify { messageRepositoryImpl.sendMessage("conv1", "reply text", replyToId = "reply-target-id") }
        }
    }

    // ─── Read Receipt Emission Tests (Requirements 5.1, 5.5) ─────────────────

    "emits read event on conversation open when connected" {
        runTest {
            connectionState.value = ChatSocketIoClient.ConnectionState.CONNECTED

            val vm = createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { markConversationReadUseCase("conv1", "user123") }
            coVerify(exactly = 1) { wsClient.sendRead() }
        }
    }

    "defers read event when disconnected and emits when connected" {
        runTest {
            connectionState.value = ChatSocketIoClient.ConnectionState.DISCONNECTED

            val vm = createViewModel()
            advanceUntilIdle()

            // Should have called markRead on repository (which defers internally)
            coVerify(exactly = 1) { messageRepositoryImpl.markRead("conv1", "user123") }
            // Should NOT have called markConversationReadUseCase yet (waiting for connection)
            coVerify(exactly = 0) { markConversationReadUseCase("conv1", "user123") }

            // Simulate connection restored
            connectionState.value = ChatSocketIoClient.ConnectionState.CONNECTED
            advanceUntilIdle()

            // Now the deferred read event should be emitted
            coVerify(exactly = 1) { markConversationReadUseCase("conv1", "user123") }
            coVerify(exactly = 1) { wsClient.sendRead() }
        }
    }

    "does not emit read event when conversationId is null" {
        runTest {
            val vm = createViewModel(null)
            advanceUntilIdle()

            coVerify(exactly = 0) { markConversationReadUseCase(any(), any()) }
            coVerify(exactly = 0) { wsClient.sendRead() }
        }
    }
})
