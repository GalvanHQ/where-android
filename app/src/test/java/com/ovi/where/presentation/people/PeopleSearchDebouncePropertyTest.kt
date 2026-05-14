package com.ovi.where.presentation.people

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * Property-based test verifying debounce semantics on the query flow.
 *
 * The property under test: For any sequence of text inputs arriving within 300ms
 * of each other, only the final value in the sequence triggers the search callback,
 * and it triggers exactly once after 300ms of silence.
 *
 * This tests the same debounce pattern used in [PeopleViewModel]:
 * `_query.debounce(300).distinctUntilChanged()`
 *
 * **Validates: Requirements 5.1**
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PeopleSearchDebouncePropertyTest : StringSpec({

    // Generator: lists of 2..10 non-empty alphanumeric strings (simulating rapid keystrokes)
    val rapidInputsArb: Arb<List<String>> = Arb.list(
        Arb.string(minSize = 1, codepoints = Codepoint.alphanumeric()),
        range = 2..10
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Property 2: Debounce emits only final value
    // Tag: Feature: messenger-style-search, Property 2: Debounce emits only final value
    // ─────────────────────────────────────────────────────────────────────────

    "Feature: messenger-style-search, Property 2: Debounce emits only final value" {
        checkAll(iterations = 100, rapidInputsArb) { inputs ->
            runTest {
                // Use a SharedFlow (no replay/conflation) to ensure each emit is seen
                val queryFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

                // Apply debounce(300) + distinctUntilChanged (same pattern as ViewModel)
                val debouncedFlow = queryFlow
                    .debounce(300)
                    .distinctUntilChanged()

                // Collect debounced emissions using UnconfinedTestDispatcher so
                // collection processes eagerly when time advances
                val emissions = mutableListOf<String>()
                val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
                    debouncedFlow.collect { emissions.add(it) }
                }

                // Emit all inputs rapidly (no time advancement between them)
                // This simulates typing within the 300ms debounce window
                for (input in inputs) {
                    queryFlow.emit(input)
                }

                // Advance virtual time past the debounce window to trigger emission
                advanceTimeBy(301)

                // The debounced flow should emit only the final value exactly once
                val finalValue = inputs.last()
                emissions shouldHaveSize 1
                emissions.first() shouldBe finalValue

                // Clean up
                collectJob.cancel()
            }
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
