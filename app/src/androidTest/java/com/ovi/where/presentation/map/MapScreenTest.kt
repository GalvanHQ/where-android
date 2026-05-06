package com.ovi.where.presentation.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ovi.where.MainActivity
import com.ovi.where.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `Map screen shows start sharing button`() {
        composeTestRule.setContent {
            MapScreen(
                groupId = "test_group",
                onNavigateBack = {}
            )
        }

        onNodeWithText(composeTestRule.activity.getString(R.string.btn_start_sharing))
            .assertIsDisplayed()
    }

    @Test
    fun `Map screen shows my location button`() {
        composeTestRule.setContent {
            MapScreen(
                groupId = "test_group",
                onNavigateBack = {}
            )
        }

        onNodeWithText(composeTestRule.activity.getString(R.string.btn_my_location))
            .assertIsDisplayed()
    }
}