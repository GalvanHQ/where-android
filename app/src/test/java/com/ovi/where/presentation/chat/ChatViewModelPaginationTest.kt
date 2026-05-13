package com.ovi.where.presentation.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
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
 * Unit tests for ChatViewModel task 6.2:
 * - Pagination trigger (loadOlderMessages on scroll-to-top)
 * - Guard against concurrent pagination requests (isLoadingMore flag)
 * - Update paginationCursor and hasMoreMessages from server response
 * - Stop requesting when hasMore is false
 *
 * Requirements: 2.2, 2.3, 2.6
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPaginationTest : StringSpec({

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
        every { wsClient.connectionState } returns MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)

        // Default: observeMessages returns empty flow, observeConversations returns empty
        every { observeMessagesUseCase(any()) } returns flowOf(emptyList())
        every { observeConversationsUseCase() } returns flowOf(emptyList())
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
            friendshipRepository
        )
    }

    "loadOlderMessages sets isLoadingMore to true while request is in flight" {
        runTest {
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), "1000", true)
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", "1000", 30) } returns
                MessagePage(emptyList(), "500", true)

            val vm = createViewModel()
            advanceUntilIdle()

            // Verify initial state after init
            vm.uiState.value.hasMoreMessages shouldBe true
            vm.uiState.value.paginationCursor shouldBe "1000"
            vm.uiState.value.isLoadingMore shouldBe false

            // Trigger pagination
            vm.loadOlderMessages()

            // isLoadingMore should be true immediately
            vm.uiState.value.isLoadingMore shouldBe true

            advanceUntilIdle()

            // After completion, isLoadingMore should be false
            vm.uiState.value.isLoadingMore shouldBe false
        }
    }

    "loadOlderMessages updates paginationCursor from server response" {
        runTest {
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), "1000", true)
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", "1000", 30) } returns
                MessagePage(emptyList(), "500", true)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.paginationCursor shouldBe "1000"

            vm.loadOlderMessages()
            advanceUntilIdle()

            vm.uiState.value.paginationCursor shouldBe "500"
        }
    }

    "loadOlderMessages updates hasMoreMessages from server response" {
        runTest {
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), "1000", true)
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", "1000", 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.hasMoreMessages shouldBe true

            vm.loadOlderMessages()
            advanceUntilIdle()

            vm.uiState.value.hasMoreMessages shouldBe false
        }
    }

    "loadOlderMessages does nothing when hasMoreMessages is false" {
        runTest {
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), null, false)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.hasMoreMessages shouldBe false

            // Attempt to load more - should be a no-op
            vm.loadOlderMessages()
            advanceUntilIdle()

            // Should only have been called once (the initial load with null cursor)
            coVerify(exactly = 1) { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) }
            coVerify(exactly = 0) { messageRepositoryImpl.loadOlderMessages("conv1", any<String>(), 30) }
        }
    }

    "loadOlderMessages guards against concurrent requests" {
        runTest {
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", null, 30) } returns
                MessagePage(emptyList(), "1000", true)
            coEvery { messageRepositoryImpl.loadOlderMessages("conv1", "1000", 30) } returns
                MessagePage(emptyList(), "500", true)

            val vm = createViewModel()
            advanceUntilIdle()

            // Trigger first pagination
            vm.loadOlderMessages()

            // Immediately trigger second pagination (should be ignored due to isLoadingMore)
            vm.loadOlderMessages()

            advanceUntilIdle()

            // Should only have been called once with cursor "1000"
            coVerify(exactly = 1) { messageRepositoryImpl.loadOlderMessages("conv1", "1000", 30) }
        }
    }

    "loadOlderMessages does nothing when conversationId is null" {
        runTest {
            val vm = createViewModel(convId = null)
            advanceUntilIdle()

            vm.loadOlderMessages()
            advanceUntilIdle()

            // Should not call repository at all (no conversationId set)
            coVerify(exactly = 0) { messageRepositoryImpl.loadOlderMessages(any(), any(), any()) }
        }
    }
})
