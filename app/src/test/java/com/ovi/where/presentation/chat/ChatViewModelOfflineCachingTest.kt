package com.ovi.where.presentation.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.ovi.where.data.location.LocationManager
import com.ovi.where.data.network.ConnectivityObserver
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.repository.MessageRepositoryImpl
import com.ovi.where.domain.model.MessagePage
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.repository.InteractionRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.chat.MarkConversationReadUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.chat.ObserveMessagesUseCase
import com.ovi.where.domain.usecase.chat.SendLocationMessageUseCase
import com.ovi.where.domain.usecase.chat.SendMessageUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * Unit tests for ChatViewModel task 2.4: Aggressive Local Caching Strategy.
 *
 * Tests cover:
 * - Requirement 7.2: Cached locations served from Room within 100ms
 * - Requirement 7.3: Socket.IO subscription with 10s Firestore fallback
 * - Requirement 7.5: Offline mode displays cached data without loading indicators
 * - Requirement 7.6: Offline write action shows "Queued for sync" banner
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelOfflineCachingTest : StringSpec({

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
    lateinit var connectivityState: MutableStateFlow<Boolean>

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

        connectivityState = MutableStateFlow(true) // Online by default
        every { connectivityObserver.isConnected } returns connectivityState

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
        every { observeMessagesUseCase(any()) } returns flowOf(emptyList())
        coEvery { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) } returns
            MessagePage(emptyList(), null, false)

        // Default: empty cached locations
        every { locationRepository.observeCachedLocations() } returns flowOf(emptyList())
        every { locationRepository.observeLocationsWithCacheFallback(any()) } returns flowOf(emptyList())
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createViewModel(conversationId: String = "conv1"): ChatViewModel {
        savedStateHandle = SavedStateHandle().apply {
            set("conversationId", conversationId)
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
            mockk(relaxed = true), // startLocationSharingUseCase
            mockk(relaxed = true), // stopLocationSharingUseCase
            mockk(relaxed = true), // fetchLinkPreviewUseCase
            mockk(relaxed = true), // muteGroupMemberUseCase
            mockk(relaxed = true), // voiceRecorder
            mockk(relaxed = true), // onlineStatusDao
            mockk(relaxed = true)  // userRepository
        )
    }

    // ─── Requirement 7.2: Cached locations served from Room ───────────────────

    "observes cached locations from Room on init" {
        runTest {
            val cachedLocations = listOf(
                SharedLocation(
                    id = "loc1",
                    userId = "friend1",
                    groupId = "group1",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    accuracy = 10f,
                    timestamp = System.currentTimeMillis(),
                    isSharingActive = true
                )
            )
            every { locationRepository.observeCachedLocations() } returns flowOf(cachedLocations)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.cachedLocations shouldBe cachedLocations
        }
    }

    "subscribes to observeLocationsWithCacheFallback with 10s timeout" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            verify { locationRepository.observeLocationsWithCacheFallback(10_000L) }
        }
    }

    // ─── Requirement 7.5: Offline mode without loading indicators ─────────────

    "does not show loading indicator when offline" {
        runTest {
            connectivityState.value = false // Start offline

            val vm = createViewModel()
            advanceUntilIdle()

            // When offline, isLoading should be false (display cached data without spinners)
            vm.uiState.value.isLoading shouldBe false
        }
    }

    "sets isOffline state when connectivity changes" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.isOffline shouldBe false

            connectivityState.value = false
            advanceUntilIdle()

            vm.uiState.value.isOffline shouldBe true
        }
    }

    // ─── Requirement 7.6: Queued for sync banner on offline write ─────────────

    "shows queued for sync banner when sending message while offline" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Go offline
            connectivityState.value = false
            advanceUntilIdle()

            // Type and send a message
            vm.onInputChange("Hello offline")
            vm.sendMessage()
            advanceUntilIdle()

            vm.uiState.value.showQueuedForSyncBanner shouldBe true
        }
    }

    "dismisses queued for sync banner when connectivity is restored" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Go offline and trigger banner
            connectivityState.value = false
            advanceUntilIdle()
            vm.showQueuedForSyncBanner()
            advanceUntilIdle()

            vm.uiState.value.showQueuedForSyncBanner shouldBe true

            // Restore connectivity
            connectivityState.value = true
            advanceUntilIdle()

            vm.uiState.value.showQueuedForSyncBanner shouldBe false
        }
    }

    "dismissQueuedForSyncBanner clears the banner state" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.showQueuedForSyncBanner()
            vm.uiState.value.showQueuedForSyncBanner shouldBe true

            vm.dismissQueuedForSyncBanner()
            vm.uiState.value.showQueuedForSyncBanner shouldBe false
        }
    }

    // ─── Requirement 7.3: Live locations update from Socket.IO with fallback ──

    "updates cachedLocations when live location flow emits" {
        runTest {
            val liveLocations = listOf(
                SharedLocation(
                    id = "loc2",
                    userId = "friend2",
                    groupId = "group1",
                    latitude = 40.7128,
                    longitude = -74.0060,
                    accuracy = 5f,
                    timestamp = System.currentTimeMillis(),
                    isSharingActive = true
                )
            )
            every { locationRepository.observeLocationsWithCacheFallback(any()) } returns flowOf(liveLocations)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.cachedLocations shouldBe liveLocations
        }
    }
})
