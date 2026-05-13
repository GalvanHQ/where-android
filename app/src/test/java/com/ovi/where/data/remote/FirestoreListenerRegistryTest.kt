package com.ovi.where.data.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify

/**
 * Unit tests for [FirestoreListenerRegistry].
 *
 * Verifies:
 * 1. Listener registration and tracking
 * 2. Maximum concurrent listener enforcement (10)
 * 3. Combination of document listeners into collection-level queries when limit reached
 * 4. Unregistration and cleanup
 *
 * Tag: Feature: android-project-enhancement, Task 13.2
 */
class FirestoreListenerRegistryTest : StringSpec({

    lateinit var firestore: FirebaseFirestore
    lateinit var registry: FirestoreListenerRegistry

    beforeTest {
        firestore = mockk(relaxed = true)
        registry = FirestoreListenerRegistry(firestore)

        // Set up default mock behavior for collection/document/query chains
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        val mockQuery = mockk<Query>(relaxed = true)
        val mockRegistration = mockk<ListenerRegistration>(relaxed = true)

        every { firestore.collection(any()) } returns mockCollection
        every { mockCollection.document(any()) } returns mockDocument
        every { mockDocument.addSnapshotListener(any<EventListener<DocumentSnapshot>>()) } returns mockRegistration
        every { mockCollection.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } returns mockRegistration
        every { mockCollection.whereIn(any<FieldPath>(), any()) } returns mockQuery
        every { mockQuery.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } returns mockRegistration
    }

    "registerDocumentListener returns a ManagedListenerRegistration when under limit" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        val result = registry.registerDocumentListener("users", "user1", listener)

        result.shouldNotBeNull()
        registry.activeCount shouldBe 1
    }

    "registerDocumentListener tracks multiple listeners correctly" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        registry.registerDocumentListener("users", "user1", listener)
        registry.registerDocumentListener("users", "user2", listener)
        registry.registerDocumentListener("groups", "group1", listener)

        registry.activeCount shouldBe 3
    }

    "registerQueryListener returns a ManagedListenerRegistration when under limit" {
        val query = mockk<Query>(relaxed = true)
        val mockRegistration = mockk<ListenerRegistration>(relaxed = true)
        every { query.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } returns mockRegistration
        val listener = mockk<EventListener<QuerySnapshot>>(relaxed = true)

        val result = registry.registerQueryListener("conversations", query, listener)

        result.shouldNotBeNull()
        registry.activeCount shouldBe 1
    }

    "enforces maximum of 10 concurrent listeners" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        // Register 10 listeners from different collections (no combination possible)
        for (i in 1..10) {
            val result = registry.registerDocumentListener("collection_$i", "doc_$i", listener)
            result.shouldNotBeNull()
        }

        registry.activeCount shouldBe 10

        // 11th listener from a new collection should return null (no combination possible)
        val result = registry.registerDocumentListener("collection_11", "doc_11", listener)
        result.shouldBeNull()

        registry.activeCount shouldBe 10
    }

    "active count never exceeds MAX_CONCURRENT_LISTENERS" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        // Register many listeners
        for (i in 1..20) {
            registry.registerDocumentListener("collection_$i", "doc_$i", listener)
        }

        registry.activeCount shouldBeLessThanOrEqual FirestoreListenerRegistry.MAX_CONCURRENT_LISTENERS
    }

    "combines document listeners from same collection when limit reached" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        // Fill up 9 slots with different collections
        for (i in 1..9) {
            registry.registerDocumentListener("collection_$i", "doc_$i", listener)
        }

        // Add one document listener from "shared_collection"
        registry.registerDocumentListener("shared_collection", "doc_a", listener)
        registry.activeCount shouldBe 10

        // Now at limit — add another from "shared_collection" — should combine
        val result = registry.registerDocumentListener("shared_collection", "doc_b", listener)
        result.shouldNotBeNull()

        // After combination: 9 individual + 1 combined = 10 (the original "shared_collection" doc_a
        // was removed and replaced with a combined query)
        registry.activeCount shouldBeLessThanOrEqual FirestoreListenerRegistry.MAX_CONCURRENT_LISTENERS
    }

    "unregister removes listener and decrements count" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        val reg1 = registry.registerDocumentListener("users", "user1", listener)!!
        val reg2 = registry.registerDocumentListener("users", "user2", listener)!!

        registry.activeCount shouldBe 2

        reg1.remove()
        registry.activeCount shouldBe 1

        reg2.remove()
        registry.activeCount shouldBe 0
    }

    "unregisterAll removes all listeners" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        for (i in 1..5) {
            registry.registerDocumentListener("users", "user_$i", listener)
        }

        registry.activeCount shouldBe 5

        registry.unregisterAll()
        registry.activeCount shouldBe 0
    }

    "isActive returns correct state" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        val reg = registry.registerDocumentListener("users", "user1", listener)!!

        registry.isActive(reg.id) shouldBe true

        reg.remove()
        registry.isActive(reg.id) shouldBe false
    }

    "getActiveListeners returns all active entries" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        registry.registerDocumentListener("users", "user1", listener)
        registry.registerDocumentListener("groups", "group1", listener)

        val entries = registry.getActiveListeners()
        entries.size shouldBe 2
    }

    "ManagedListenerRegistration.remove calls unregister on registry" {
        val listener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)

        val reg = registry.registerDocumentListener("users", "user1", listener)!!
        registry.activeCount shouldBe 1

        reg.remove()
        registry.activeCount shouldBe 0
    }

    "returns null for query listener when limit reached and no combination possible" {
        val docListener = mockk<EventListener<DocumentSnapshot>>(relaxed = true)
        val queryListener = mockk<EventListener<QuerySnapshot>>(relaxed = true)
        val query = mockk<Query>(relaxed = true)
        val mockRegistration = mockk<ListenerRegistration>(relaxed = true)
        every { query.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } returns mockRegistration

        // Fill up all 10 slots
        for (i in 1..10) {
            registry.registerDocumentListener("collection_$i", "doc_$i", docListener)
        }

        // Query listener should return null when limit reached
        val result = registry.registerQueryListener("new_collection", query, queryListener)
        result.shouldBeNull()
    }

    "unregister with unknown ID does nothing" {
        registry.unregister("nonexistent_id")
        registry.activeCount shouldBe 0
    }
})
