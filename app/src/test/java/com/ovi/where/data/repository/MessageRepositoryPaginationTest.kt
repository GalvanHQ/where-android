package com.ovi.where.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.storage.FirebaseStorage
import com.ovi.where.core.utils.ImageCompressor
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.remote.chat.ChatApiClient
import com.ovi.where.data.remote.chat.ChatApiService
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.MessageDto
import com.ovi.where.data.remote.chat.MessagePageDto
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [MessageRepositoryImpl] task 3.2:
 * - Cursor-based pagination (loadOlderMessages)
 * - Persist fetched messages to Room via upsertAll
 * - Return MessagePage with nextCursor and hasMore flag
 * - Merge server results with local cache by unique ID (discard duplicates)
 */
class MessageRepositoryPaginationTest : StringSpec({

    lateinit var wsClient: ChatSocketIoClient
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var firebaseUser: FirebaseUser
    lateinit var messageDao: MessageDao
    lateinit var apiService: ChatApiService
    lateinit var firebaseStorage: FirebaseStorage
    lateinit var imageCompressor: ImageCompressor
    lateinit var context: Context
    lateinit var incomingFrames: MutableSharedFlow<ServerFrame>
    lateinit var connectionState: MutableStateFlow<ChatSocketIoClient.ConnectionState>

    beforeEach {
        wsClient = mockk(relaxed = true)
        firebaseAuth = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        firebaseStorage = mockk(relaxed = true)
        imageCompressor = mockk(relaxed = true)
        context = mockk(relaxed = true)
        incomingFrames = MutableSharedFlow(extraBufferCapacity = 64)
        connectionState = MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "user123"
        every { firebaseUser.displayName } returns "Test User"
        every { firebaseUser.photoUrl } returns null
        every { wsClient.incomingFrames } returns incomingFrames
        every { wsClient.connectionState } returns connectionState

        val tokenResult = mockk<GetTokenResult>()
        every { tokenResult.token } returns "test-token"
        val tokenTask = mockk<com.google.android.gms.tasks.Task<GetTokenResult>>(relaxed = true)
        every { tokenTask.isComplete } returns true
        every { tokenTask.isCanceled } returns false
        every { tokenTask.exception } returns null
        every { tokenTask.result } returns tokenResult
        every { firebaseUser.getIdToken(any()) } returns tokenTask

        // Mock ChatApiClient singleton
        mockkObject(ChatApiClient)
        every { ChatApiClient.apiService } returns apiService
    }

    fun createRepo() = MessageRepositoryImpl(wsClient, firebaseAuth, messageDao, firebaseStorage, imageCompressor, context)

    afterEach {
        unmockkObject(ChatApiClient)
    }

    "loadOlderMessages with cursor fetches from server and persists to Room" {
        runTest {
            val serverMessages = listOf(
                MessageDto("msg10", "conv1", "user1", "Alice", null, "Old msg 1", "TEXT", null, null, 500L),
                MessageDto("msg11", "conv1", "user2", "Bob", null, "Old msg 2", "TEXT", null, null, 600L)
            )
            coEvery { apiService.getMessagesPaginated(any(), "conv1", "1000", 30) } returns
                MessagePageDto(serverMessages, "500", true)
            coEvery { messageDao.upsertAll(any()) } returns Unit

            val repo = createRepo()
            val page = repo.loadOlderMessages("conv1", "1000", 30)

            // Should return server messages
            page.messages shouldHaveSize 2

            // Should persist to Room via upsertAll
            coVerify { messageDao.upsertAll(any()) }
        }
    }

    "loadOlderMessages with cursor returns server-provided nextCursor and hasMore" {
        runTest {
            val serverMessages = listOf(
                MessageDto("msg10", "conv1", "user1", "Alice", null, "Old msg", "TEXT", null, null, 500L)
            )
            coEvery { apiService.getMessagesPaginated(any(), "conv1", "1000", 30) } returns
                MessagePageDto(serverMessages, "400", true)
            coEvery { messageDao.upsertAll(any()) } returns Unit

            val repo = createRepo()
            val page = repo.loadOlderMessages("conv1", "1000", 30)

            page.nextCursor shouldBe "400"
            page.hasMore shouldBe true
        }
    }

    "loadOlderMessages with cursor returns hasMore=false when server says no more" {
        runTest {
            val serverMessages = listOf(
                MessageDto("msg1", "conv1", "user1", "Alice", null, "First msg", "TEXT", null, null, 100L)
            )
            coEvery { apiService.getMessagesPaginated(any(), "conv1", "500", 30) } returns
                MessagePageDto(serverMessages, null, false)
            coEvery { messageDao.upsertAll(any()) } returns Unit

            val repo = createRepo()
            val page = repo.loadOlderMessages("conv1", "500", 30)

            page.hasMore shouldBe false
        }
    }

    "loadOlderMessages handles server error gracefully" {
        runTest {
            coEvery { apiService.getMessagesPaginated(any(), "conv1", "1000", 30) } throws
                RuntimeException("Network error")

            val repo = createRepo()
            val page = repo.loadOlderMessages("conv1", "1000", 30)

            // Should return empty page with hasMore=true for retry
            page.messages shouldHaveSize 0
            page.nextCursor shouldBe "1000"
            page.hasMore shouldBe true
        }
    }

    "loadOlderMessages uses upsertAll for deduplication via Room PK constraint" {
        runTest {
            // Server returns messages that may overlap with existing Room data
            val serverMessages = listOf(
                MessageDto("msg1", "conv1", "user1", "Alice", null, "Updated text", "TEXT", null, null, 1000L),
                MessageDto("msg2", "conv1", "user2", "Bob", null, "New msg", "TEXT", null, null, 2000L)
            )
            coEvery { apiService.getMessagesPaginated(any(), "conv1", "3000", 30) } returns
                MessagePageDto(serverMessages, "1000", true)
            coEvery { messageDao.upsertAll(any()) } returns Unit

            val repo = createRepo()
            repo.loadOlderMessages("conv1", "3000", 30)

            // upsertAll should be called (Room's REPLACE strategy handles dedup)
            coVerify { messageDao.upsertAll(match { it.size == 2 }) }
        }
    }
})

private fun createMessageEntity(
    id: String,
    conversationId: String,
    timestamp: Long,
    text: String = "Test message"
): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = "user1",
    senderName = "Test User",
    senderPhotoUrl = null,
    text = text,
    type = MessageType.TEXT.name,
    timestamp = timestamp,
    status = MessageStatus.SENT.name,
    latitude = null,
    longitude = null,
    imageUrl = null,
    thumbnailUrl = null,
    replyToId = null,
    replyToText = null,
    replyToSenderName = null,
    reactionsJson = "{}",
    readByJson = "[]"
)
