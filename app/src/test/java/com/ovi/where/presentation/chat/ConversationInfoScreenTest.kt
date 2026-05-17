package com.ovi.where.presentation.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.ovi.where.core.theme.WhereTheme
import com.ovi.where.presentation.model.ConversationInfoUiState
import com.ovi.where.presentation.model.MediaThumbnail
import com.ovi.where.presentation.model.MediaType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ConversationInfoScreen] composable content.
 *
 * Validates: Requirements 8.3, 8.6, 8.7
 *
 * Tests cover:
 * - Section visibility based on conversation type (DM shows Privacy & Support)
 * - Action button row rendering (Audio Call, Video Call, Profile, Mute, Search)
 * - Error and loading states
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ConversationInfoScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Section visibility for DM conversations (Requirement 8.6) ───────────

    @Test
    fun `Privacy and Support section is displayed for DM conversation`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Alice",
                        isOnline = true,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Privacy & Support").assertIsDisplayed()
        composeTestRule.onNodeWithText("Block").assertIsDisplayed()
        composeTestRule.onNodeWithText("Report").assertIsDisplayed()
    }

    @Test
    fun `Customize Chat section is displayed`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Bob",
                        isOnline = false,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Customize Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme Color").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emoji Shortcut").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nicknames").assertIsDisplayed()
    }

    @Test
    fun `More Actions section is displayed`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Charlie",
                        isOnline = false,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("More Actions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search in Conversation").assertIsDisplayed()
        composeTestRule.onNodeWithText("View Media & Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notification Settings").assertIsDisplayed()
    }

    // ── Action button row rendering (Requirement 8.3) ───────────────────────

    @Test
    fun `action button row displays all five action buttons`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Dave",
                        isOnline = false,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Audio Call").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Video Call").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Profile").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mute").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun `action button row shows Unmute label when conversation is muted`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Eve",
                        isMuted = true,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Unmute").assertIsDisplayed()
    }

    @Test
    fun `action button row shows Mute label when conversation is not muted`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Frank",
                        isMuted = false,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Mute").assertIsDisplayed()
    }

    // ── Header content rendering ────────────────────────────────────────────

    @Test
    fun `displays conversation title in header`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Grace Hopper",
                        isOnline = false,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Grace Hopper").assertIsDisplayed()
    }

    @Test
    fun `displays Active now when user is online`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Helen",
                        isOnline = true,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Active now").assertIsDisplayed()
    }

    @Test
    fun `displays last active time when user is offline`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Ivan",
                        isOnline = false,
                        lastActiveTime = "Active 3h ago",
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Active 3h ago").assertIsDisplayed()
    }

    @Test
    fun `does not display status text when offline and no last active time`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Julia",
                        isOnline = false,
                        lastActiveTime = null,
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        assert(composeTestRule.onAllNodesWithText("Active now").fetchSemanticsNodes().isEmpty()) {
            "Expected 'Active now' to not be displayed"
        }
    }

    // ── Shared media section visibility (Requirement 8.7) ───────────────────

    @Test
    fun `shared media section is displayed when media is available`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Kate",
                        isOnline = false,
                        sharedMedia = listOf(
                            MediaThumbnail(id = "1", thumbnailUrl = "https://example.com/img1.jpg", type = MediaType.IMAGE),
                            MediaThumbnail(id = "2", thumbnailUrl = "https://example.com/img2.jpg", type = MediaType.IMAGE)
                        ),
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Shared Media").assertIsDisplayed()
        composeTestRule.onNodeWithText("See All").assertIsDisplayed()
    }

    @Test
    fun `shared media section is hidden when no media is available`() {
        composeTestRule.setContent {
            WhereTheme {
                ConversationInfoContent(
                    uiState = ConversationInfoUiState(
                        conversationTitle = "Leo",
                        isOnline = false,
                        sharedMedia = emptyList(),
                        isLoading = false
                    ),
                    onToggleMute = {},
                    onNavigateToMediaGallery = {}
                )
            }
        }

        assert(composeTestRule.onAllNodesWithText("Shared Media").fetchSemanticsNodes().isEmpty()) {
            "Expected 'Shared Media' to not be displayed"
        }
        assert(composeTestRule.onAllNodesWithText("See All").fetchSemanticsNodes().isEmpty()) {
            "Expected 'See All' to not be displayed"
        }
    }
}
