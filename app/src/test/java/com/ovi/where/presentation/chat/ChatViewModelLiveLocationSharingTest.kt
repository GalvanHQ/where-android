package com.ovi.where.presentation.chat

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
import com.ovi.where.domain.model.MessagePage
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.repository.InteractionRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.chat.MarkConversationReadUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.chat.ObserveMessagesUseCase
import com.ovi.where.domain.usecase.chat.SendLocationMessageUseCase
import com.ovi.where.domain.usecase.chat.SendMessageUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Unit tests for ChatViewModel Task 3.2: Live location sharing UI flow.
 *
 * Tests cover:
 * - Bottom sheet state management (Requirement 1.1)
 * - Hide "Share Live Location" for 1:1 conversations (Requirement 1.12)
 * - Duration picker with default 1h (Requirement 1.2)
 * - Start sharing flow: use case invocation, banner, timer (Requirements 1.3, 1.4)
 * - Stop sharing flow (Requirement 1.5)
 * - Permission handling (Requirements 1.8, 1.10)
 * - Error handling (Requirement 1.11)
 * - Time remaining formatting (Requirement 1.4)
 * - Timer auto-expiry (Requirement 1.9)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelLiveLocationSharingTest : StringSpec({
    timeout = 10000 // 10 second timeout per test to prevent hanging

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
    lateinit var friendshipRepository: FriendshipRepository
    lateinit var interactionRepository: InteractionRepository
    lateinit var locationRepository: LocationRepository
    lateinit var connectivityObserver: ConnectivityObserver
    lateinit var startLocationSharingUseCase: StartLocationSharingUseCase
    lateinit var stopLocationSharingUseCase: StopLocationSharingUseCase

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
        startLocationSharingUseCase = mockk(relaxed = true)
        stopLocationSharingUseCase = mockk(relaxed = true)

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
        coEvery { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) } returns
            MessagePage(emptyList(), null, false)
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createGroupConversation(): Conversation {
        return Conversation(
            id = "conv1",
            name = "Test Group",
            type = ConversationType.GROUP,
            groupId = "group123",
            participantIds = listOf("user123", "user456", "user789"),
            lastMessageText = "Hello",
            lastMessageTimestamp = System.currentTimeMillis()
        )
    }

    fun createDirectConversation(): Conversation {
        return Conversation(
            id = "conv1",
            name = "John",
            type = ConversationType.DIRECT,
            groupId = null,
            participantIds = listOf("user123", "user456"),
            lastMessageText = "Hi",
            lastMessageTimestamp = System.currentTimeMillis()
        )
    }

    fun createViewModel(conversation: Conversation? = null): ChatViewModel {
        if (conversation != null) {
            every { observeConversationsUseCase() } returns flowOf(listOf(conversation))
        } else {
            every { observeConversationsUseCase() } returns flowOf(emptyList())
        }

        savedStateHandle = SavedStateHandle().apply {
            set("conversationId", "conv1")
        }
        return ChatViewModel(
            application,
            savedStateHandle,
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
            startLocationSharingUseCase,
            stopLocationSharingUseCase,
            mockk(relaxed = true), // fetchLinkPreviewUseCase
            mockk(relaxed = true), // muteGroupMemberUseCase
            mockk(relaxed = true), // voiceRecorder
            mockk(relaxed = true), // onlineStatusDao
            mockk(relaxed = true)  // userRepository
        ).also { vm ->
            // Use a testable clock that advances with each call to simulate time passing.
            // Starts at a fixed time and advances by 60s per call to prevent infinite loops.
            var testTime = 1_000_000_000L
            vm.clock = {
                val current = testTime
                testTime += 60_000L // advance 60s per call
                current
            }
        }
    }

    // ─── Requirement 1.1: Bottom sheet with options ───────────────────────────

    "onLocationShareButtonTap shows bottom sheet in OPTIONS state for group conversation" {
        runTest {
            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()

            vm.uiState.value.locationBottomSheetState shouldBe LocationBottomSheetState.OPTIONS
        }
    }

    "onLocationShareButtonTap shows showLiveLocationOption=true for group conversation" {
        runTest {
            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()

            vm.uiState.value.showLiveLocationOption shouldBe true
        }
    }

    // ─── Requirement 1.12: Hide live location for 1:1 direct ──────────────────

    "onLocationShareButtonTap hides live location option for 1:1 direct conversation" {
        runTest {
            val vm = createViewModel(createDirectConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()

            vm.uiState.value.showLiveLocationOption shouldBe false
            vm.uiState.value.locationBottomSheetState shouldBe LocationBottomSheetState.OPTIONS
        }
    }

    // ─── Requirement 1.2: Duration picker ─────────────────────────────────────

    "onShareLiveLocationSelected shows duration picker with default 1h" {
        runTest {
            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()

            vm.uiState.value.locationBottomSheetState shouldBe LocationBottomSheetState.DURATION_PICKER
            vm.uiState.value.selectedDurationMinutes shouldBe 60L
        }
    }

    "onDurationSelected updates selected duration" {
        runTest {
            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onDurationSelected(240L) // 4 hours

            vm.uiState.value.selectedDurationMinutes shouldBe 240L
        }
    }

    // ─── Requirement 1.8: Permission handling ─────────────────────────────────

    "onConfirmLiveLocationSharing sets liveLocationPermissionNeeded when permission not granted" {
        runTest {
            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onConfirmLiveLocationSharing(hasLocationPermission = false)

            vm.uiState.value.liveLocationPermissionNeeded shouldBe true
        }
    }

    "onLiveLocationPermissionGranted clears permission flag and starts sharing" {
        runTest {
            coEvery { startLocationSharingUseCase(any(), any()) } returns Resource.Success(Unit)
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns Resource.Success(mockk())

            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onConfirmLiveLocationSharing(hasLocationPermission = false)
            vm.uiState.value.liveLocationPermissionNeeded shouldBe true

            vm.onLiveLocationPermissionGranted()
            advanceUntilIdle()

            vm.uiState.value.liveLocationPermissionNeeded shouldBe false
            vm.uiState.value.isLiveLocationSharingActive shouldBe true
        }
    }

    // ─── Requirement 1.10: Permission denied ──────────────────────────────────

    "onLiveLocationPermissionDenied shows error and does not start session" {
        runTest {
            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onConfirmLiveLocationSharing(hasLocationPermission = false)
            vm.onLiveLocationPermissionDenied()

            vm.uiState.value.liveLocationPermissionNeeded shouldBe false
            vm.uiState.value.locationBottomSheetState shouldBe LocationBottomSheetState.HIDDEN
            vm.uiState.value.liveLocationError shouldNotBe null
            vm.uiState.value.isLiveLocationSharingActive shouldBe false
        }
    }

    // ─── Requirement 1.3: Confirm starts sharing ──────────────────────────────

    "onConfirmLiveLocationSharing with permission invokes use case and activates banner" {
        runTest {
            coEvery { startLocationSharingUseCase("group123", 60L) } returns Resource.Success(Unit)
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns Resource.Success(mockk())

            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onConfirmLiveLocationSharing(hasLocationPermission = true)
            advanceUntilIdle()

            coVerify { startLocationSharingUseCase("group123", 60L) }
            vm.uiState.value.locationBottomSheetState shouldBe LocationBottomSheetState.HIDDEN
            vm.uiState.value.isLiveLocationSharingActive shouldBe true
            vm.uiState.value.liveLocationTimeRemaining shouldBe "1h"
        }
    }

    // ─── Requirement 1.11: Use case error ─────────────────────────────────────

    "shows error when StartLocationSharingUseCase fails" {
        runTest {
            coEvery { startLocationSharingUseCase(any(), any()) } returns Resource.Error("Network error")

            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onConfirmLiveLocationSharing(hasLocationPermission = true)
            advanceUntilIdle()

            vm.uiState.value.locationBottomSheetState shouldBe LocationBottomSheetState.HIDDEN
            vm.uiState.value.liveLocationError shouldBe "Network error"
            vm.uiState.value.isLiveLocationSharingActive shouldBe false
        }
    }

    // ─── Requirement 1.5: Stop sharing ────────────────────────────────────────

    "stopLiveLocationSharing invokes use case and removes banner" {
        runTest {
            coEvery { startLocationSharingUseCase(any(), any()) } returns Resource.Success(Unit)
            coEvery { stopLocationSharingUseCase("group123") } returns Resource.Success(Unit)
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns Resource.Success(mockk())

            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            // Start sharing first
            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onConfirmLiveLocationSharing(hasLocationPermission = true)
            advanceUntilIdle()
            vm.uiState.value.isLiveLocationSharingActive shouldBe true

            // Stop sharing
            vm.stopLiveLocationSharing()
            advanceUntilIdle()

            coVerify { stopLocationSharingUseCase("group123") }
            vm.uiState.value.isLiveLocationSharingActive shouldBe false
            vm.uiState.value.liveLocationTimeRemaining shouldBe null
        }
    }

    // ─── Requirement 1.4: Time remaining formatting ───────────────────────────

    "formatTimeRemaining returns Xh Ym for durations >= 60 minutes" {
        ChatViewModel.formatTimeRemaining(90L) shouldBe "1h 30m"
        ChatViewModel.formatTimeRemaining(120L) shouldBe "2h"
        ChatViewModel.formatTimeRemaining(150L) shouldBe "2h 30m"
        ChatViewModel.formatTimeRemaining(60L) shouldBe "1h"
        ChatViewModel.formatTimeRemaining(480L) shouldBe "8h"
    }

    "formatTimeRemaining returns Xm for durations < 60 minutes" {
        ChatViewModel.formatTimeRemaining(15L) shouldBe "15m"
        ChatViewModel.formatTimeRemaining(45L) shouldBe "45m"
        ChatViewModel.formatTimeRemaining(1L) shouldBe "1m"
        ChatViewModel.formatTimeRemaining(59L) shouldBe "59m"
    }

    // ─── Requirement 1.4: Timer updates every 60 seconds ──────────────────────

    "timer updates timeRemaining every 60 seconds" {
        runTest {
            coEvery { startLocationSharingUseCase(any(), any()) } returns Resource.Success(Unit)
            coEvery { messageRepositoryImpl.sendMessage(any(), any(), any()) } returns Resource.Success(mockk())

            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            // Start sharing with 2h duration
            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onDurationSelected(120L)
            vm.onConfirmLiveLocationSharing(hasLocationPermission = true)
            advanceUntilIdle()

            vm.uiState.value.liveLocationTimeRemaining shouldBe "2h"

            // Advance 60 seconds - timer should update
            advanceTimeBy(60_001L)

            // After 1 minute, remaining should be ~119 minutes = "1h 59m"
            val remaining = vm.uiState.value.liveLocationTimeRemaining
            remaining shouldNotBe null
        }
    }

    // ─── Bottom sheet dismiss ─────────────────────────────────────────────────

    "dismissLocationBottomSheet hides the bottom sheet" {
        runTest {
            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.uiState.value.locationBottomSheetState shouldBe LocationBottomSheetState.OPTIONS

            vm.dismissLocationBottomSheet()
            vm.uiState.value.locationBottomSheetState shouldBe LocationBottomSheetState.HIDDEN
        }
    }

    // ─── Error dismissal ──────────────────────────────────────────────────────

    "dismissLiveLocationError clears the error" {
        runTest {
            coEvery { startLocationSharingUseCase(any(), any()) } returns Resource.Error("Failed")

            val vm = createViewModel(createGroupConversation())
            advanceUntilIdle()

            vm.onLocationShareButtonTap()
            vm.onShareLiveLocationSelected()
            vm.onConfirmLiveLocationSharing(hasLocationPermission = true)
            advanceUntilIdle()

            vm.uiState.value.liveLocationError shouldNotBe null

            vm.dismissLiveLocationError()
            vm.uiState.value.liveLocationError shouldBe null
        }
    }
})
