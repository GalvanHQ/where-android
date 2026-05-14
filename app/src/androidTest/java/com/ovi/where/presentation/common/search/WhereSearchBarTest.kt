package com.ovi.where.presentation.common.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ovi.where.core.utils.LocalReducedMotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [WhereSearchBar].
 *
 * Validates:
 * - Requirements 4.3: Clear button toggles with text presence, tap invokes onClearQuery
 * - Requirements 4.4: Focus toggles the expanded content
 * - Requirements 7.5: Empty/whitespace-only submission is ignored
 */
@RunWith(AndroidJUnit4::class)
class WhereSearchBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Clear button visibility and behavior (Requirement 4.3) ──────────────

    @Test
    fun clearButton_notVisible_whenQueryIsEmpty() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                WhereSearchBar(
                    query = "",
                    onQueryChanged = {},
                    onQuerySubmitted = {},
                    onClearQuery = {},
                    placeholderText = "Search...",
                    isFocused = false
                )
            }
        }

        composeTestRule
            .onAllNodesWithContentDescription("Clear search")
            .assertCountEquals(0)
    }

    @Test
    fun clearButton_visible_whenQueryIsNonEmpty() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                WhereSearchBar(
                    query = "hello",
                    onQueryChanged = {},
                    onQuerySubmitted = {},
                    onClearQuery = {},
                    placeholderText = "Search...",
                    isFocused = true
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Clear search")
            .assertIsDisplayed()
    }

    @Test
    fun clearButton_tap_invokesOnClearQuery() {
        var clearInvoked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                WhereSearchBar(
                    query = "some text",
                    onQueryChanged = {},
                    onQuerySubmitted = {},
                    onClearQuery = { clearInvoked = true },
                    placeholderText = "Search...",
                    isFocused = true
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Clear search")
            .performClick()

        assertTrue("onClearQuery should be invoked on clear button tap", clearInvoked)
    }

    // ── Focus toggles expanded content (Requirement 4.4) ────────────────────

    @Test
    fun expandedContent_shown_whenFocusedAndQueryEmpty() {
        val recentSearches = listOf("pizza", "sushi")

        composeTestRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                WhereSearchBar(
                    query = "",
                    onQueryChanged = {},
                    onQuerySubmitted = {},
                    onClearQuery = {},
                    placeholderText = "Search...",
                    isFocused = true,
                    recentSearches = recentSearches,
                    onRecentSearchTapped = {},
                    onRecentSearchDeleted = {},
                    onClearAllRecentSearches = {}
                )
            }
        }

        // Recent searches section should be visible (header text "Recent")
        composeTestRule
            .onNodeWithText("Recent")
            .assertIsDisplayed()

        // Individual chips should be visible
        composeTestRule
            .onNodeWithText("pizza")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("sushi")
            .assertIsDisplayed()
    }

    @Test
    fun expandedContent_hidden_whenNotFocused() {
        val recentSearches = listOf("pizza", "sushi")

        composeTestRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                WhereSearchBar(
                    query = "",
                    onQueryChanged = {},
                    onQuerySubmitted = {},
                    onClearQuery = {},
                    placeholderText = "Search...",
                    isFocused = false,
                    recentSearches = recentSearches,
                    onRecentSearchTapped = {},
                    onRecentSearchDeleted = {},
                    onClearAllRecentSearches = {}
                )
            }
        }

        // Recent searches section should NOT be visible when unfocused
        composeTestRule
            .onAllNodesWithText("Recent")
            .assertCountEquals(0)
    }

    // ── Empty/whitespace-only submission is ignored (Requirement 7.5) ────────

    @Test
    fun submission_ignored_whenQueryIsEmpty() {
        var submitted = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                WhereSearchBar(
                    query = "",
                    onQueryChanged = {},
                    onQuerySubmitted = { submitted = true },
                    onClearQuery = {},
                    placeholderText = "Search...",
                    isFocused = true
                )
            }
        }

        // Trigger IME search action on the text field
        composeTestRule
            .onNodeWithText("Search...", useUnmergedTree = true)
            .performImeAction()

        assertFalse("onQuerySubmitted should NOT be invoked for empty query", submitted)
    }

    @Test
    fun submission_ignored_whenQueryIsWhitespaceOnly() {
        var submitted = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                WhereSearchBar(
                    query = "   ",
                    onQueryChanged = {},
                    onQuerySubmitted = { submitted = true },
                    onClearQuery = {},
                    placeholderText = "Search...",
                    isFocused = true
                )
            }
        }

        // The BasicTextField has the whitespace text; trigger IME search action
        composeTestRule
            .onNodeWithText("   ", useUnmergedTree = true)
            .performImeAction()

        assertFalse("onQuerySubmitted should NOT be invoked for whitespace-only query", submitted)
    }
}
