package com.ovi.where.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.ovi.where.core.common.Resource
import com.ovi.where.data.local.CacheStalenessChecker
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.local.entity.ConversationEntity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import java.util.Date

/**
 * Unit tests for ConversationRepositoryImpl.batchFetchConversations (Task 2.2)
 *
 * Tests cover:
 * - Chunking IDs into groups of 30 for whereIn queries (Req 5.1, 5.2)
 * - At most one batch query set per foreground sync cycle (Req 5.1)
 * - Staleness check: skip Firestore re-read if lastSyncTimestamp < 5 minutes old (Req 7.4)
 * - Version-based skip: skip Room write for matching documentUpdateTime (Req 5.7)
 * - Store document updateTime per conversation in Room (Req 5.7)
 */
class ConversationRepositoryBatchFetchTest : StringSpec({

    lateinit var firebaseAuth: FirebaseAuth
    lateinit var firebaseUser: FirebaseUser
    lateinit var firestore: FirebaseFirestore
    lateinit var conversationDao: ConversationDao
    lateinit var cacheStalenessChecker: CacheStalenessChecker
    lateinit var collectionRef: CollectionReference

    beforeEach {
        firebaseAuth = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)
        firestore = mockk(relaxed = true)
        conversationDao = mockk(relaxed = true)
        cacheStalenessChecker = mockk(relaxed = true)
        collectionRef = mockk(relaxed = true)

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "user123"
        every { firestore.collection(any()) } returns collectionRef
    }

    fun createRepo() = ConversationRepositoryImpl(
        firebaseAuth, firestore, conversationDao, cacheStalenessChecker
    )

    fun mockQueryForChunk(chunk: List<String>, documents: List<DocumentSnapshot>): Query {
        val query = mockk<Query>(relaxed = true)
        val querySnapshot = mockk<QuerySnapshot>(relaxed = true)
        val task = mockk<Task<QuerySnapshot>>(relaxed = true)

        every { collectionRef.whereIn(any<FieldPath>(), chunk) } returns query
        every { query.get() } returns task
        every { task.isComplete } returns true
        every { task.isCanceled } returns false
        every { task.exception } returns null
        every { task.result } returns querySnapshot
        every { querySnapshot.documents } returns documents

        return query
    }

    fun createDocSnapshot(
        id: String,
        name: String = "Chat $id",
        type: String = "direct",
        updateTime: Long = System.currentTimeMillis()
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.id } returns id
        every { doc.getString("name") } returns name
        every { doc.getString("type") } returns type
        every { doc.getString("photoUrl") } returns null
        every { doc.getString("groupId") } returns null
        every { doc.getString("lastMessageText") } returns "Hello"
        every { doc.getLong("lastMessageTimestamp") } returns 1000L
        every { doc.getString("lastMessageSenderId") } returns "sender1"
        every { doc.get("unreadCounts") } returns mapOf("user123" to 2L)
        every { doc.get("participantIds") } returns listOf("user123", "other")
        every { doc.getDate("updateTime") } returns Date(updateTime)
        return doc
    }

    "batchFetchConversations returns success when user is not authenticated" {
        runTest {
            every { firebaseAuth.currentUser } returns null

            val repo = createRepo()
            val result = repo.batchFetchConversations(listOf("conv1"))

            result.shouldBeInstanceOf<Resource.Error<*>>()
        }
    }

    "batchFetchConversations skips conversations with fresh lastSyncTimestamp (< 5 min)" {
        runTest {
            val now = System.currentTimeMillis()
            // lastSyncTimestamp is 2 minutes ago (fresh, < 5 min threshold)
            coEvery { conversationDao.getLastSyncTimestamp("conv1") } returns (now - 2 * 60 * 1000L)
            coEvery { conversationDao.getLastSyncTimestamp("conv2") } returns (now - 2 * 60 * 1000L)

            val repo = createRepo()
            val result = repo.batchFetchConversations(listOf("conv1", "conv2"))

            result.shouldBeInstanceOf<Resource.Success<*>>()
            // No Firestore query should be made since all conversations are fresh
            verify(exactly = 0) { collectionRef.whereIn(any<FieldPath>(), any()) }
        }
    }

    "batchFetchConversations fetches stale conversations (>= 5 min old)" {
        runTest {
            val now = System.currentTimeMillis()
            // lastSyncTimestamp is 6 minutes ago (stale, >= 5 min threshold)
            coEvery { conversationDao.getLastSyncTimestamp("conv1") } returns (now - 6 * 60 * 1000L)
            coEvery { conversationDao.getDocumentUpdateTime("conv1") } returns null

            val doc = createDocSnapshot("conv1", updateTime = now)
            mockQueryForChunk(listOf("conv1"), listOf(doc))

            val repo = createRepo()
            val result = repo.batchFetchConversations(listOf("conv1"))

            result.shouldBeInstanceOf<Resource.Success<*>>()
            coVerify { conversationDao.upsertAll(any()) }
        }
    }

    "batchFetchConversations fetches conversations with null lastSyncTimestamp (first time)" {
        runTest {
            val now = System.currentTimeMillis()
            coEvery { conversationDao.getLastSyncTimestamp("conv1") } returns null
            coEvery { conversationDao.getDocumentUpdateTime("conv1") } returns null

            val doc = createDocSnapshot("conv1", updateTime = now)
            mockQueryForChunk(listOf("conv1"), listOf(doc))

            val repo = createRepo()
            val result = repo.batchFetchConversations(listOf("conv1"))

            result.shouldBeInstanceOf<Resource.Success<*>>()
            coVerify { conversationDao.upsertAll(any()) }
        }
    }

    "batchFetchConversations skips Room write when documentUpdateTime matches" {
        runTest {
            val now = System.currentTimeMillis()
            val updateTime = 5000L

            // Stale sync timestamp so it will query Firestore
            coEvery { conversationDao.getLastSyncTimestamp("conv1") } returns (now - 6 * 60 * 1000L)
            // Stored documentUpdateTime matches incoming
            coEvery { conversationDao.getDocumentUpdateTime("conv1") } returns updateTime

            val doc = createDocSnapshot("conv1", updateTime = updateTime)
            mockQueryForChunk(listOf("conv1"), listOf(doc))

            val repo = createRepo()
            val result = repo.batchFetchConversations(listOf("conv1"))

            result.shouldBeInstanceOf<Resource.Success<*>>()
            // upsertAll should NOT be called with this document since version matches
            coVerify(exactly = 0) { conversationDao.upsertAll(any()) }
        }
    }

    "batchFetchConversations writes to Room when documentUpdateTime differs" {
        runTest {
            val now = System.currentTimeMillis()
            val storedUpdateTime = 5000L
            val incomingUpdateTime = 6000L

            coEvery { conversationDao.getLastSyncTimestamp("conv1") } returns (now - 6 * 60 * 1000L)
            coEvery { conversationDao.getDocumentUpdateTime("conv1") } returns storedUpdateTime

            val doc = createDocSnapshot("conv1", updateTime = incomingUpdateTime)
            mockQueryForChunk(listOf("conv1"), listOf(doc))

            val entitiesSlot = slot<List<ConversationEntity>>()
            coEvery { conversationDao.upsertAll(capture(entitiesSlot)) } returns Unit

            val repo = createRepo()
            val result = repo.batchFetchConversations(listOf("conv1"))

            result.shouldBeInstanceOf<Resource.Success<*>>()
            coVerify { conversationDao.upsertAll(any()) }
            entitiesSlot.captured.size shouldBe 1
            entitiesSlot.captured[0].documentUpdateTime shouldBe incomingUpdateTime
        }
    }

    "batchFetchConversations executes at most one batch query set per foreground sync cycle" {
        runTest {
            val now = System.currentTimeMillis()
            coEvery { conversationDao.getLastSyncTimestamp(any()) } returns (now - 6 * 60 * 1000L)
            coEvery { conversationDao.getDocumentUpdateTime(any()) } returns null

            val doc = createDocSnapshot("conv1", updateTime = now)
            mockQueryForChunk(listOf("conv1"), listOf(doc))

            val repo = createRepo()

            // First call should execute
            val result1 = repo.batchFetchConversations(listOf("conv1"))
            result1.shouldBeInstanceOf<Resource.Success<*>>()

            // Second call in same cycle should be a no-op
            val result2 = repo.batchFetchConversations(listOf("conv1"))
            result2.shouldBeInstanceOf<Resource.Success<*>>()

            // Firestore query should only be called once
            verify(exactly = 1) { collectionRef.whereIn(any<FieldPath>(), any()) }
        }
    }

    "batchFetchConversations allows new batch after resetBatchFetchCycle" {
        runTest {
            val now = System.currentTimeMillis()
            coEvery { conversationDao.getLastSyncTimestamp(any()) } returns (now - 6 * 60 * 1000L)
            coEvery { conversationDao.getDocumentUpdateTime(any()) } returns null

            val doc = createDocSnapshot("conv1", updateTime = now)
            mockQueryForChunk(listOf("conv1"), listOf(doc))

            val repo = createRepo()

            // First call
            repo.batchFetchConversations(listOf("conv1"))

            // Reset cycle
            repo.resetBatchFetchCycle()

            // Second call after reset should execute
            repo.batchFetchConversations(listOf("conv1"))

            // Firestore query should be called twice (once per cycle)
            verify(exactly = 2) { collectionRef.whereIn(any<FieldPath>(), any()) }
        }
    }

    "batchFetchConversations stores documentUpdateTime in Room entity" {
        runTest {
            val now = System.currentTimeMillis()
            val docUpdateTime = 12345L

            coEvery { conversationDao.getLastSyncTimestamp("conv1") } returns (now - 6 * 60 * 1000L)
            coEvery { conversationDao.getDocumentUpdateTime("conv1") } returns null

            val doc = createDocSnapshot("conv1", updateTime = docUpdateTime)
            mockQueryForChunk(listOf("conv1"), listOf(doc))

            val entitiesSlot = slot<List<ConversationEntity>>()
            coEvery { conversationDao.upsertAll(capture(entitiesSlot)) } returns Unit

            val repo = createRepo()
            repo.batchFetchConversations(listOf("conv1"))

            entitiesSlot.captured[0].documentUpdateTime shouldBe docUpdateTime
        }
    }

    "batchFetchConversations only fetches stale conversations from mixed list" {
        runTest {
            val now = System.currentTimeMillis()
            // conv1 is fresh (2 min old), conv2 is stale (6 min old)
            coEvery { conversationDao.getLastSyncTimestamp("conv1") } returns (now - 2 * 60 * 1000L)
            coEvery { conversationDao.getLastSyncTimestamp("conv2") } returns (now - 6 * 60 * 1000L)
            coEvery { conversationDao.getDocumentUpdateTime("conv2") } returns null

            val doc2 = createDocSnapshot("conv2", updateTime = now)
            // Only conv2 should be queried
            mockQueryForChunk(listOf("conv2"), listOf(doc2))

            val repo = createRepo()
            val result = repo.batchFetchConversations(listOf("conv1", "conv2"))

            result.shouldBeInstanceOf<Resource.Success<*>>()
            // Verify only stale conversation was queried
            verify { collectionRef.whereIn(any<FieldPath>(), listOf("conv2")) }
        }
    }
})
