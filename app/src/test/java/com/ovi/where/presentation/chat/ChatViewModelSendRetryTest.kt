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
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Unit tests for ChatViewModel task 6.3:
 * - Clear input text and reply state on send
 * - Handle FAILED status: expose retry action per message
 * - Track consecutive failures (3 retries → snackbar "Message could not be sent" for 4s)
 * - Reject sends when offline queue is full (50 messages)
 *
 * Requirements: 1.3, 1.4, 1.5, 1.7
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSendRetryTest : StringSpec({

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
    lateinit var friendshipRepository: com.ovi.where.domain.repository.FriendshipRepository
    lateinit var interactionRepository: com.ovi.where.domain.repository.InteractionRepository
    lateinit var locationRepository: com.ovi.where.domain.repository.LocationRepository
    lateinit var connectivityObserver: com.ovi.where.data.network.ConnectivityObserver

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
        locationRepository = mockk(relaxed = true)
        connectivityObserver = mockk(relaxed = true)

        every { connectivityObserver.isConnected } returns MutableStateFlow(true)
        every { locationRepository.observeCachedLocations() } returns flowOf(emptyList())
        every { locationRepository.observeLocationsWithCacheFallback(any()) } returns flowOf(emptyList())

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
        every { observeConversationsUseCase() } returns flowOf(emptyList())

        // Default: initial load returns empty page
        coEvery { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) } returns
            MessagePage(emptyList(), null, false)

        // Default: offlineQueueSize is 0
        every { messageRepositoryImpl.offlineQueueSize } returns 0
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createViewModel(convId: String = "conv1"): ChatViewModel {
        val handle = SavedStateHandle(mapOf("conversationId" to convId))
        return ChatViewModel(
            application,
            handle,
            observeMessagesUseCase,
            sendMessageUseCase,
            sendLocationMessageUseCase,
            markConversationReadUseCase,
            observeConversationsUseCase,
            dagger.Lazy { wsClient },
            messageRepositoryImpl,
            firebaseAuth,
            locationManager,
            friendshipRepository,
            interactionRepository,
            mockk(relaxed = true), // groupRepository
            locationRepository,
            connectivityObserver,
            mockk(relaxed = true), // startLocationSharingUseCase
            mockk(relaxed = true), // stopLocationSharingUseCase
            mockk(relaxed = true), // fetchLinkPreviewUseCase
            mockk(relaxed = true), // muteGroupMemberUseCase
            mockk(relaxed = true), // voiceRecorder
            mockk(relaxed = true), // onlineStatusDao
            mockk(relaxed = true)  // userRepository
        )
    }

    // ─── Clear input text and reply state on send ─────────────────────────────

    "sendMessage clears input text on send" {
        runTest {
            val dummyMessage = Message(
                id = "temp1", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", text = "Hello", type = MessageType.TEXT,
                timestamp = 1000L, status = MessageStatus.PENDING
            )
            coEvery { messageRepositoryImpl.sendMessage("conv1", "Hello", null) } returns
                Resource.Success(dummyMessage)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onInputChange("Hello")
            advanceUntilIdle()
            vm.uiState.value.inputText shouldBe "Hello"

            vm.sendMessage()
            advanceUntilIdle()

            vm.uiState.value.inputText shouldBe ""
        }
    }

    "sendMessage clears reply state on send" {
        runTest {
            val dummyMessage = Message(
                id = "temp1", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", text = "Reply text", type = MessageType.TEXT,
                timestamp = 1000L, status = MessageStatus.PENDING
            )
            coEvery { messageRepositoryImpl.sendMessage("conv1", "Reply text", "msg-original") } returns
                Resource.Success(dummyMessage)

            val vm = createViewModel()
            advanceUntilIdle()

            // Set reply state
            val replyMsg = com.ovi.where.presentation.model.MessageUiModel(
                id = "msg-original",
                senderId = "other-user",
                senderName = "Other",
                senderPhotoUrl = null,
                senderInitials = "O",
                text = "Original message",
                formattedTime = "10:00",
                dateKey = "2024-01-01",
                direction = com.ovi.where.presentation.model.BubbleDirection.RECEIVED,
                isLocation = false,
                latitude = null,
                longitude = null,
                locationLabel = null
            )
            vm.setReplyingTo(replyMsg)
            vm.onInputChange("Reply text")
            advanceUntilIdle()

            vm.uiState.value.replyingToMessage shouldNotBe null

            vm.sendMessage()
            advanceUntilIdle()

            vm.uiState.value.replyingToMessage shouldBe null
            vm.uiState.value.inputText shouldBe ""
        }
    }

    // ─── Retry action per message ─────────────────────────────────────────────

    "retryMessage calls messageRepositoryImpl.retryMessage with the message ID" {
        runTest {
            val retriedMessage = Message(
                id = "msg-failed", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", text = "Failed msg", type = MessageType.TEXT,
                timestamp = 1000L, status = MessageStatus.PENDING
            )
            coEvery { messageRepositoryImpl.retryMessage("msg-failed") } returns
                Resource.Success(retriedMessage)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.retryMessage("msg-failed")
            advanceUntilIdle()

            coVerify(exactly = 1) { messageRepositoryImpl.retryMessage("msg-failed") }
        }
    }

    // ─── Consecutive failure tracking (3 retries → snackbar) ──────────────────

    "3 consecutive send failures emit snackbar event" {
        runTest {
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns
                Resource.Error("Send failed")

            val vm = createViewModel()
            advanceUntilIdle()

            var snackbarReceived: SnackbarEvent? = null
            val collectJob = launch {
                vm.snackbarEvent.first().let { snackbarReceived = it }
            }

            // Trigger 3 consecutive failures
            vm.onInputChange("msg1")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            vm.onInputChange("msg2")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            vm.onInputChange("msg3")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            snackbarReceived shouldNotBe null
            snackbarReceived!!.message shouldBe "Message could not be sent"
            snackbarReceived!!.durationMs shouldBe 4000L

            collectJob.cancel()
        }
    }

    "2 consecutive failures do NOT emit snackbar event" {
        runTest {
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns
                Resource.Error("Send failed")

            val vm = createViewModel()
            advanceUntilIdle()

            var snackbarReceived: SnackbarEvent? = null
            val collectJob = launch {
                vm.snackbarEvent.first().let { snackbarReceived = it }
            }

            // Trigger only 2 consecutive failures
            vm.onInputChange("msg1")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            vm.onInputChange("msg2")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            // Snackbar should NOT have been emitted
            snackbarReceived shouldBe null

            collectJob.cancel()
        }
    }

    "successful send resets consecutive failure counter" {
        runTest {
            val dummyMessage = Message(
                id = "temp1", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", text = "ok", type = MessageType.TEXT,
                timestamp = 1000L, status = MessageStatus.PENDING
            )

            // First 2 calls fail, third succeeds, then 2 more fail
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returnsMany listOf(
                Resource.Error("fail"),
                Resource.Error("fail"),
                Resource.Success(dummyMessage),
                Resource.Error("fail"),
                Resource.Error("fail")
            )

            val vm = createViewModel()
            advanceUntilIdle()

            var snackbarReceived: SnackbarEvent? = null
            val collectJob = launch {
                vm.snackbarEvent.first().let { snackbarReceived = it }
            }

            // 2 failures
            vm.onInputChange("msg1")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            vm.onInputChange("msg2")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            // 1 success (resets counter)
            vm.onInputChange("msg3")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            // 2 more failures (total consecutive = 2, not 3)
            vm.onInputChange("msg4")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            vm.onInputChange("msg5")
            advanceUntilIdle()
            vm.sendMessage()
            advanceUntilIdle()

            // Should NOT have triggered snackbar (never reached 3 consecutive)
            snackbarReceived shouldBe null

            collectJob.cancel()
        }
    }

    "3 consecutive retry failures emit snackbar event" {
        runTest {
            coEvery { messageRepositoryImpl.retryMessage(any()) } returns
                Resource.Error("Retry failed")

            val vm = createViewModel()
            advanceUntilIdle()

            var snackbarReceived: SnackbarEvent? = null
            val collectJob = launch {
                vm.snackbarEvent.first().let { snackbarReceived = it }
            }

            // 3 consecutive retry failures
            vm.retryMessage("msg1")
            advanceUntilIdle()
            vm.retryMessage("msg2")
            advanceUntilIdle()
            vm.retryMessage("msg3")
            advanceUntilIdle()

            snackbarReceived shouldNotBe null
            snackbarReceived!!.message shouldBe "Message could not be sent"

            collectJob.cancel()
        }
    }

    // ─── Reject sends when offline queue is full ──────────────────────────────

    "sendMessage rejects when offline queue is full (50 messages)" {
        runTest {
            every { messageRepositoryImpl.offlineQueueSize } returns 50

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onInputChange("Hello")
            advanceUntilIdle()

            vm.sendMessage()
            advanceUntilIdle()

            // Message should NOT have been sent
            coVerify(exactly = 0) { messageRepositoryImpl.sendMessage(any(), any(), any()) }

            // Queue full indicator should be set
            vm.uiState.value.isOfflineQueueFull shouldBe true
        }
    }

    "sendMessage does not reject when offline queue has room" {
        runTest {
            every { messageRepositoryImpl.offlineQueueSize } returns 49

            val dummyMessage = Message(
                id = "temp1", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", text = "Hello", type = MessageType.TEXT,
                timestamp = 1000L, status = MessageStatus.PENDING
            )
            coEvery { messageRepositoryImpl.sendMessage("conv1", "Hello", null) } returns
                Resource.Success(dummyMessage)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onInputChange("Hello")
            advanceUntilIdle()

            vm.sendMessage()
            advanceUntilIdle()

            // Message should have been sent
            coVerify(exactly = 1) { messageRepositoryImpl.sendMessage("conv1", "Hello", null) }
            vm.uiState.value.isOfflineQueueFull shouldBe false
        }
    }

    // ─── Reply state management ───────────────────────────────────────────────

    "setReplyingTo sets the reply state" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            val replyMsg = com.ovi.where.presentation.model.MessageUiModel(
                id = "msg1",
                senderId = "other",
                senderName = "Other",
                senderPhotoUrl = null,
                senderInitials = "O",
                text = "Original",
                formattedTime = "10:00",
                dateKey = "2024-01-01",
                direction = com.ovi.where.presentation.model.BubbleDirection.RECEIVED,
                isLocation = false,
                latitude = null,
                longitude = null,
                locationLabel = null
            )
            vm.setReplyingTo(replyMsg)

            vm.uiState.value.replyingToMessage shouldBe replyMsg
        }
    }

    "clearReply clears reply state without affecting input text" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            val replyMsg = com.ovi.where.presentation.model.MessageUiModel(
                id = "msg1",
                senderId = "other",
                senderName = "Other",
                senderPhotoUrl = null,
                senderInitials = "O",
                text = "Original",
                formattedTime = "10:00",
                dateKey = "2024-01-01",
                direction = com.ovi.where.presentation.model.BubbleDirection.RECEIVED,
                isLocation = false,
                latitude = null,
                longitude = null,
                locationLabel = null
            )
            vm.setReplyingTo(replyMsg)
            vm.onInputChange("Some text")
            advanceUntilIdle()

            vm.clearReply()

            vm.uiState.value.replyingToMessage shouldBe null
            vm.uiState.value.inputText shouldBe "Some text"
        }
    }

    "sendMessage does nothing when input is empty" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.sendMessage()
            advanceUntilIdle()

            coVerify(exactly = 0) { messageRepositoryImpl.sendMessage(any(), any(), any()) }
        }
    }
})
