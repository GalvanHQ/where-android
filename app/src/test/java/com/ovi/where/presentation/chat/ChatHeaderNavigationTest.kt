package com.ovi.where.presentation.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.ovi.where.core.theme.WhereTheme
import com.ovi.where.presentation.model.ConversationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for ChatHeader navigation flows.
 *
 * Validates: Requirement 7.9
 *
 * Tests cover:
 * - Tapping header avatar/title navigates to ConversationInfo for DM conversations
 * - Tapping header avatar/title navigates to GroupInfo for group conversations
 * - Back navigation callback is triggered correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ChatHeaderNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper to create a ConversationUiModel for testing ───────────────────

    private fun createDmConversation(
        id: String = "conv-dm-1",
        title: String = "Alice Johnson",
        otherUserId: String = "user-alice"
    ) = ConversationUiModel(
        id = id,
        title = title,
        lastMessageText = "Hey there!",
        lastMessageTime = "10:30",
        unreadCount = 0,
        photoUrl = null,
        isGroup = false,
        groupId = null,
        currentUserId = "current-user",
        memberCount = 2,
        isOtherUserOnline = true,
        otherUserId = otherUserId
    )

    private fun createGroupConversation(
        id: String = "conv-group-1",
        title: String = "Project Team",
        groupId: String = "group-123",
        memberCount: Int = 5
    ) = ConversationUiModel(
        id = id,
        title = title,
        lastMessageText = "Meeting at 3pm",
        lastMessageTime = "09:15",
        unreadCount = 2,
        photoUrl = null,
        isGroup = true,
        groupId = groupId,
        currentUserId = "current-user",
        memberCount = memberCount,
        isOtherUserOnline = false,
        otherUserId = null
    )

    // ── DM navigation: tapping avatar/title navigates to ConversationInfo ────

    @Test
    fun `tapping header avatar area in DM navigates to ConversationInfo`() {
        var navigatedConversationId: String? = null

        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createDmConversation(id = "conv-dm-42", title = "Bob"),
                    onNavigateBack = {},
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = {},
                    onNavigateToConversationInfo = { conversationId ->
                        navigatedConversationId = conversationId
                    },
                    onNavigateToGroupMap = {}
                )
            }
        }

        // Tap the avatar/title area (content description: "View {title} info")
        composeTestRule
            .onNodeWithContentDescription("View Bob info")
            .performClick()

        // Verify navigation was triggered with the correct conversation ID
        assertEquals("conv-dm-42", navigatedConversationId)
    }

    @Test
    fun `tapping header avatar area in DM does not navigate to GroupInfo`() {
        var groupInfoNavigated = false

        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createDmConversation(title = "Carol"),
                    onNavigateBack = {},
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = { groupInfoNavigated = true },
                    onNavigateToConversationInfo = {},
                    onNavigateToGroupMap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("View Carol info")
            .performClick()

        // GroupInfo should NOT be triggered for DM conversations
        assertEquals(false, groupInfoNavigated)
    }

    // ── Group navigation: tapping avatar/title navigates to GroupInfo ─────────

    @Test
    fun `tapping header avatar area in group navigates to GroupInfo`() {
        var navigatedGroupId: String? = null

        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createGroupConversation(
                        title = "Design Team",
                        groupId = "group-design-99"
                    ),
                    onNavigateBack = {},
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = { groupId ->
                        navigatedGroupId = groupId
                    },
                    onNavigateToConversationInfo = {},
                    onNavigateToGroupMap = {}
                )
            }
        }

        // Tap the avatar/title area
        composeTestRule
            .onNodeWithContentDescription("View Design Team info")
            .performClick()

        // Verify navigation was triggered with the correct group ID
        assertEquals("group-design-99", navigatedGroupId)
    }

    @Test
    fun `tapping header avatar area in group does not navigate to ConversationInfo`() {
        var conversationInfoNavigated = false

        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createGroupConversation(title = "Friends"),
                    onNavigateBack = {},
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = {},
                    onNavigateToConversationInfo = { conversationInfoNavigated = true },
                    onNavigateToGroupMap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("View Friends info")
            .performClick()

        // ConversationInfo should NOT be triggered for group conversations
        assertEquals(false, conversationInfoNavigated)
    }

    // ── Back navigation ──────────────────────────────────────────────────────

    @Test
    fun `tapping back button triggers onNavigateBack callback`() {
        var backNavigated = false

        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createDmConversation(title = "Dave"),
                    onNavigateBack = { backNavigated = true },
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = {},
                    onNavigateToConversationInfo = {},
                    onNavigateToGroupMap = {}
                )
            }
        }

        // Tap the back button
        composeTestRule
            .onNodeWithContentDescription("Back")
            .performClick()

        assertTrue("Back navigation should be triggered", backNavigated)
    }

    @Test
    fun `back button is displayed and accessible from DM info context`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createDmConversation(title = "Eve"),
                    onNavigateBack = {},
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = {},
                    onNavigateToConversationInfo = {},
                    onNavigateToGroupMap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }

    @Test
    fun `back button is displayed and accessible from group info context`() {
        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createGroupConversation(title = "Work Group"),
                    onNavigateBack = {},
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = {},
                    onNavigateToConversationInfo = {},
                    onNavigateToGroupMap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }

    // ── Navigation target correctness with different IDs ─────────────────────

    @Test
    fun `DM navigation passes correct conversation ID for different conversations`() {
        var navigatedId: String? = null

        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createDmConversation(
                        id = "unique-conv-abc123",
                        title = "Frank"
                    ),
                    onNavigateBack = {},
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = {},
                    onNavigateToConversationInfo = { navigatedId = it },
                    onNavigateToGroupMap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("View Frank info")
            .performClick()

        assertEquals("unique-conv-abc123", navigatedId)
    }

    @Test
    fun `group navigation passes correct group ID for different groups`() {
        var navigatedGroupId: String? = null

        composeTestRule.setContent {
            WhereTheme {
                ChatHeader(
                    conversation = createGroupConversation(
                        title = "Gaming Squad",
                        groupId = "grp-gaming-xyz"
                    ),
                    onNavigateBack = {},
                    onNavigateToUserProfile = {},
                    onNavigateToGroupInfo = { navigatedGroupId = it },
                    onNavigateToConversationInfo = {},
                    onNavigateToGroupMap = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("View Gaming Squad info")
            .performClick()

        assertEquals("grp-gaming-xyz", navigatedGroupId)
    }
}
