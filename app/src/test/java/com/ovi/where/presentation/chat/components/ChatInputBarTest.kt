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
 * Unit tests for [ChatInputBar] composable.
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.6
 *
 * Tests cover:
 * - Send button visibility toggle based on text content
 * - Placeholder text "Aa" when empty
 * - Camera/attachment icons visible when text is empty, hidden when text present
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ChatInputBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Send button visibility (Requirement 5.2) ────────────────────────────

    @Test
    fun `send button is visible when text is non-empty`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "Hello",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Send message")
            .assertIsDisplayed()
    }

    @Test
    fun `send button is hidden when text is empty`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Send message")
            .assertDoesNotExist()
    }

    @Test
    fun `send button is hidden when text is only whitespace`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "   ",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Send message")
            .assertDoesNotExist()
    }

    // ── Placeholder text (Requirement 5.6) ──────────────────────────────────

    @Test
    fun `placeholder Aa is shown when text is empty`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Aa")
            .assertIsDisplayed()
    }

    @Test
    fun `placeholder Aa is hidden when text is present`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "typing...",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Aa")
            .assertDoesNotExist()
    }

    // ── Action icons swap (Requirement 5.3) ─────────────────────────────────

    @Test
    fun `camera icon is visible when text is empty`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Camera")
            .assertIsDisplayed()
    }

    @Test
    fun `attachment icon is visible when text is empty`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Attach file")
            .assertIsDisplayed()
    }

    @Test
    fun `camera icon is hidden when text is non-empty`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "Hello",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Camera")
            .assertDoesNotExist()
    }

    @Test
    fun `attachment icon is hidden when text is non-empty`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatInputBar(
                    text = "Hello",
                    onTextChange = {},
                    onSend = {},
                    onCameraTap = {},
                    onAttachmentTap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Attach file")
            .assertDoesNotExist()
    }
}
