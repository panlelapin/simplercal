package com.example.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class LauncherTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun launcherShowsContent() {
        composeRule.onNodeWithText("Hello").assertIsDisplayed()
    }
}
