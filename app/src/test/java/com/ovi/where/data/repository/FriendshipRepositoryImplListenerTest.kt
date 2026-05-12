package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.functions.FirebaseFunctions
import com.ovi.where.domain.model.BlockEntry
import com.ovi.where.domain.model.FriendEntry
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.RequestEntry
import com.ovi.where.domain.model.RequestInbox
import com.ovi.where.domain.model.SocialSummary
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

/**
 * Unit tests for [FriendshipRepositoryImpl] listener behavior.
 *
 * These tests verify:
 * 1. Each observe* method correctly creates a listener and emits data via the Flow
 * 2. The listener is properly removed when the flow is cancelled (via awaitClose)
 * 3. Empty state handling when user is unauthenticated (currentUid is null)
 * 4. The getFriendshipStatus and getFriendship one-shot methods work correctly
 *
 * Tag: Feature: people-ux-and-friendship-data-redesign, Task 6.2
 */
class FriendshipRepositoryImplListenerTest : StringSpec({

    // Mocks
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var firestore: FirebaseFirestore
    lateinit var functions: FirebaseFunctions
    lateinit var firebaseUser: FirebaseUser
    lateinit var repository: FriendshipRepositoryImpl

    // Reusable mock collections and documents
    lateinit var usersCollection: CollectionReference
    lateinit var userDoc: DocumentReference
    lateinit var friendsCollection: CollectionReference
    lateinit var inboxCollection: CollectionReference
    lateinit var outboxCollection: CollectionReference
    lateinit var summaryCollection: CollectionReference
    lateinit var blocksCollection: CollectionReference
    lateinit var friendshipsCollection: CollectionReference

    beforeEach {
        // Set up mocks
        firebaseAuth = mockk(relaxed = true)
        firestore = mockk(relaxed = true)
        functions = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)

        // Default authenticated state
        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "currentUserId"

        // Set up Firestore chain
        usersCollection = mockk(relaxed = true)
        userDoc = mockk(relaxed = true)
        friendsCollection = mockk(relaxed = true)
        inboxCollection = mockk(relaxed = true)
        outboxCollection = mockk(relaxed = true)
        summaryCollection = mockk(relaxed = true)
        blocksCollection = mockk(relaxed = true)
        friendshipsCollection = mockk(relaxed = true)

        every { firestore.collection("users") } returns usersCollection
        every { firestore.collection("friendships") } returns friendshipsCollection
        every { usersCollection.document("currentUserId") } returns userDoc
        every { userDoc.collection("friends") } returns friendsCollection
        every { userDoc.collection("inbox") } returns inboxCollection
        every { userDoc.collection("outbox") } returns outboxCollection
        every { userDoc.collection("summary") } returns summaryCollection
        every { userDoc.collection("blocks") } returns blocksCollection

        // Create repository
        repository = FriendshipRepositoryImpl(firebaseAuth, firestore, functions)
    }

    fun <T> mockQuerySnapshot(
        objects: List<T>,
        metadata: SnapshotMetadata = mockk(relaxed = true)
    ): QuerySnapshot {
        return mockk {
            every { toObjects(any<Class<T>>()) } returns objects
            every { this@mockk.metadata } returns metadata
            every { isEmpty } returns objects.isEmpty()
            every { documents } returns objects.map { obj ->
                mockk {
                    every { toObject(any<Class<T>>()) } returns obj
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // observeFriends tests
    // ═══════════════════════════════════════════════════════════════════

    "observeFriends emits list of FriendEntry when authenticated" {
        // Arrange
        val friends = listOf(
            FriendEntry(friendUid = "friend1", displayName = "Alice"),
            FriendEntry(friendUid = "friend2", displayName = "Bob")
        )
        val orderedQuery = mockk<Query>(relaxed = true)
        val listenerSlot = slot<com.google.firebase.firestore.EventListener<QuerySnapshot>>()

        every { friendsCollection.orderBy("displayName") } returns orderedQuery
        every { orderedQuery.addSnapshotListener(capture(listenerSlot)) } returns mockk()

        // Act
        val flow = repository.observeFriends()

        // Simulate listener callback
        listenerSlot.captured.onEvent(mockQuerySnapshot(friends), null)

        // Assert
        val result = flow.first()
        result.shouldContainExactly(friends)
    }

    "observeFriends emits empty list when unauthenticated" {
        // Arrange - no current user
        every { firebaseAuth.currentUser } returns null

        // Act
        val flow = repository.observeFriends()

        // Assert
        val result = flow.first()
        result.shouldBeEmpty()
    }

    "observeFriends removes listener on flow cancellation" {
        // Arrange
        val orderedQuery = mockk<Query>(relaxed = true)
        val registration = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        val listenerSlot = slot<com.google.firebase.firestore.EventListener<QuerySnapshot>>()

        every { friendsCollection.orderBy("displayName") } returns orderedQuery
        every { orderedQuery.addSnapshotListener(capture(listenerSlot)) } returns registration

        // Act & Assert
        runTest {
            val job = launch {
                repository.observeFriends().collect { }
            }
            // Give time for the flow to start
            kotlinx.coroutines.delay(100)
            job.cancelAndJoin()

            // Verify listener was removed
            verify { registration.remove() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // observeIncomingRequests tests
    // ═══════════════════════════════════════════════════════════════════

    "observeIncomingRequests emits sorted RequestEntry list when authenticated" {
        // Arrange
        val requests = listOf(
            RequestEntry(uid = "user1", displayName = "Alice", sentAt = 1000L),
            RequestEntry(uid = "user2", displayName = "Bob", sentAt = 2000L)
        )
        val inboxDoc = mockk<DocumentReference>(relaxed = true)
        val inboxDocListenerSlot = slot<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()

        every { inboxCollection.document("friendRequests") } returns inboxDoc
        every { inboxDoc.addSnapshotListener(capture(inboxDocListenerSlot)) } returns mockk()

        val inbox = RequestInbox(requests.associateBy { it.uid })

        // Act
        val flow = repository.observeIncomingRequests()

        // Simulate listener callback
        val docSnap = mockk<DocumentSnapshot> {
            every { toObject(RequestInbox::class.java) } returns inbox
        }
        inboxDocListenerSlot.captured.onEvent(docSnap, null)

        // Assert - should be sorted by sentAt descending
        val result = flow.first()
        result.map { it.sentAt } shouldBe listOf(2000L, 1000L)
    }

    "observeIncomingRequests emits empty list when doc absent" {
        // Arrange
        val inboxDoc = mockk<DocumentReference>(relaxed = true)
        val inboxDocListenerSlot = slot<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()

        every { inboxCollection.document("friendRequests") } returns inboxDoc
        every { inboxDoc.addSnapshotListener(capture(inboxDocListenerSlot)) } returns mockk()

        // Act
        val flow = repository.observeIncomingRequests()

        // Simulate listener callback with null snapshot
        inboxDocListenerSlot.captured.onEvent(null, null)

        // Assert
        val result = flow.first()
        result.shouldBeEmpty()
    }

    "observeIncomingRequests emits empty list when unauthenticated" {
        // Arrange - no current user
        every { firebaseAuth.currentUser } returns null

        // Act
        val flow = repository.observeIncomingRequests()

        // Assert
        val result = flow.first()
        result.shouldBeEmpty()
    }

    "observeIncomingRequests removes listener on flow cancellation" {
        // Arrange
        val inboxDoc = mockk<DocumentReference>(relaxed = true)
        val registration = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        val listenerSlot = slot<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()

        every { inboxCollection.document("friendRequests") } returns inboxDoc
        every { inboxDoc.addSnapshotListener(capture(listenerSlot)) } returns registration

        // Act & Assert
        runTest {
            val job = launch {
                repository.observeIncomingRequests().collect { }
            }
            kotlinx.coroutines.delay(100)
            job.cancelAndJoin()

            verify { registration.remove() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // observeOutgoingRequests tests
    // ═══════════════════════════════════════════════════════════════════

    "observeOutgoingRequests emits sorted RequestEntry list when authenticated" {
        // Arrange
        val requests = listOf(
            RequestEntry(uid = "user1", displayName = "Alice", sentAt = 1000L),
            RequestEntry(uid = "user2", displayName = "Bob", sentAt = 2000L)
        )
        val outboxDoc = mockk<DocumentReference>(relaxed = true)
        val outboxDocListenerSlot = slot<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()

        every { outboxCollection.document("friendRequests") } returns outboxDoc
        every { outboxDoc.addSnapshotListener(capture(outboxDocListenerSlot)) } returns mockk()

        val outbox = RequestInbox(requests.associateBy { it.uid })

        // Act
        val flow = repository.observeOutgoingRequests()

        // Simulate listener callback
        val docSnap = mockk<DocumentSnapshot> {
            every { toObject(RequestInbox::class.java) } returns outbox
        }
        outboxDocListenerSlot.captured.onEvent(docSnap, null)

        // Assert - should be sorted by sentAt descending
        val result = flow.first()
        result.map { it.sentAt } shouldBe listOf(2000L, 1000L)
    }

    "observeOutgoingRequests emits empty list when unauthenticated" {
        // Arrange - no current user
        every { firebaseAuth.currentUser } returns null

        // Act
        val flow = repository.observeOutgoingRequests()

        // Assert
        val result = flow.first()
        result.shouldBeEmpty()
    }

    // ═══════════════════════════════════════════════════════════════════
    // observeSocialSummary tests
    // ═══════════════════════════════════════════════════════════════════

    "observeSocialSummary emits SocialSummary when authenticated" {
        // Arrange
        val summary = SocialSummary(
            friendsCount = 5,
            pendingIncomingCount = 2,
            pendingOutgoingCount = 1,
            blockedCount = 0
        )
        val summaryDoc = mockk<DocumentReference>(relaxed = true)
        val summaryListenerSlot = slot<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()

        every { summaryCollection.document("social") } returns summaryDoc
        every { summaryDoc.addSnapshotListener(capture(summaryListenerSlot)) } returns mockk()

        // Act
        val flow = repository.observeSocialSummary()

        // Simulate listener callback
        val docSnap = mockk<DocumentSnapshot> {
            every { toObject(SocialSummary::class.java) } returns summary
        }
        summaryListenerSlot.captured.onEvent(docSnap, null)

        // Assert
        val result = flow.first()
        result.friendsCount shouldBe 5
        result.pendingIncomingCount shouldBe 2
    }

    "observeSocialSummary emits zero-valued summary when doc absent" {
        // Arrange
        val summaryDoc = mockk<DocumentReference>(relaxed = true)
        val summaryListenerSlot = slot<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()

        every { summaryCollection.document("social") } returns summaryDoc
        every { summaryDoc.addSnapshotListener(capture(summaryListenerSlot)) } returns mockk()

        // Act
        val flow = repository.observeSocialSummary()

        // Simulate listener callback with null snapshot
        summaryListenerSlot.captured.onEvent(null, null)

        // Assert
        val result = flow.first()
        result.friendsCount shouldBe 0
    }

    "observeSocialSummary emits empty SocialSummary when unauthenticated" {
        // Arrange - no current user
        every { firebaseAuth.currentUser } returns null

        // Act
        val flow = repository.observeSocialSummary()

        // Assert
        val result = flow.first()
        result.friendsCount shouldBe 0
    }

    // ═══════════════════════════════════════════════════════════════════
    // observeBlockedUsers tests
    // ═══════════════════════════════════════════════════════════════════

    "observeBlockedUsers emits list of BlockEntry when authenticated" {
        // Arrange
        val blocked = listOf(
            BlockEntry(blockedUid = "blocked1", blockedAt = 1000L),
            BlockEntry(blockedUid = "blocked2", blockedAt = 2000L)
        )
        val listenerSlot = slot<com.google.firebase.firestore.EventListener<QuerySnapshot>>()

        every { blocksCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk()

        // Act
        val flow = repository.observeBlockedUsers()

        // Simulate listener callback
        listenerSlot.captured.onEvent(mockQuerySnapshot(blocked), null)

        // Assert
        val result = flow.first()
        result.shouldContainExactly(blocked)
    }

    "observeBlockedUsers emits empty list when unauthenticated" {
        // Arrange - no current user
        every { firebaseAuth.currentUser } returns null

        // Act
        val flow = repository.observeBlockedUsers()

        // Assert
        val result = flow.first()
        result.shouldBeEmpty()
    }

    "observeBlockedUsers removes listener on flow cancellation" {
        // Arrange
        val registration = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        val listenerSlot = slot<com.google.firebase.firestore.EventListener<QuerySnapshot>>()

        every { blocksCollection.addSnapshotListener(capture(listenerSlot)) } returns registration

        // Act & Assert
        runTest {
            val job = launch {
                repository.observeBlockedUsers().collect { }
            }
            kotlinx.coroutines.delay(100)
            job.cancelAndJoin()

            verify { registration.remove() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getFriendshipStatus tests
    // ═══════════════════════════════════════════════════════════════════

    "getFriendshipStatus returns status when friendship exists" {
        // Arrange
        val friendship = Friendship(
            pairId = "currentUserId_otherUserId",
            members = listOf("currentUserId", "otherUserId"),
            status = FriendshipStatus.ACCEPTED
        )
        val docRef = mockk<DocumentReference>(relaxed = true)
        val docSnap = mockk<DocumentSnapshot> {
            every { toObject(Friendship::class.java) } returns friendship
        }

        every { friendshipsCollection.document(any()) } returns docRef
        every { docRef.get() } returns mockk {
            every { await() } returns docSnap
        }

        // Act
        val result = repository.getFriendshipStatus("otherUserId")

        // Assert
        result shouldBe FriendshipStatus.ACCEPTED
    }

    "getFriendshipStatus returns null when no friendship exists" {
        // Arrange
        val docRef = mockk<DocumentReference>(relaxed = true)
        val docSnap = mockk<DocumentSnapshot> {
            every { toObject(Friendship::class.java) } returns null
        }

        every { friendshipsCollection.document(any()) } returns docRef
        every { docRef.get() } returns mockk {
            every { await() } returns docSnap
        }

        // Act
        val result = repository.getFriendshipStatus("otherUserId")

        // Assert
        result shouldBe null
    }

    "getFriendshipStatus returns null when unauthenticated" {
        // Arrange - no current user
        every { firebaseAuth.currentUser } returns null

        // Act
        val result = repository.getFriendshipStatus("otherUserId")

        // Assert
        result shouldBe null
    }

    // ═══════════════════════════════════════════════════════════════════
    // getFriendship tests
    // ═══════════════════════════════════════════════════════════════════

    "getFriendship returns full Friendship object when exists" {
        // Arrange
        val friendship = Friendship(
            pairId = "currentUserId_otherUserId",
            members = listOf("currentUserId", "otherUserId"),
            status = FriendshipStatus.PENDING,
            requesterId = "otherUserId",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val docRef = mockk<DocumentReference>(relaxed = true)
        val docSnap = mockk<DocumentSnapshot> {
            every { toObject(Friendship::class.java) } returns friendship
        }

        every { friendshipsCollection.document(any()) } returns docRef
        every { docRef.get() } returns mockk {
            every { await() } returns docSnap
        }

        // Act
        val result = repository.getFriendship("otherUserId")

        // Assert
        result.shouldBeInstanceOf<Friendship>()
        result?.status shouldBe FriendshipStatus.PENDING
        result?.requesterId shouldBe "otherUserId"
    }

    "getFriendship returns null when no friendship exists" {
        // Arrange
        val docRef = mockk<DocumentReference>(relaxed = true)
        val docSnap = mockk<DocumentSnapshot> {
            every { toObject(Friendship::class.java) } returns null
        }

        every { friendshipsCollection.document(any()) } returns docRef
        every { docRef.get() } returns mockk {
            every { await() } returns docSnap
        }

        // Act
        val result = repository.getFriendship("otherUserId")

        // Assert
        result shouldBe null
    }

    "getFriendship returns null when unauthenticated" {
        // Arrange - no current user
        every { firebaseAuth.currentUser } returns null

        // Act
        val result = repository.getFriendship("otherUserId")

        // Assert
        result shouldBe null
    }

    // ═══════════════════════════════════════════════════════════════════
    // Error handling tests
    // ═══════════════════════════════════════════════════════════════════

    "observeFriends handles Firestore error" {
        // Arrange
        val orderedQuery = mockk<Query>(relaxed = true)
        val listenerSlot = slot<com.google.firebase.firestore.EventListener<QuerySnapshot>>()

        every { friendsCollection.orderBy("displayName") } returns orderedQuery
        every { orderedQuery.addSnapshotListener(capture(listenerSlot)) } returns mockk()

        // Act & Assert - error should close the flow
        runTest {
            val exception = RuntimeException("Firestore error")
            val job = launch {
                shouldThrow<RuntimeException> {
                    repository.observeFriends().collect { }
                }
            }
            // Trigger the error
            listenerSlot.captured.onEvent(null, exception)
            kotlinx.coroutines.delay(100)
        }
    }

    "getFriendshipStatus returns null on exception" {
        // Arrange
        val docRef = mockk<DocumentReference>(relaxed = true)

        every { friendshipsCollection.document(any()) } returns docRef
        every { docRef.get() } returns mockk {
            every { await() } throws RuntimeException("Network error")
        }

        // Act
        val result = repository.getFriendshipStatus("otherUserId")

        // Assert
        result shouldBe null
    }
})