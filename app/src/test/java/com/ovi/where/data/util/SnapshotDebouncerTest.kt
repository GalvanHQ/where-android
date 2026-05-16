package com.ovi.where.data.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize as mapShouldHaveSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [SnapshotDebouncer].
 *
 * Validates Requirement 5.6: Firestore snapshot listener emitting multiple updates
 * within a 500ms fixed window are coalesced into a single Room database write.
 */
class SnapshotDebouncerTest : StringSpec({

    "single submit triggers writer after debounce window" {
        runTest {
            val debouncer = SnapshotDebouncer(this)
            val writtenBatches = mutableListOf<Map<String, String>>()

            debouncer.submit("doc1", "state-A") { batch ->
                writtenBatches.add(batch)
            }

            // Before window expires, no write should have occurred
            delay(200)
            writtenBatches shouldHaveSize 0

            // After window expires, write should occur
            delay(400)
            writtenBatches shouldHaveSize 1
            writtenBatches[0] shouldBe mapOf("doc1" to "state-A")
        }
    }

    "multiple updates to same document coalesces to latest state" {
        runTest {
            val debouncer = SnapshotDebouncer(this)
            val writtenBatches = mutableListOf<Map<String, String>>()

            debouncer.submit("doc1", "state-A") { batch ->
                writtenBatches.add(batch)
            }
            debouncer.submit("doc1", "state-B") { batch ->
                writtenBatches.add(batch)
            }
            debouncer.submit("doc1", "state-C") { batch ->
                writtenBatches.add(batch)
            }

            // Wait for debounce window to expire
            delay(600)

            // Only one write should have occurred with the latest state
            writtenBatches shouldHaveSize 1
            writtenBatches[0] shouldBe mapOf("doc1" to "state-C")
        }
    }

    "multiple different documents are batched into single write" {
        runTest {
            val debouncer = SnapshotDebouncer(this)
            val writtenBatches = mutableListOf<Map<String, String>>()

            debouncer.submit("doc1", "state-1") { batch ->
                writtenBatches.add(batch)
            }
            debouncer.submit("doc2", "state-2") { batch ->
                writtenBatches.add(batch)
            }
            debouncer.submit("doc3", "state-3") { batch ->
                writtenBatches.add(batch)
            }

            // Wait for debounce window to expire
            delay(600)

            // Single write with all 3 documents
            writtenBatches shouldHaveSize 1
            writtenBatches[0].size shouldBe 3
            writtenBatches[0]["doc1"] shouldBe "state-1"
            writtenBatches[0]["doc2"] shouldBe "state-2"
            writtenBatches[0]["doc3"] shouldBe "state-3"
        }
    }

    "updates after window expires start a new batch" {
        runTest {
            val debouncer = SnapshotDebouncer(this)
            val writtenBatches = mutableListOf<Map<String, String>>()

            // First batch
            debouncer.submit("doc1", "batch1-state") { batch ->
                writtenBatches.add(batch)
            }

            // Wait for first window to expire
            delay(600)
            writtenBatches shouldHaveSize 1

            // Second batch (new window)
            debouncer.submit("doc2", "batch2-state") { batch ->
                writtenBatches.add(batch)
            }

            // Wait for second window to expire
            delay(600)
            writtenBatches shouldHaveSize 2
            writtenBatches[0] shouldBe mapOf("doc1" to "batch1-state")
            writtenBatches[1] shouldBe mapOf("doc2" to "batch2-state")
        }
    }

    "latest state wins when same document updated multiple times" {
        runTest {
            val debouncer = SnapshotDebouncer(this)
            val writtenBatches = mutableListOf<Map<String, Int>>()

            // Simulate rapid Firestore snapshot emissions
            for (i in 1..10) {
                debouncer.submit("doc1", i) { batch ->
                    writtenBatches.add(batch)
                }
            }

            // Wait for debounce window
            delay(600)

            // Only one write with the latest value (10)
            writtenBatches shouldHaveSize 1
            writtenBatches[0] shouldBe mapOf("doc1" to 10)
        }
    }

    "mixed document updates coalesce correctly" {
        runTest {
            val debouncer = SnapshotDebouncer(this)
            val writtenBatches = mutableListOf<Map<String, String>>()

            // doc1 updated 3 times, doc2 updated 2 times, doc3 updated once
            debouncer.submit("doc1", "v1") { batch -> writtenBatches.add(batch) }
            debouncer.submit("doc2", "v1") { batch -> writtenBatches.add(batch) }
            debouncer.submit("doc1", "v2") { batch -> writtenBatches.add(batch) }
            debouncer.submit("doc3", "v1") { batch -> writtenBatches.add(batch) }
            debouncer.submit("doc2", "v2") { batch -> writtenBatches.add(batch) }
            debouncer.submit("doc1", "v3") { batch -> writtenBatches.add(batch) }

            // Wait for debounce window
            delay(600)

            // Single write with latest state per document
            writtenBatches shouldHaveSize 1
            writtenBatches[0].size shouldBe 3
            writtenBatches[0]["doc1"] shouldBe "v3"
            writtenBatches[0]["doc2"] shouldBe "v2"
            writtenBatches[0]["doc3"] shouldBe "v1"
        }
    }

    "empty batch does not invoke writer" {
        runTest {
            val debouncer = SnapshotDebouncer(this)
            val writtenBatches = mutableListOf<Map<String, String>>()

            // Submit and immediately the debounce window starts
            debouncer.submit("doc1", "state") { batch -> writtenBatches.add(batch) }

            // Wait for window to expire and write to complete
            delay(600)
            writtenBatches shouldHaveSize 1

            // No more submits - next window should not trigger writer
            delay(600)
            writtenBatches shouldHaveSize 1
        }
    }

    "pendingCount reflects current buffer size" {
        runTest {
            val debouncer = SnapshotDebouncer(this)

            debouncer.pendingCount shouldBe 0

            debouncer.submit("doc1", "state1") { _ -> }
            debouncer.submit("doc2", "state2") { _ -> }

            debouncer.pendingCount shouldBe 2

            // After flush
            delay(600)
            debouncer.pendingCount shouldBe 0
        }
    }
})
