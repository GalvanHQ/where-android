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
import com.ovi.where.domain.model.MessagePage
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
import io.mockk.verify
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
 * Unit tests for ChatViewModel task 6.7:
 * - Observe ChatSocketIoClient.connectionState
 * - Expose reconnecting banner state (show within 500ms of disconnect)
 * - After 10 failed attempts: show manual retry action
 * - On reconnect: fetch missed messages via REST, flush queue, hide banner with 300ms fade
 *
 * Requirements: 13.1, 13.3, 13.4, 13.5, 13.6, 13.7
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelConnectionStateTest : StringSpec({

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
    lateinit var connectionStateFlow: MutableStateFlow<ChatSocketIoClient.ConnectionState>
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
        connectionStateFlow = MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)
        every { wsClient.connectionState } returns connectionStateFlow

        every { observeConversationsUseCase() } returns flowOf(emptyList())
        every { observeMessagesUseCase(any()) } returns flowOf(emptyList())
        coEvery { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) } returns
            MessagePage(emptyList(), null, false)
        coEvery { messageRepositoryImpl.fetchMissedMessages(any()) } returns Resource.Success(Unit)
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
            wsClient,
            messageRepositoryImpl,
            firebaseAuth,
            locationManager,
            friendshipRepository,
            interactionRepository
        )
    }

    // ─── Requirement 13.1: Show reconnecting banner within 500ms of disconnect ─

    "shows reconnecting banner within 500ms of DISCONNECTED state" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Initially no banner
            vm.uiState.value.showReconnectingBanner shouldBe false

            // Simulate disconnect
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.DISCONNECTED
            advanceUntilIdle()

            // Before 500ms, banner should not be shown yet
            // (advanceUntilIdle already advances past the delay in test dispatcher)
            vm.uiState.value.showReconnectingBanner shouldBe true
        }
    }

    "does not show banner before 500ms delay elapses" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Simulate disconnect
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.DISCONNECTED

            // Advance only 400ms - banner should not be visible yet
            advanceTimeBy(400)
            vm.uiState.value.showReconnectingBanner shouldBe false

            // Advance past 500ms - banner should now be visible
            advanceTimeBy(200)
            vm.uiState.value.showReconnectingBanner shouldBe true
        }
    }

    // ─── Requirement 13.3: After 10 failed attempts, show manual retry action ──

    "shows manual retry action when connection state transitions to ERROR" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Simulate ERROR state (exhausted reconnection attempts)
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.ERROR
            advanceUntilIdle()

            vm.uiState.value.showReconnectingBanner shouldBe true
            vm.uiState.value.showManualRetryAction shouldBe true
            vm.uiState.value.reconnectAttempts shouldBe ChatSocketIoClient.MAX_RECONNECT_ATTEMPTS
        }
    }

    "ERROR state shows banner immediately without waiting for 500ms delay" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Simulate ERROR state
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.ERROR

            // Even without advancing time much, the banner should show immediately for ERROR
            advanceTimeBy(10)
            vm.uiState.value.showReconnectingBanner shouldBe true
            vm.uiState.value.showManualRetryAction shouldBe true
        }
    }

    // ─── Requirement 13.7: On reconnect, hide banner with 300ms fade ───────────

    "hides banner with fade-out on reconnect after being disconnected" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Simulate disconnect then reconnect
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.DISCONNECTED
            advanceUntilIdle()
            vm.uiState.value.showReconnectingBanner shouldBe true

            // Reconnect
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.CONNECTED
            advanceTimeBy(100)

            // Banner should be fading out
            vm.uiState.value.isBannerFadingOut shouldBe true

            // After 300ms fade completes
            advanceTimeBy(300)
            vm.uiState.value.showReconnectingBanner shouldBe false
            vm.uiState.value.isBannerFadingOut shouldBe false
        }
    }

    // ─── Requirement 13.5: On reconnect, fetch missed messages via REST ────────

    "fetches missed messages on reconnect" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Simulate disconnect then reconnect
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.DISCONNECTED
            advanceUntilIdle()

            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.CONNECTED
            advanceUntilIdle()

            coVerify { messageRepositoryImpl.fetchMissedMessages("conv1") }
        }
    }

    // ─── Requirement 13.6: Show inline retry if missed messages fetch fails ────

    "shows missedMessagesFetchFailed when fetch fails on reconnect" {
        runTest {
            coEvery { messageRepositoryImpl.fetchMissedMessages(any()) } returns
                Resource.Error("Network error")

            val vm = createViewModel()
            advanceUntilIdle()

            // Simulate disconnect then reconnect
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.DISCONNECTED
            advanceUntilIdle()

            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.CONNECTED
            advanceUntilIdle()

            vm.uiState.value.missedMessagesFetchFailed shouldBe true
        }
    }

    "retryFetchMissedMessages clears error and retries" {
        runTest {
            coEvery { messageRepositoryImpl.fetchMissedMessages(any()) } returns
                Resource.Error("Network error") andThen Resource.Success(Unit)

            val vm = createViewModel()
            advanceUntilIdle()

            // Simulate disconnect then reconnect (first fetch fails)
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.DISCONNECTED
            advanceUntilIdle()
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.CONNECTED
            advanceUntilIdle()

            vm.uiState.value.missedMessagesFetchFailed shouldBe true

            // Retry (second call succeeds)
            vm.retryFetchMissedMessages()
            advanceUntilIdle()

            vm.uiState.value.missedMessagesFetchFailed shouldBe false
        }
    }

    // ─── manualRetry() ─────────────────────────────────────────────────────────

    "manualRetry clears retry action and calls wsClient.manualReconnect" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Simulate ERROR state (exhausted attempts)
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.ERROR
            advanceUntilIdle()

            vm.uiState.value.showManualRetryAction shouldBe true

            // User taps retry
            vm.manualRetry()

            vm.uiState.value.showManualRetryAction shouldBe false
            vm.uiState.value.reconnectAttempts shouldBe 0
            verify { wsClient.manualReconnect() }
        }
    }

    // ─── Initial connection (no banner) ────────────────────────────────────────

    "initial CONNECTED state does not show banner" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.showReconnectingBanner shouldBe false
            vm.uiState.value.showManualRetryAction shouldBe false
            vm.uiState.value.isBannerFadingOut shouldBe false
        }
    }

    // ─── Banner cancellation on quick reconnect ────────────────────────────────

    "cancels banner show if reconnected before 500ms delay" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Disconnect
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.DISCONNECTED
            advanceTimeBy(200) // Only 200ms elapsed

            // Quick reconnect before 500ms
            connectionStateFlow.value = ChatSocketIoClient.ConnectionState.CONNECTED
            advanceUntilIdle()

            // Banner should never have been shown (or already hidden)
            vm.uiState.value.showReconnectingBanner shouldBe false
        }
    }
})
