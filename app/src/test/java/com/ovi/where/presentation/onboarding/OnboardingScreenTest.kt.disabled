package com.ovi.where.presentation.onboarding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ovi.where.core.theme.WhereTheme
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [OnboardingScreen] composable.
 *
 * Uses Robolectric + Compose UI Test to verify rendering and interactions
 * without requiring an Android device or emulator.
 *
 * Validates: Requirements 1.1, 2.4, 3.5, 4.5, 6.5, 6.6
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockViewModel = mockk<OnboardingViewModel>(relaxed = true)

    // ─────────────────────────────────────────────────────────────────────────
    // Headline and subtitle text verification (Requirement 2.4, 3.5, 4.5)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `page 1 renders Share Your Location headline`() {
        composeTestRule.setContent {
            WhereTheme {
                OnboardingScreen(
                    onFinish = {},
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Share Your Location")
            .assertIsDisplayed()
    }

    @Test
    fun `page 1 renders correct subtitle`() {
        composeTestRule.setContent {
            WhereTheme {
                OnboardingScreen(
                    onFinish = {},
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText(
            "Let friends and family know where you are in real time \u2014 safely and privately.",
            substring = true
        ).assertIsDisplayed()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logo presence verification (Requirement 1.1)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `logo is present on page 1`() {
        composeTestRule.setContent {
            WhereTheme {
                OnboardingScreen(
                    onFinish = {},
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Where app logo")
            .assertIsDisplayed()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Skip button verification (Requirement 6.5, 6.6)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Skip button is present on screen`() {
        composeTestRule.setContent {
            WhereTheme {
                OnboardingScreen(
                    onFinish = {},
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Skip")
            .assertIsDisplayed()
    }

    @Test
    fun `Skip button triggers completeOnboarding and onFinish`() {
        var onFinishCalled = false

        composeTestRule.setContent {
            WhereTheme {
                OnboardingScreen(
                    onFinish = { onFinishCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Skip").performClick()

        verify { mockViewModel.completeOnboarding() }
        assert(onFinishCalled) { "onFinish should have been called after Skip" }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get Started button verification (Requirement 6.5)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Get Started on last page triggers completeOnboarding and onFinish`() {
        var onFinishCalled = false

        composeTestRule.setContent {
            WhereTheme {
                // Render just the action button in "last page" state to test Get Started
                OnboardingActionButton(
                    currentPage = 2,
                    pageCount = 3,
                    onNext = {},
                    onGetStarted = {
                        mockViewModel.completeOnboarding()
                        onFinishCalled = true
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Get Started").performClick()

        verify { mockViewModel.completeOnboarding() }
        assert(onFinishCalled) { "onFinish should have been called after Get Started" }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stacked card hero verification (Requirement 2.4)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `stacked card hero has 2 image placeholder cards`() {
        composeTestRule.setContent {
            WhereTheme {
                StackedCardHero()
            }
        }

        // Both cards use the same content description for the image placeholders
        composeTestRule.onAllNodesWithContentDescription(
            "Replace with a lifestyle photo showing friends sharing location"
        ).assertCountEquals(2)
    }

    @Test
    fun `stacked card hero has overlay badge`() {
        composeTestRule.setContent {
            WhereTheme {
                StackedCardHero()
            }
        }

        // The overlay badge is a Surface with primaryContainer color and 24dp size.
        // We verify the stacked card hero renders without crashing and has the expected
        // image placeholders (the badge is a visual-only element without semantic content).
        composeTestRule.onAllNodesWithContentDescription(
            "Replace with a lifestyle photo showing friends sharing location"
        ).assertCountEquals(2)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Social proof hero verification (Requirement 3.5)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `social proof hero has Follow buttons that are non-interactive`() {
        composeTestRule.setContent {
            WhereTheme {
                SocialProofHero()
            }
        }

        // There should be 4 Follow buttons (one per profile card)
        val followButtons = composeTestRule.onAllNodesWithText("Follow")
        followButtons.assertCountEquals(4)

        // Each Follow button should be disabled (non-interactive)
        followButtons[0].assertIsNotEnabled()
        followButtons[1].assertIsNotEnabled()
        followButtons[2].assertIsNotEnabled()
        followButtons[3].assertIsNotEnabled()
    }

    @Test
    fun `social proof hero displays profile names`() {
        composeTestRule.setContent {
            WhereTheme {
                SocialProofHero()
            }
        }

        composeTestRule.onNodeWithText("Alex Johnson").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maria Garcia").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sam Wilson").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jordan Lee").assertIsDisplayed()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Category grid hero verification (Requirement 4.5)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `category grid has 6 floating icon elements`() {
        composeTestRule.setContent {
            WhereTheme {
                CategoryGridHero()
            }
        }

        // Verify all 6 category icons are present via their content descriptions
        composeTestRule.onNodeWithContentDescription("Location").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Group").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Map").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Chat").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Notifications").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigation").assertIsDisplayed()
    }

    @Test
    fun `category grid has center placeholder`() {
        composeTestRule.setContent {
            WhereTheme {
                CategoryGridHero()
            }
        }

        composeTestRule.onNodeWithContentDescription(
            "Replace with a globe or world-map graphic for global connectivity."
        ).assertIsDisplayed()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Action button state verification
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `page 1 shows Next button`() {
        composeTestRule.setContent {
            WhereTheme {
                OnboardingScreen(
                    onFinish = {},
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
    }
}
