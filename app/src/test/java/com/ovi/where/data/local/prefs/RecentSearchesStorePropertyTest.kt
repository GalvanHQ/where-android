package com.ovi.where.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Property-based tests for [RecentSearchesStore].
 *
 * Uses an in-memory DataStore<Preferences> (backed by a temp file) for each iteration.
 * Each property test runs at least 100 iterations.
 *
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7**
 */
class RecentSearchesStorePropertyTest : StringSpec({

    // Generator: non-blank alphanumeric strings (1..20 chars).
    // The store ignores blank queries, so we filter them out at the generator level.
    val queryArb: Arb<String> = Arb.string(
        minSize = 1,
        maxSize = 20,
        codepoints = Codepoint.alphanumeric(),
    ).filter { it.isNotBlank() }

    // Generator: list of distinct non-blank queries (1..30 items)
    val queryListArb: Arb<List<String>> = Arb.list(queryArb, range = 1..30)
        .filter { list -> list.map { it.trim() }.distinct().size == list.size }

    // Generator: list of distinct non-blank queries with size > 15 (16..30 items)
    val overflowQueryListArb: Arb<List<String>> = Arb.list(queryArb, range = 16..30)
        .filter { list -> list.map { it.trim() }.distinct().size == list.size }

    /**
     * Creates a fresh in-memory DataStore for each test iteration.
     */
    fun createTestDataStore(): DataStore<Preferences> {
        val tempFile = File.createTempFile("test_recent_searches_", ".preferences_pb")
        tempFile.deleteOnExit()
        // Delete the file so DataStore starts fresh
        tempFile.delete()
        return PreferenceDataStoreFactory.create { tempFile }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 5: Recent searches persistence round-trip
    // Tag: Feature: messenger-style-search, Property 5: Recent searches persistence round-trip
    // ─────────────────────────────────────────────────────────────────────────

    "Feature: messenger-style-search, Property 5: Recent searches persistence round-trip" {
        checkAll(iterations = 100, queryListArb) { queries ->
            val dataStore = createTestDataStore()
            val store = RecentSearchesStore(dataStore)

            // Add all queries
            for (query in queries) {
                store.addSearch("people", query)
            }

            // Read back
            val result = store.getRecentSearches("people").first()

            // Expected: queries in reverse-chronological order (most recent first), capped at 15
            val expected = queries.reversed().take(RecentSearchesStore.MAX_ENTRIES)
            result shouldContainExactly expected
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 6: Recent searches max capacity with FIFO eviction
    // Tag: Feature: messenger-style-search, Property 6: Recent searches max capacity with FIFO eviction
    // ─────────────────────────────────────────────────────────────────────────

    "Feature: messenger-style-search, Property 6: Recent searches max capacity with FIFO eviction" {
        checkAll(iterations = 100, overflowQueryListArb) { queries ->
            val dataStore = createTestDataStore()
            val store = RecentSearchesStore(dataStore)

            // Add all queries (more than 15)
            for (query in queries) {
                store.addSearch("people", query)
            }

            // Read back
            val result = store.getRecentSearches("people").first()

            // Store should contain exactly MAX_ENTRIES items
            result.size shouldBe RecentSearchesStore.MAX_ENTRIES

            // The entries should be the 15 most recently added (in reverse order)
            val expected = queries.reversed().take(RecentSearchesStore.MAX_ENTRIES)
            result shouldContainExactly expected
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 7: Single entry deletion preserves others
    // Tag: Feature: messenger-style-search, Property 7: Single entry deletion preserves others
    // ─────────────────────────────────────────────────────────────────────────

    "Feature: messenger-style-search, Property 7: Single entry deletion preserves others" {
        // Use a list of 2..15 distinct queries and pick one to delete
        val boundedListArb: Arb<List<String>> = Arb.list(queryArb, range = 2..15)
            .filter { list -> list.map { it.trim() }.distinct().size == list.size }

        checkAll(iterations = 100, boundedListArb, Arb.int(0..14)) { queries, deleteIndexRaw ->
            val dataStore = createTestDataStore()
            val store = RecentSearchesStore(dataStore)

            // Add all queries
            for (query in queries) {
                store.addSearch("people", query)
            }

            // The stored order is reverse-chronological
            val storedOrder = queries.reversed()

            // Pick a valid index to delete
            val deleteIndex = deleteIndexRaw % storedOrder.size
            val toDelete = storedOrder[deleteIndex]

            // Delete the entry
            store.removeSearch("people", toDelete)

            // Read back
            val result = store.getRecentSearches("people").first()

            // Should contain all entries except the deleted one, in original order
            val expected = storedOrder.filter { it != toDelete }
            result shouldContainExactly expected
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 8: Clear all results in empty list
    // Tag: Feature: messenger-style-search, Property 8: Clear all results in empty list
    // ─────────────────────────────────────────────────────────────────────────

    "Feature: messenger-style-search, Property 8: Clear all results in empty list" {
        val nonEmptyListArb: Arb<List<String>> = Arb.list(queryArb, range = 1..15)
            .filter { list -> list.map { it.trim() }.distinct().size == list.size }

        checkAll(iterations = 100, nonEmptyListArb) { queries ->
            val dataStore = createTestDataStore()
            val store = RecentSearchesStore(dataStore)

            // Add all queries
            for (query in queries) {
                store.addSearch("people", query)
            }

            // Verify non-empty before clearing
            val beforeClear = store.getRecentSearches("people").first()
            beforeClear.size shouldBe queries.size

            // Clear all
            store.clearAll("people")

            // Read back — should be empty
            val result = store.getRecentSearches("people").first()
            result shouldBe emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 9: Separate histories per screen
    // Tag: Feature: messenger-style-search, Property 9: Separate histories per screen
    // ─────────────────────────────────────────────────────────────────────────

    "Feature: messenger-style-search, Property 9: Separate histories per screen" {
        // Two distinct lists of queries for people and chats
        val twoListsArb = Arb.list(queryArb, range = 1..10)
            .filter { list -> list.map { it.trim() }.distinct().size == list.size }

        checkAll(iterations = 100, twoListsArb, twoListsArb) { peopleQueries, chatsQueries ->
            val dataStore = createTestDataStore()
            val store = RecentSearchesStore(dataStore)

            // Add queries to people screen
            for (query in peopleQueries) {
                store.addSearch("people", query)
            }

            // Add queries to chats screen
            for (query in chatsQueries) {
                store.addSearch("chats", query)
            }

            // Read people searches
            val peopleResult = store.getRecentSearches("people").first()
            // Read chats searches
            val chatsResult = store.getRecentSearches("chats").first()

            // People should contain exactly the people queries (reversed)
            val expectedPeople = peopleQueries.reversed()
            peopleResult shouldContainExactly expectedPeople

            // Chats should contain exactly the chats queries (reversed)
            val expectedChats = chatsQueries.reversed()
            chatsResult shouldContainExactly expectedChats

            // Modifying one screen should not affect the other
            store.clearAll("chats")
            val peopleAfterChatsClear = store.getRecentSearches("people").first()
            peopleAfterChatsClear shouldContainExactly expectedPeople

            val chatsAfterClear = store.getRecentSearches("chats").first()
            chatsAfterClear shouldBe emptyList()
        }
    }

}) {
    companion object {
        init {
            // Lock the seed for reproducible counter-examples.
            PropertyTesting.defaultSeed = 0xCAFEL
        }
    }
}
