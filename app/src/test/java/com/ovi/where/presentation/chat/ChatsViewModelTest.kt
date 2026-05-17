package com.ovi.where.presentation.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.ovi.where.data.local.dao.OnlineStatusDao
import com.ovi.where.data.local.prefs.RecentSearchesStore
import com.ovi.where.data.network.ConnectivityObserver
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.GetSuggestionsUseCase
import com.ovi.where.domain.usecase.SearchChatsUseCase
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.location.ObserveActiveLocationsUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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
import com.ovi.where.data.util.Resource as DataResource

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelTest : StringSpec({

    lateinit var observeConversationsUseCase: ObserveConversationsUseCase
    lateinit var getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase
    lateinit var chatSocketIoClient: ChatSocketIoClient
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var firebaseUser: FirebaseUser
    lateinit var conversationRepository: ConversationRepository
    lateinit var userRepository: UserRepository
    lateinit var searchChatsUseCase: SearchChatsUseCase
    lateinit var recentSearchesStore: RecentSearchesStore
    lateinit var getSuggestionsUseCase: GetSuggestionsUseCase
    lateinit var onlineStatusDao: OnlineStatusDao
    lateinit var connectivityObserver: ConnectivityObserver
    lateinit var observeActiveLocationsUseCase: ObserveActiveLocationsUseCase
    lateinit var incomingFrames: MutableSharedFlow<ServerFrame>
    lateinit var connectionState: MutableStateFlow<ChatSocketIoClient.ConnectionState>

    val testDispatcher = StandardTestDispatcher()

    beforeEach {
        Dispatchers.setMain(testDispatcher)
        observeConversationsUseCase = mockk(relaxed = true)
        getOrCreateDirectConversationUseCase = mockk(relaxed = true)
        chatSocketIoClient = mockk(relaxed = true)
        firebaseAuth = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)
        conversationRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        searchChatsUseCase = mockk(relaxed = true)
        recentSearchesStore = mockk(relaxed = true)
        getSuggestionsUseCase = mockk(relaxed = true)
        onlineStatusDao = mockk(relaxed = true)
        connectivityObserver = mockk(relaxed = true)
        observeActiveLocationsUseCase = mockk(relaxed = true)
        incomingFrames = MutableSharedFlow(extraBufferCapacity = 64)
        connectionState = MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "user1"
        every { chatSocketIoClient.incomingFrames } returns incomingFrames
        every { chatSocketIoClient.connectionState } returns connectionState
        every { connectivityObserver.isConnected } returns MutableStateFlow(true)
        every { recentSearchesStore.getRecentSearches(any()) } returns flowOf(emptyList())
        every { getSuggestionsUseCase() } returns flowOf(emptyList())
        every { observeActiveLocationsUseCase() } returns flowOf(emptyList())
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createConversation(
        id: String,
        name: String,
        lastMessageText: String = "",
        lastMessageTimestamp: Long = 0L,
        type: ConversationType = ConversationType.DIRECT,
        participantIds: List<String> = listOf("user1", "user2"),
        unreadCounts: Map<String, Int> = emptyMap()
    ) = Conversation(
        id = id,
        name = name,
        type = type,
        participantIds = participantIds,
        lastMessageText = lastMessageText,
        lastMessageTimestamp = lastMessageTimestamp,
        unreadCounts = unreadCounts
    )

    fun createViewModel() = ChatsViewModel(
        observeConversationsUseCase,
        getOrCreateDirectConversationUseCase,
        dagger.Lazy { chatSocketIoClient },
        firebaseAuth,
        conversationRepository,
        userRepository,
        searchChatsUseCase,
        recentSearchesStore,
        getSuggestionsUseCase,
        onlineStatusDao,
        connectivityObserver,
        observeActiveLocationsUseCase
    )

    /** Helper to mock getConversationsResource with a Success result */
    fun mockConversationsResource(conversations: List<Conversation>) {
        every { conversationRepository.getConversationsResource() } returns
            flowOf(DataResource.Success(conversations))
    }

    /** Helper to mock getConversationsResource with Loading then Success */
    fun mockConversationsResourceWithLoading(conversations: List<Conversation>) {
        every { conversationRepository.getConversationsResource() } returns
            flowOf(
                DataResource.Loading(conversations),
                DataResource.Success(conversations)
            )
    }

    // ── Fast First Paint Tests (Requirements 20.2, 20.3) ────────────────────

    "fast first paint: emits cached conversations immediately when cache non-empty (Req 20.2)" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation("c1", "Alice", lastMessageTimestamp = 2000L),
                createConversation("c2", "Bob", lastMessageTimestamp = 1000L)
            )

            // Simulate Room having cached data (Loading emits with data)
            mockConversationsResourceWithLoading(conversations)

            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            state.conversations.size shouldBe 2
            state.isLoading shouldBe false
        }
    }

    "fast first paint: emits loading state when cache is empty (Req 20.3)" {
        runTest(testDispatcher) {
            // Simulate empty Room cache (Loading emits with empty list)
            every { conversationRepository.getConversationsResource() } returns
                flowOf(DataResource.Loading(emptyList<Conversation>()))

            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            state.isLoading shouldBe true
            state.conversations shouldBe emptyList()
        }
    }

    "fast first paint: shows empty list after 10s timeout when network fails (Req 20.3)" {
        runTest(testDispatcher) {
            // Simulate empty Room cache that never gets populated
            every { conversationRepository.getConversationsResource() } returns
                flowOf(DataResource.Loading(emptyList<Conversation>()))
            // fetchInitialConversationsIfNeeded never completes (simulates network hang)
            coEvery { conversationRepository.fetchInitialConversationsIfNeeded() } coAnswers {
                kotlinx.coroutines.delay(20_000L) // Longer than timeout
                com.ovi.where.core.common.Resource.Success(Unit)
            }

            val vm = createViewModel()
            advanceUntilIdle()

            // Before timeout: still loading
            vm.uiState.value.isLoading shouldBe true

            // Advance past the 10s timeout
            advanceTimeBy(10_001L)
            advanceUntilIdle()

            // After timeout: loading dismissed, empty list shown
            vm.uiState.value.isLoading shouldBe false
            vm.uiState.value.conversations shouldBe emptyList()
        }
    }

    "fast first paint: network fetch completes before timeout shows conversations (Req 20.3)" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation("c1", "Alice", lastMessageTimestamp = 1000L)
            )

            // First emit Loading with empty, then Success with data (simulates fetch completing)
            every { conversationRepository.getConversationsResource() } returns
                flowOf(
                    DataResource.Loading(emptyList()),
                    DataResource.Success(conversations)
                )

            val vm = createViewModel()
            advanceUntilIdle()

            // Network fetch completed, conversations should be shown
            val state = vm.uiState.value
            state.isLoading shouldBe false
            state.conversations.size shouldBe 1
            state.conversations[0].title shouldBe "Alice"
        }
    }

    // ── Existing Tests (updated for new constructor) ────────────────────────

    "conversations are sorted by lastMessageTimestamp DESC from repository" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation("c1", "Alice", lastMessageTimestamp = 1000L),
                createConversation("c2", "Bob", lastMessageTimestamp = 3000L),
                createConversation("c3", "Charlie", lastMessageTimestamp = 2000L)
            ).sortedByDescending { it.lastMessageTimestamp }

            mockConversationsResource(conversations)

            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            state.conversations.size shouldBe 3
            state.conversations[0].title shouldBe "Bob"
            state.conversations[1].title shouldBe "Charlie"
            state.conversations[2].title shouldBe "Alice"
            state.isLoading shouldBe false
        }
    }

    "search filters conversations by name (case-insensitive)" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation("c1", "Alice", lastMessageTimestamp = 3000L),
                createConversation("c2", "Bob", lastMessageTimestamp = 2000L),
                createConversation("c3", "ALICE Smith", lastMessageTimestamp = 1000L)
            )

            mockConversationsResource(conversations)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onSearchQueryChanged("alice")
            advanceUntilIdle()

            val state = vm.uiState.value
            state.conversations.size shouldBe 2
            state.searchQuery shouldBe "alice"
        }
    }

    "search filters conversations by lastMessageText (case-insensitive)" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation("c1", "Alice", lastMessageText = "Hello there", lastMessageTimestamp = 2000L),
                createConversation("c2", "Bob", lastMessageText = "Goodbye", lastMessageTimestamp = 1000L)
            )

            mockConversationsResource(conversations)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onSearchQueryChanged("hello")
            advanceUntilIdle()

            val state = vm.uiState.value
            state.conversations.size shouldBe 1
            state.conversations[0].title shouldBe "Alice"
        }
    }

    "clearing search restores full conversation list" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation("c1", "Alice", lastMessageTimestamp = 2000L),
                createConversation("c2", "Bob", lastMessageTimestamp = 1000L)
            )

            mockConversationsResource(conversations)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onSearchQueryChanged("alice")
            advanceUntilIdle()
            vm.uiState.value.conversations.size shouldBe 1

            vm.onSearchQueryChanged("")
            advanceUntilIdle()
            vm.uiState.value.conversations.size shouldBe 2
        }
    }

    "formatUnreadCount caps at 99+" {
        runTest(testDispatcher) {
            mockConversationsResource(emptyList())

            val vm = createViewModel()
            advanceUntilIdle()

            vm.formatUnreadCount(0) shouldBe ""
            vm.formatUnreadCount(1) shouldBe "1"
            vm.formatUnreadCount(99) shouldBe "99"
            vm.formatUnreadCount(100) shouldBe "99+"
            vm.formatUnreadCount(500) shouldBe "99+"
        }
    }

    "presence update marks user as online" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation("c1", "Alice", lastMessageTimestamp = 1000L, participantIds = listOf("user1", "user2"))
            )

            mockConversationsResource(conversations)

            val vm = createViewModel()
            advanceUntilIdle()

            // Emit presence online for user2
            incomingFrames.emit(ServerFrame.Presence(userId = "user2", status = "online"))
            advanceUntilIdle()

            val conversationUi = vm.uiState.value.conversations[0]
            vm.isConversationOnline(conversationUi) shouldBe true
        }
    }

    "presence update marks user as offline" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation("c1", "Alice", lastMessageTimestamp = 1000L, participantIds = listOf("user1", "user2"))
            )

            mockConversationsResource(conversations)

            val vm = createViewModel()
            advanceUntilIdle()

            // First mark online, then offline
            incomingFrames.emit(ServerFrame.Presence(userId = "user2", status = "online"))
            advanceUntilIdle()
            incomingFrames.emit(ServerFrame.Presence(userId = "user2", status = "offline"))
            advanceUntilIdle()

            val conversationUi = vm.uiState.value.conversations[0]
            vm.isConversationOnline(conversationUi) shouldBe false
        }
    }

    "group conversations are never marked as online" {
        runTest(testDispatcher) {
            val conversations = listOf(
                createConversation(
                    "c1", "Group Chat",
                    lastMessageTimestamp = 1000L,
                    type = ConversationType.GROUP,
                    participantIds = listOf("user1", "user2", "user3")
                )
            )

            mockConversationsResource(conversations)

            val vm = createViewModel()
            advanceUntilIdle()

            incomingFrames.emit(ServerFrame.Presence(userId = "user2", status = "online"))
            advanceUntilIdle()

            val conversationUi = vm.uiState.value.conversations[0]
            vm.isConversationOnline(conversationUi) shouldBe false
        }
    }
})
