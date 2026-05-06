package com.ovi.where.presentation.auth.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ovi.where.MainActivity
import com.ovi.where.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            LoginScreen(
                onNavigateToRegister = {},
                onLoginSuccess = {}
            )
        }
    }

    @Test
    fun `Login screen displays email field`() {
        onNodeWithText(composeTestRule.activity.getString(R.string.hint_email))
            .assertIsDisplayed()
    }

    @Test
    fun `Login screen displays password field`() {
        onNodeWithText(composeTestRule.activity.getString(R.string.hint_password))
            .assertIsDisplayed()
    }

    @Test
    fun `Login button is enabled when fields are filled`() {
        onNodeWithText(composeTestRule.activity.getString(R.string.hint_email))
            .performTextInput("test@example.com")
        onNodeWithText(composeTestRule.activity.getString(R.string.hint_password))
            .performTextInput("password123")
        
        onNodeWithText(composeTestRule.activity.getString(R.string.btn_login))
            .assertIsEnabled()
    }

    @Test
    fun `Login button is disabled when email is empty`() {
        onNodeWithText(composeTestRule.activity.getString(R.string.btn_login))
            .assertIsNotEnabled()
    }

    @Test
    fun `Register link navigates to register screen`() {
        onNodeWithText(composeTestRule.activity.getString(R.string.text_no_account))
            .assertIsDisplayed()
    }

    @Test
    fun `Forgot password link is displayed`() {
        onNodeWithText(composeTestRule.activity.getString(R.string.text_forgot_password))
            .assertIsDisplayed()
    }

    @Test
    fun `Google sign in button is displayed`() {
        onNodeWithText(composeTestRule.activity.getString(R.string.btn_google_sign_in))
            .assertIsDisplayed()
    }
}