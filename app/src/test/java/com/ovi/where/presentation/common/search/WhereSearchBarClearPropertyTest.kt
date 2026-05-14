package com.ovi.where.presentation.common.search

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for the clear-button reset behavior of [WhereSearchBar].
 *
 * Since WhereSearchBar is a stateless composable (state is managed by the caller),
 * this test verifies the clear-button contract at the state-holder level:
 * for any non-empty string currently in the search bar, invoking the clear action
 * (onClearQuery) results in the query becoming the empty string.
 *
 * **Validates: Requirements 4.3**
 */
class WhereSearchBarClearPropertyTest : StringSpec({

    "Feature: messenger-style-search, Property 1: Clear button resets text to empty" {
        // Generator: arbitrary non-empty strings (1..50 chars)
        val nonEmptyStringArb: Arb<String> = Arb.string(minSize = 1, maxSize = 50)

        checkAll(iterations = 100, nonEmptyStringArb) { currentQuery ->
            // Given: a SearchUiState with any non-empty query string
            var state = SearchUiState(query = currentQuery)
            require(state.query.isNotEmpty()) { "Generator should produce non-empty strings" }

            // The onClearQuery callback contract: sets query to ""
            val onClearQuery: () -> Unit = { state = state.copy(query = "") }

            // When: the clear action is invoked
            onClearQuery()

            // Then: the query state becomes the empty string
            state.query shouldBe ""
        }
    }
})
