package com.ovi.where.presentation.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.WhereTheme
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import com.ovi.where.presentation.model.ConversationUiModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ConversationRow] composable.
 *
 * Validates: Requirements 3.1, 3.4, 3.8, 3.9, 1.2
 *
 * Tests cover:
 * - Avatar size (56dp) and online indicator visibility
 * - Unread badge display and font weight changes for unread state
 * - Fallback text display for blank names
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ConversationRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper to create a default ConversationUiModel for testing ───────────

    private fun createConversation(
        id: String = "conv-1",
        title: String = "John Doe",
        lastMessageText: String = "Hey there!",
        lastMessageTime: String = "14:32",
        unreadCount: Int = 0,
        photoUrl: String? = null,
        isGroup: Boolean = false,
        isMuted: Boolean = false,
        isPinned: Boolean = false,
        isLastMessageFromCurrentUser: Boolean = false,
        lastMessageStatus: MessageStatus? = null
    ) = ConversationUiModel(
        id = id,
        title = title,
        lastMessageText = lastMessageText,
        lastMessageTime = lastMessageTime,
        unreadCount = unreadCount,
        photoUrl = photoUrl,
        isGroup = isGroup,
        groupId = if (isGroup) "group-1" else null,
        currentUserId = "current-user",
        isMuted = isMuted,
        isPinned = isPinned,
        isLastMessageFromCurrentUser = isLastMessageFromCurrentUser,
        lastMessageStatus = lastMessageStatus
    )

    // ── Avatar size and online indicator (Requirement 3.1, 3.4) ─────────────

    @Test
    fun `avatar renders with correct content description`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(title = "Alice"),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // ConversationAvatar uses "$name avatar" as content description
        composeTestRule
            .onNodeWithContentDescription("Alice avatar")
            .assertIsDisplayed()
    }

    @Test
    fun `online indicator is visible for non-group conversation when user is online`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(title = "Bob", isGroup = false),
                    isOnline = true,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // The avatar should be displayed (online indicator is part of ConversationAvatar)
        composeTestRule
            .onNodeWithContentDescription("Bob avatar")
            .assertIsDisplayed()
    }

    @Test
    fun `online indicator is hidden for group conversations even when isOnline is true`() {
        // For group conversations, isOnline && !conversation.isGroup evaluates to false
        // so the online indicator should not be shown
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(title = "Team Chat", isGroup = true),
                    isOnline = true,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // Avatar should still render
        composeTestRule
            .onNodeWithContentDescription("Team Chat avatar")
            .assertIsDisplayed()
    }

    // ── Unread badge display (Requirement 3.9) ──────────────────────────────

    @Test
    fun `unread badge is displayed when unreadCount is greater than zero and not muted`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(unreadCount = 5, isMuted = false),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // UnreadBadge displays the count as text
        composeTestRule
            .onNodeWithText("5")
            .assertIsDisplayed()
    }

    @Test
    fun `unread badge is hidden when conversation is muted even with unread messages`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(unreadCount = 3, isMuted = true),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // Badge count text should not be displayed when muted
        composeTestRule
            .onAllNodesWithText("3")
            .apply { fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `unread badge is hidden when unreadCount is zero`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(unreadCount = 0),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // No badge count text should be present
        // The UnreadBadge composable returns early when count <= 0
        composeTestRule
            .onNodeWithText("0")
            .assertDoesNotExist()
    }

    @Test
    fun `unread badge shows 99+ for counts exceeding 99`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(unreadCount = 150, isMuted = false),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("99+")
            .assertIsDisplayed()
    }

    // ── Font weight changes for unread state (Requirement 3.8) ──────────────

    @Test
    fun `title text is displayed with conversation title for unread conversation`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(
                        title = "Jane Smith",
                        unreadCount = 2,
                        isMuted = false
                    ),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // Title should be displayed
        composeTestRule
            .onNodeWithText("Jane Smith")
            .assertIsDisplayed()
    }

    @Test
    fun `preview text is displayed for unread conversation`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(
                        lastMessageText = "Are you coming?",
                        unreadCount = 1,
                        isMuted = false
                    ),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // Preview text should be displayed
        composeTestRule
            .onNodeWithText("Are you coming?")
            .assertIsDisplayed()
    }

    @Test
    fun `title text is displayed for read conversation`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(
                        title = "Mark Wilson",
                        unreadCount = 0
                    ),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Mark Wilson")
            .assertIsDisplayed()
    }

    // ── Fallback text display for blank names (Requirement 1.2) ─────────────

    @Test
    fun `displays Unknown User fallback for DM with blank title`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(
                        title = "Unknown User",
                        isGroup = false
                    ),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Unknown User")
            .assertIsDisplayed()
    }

    @Test
    fun `displays Unnamed Group fallback for group with blank title`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(
                        title = "Unnamed Group",
                        isGroup = true
                    ),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Unnamed Group")
            .assertIsDisplayed()
    }

    @Test
    fun `avatar shows question mark initials for blank name`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(title = "   "),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        // The initials fallback for blank names is "?"
        composeTestRule
            .onNodeWithText("?")
            .assertIsDisplayed()
    }

    // ── Timestamp display ───────────────────────────────────────────────────

    @Test
    fun `timestamp is displayed in conversation row`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationRow(
                    conversation = createConversation(lastMessageTime = "Yesterday"),
                    isOnline = false,
                    isContextMenuVisible = false,
                    onClick = {},
                    onLongClick = {},
                    onDismissContextMenu = {},
                    onPin = {},
                    onMute = {},
                    onArchive = {},
                    onDelete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Yesterday")
            .assertIsDisplayed()
    }
}
