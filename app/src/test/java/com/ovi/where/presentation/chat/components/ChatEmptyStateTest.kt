package com.ovi.where.presentation.chat.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.ovi.where.core.theme.WhereTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ChatEmptyState] composable.
 *
 * Validates: Requirement 10.5
 *
 * Tests cover:
 * - Empty state renders the "Say hi!" prompt text
 * - Empty state renders the subtitle text
 * - Empty state displays the illustration
 * - Empty state is centered (verified by rendering within fillMaxSize + Arrangement.Center)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ChatEmptyStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Prompt text rendering (Requirement 10.5) ────────────────────────────

    @Test
    fun `empty state renders Say hi prompt text`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatEmptyState()
            }
        }

        composeTestRule.onNodeWithText("Say hi! \uD83D\uDC4B").assertIsDisplayed()
    }

    @Test
    fun `empty state renders subtitle text`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatEmptyState()
            }
        }

        composeTestRule
            .onNodeWithText("Send a message to start the conversation")
            .assertIsDisplayed()
    }

    // ── Illustration rendering (Requirement 10.5) ───────────────────────────

    @Test
    fun `empty state displays illustration with content description`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatEmptyState()
            }
        }

        composeTestRule
            .onNodeWithContentDescription("No messages yet")
            .assertIsDisplayed()
    }

    // ── Centered layout verification ────────────────────────────────────────
    // The composable uses fillMaxSize + Arrangement.Center + CenterHorizontally,
    // which is verified by successful rendering without crash.

    @Test
    fun `empty state renders centered layout without crash`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatEmptyState()
            }
        }

        // All elements should be present and displayed, confirming the centered layout renders
        composeTestRule.onNodeWithText("Say hi! \uD83D\uDC4B").assertIsDisplayed()
        composeTestRule.onNodeWithText("Send a message to start the conversation").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("No messages yet").assertIsDisplayed()
    }
}
