package com.ovi.where.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.storage.FirebaseStorage
import com.ovi.where.core.common.Resource
import com.ovi.where.core.utils.ImageCompressor
import com.ovi.where.data.local.CacheStalenessChecker
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.local.entity.toDomain
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [MessageRepositoryImpl] task 3.1:
 * - Optimistic insert with PENDING status
 * - Ack handling (tempId → serverId, status → SENT)
 * - Timeout (10s) → FAILED status
 * - Offline queue (FIFO, max 50)
 * - Queue flush on reconnection
 */
class MessageRepositoryImplTest : StringSpec({

    lateinit var wsClient: ChatSocketIoClient
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var firebaseUser: FirebaseUser
    lateinit var messageDao: MessageDao
    lateinit var firebaseStorage: FirebaseStorage
    lateinit var imageCompressor: ImageCompressor
    lateinit var cacheStalenessChecker: CacheStalenessChecker
    lateinit var context: Context
    lateinit var incomingFrames: MutableSharedFlow<ServerFrame>
    lateinit var connectionState: MutableStateFlow<ChatSocketIoClient.ConnectionState>

    beforeEach {
        wsClient = mockk(relaxed = true)
        firebaseAuth = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        firebaseStorage = mockk(relaxed = true)
        imageCompressor = mockk(relaxed = true)
        cacheStalenessChecker = mockk(relaxed = true)
        context = mockk(relaxed = true)
        incomingFrames = MutableSharedFlow(extraBufferCapacity = 64)
        connectionState = MutableStateFlow(ChatSocketIoClient.ConnectionState.CONNECTED)

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "user123"
        every { firebaseUser.displayName } returns "Test User"
        every { firebaseUser.photoUrl } returns null
        every { wsClient.incomingFrames } returns incomingFrames
        every { wsClient.connectionState } returns connectionState
    }

    fun createRepo() = MessageRepositoryImpl(dagger.Lazy { wsClient }, firebaseAuth, messageDao, firebaseStorage, imageCompressor, cacheStalenessChecker, context)

    "sendMessage inserts optimistic message with PENDING status into Room" {
        runTest {
            val insertedEntity = slot<MessageEntity>()
            coEvery { messageDao.insert(capture(insertedEntity)) } returns Unit

            val repo = createRepo()
            val result = repo.sendMessage("conv1", "Hello world")

            result.shouldBeInstanceOf<Resource.Success<*>>()

            // Verify the message was inserted with PENDING status
            coVerify { messageDao.insert(any()) }
            insertedEntity.captured.status shouldBe MessageStatus.PENDING.name
            insertedEntity.captured.text shouldBe "Hello world"
            insertedEntity.captured.conversationId shouldBe "conv1"
            insertedEntity.captured.senderId shouldBe "user123"
            insertedEntity.captured.type shouldBe MessageType.TEXT.name
        }
    }

    "sendMessage emits via ChatSocketIoClient when connected" {
        runTest {
            coEvery { messageDao.insert(any()) } returns Unit

            val repo = createRepo()
            repo.sendMessage("conv1", "Hello")

            coVerify { wsClient.sendText("Hello", any(), null) }
        }
    }

    "sendMessage with replyToId passes replyToId to socket" {
        runTest {
            coEvery { messageDao.insert(any()) } returns Unit

            val repo = createRepo()
            repo.sendMessage("conv1", "Reply text", replyToId = "msg-original")

            coVerify { wsClient.sendText("Reply text", any(), "msg-original") }
        }
    }

    "sendMessage rejects blank text" {
        runTest {
            val repo = createRepo()
            val result = repo.sendMessage("conv1", "   ")

            result.shouldBeInstanceOf<Resource.Error<*>>()
        }
    }

    "sendMessage queues message when disconnected" {
        runTest {
            connectionState.value = ChatSocketIoClient.ConnectionState.DISCONNECTED
            coEvery { messageDao.insert(any()) } returns Unit

            val repo = createRepo()
            repo.sendMessage("conv1", "Offline message")

            // Should NOT call sendText when disconnected
            coVerify(exactly = 0) { wsClient.sendText(any(), any(), any()) }

            // Queue should have 1 message
            repo.offlineQueueSize shouldBe 1
        }
    }

    "sendMessage rejects when offline queue is full (50 messages)" {
        runTest {
            connectionState.value = ChatSocketIoClient.ConnectionState.DISCONNECTED
            coEvery { messageDao.insert(any()) } returns Unit

            val repo = createRepo()

            // Fill the queue to max
            repeat(50) {
                repo.sendMessage("conv1", "Message $it")
            }
            repo.offlineQueueSize shouldBe 50

            // 51st message should be rejected (status set to FAILED)
            repo.sendMessage("conv1", "Overflow message")
            coVerify { messageDao.updateStatus(any(), MessageStatus.FAILED.name) }
        }
    }

    "ack handling updates tempId to serverId and status to SENT" {
        runTest {
            val insertedEntity = slot<MessageEntity>()
            coEvery { messageDao.insert(capture(insertedEntity)) } returns Unit
            coEvery { messageDao.updateIdAndStatus(any(), any(), any(), any()) } returns Unit

            val repo = createRepo()
            repo.sendMessage("conv1", "Hello")

            val tempId = insertedEntity.captured.id

            // Simulate ack from server
            incomingFrames.emit(ServerFrame.MessageAck(tempId = tempId, id = "server-id-1", timestamp = 1000L))

            // Give time for the collector to process
            delay(100)

            coVerify {
                messageDao.updateIdAndStatus(
                    oldId = tempId,
                    newId = "server-id-1",
                    status = MessageStatus.SENT.name,
                    timestamp = 1000L
                )
            }
        }
    }

    "observeMessages returns Flow from Room database" {
        runTest {
            val entities = listOf(
                MessageEntity(
                    id = "msg1", conversationId = "conv1", senderId = "user1",
                    senderName = "Alice", senderPhotoUrl = null, text = "Hi",
                    type = "TEXT", timestamp = 1000L, status = "SENT",
                    latitude = null, longitude = null, imageUrl = null,
                    thumbnailUrl = null, replyToId = null, replyToText = null,
                    replyToSenderName = null, reactionsJson = "{}", readByJson = "[]"
                )
            )
            every { messageDao.observeByConversation("conv1") } returns flowOf(entities)

            val repo = createRepo()
            val flow = repo.observeMessages("conv1")

            // Collect first emission
            var messages: List<com.ovi.where.domain.model.Message>? = null
            flow.collect {
                messages = it
                return@collect
            }

            messages?.size shouldBe 1
            messages?.first()?.text shouldBe "Hi"
            messages?.first()?.status shouldBe MessageStatus.SENT
        }
    }

    "sendLocationMessage validates coordinates" {
        runTest {
            val repo = createRepo()

            // Invalid latitude (out of range)
            val result1 = repo.sendLocationMessage("conv1", 91.0, 0.0)
            result1.shouldBeInstanceOf<Resource.Error<*>>()

            // Invalid longitude (out of range)
            val result2 = repo.sendLocationMessage("conv1", 0.0, 181.0)
            result2.shouldBeInstanceOf<Resource.Error<*>>()

            // Null latitude (Requirement 15.4)
            val result3 = repo.sendLocationMessage("conv1", null, 0.0)
            result3.shouldBeInstanceOf<Resource.Error<*>>()

            // Null longitude (Requirement 15.4)
            val result4 = repo.sendLocationMessage("conv1", 45.0, null)
            result4.shouldBeInstanceOf<Resource.Error<*>>()

            // Both null (Requirement 15.4)
            val result5 = repo.sendLocationMessage("conv1", null, null)
            result5.shouldBeInstanceOf<Resource.Error<*>>()

            // Valid coordinates
            coEvery { messageDao.insert(any()) } returns Unit
            val result6 = repo.sendLocationMessage("conv1", 45.0, 90.0)
            result6.shouldBeInstanceOf<Resource.Success<*>>()
        }
    }

    "sendLocationMessage creates optimistic message with LOCATION type and emits via socket" {
        runTest {
            val insertedEntity = slot<MessageEntity>()
            coEvery { messageDao.insert(capture(insertedEntity)) } returns Unit

            val repo = createRepo()
            val result = repo.sendLocationMessage("conv1", 37.7749, -122.4194)

            result.shouldBeInstanceOf<Resource.Success<*>>()

            // Verify optimistic insert with LOCATION type
            coVerify { messageDao.insert(any()) }
            insertedEntity.captured.type shouldBe MessageType.LOCATION.name
            insertedEntity.captured.status shouldBe MessageStatus.PENDING.name
            insertedEntity.captured.latitude shouldBe 37.7749
            insertedEntity.captured.longitude shouldBe -122.4194
            insertedEntity.captured.conversationId shouldBe "conv1"

            // Verify emitted via ChatSocketIoClient.sendLocation
            coVerify { wsClient.sendLocation(37.7749, -122.4194, any()) }
        }
    }

    "sendLocationMessage does not transmit when coordinates are invalid" {
        runTest {
            val repo = createRepo()

            // Out of range
            repo.sendLocationMessage("conv1", -91.0, 0.0)
            repo.sendLocationMessage("conv1", 0.0, -181.0)

            // Null
            repo.sendLocationMessage("conv1", null, 50.0)
            repo.sendLocationMessage("conv1", 50.0, null)

            // Verify no message was inserted or emitted
            coVerify(exactly = 0) { messageDao.insert(any()) }
            coVerify(exactly = 0) { wsClient.sendLocation(any(), any(), any()) }
        }
    }

    "sendLocationMessage validates boundary values" {
        runTest {
            coEvery { messageDao.insert(any()) } returns Unit

            val repo = createRepo()

            // Exact boundary values should be valid
            val result1 = repo.sendLocationMessage("conv1", 90.0, 180.0)
            result1.shouldBeInstanceOf<Resource.Success<*>>()

            val result2 = repo.sendLocationMessage("conv1", -90.0, -180.0)
            result2.shouldBeInstanceOf<Resource.Success<*>>()

            val result3 = repo.sendLocationMessage("conv1", 0.0, 0.0)
            result3.shouldBeInstanceOf<Resource.Success<*>>()
        }
    }

    "incoming MessageDelivered frame is persisted to Room" {
        runTest {
            coEvery { messageDao.insert(any()) } returns Unit

            val repo = createRepo()

            // Simulate incoming message from another user
            val frame = ServerFrame.MessageDelivered(
                id = "server-msg-1",
                conversationId = "conv1",
                senderId = "other-user",
                senderName = "Other User",
                text = "Hello from server",
                messageType = "TEXT",
                timestamp = 2000L,
                readBy = listOf("other-user")
            )
            incomingFrames.emit(frame)

            // Give time for the collector to process
            delay(100)

            coVerify {
                messageDao.insert(match {
                    it.id == "server-msg-1" &&
                    it.text == "Hello from server" &&
                    it.status == MessageStatus.SENT.name
                })
            }
        }
    }

    // ─── Read Receipt Tests (Task 3.4) ─────────────────────────────────────────

    "markRead emits read event via ChatSocketIoClient when connected" {
        runTest {
            connectionState.value = ChatSocketIoClient.ConnectionState.CONNECTED

            val repo = createRepo()
            repo.markRead("conv1", "user123")

            coVerify { wsClient.sendRead() }
        }
    }

    "markRead defers read event when disconnected" {
        runTest {
            connectionState.value = ChatSocketIoClient.ConnectionState.DISCONNECTED

            val repo = createRepo()
            repo.markRead("conv1", "user123")

            // Should NOT call sendRead when disconnected
            coVerify(exactly = 0) { wsClient.sendRead() }
        }
    }

    "markRead deferred event is sent when connection is restored" {
        runTest {
            connectionState.value = ChatSocketIoClient.ConnectionState.DISCONNECTED

            val repo = createRepo()
            repo.markRead("conv1", "user123")

            // Verify not sent while disconnected
            coVerify(exactly = 0) { wsClient.sendRead() }

            // Simulate reconnection
            connectionState.value = ChatSocketIoClient.ConnectionState.CONNECTED

            // Give time for the connection state collector to process
            delay(200)

            // Now the deferred read receipt should be flushed
            coVerify(atLeast = 1) { wsClient.sendRead() }
        }
    }

    "incoming ReadReceipt frame appends userId to message readBy list" {
        runTest {
            val existingEntity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null, reactionsJson = "{}", readByJson = "[]"
            )
            coEvery { messageDao.getById("msg1") } returns existingEntity
            coEvery { messageDao.updateReadBy(any(), any()) } returns Unit

            val repo = createRepo()

            // Simulate incoming read receipt
            incomingFrames.emit(ServerFrame.ReadReceipt(messageId = "msg1", userId = "reader1", timestamp = 2000L))

            // Give time for the collector to process
            delay(100)

            coVerify {
                messageDao.updateReadBy("msg1", match { it.contains("reader1") })
            }
        }
    }

    "incoming ReadReceipt does not add duplicate userId to readBy list" {
        runTest {
            // Message already has "reader1" in readBy
            val existingEntity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null, reactionsJson = "{}", readByJson = "[\"reader1\"]"
            )
            coEvery { messageDao.getById("msg1") } returns existingEntity

            val repo = createRepo()

            // Simulate duplicate read receipt for same user
            incomingFrames.emit(ServerFrame.ReadReceipt(messageId = "msg1", userId = "reader1", timestamp = 2000L))

            // Give time for the collector to process
            delay(100)

            // updateReadBy should NOT be called since userId is already present
            coVerify(exactly = 0) { messageDao.updateReadBy(any(), any()) }
        }
    }

    "incoming ReadReceipt appends new userId to existing readBy list (monotonic growth)" {
        runTest {
            // Message already has "reader1" in readBy
            val existingEntity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "user123",
                senderName = "Test User", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null, reactionsJson = "{}", readByJson = "[\"reader1\"]"
            )
            coEvery { messageDao.getById("msg1") } returns existingEntity
            coEvery { messageDao.updateReadBy(any(), any()) } returns Unit

            val repo = createRepo()

            // Simulate read receipt from a different user
            incomingFrames.emit(ServerFrame.ReadReceipt(messageId = "msg1", userId = "reader2", timestamp = 3000L))

            // Give time for the collector to process
            delay(100)

            // Verify readBy now contains both reader1 and reader2
            coVerify {
                messageDao.updateReadBy("msg1", match {
                    it.contains("reader1") && it.contains("reader2")
                })
            }
        }
    }

    "incoming ReadReceipt for non-existent message is ignored" {
        runTest {
            coEvery { messageDao.getById("nonexistent") } returns null

            val repo = createRepo()

            // Simulate read receipt for a message that doesn't exist locally
            incomingFrames.emit(ServerFrame.ReadReceipt(messageId = "nonexistent", userId = "reader1", timestamp = 2000L))

            // Give time for the collector to process
            delay(100)

            // updateReadBy should NOT be called
            coVerify(exactly = 0) { messageDao.updateReadBy(any(), any()) }
        }
    }

    // ─── Reaction Toggle Tests (Task 3.3) ──────────────────────────────────────

    "reactToMessage adds user to emoji reactor list when not already reacted" {
        runTest {
            val entity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "other-user",
                senderName = "Other", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null, reactionsJson = "{}", readByJson = "[]"
            )
            coEvery { messageDao.getById("msg1") } returns entity
            coEvery { messageDao.updateReactions(any(), any()) } returns Unit

            val repo = createRepo()
            val result = repo.reactToMessage("conv1", "msg1", "👍")

            result.shouldBeInstanceOf<Resource.Success<*>>()

            // Verify optimistic update was applied with user added
            coVerify {
                messageDao.updateReactions("msg1", match {
                    it.contains("\"user123\"") && it.contains("👍")
                })
            }

            // Verify sendReaction was called (not removeReaction)
            coVerify { wsClient.sendReaction("msg1", "👍") }
            coVerify(exactly = 0) { wsClient.removeReaction(any(), any()) }
        }
    }

    "reactToMessage removes user from emoji reactor list when already reacted (toggle)" {
        runTest {
            val entity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "other-user",
                senderName = "Other", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null,
                reactionsJson = """{"👍":["user123","user456"]}""",
                readByJson = "[]"
            )
            coEvery { messageDao.getById("msg1") } returns entity
            coEvery { messageDao.updateReactions(any(), any()) } returns Unit

            val repo = createRepo()
            val result = repo.reactToMessage("conv1", "msg1", "👍")

            result.shouldBeInstanceOf<Resource.Success<*>>()

            // Verify optimistic update was applied with user removed
            coVerify {
                messageDao.updateReactions("msg1", match {
                    !it.contains("\"user123\"") && it.contains("\"user456\"")
                })
            }

            // Verify removeReaction was called (not sendReaction)
            coVerify { wsClient.removeReaction("msg1", "👍") }
            coVerify(exactly = 0) { wsClient.sendReaction(any(), any()) }
        }
    }

    "reactToMessage removes emoji key when last user removes reaction" {
        runTest {
            val entity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "other-user",
                senderName = "Other", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null,
                reactionsJson = """{"👍":["user123"]}""",
                readByJson = "[]"
            )
            coEvery { messageDao.getById("msg1") } returns entity
            coEvery { messageDao.updateReactions(any(), any()) } returns Unit

            val repo = createRepo()
            val result = repo.reactToMessage("conv1", "msg1", "👍")

            result.shouldBeInstanceOf<Resource.Success<*>>()

            // Verify the emoji key is removed entirely (empty map)
            coVerify {
                messageDao.updateReactions("msg1", "{}")
            }
        }
    }

    "reactToMessage rolls back on server failure" {
        runTest {
            val entity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "other-user",
                senderName = "Other", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null, reactionsJson = "{}", readByJson = "[]"
            )
            coEvery { messageDao.getById("msg1") } returns entity
            coEvery { messageDao.updateReactions(any(), any()) } returns Unit
            coEvery { wsClient.sendReaction(any(), any()) } throws RuntimeException("Network error")

            val repo = createRepo()
            val result = repo.reactToMessage("conv1", "msg1", "👍")

            result.shouldBeInstanceOf<Resource.Error<*>>()

            // Verify rollback: updateReactions called twice (optimistic + rollback)
            coVerify(exactly = 2) { messageDao.updateReactions("msg1", any()) }
            // Second call should be the rollback to original state
            coVerify { messageDao.updateReactions("msg1", "{}") }
        }
    }

    "reactToMessage returns error when message not found" {
        runTest {
            coEvery { messageDao.getById("nonexistent") } returns null

            val repo = createRepo()
            val result = repo.reactToMessage("conv1", "nonexistent", "👍")

            result.shouldBeInstanceOf<Resource.Error<*>>()
        }
    }

    "reactToMessage returns error when user not authenticated" {
        runTest {
            every { firebaseAuth.currentUser } returns null

            val repo = createRepo()
            val result = repo.reactToMessage("conv1", "msg1", "👍")

            result.shouldBeInstanceOf<Resource.Error<*>>()
        }
    }

    "removeReaction removes user and rolls back on failure" {
        runTest {
            val entity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "other-user",
                senderName = "Other", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null,
                reactionsJson = """{"👍":["user123","user456"]}""",
                readByJson = "[]"
            )
            coEvery { messageDao.getById("msg1") } returns entity
            coEvery { messageDao.updateReactions(any(), any()) } returns Unit
            coEvery { wsClient.removeReaction(any(), any()) } throws RuntimeException("Network error")

            val repo = createRepo()
            val result = repo.removeReaction("conv1", "msg1", "👍")

            result.shouldBeInstanceOf<Resource.Error<*>>()

            // Verify rollback to original state
            coVerify {
                messageDao.updateReactions("msg1", """{"👍":["user123","user456"]}""")
            }
        }
    }

    "removeReaction succeeds when user has not reacted (no-op)" {
        runTest {
            val entity = MessageEntity(
                id = "msg1", conversationId = "conv1", senderId = "other-user",
                senderName = "Other", senderPhotoUrl = null, text = "Hello",
                type = "TEXT", timestamp = 1000L, status = "SENT",
                latitude = null, longitude = null, imageUrl = null,
                thumbnailUrl = null, replyToId = null, replyToText = null,
                replyToSenderName = null,
                reactionsJson = """{"👍":["user456"]}""",
                readByJson = "[]"
            )
            coEvery { messageDao.getById("msg1") } returns entity

            val repo = createRepo()
            val result = repo.removeReaction("conv1", "msg1", "👍")

            result.shouldBeInstanceOf<Resource.Success<*>>()

            // No updateReactions should be called since user hasn't reacted
            coVerify(exactly = 0) { messageDao.updateReactions(any(), any()) }
            coVerify(exactly = 0) { wsClient.removeReaction(any(), any()) }
        }
    }
})
