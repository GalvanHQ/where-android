package com.ovi.where.presentation.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.ovi.where.data.location.LocationManager
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.data.remote.chat.TypingIndicatorManager
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
 * Unit tests for ChatViewModel task 6.5: Typing indicator state management.
 *
 * - Observe incoming typing frames, display "{name} is typing…"
 * - Auto-hide after 5 seconds of no typing event from a user
 * - For groups: show at most 2 names + "+N are typing…"
 * - Emit typing events on keystroke (debounced 300ms)
 *
 * Requirements: 7.1, 7.2, 7.5, 7.6
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTypingIndicatorTest : StringSpec({

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
    lateinit var incomingFrames: MutableSharedFlow<ServerFrame>
    lateinit var typingIndicatorManager: TypingIndicatorManager
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
        typingIndicatorManager = mockk(relaxed = true)
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

        incomingFrames = MutableSharedFlow(extraBufferCapacity = 64)
        every { wsClient.incomingFrames } returns incomingFrames
        every { wsClient.connectionState } returns MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)
        every { wsClient.typingIndicatorManager } returns typingIndicatorManager

        every { observeMessagesUseCase(any()) } returns flowOf(emptyList())
        every { observeConversationsUseCase() } returns flowOf(emptyList())

        coEvery { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) } returns
            MessagePage(emptyList(), null, false)
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

    // ─── Observe incoming typing frames (Requirement 7.2) ─────────────────────

    "single user typing shows '{name} is typing…'" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()

            vm.uiState.value.typingIndicatorText shouldBe "Alice is typing\u2026"
        }
    }

    "typing event from current user is ignored" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Emit typing from the current user (user123)
            incomingFrames.emit(ServerFrame.UserTyping(userId = "user123", userName = "Test User", isTyping = true))
            advanceUntilIdle()

            vm.uiState.value.typingIndicatorText shouldBe null
        }
    }

    "stop-typing event removes user from indicator" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // User starts typing
            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()
            vm.uiState.value.typingIndicatorText shouldBe "Alice is typing\u2026"

            // User stops typing
            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = false))
            advanceUntilIdle()

            vm.uiState.value.typingIndicatorText shouldBe null
        }
    }

    // ─── Auto-hide after 5 seconds (Requirement 7.5) ──────────────────────────

    "typing indicator auto-hides after 5 seconds of no typing event" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()
            vm.uiState.value.typingIndicatorText shouldBe "Alice is typing\u2026"

            // Advance time by 5 seconds (TYPING_TIMEOUT_MS)
            advanceTimeBy(5_001L)
            advanceUntilIdle()

            vm.uiState.value.typingIndicatorText shouldBe null
        }
    }

    "typing event resets the 5-second timeout" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()

            // Advance 3 seconds (not yet timed out)
            advanceTimeBy(3_000L)
            advanceUntilIdle()
            vm.uiState.value.typingIndicatorText shouldBe "Alice is typing\u2026"

            // Another typing event resets the timer
            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()

            // Advance another 3 seconds (6s total from first, but only 3s from last event)
            advanceTimeBy(3_000L)
            advanceUntilIdle()
            vm.uiState.value.typingIndicatorText shouldBe "Alice is typing\u2026"

            // Advance 2 more seconds (5s from last event) → should auto-hide
            advanceTimeBy(2_001L)
            advanceUntilIdle()
            vm.uiState.value.typingIndicatorText shouldBe null
        }
    }

    // ─── Group typing: 2 names + "+N are typing…" (Requirement 7.6) ───────────

    "two users typing shows '{name1}, {name2} are typing…'" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()
            incomingFrames.emit(ServerFrame.UserTyping(userId = "bob2", userName = "Bob", isTyping = true))
            advanceUntilIdle()

            vm.uiState.value.typingIndicatorText shouldBe "Alice, Bob are typing\u2026"
        }
    }

    "three or more users typing shows '{name1}, {name2} +N are typing…'" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()
            incomingFrames.emit(ServerFrame.UserTyping(userId = "bob2", userName = "Bob", isTyping = true))
            advanceUntilIdle()
            incomingFrames.emit(ServerFrame.UserTyping(userId = "charlie3", userName = "Charlie", isTyping = true))
            advanceUntilIdle()

            vm.uiState.value.typingIndicatorText shouldBe "Alice, Bob +1 are typing\u2026"
        }
    }

    "four users typing shows '+2' suffix" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()
            incomingFrames.emit(ServerFrame.UserTyping(userId = "bob2", userName = "Bob", isTyping = true))
            advanceUntilIdle()
            incomingFrames.emit(ServerFrame.UserTyping(userId = "charlie3", userName = "Charlie", isTyping = true))
            advanceUntilIdle()
            incomingFrames.emit(ServerFrame.UserTyping(userId = "dave4", userName = "Dave", isTyping = true))
            advanceUntilIdle()

            vm.uiState.value.typingIndicatorText shouldBe "Alice, Bob +2 are typing\u2026"
        }
    }

    "one user stops typing in a group updates the indicator" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = true))
            advanceUntilIdle()
            incomingFrames.emit(ServerFrame.UserTyping(userId = "bob2", userName = "Bob", isTyping = true))
            advanceUntilIdle()
            vm.uiState.value.typingIndicatorText shouldBe "Alice, Bob are typing\u2026"

            // Alice stops typing
            incomingFrames.emit(ServerFrame.UserTyping(userId = "alice1", userName = "Alice", isTyping = false))
            advanceUntilIdle()

            vm.uiState.value.typingIndicatorText shouldBe "Bob is typing\u2026"
        }
    }

    // ─── Emit typing events on keystroke (Requirement 7.1) ────────────────────

    "onInputChange with non-empty text calls typingIndicatorManager.onKeystroke" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onInputChange("H")
            advanceUntilIdle()

            verify(exactly = 1) { typingIndicatorManager.onKeystroke() }
        }
    }

    "onInputChange with empty text calls typingIndicatorManager.onMessageSentOrInputCleared" {
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // First type something
            vm.onInputChange("Hello")
            advanceUntilIdle()

            // Then clear it
            vm.onInputChange("")
            advanceUntilIdle()

            verify(exactly = 1) { typingIndicatorManager.onMessageSentOrInputCleared() }
        }
    }

    // ─── formatTypingIndicatorText companion function tests ───────────────────

    "formatTypingIndicatorText returns null for empty list" {
        ChatViewModel.formatTypingIndicatorText(emptyList()) shouldBe null
    }

    "formatTypingIndicatorText returns '{name} is typing…' for single user" {
        ChatViewModel.formatTypingIndicatorText(listOf("Alice")) shouldBe "Alice is typing\u2026"
    }

    "formatTypingIndicatorText returns '{name1}, {name2} are typing…' for two users" {
        ChatViewModel.formatTypingIndicatorText(listOf("Alice", "Bob")) shouldBe "Alice, Bob are typing\u2026"
    }

    "formatTypingIndicatorText returns '{name1}, {name2} +N are typing…' for 3+ users" {
        ChatViewModel.formatTypingIndicatorText(listOf("Alice", "Bob", "Charlie")) shouldBe "Alice, Bob +1 are typing\u2026"
    }

    "formatTypingIndicatorText handles 5 users correctly" {
        ChatViewModel.formatTypingIndicatorText(listOf("A", "B", "C", "D", "E")) shouldBe "A, B +3 are typing\u2026"
    }
})
