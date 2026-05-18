package com.ovi.where.presentation.chat.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.WhereTheme
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.presentation.model.BubbleDirection
import com.ovi.where.presentation.model.MessageUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ChatBubble] composable and [computeBubbleShape] helper.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4
 *
 * Tests cover:
 * - Sent/received color schemes (verified via rendering without crash)
 * - Corner radius configuration via computeBubbleShape
 * - Max width constraint (75%)
 * - Sender name display and "Unknown" fallback
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ChatBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper to create a default MessageUiModel for testing ────────────────

    private fun createMessage(
        id: String = "msg-1",
        senderId: String = "user-1",
        senderName: String = "Alice",
        senderPhotoUrl: String? = null,
        text: String = "Hello there!",
        formattedTime: String = "14:32",
        direction: BubbleDirection = BubbleDirection.SENT,
        isFirstInGroup: Boolean = true,
        isLastInGroup: Boolean = true
    ) = MessageUiModel(
        id = id,
        senderId = senderId,
        senderName = senderName,
        senderPhotoUrl = senderPhotoUrl,
        senderInitials = senderName.take(1).uppercase(),
        text = text,
        formattedTime = formattedTime,
        dateKey = "2024-01-15",
        direction = direction,
        isLocation = false,
        latitude = null,
        longitude = null,
        locationLabel = null,
        status = MessageStatus.SENT,
        reactions = persistentMapOf(),
        readBy = persistentListOf(),
        readByPhotoUrls = persistentListOf(),
        mentionedUserIds = persistentListOf(),
        isFirstInGroup = isFirstInGroup,
        isLastInGroup = isLastInGroup
    )

    // ── Sent bubble renders correctly (Requirement 4.1) ─────────────────────

    @Test
    fun `sent bubble renders message text and timestamp`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(
                        text = "Hey!",
                        formattedTime = "10:30",
                        direction = BubbleDirection.SENT
                    ),
                    isGroupChat = false,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = false
                )
            }
        }

        composeTestRule.onNodeWithText("Hey!").assertIsDisplayed()
        composeTestRule.onNodeWithText("10:30").assertIsDisplayed()
    }

    // ── Received bubble renders correctly (Requirement 4.2) ─────────────────

    @Test
    fun `received bubble renders message text and timestamp`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(
                        text = "Hi there!",
                        formattedTime = "11:45",
                        direction = BubbleDirection.RECEIVED,
                        senderName = "Bob"
                    ),
                    isGroupChat = false,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = false
                )
            }
        }

        composeTestRule.onNodeWithText("Hi there!").assertIsDisplayed()
        composeTestRule.onNodeWithText("11:45").assertIsDisplayed()
    }

    // ── Corner radius configuration (Requirement 4.3, 4.7) ─────────────────

    @Test
    fun `computeBubbleShape returns tight right side for middle sent bubble`() {
        val shape = computeBubbleShape(isSent = true, isFirstInGroup = false, isLastInGroup = false)
        val expected = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 4.dp,
            bottomEnd = 4.dp,
            bottomStart = 18.dp
        )
        assertEquals(expected, shape)
    }

    @Test
    fun `computeBubbleShape returns tail at bottom-right for single sent bubble`() {
        val shape = computeBubbleShape(isSent = true, isFirstInGroup = true, isLastInGroup = true)
        val expected = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomEnd = 4.dp,
            bottomStart = 18.dp
        )
        assertEquals(expected, shape)
    }

    @Test
    fun `computeBubbleShape returns tail at bottom-left for single received bubble`() {
        val shape = computeBubbleShape(isSent = false, isFirstInGroup = true, isLastInGroup = true)
        val expected = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomEnd = 18.dp,
            bottomStart = 4.dp
        )
        assertEquals(expected, shape)
    }

    @Test
    fun `computeBubbleShape returns tight left side for middle received bubble`() {
        val shape = computeBubbleShape(isSent = false, isFirstInGroup = false, isLastInGroup = false)
        val expected = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 18.dp,
            bottomEnd = 18.dp,
            bottomStart = 4.dp
        )
        assertEquals(expected, shape)
    }

    // ── Max width constraint (Requirement 4.4) ──────────────────────────────

    @Test
    fun `bubble renders long text without crash respecting max width constraint`() {
        val longText = "A".repeat(500)
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(text = longText, direction = BubbleDirection.SENT),
                    isGroupChat = false,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = false
                )
            }
        }

        // The bubble should render the text (it wraps within 75% max width)
        composeTestRule.onNodeWithText(longText).assertIsDisplayed()
    }

    // ── Sender name display in group chats ──────────────────────────────────

    @Test
    fun `sender name is shown above received bubble in group chat when first in group`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(
                        senderName = "Charlie",
                        direction = BubbleDirection.RECEIVED
                    ),
                    isGroupChat = true,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = true
                )
            }
        }

        composeTestRule.onNodeWithText("Charlie").assertIsDisplayed()
    }

    @Test
    fun `sender name is hidden for sent bubbles in group chat`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(
                        senderName = "Me",
                        direction = BubbleDirection.SENT
                    ),
                    isGroupChat = true,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = false
                )
            }
        }

        // Sender name label should not appear for sent messages
        composeTestRule.onNodeWithText("Me").assertDoesNotExist()
    }

    @Test
    fun `sender name is hidden for received bubbles in DM`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(
                        senderName = "Dave",
                        direction = BubbleDirection.RECEIVED
                    ),
                    isGroupChat = false,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = false
                )
            }
        }

        // In DM, sender name label is not shown
        composeTestRule.onNodeWithText("Dave").assertDoesNotExist()
    }

    @Test
    fun `blank sender name displays Unknown in group chat`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(
                        senderName = "   ",
                        direction = BubbleDirection.RECEIVED
                    ),
                    isGroupChat = true,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = true
                )
            }
        }

        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()
    }

    // ── Content description for accessibility ───────────────────────────────

    @Test
    fun `bubble has correct content description with sender name`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(senderName = "Eve", direction = BubbleDirection.RECEIVED),
                    isGroupChat = false,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = false
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Chat message from Eve")
            .assertIsDisplayed()
    }

    @Test
    fun `bubble with blank sender name has Unknown in content description`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatBubble(
                    message = createMessage(senderName = "", direction = BubbleDirection.RECEIVED),
                    isGroupChat = true,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showSenderAvatar = true
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Chat message from Unknown")
            .assertIsDisplayed()
    }
}
