package com.github.panlelapin.simplercal

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppRecreationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun activityRecreationKeepsSundayAndTheSettingsScreen() {
        var monday: java.time.LocalDate? = null
        rule.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[AppViewModel::class.java]
            model.selectToday()
            model.onDaySelected(6)
            model.showSettings()
            monday = model.uiState.displayedMonday
        }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Calendar").assertExists()
        rule.onNodeWithContentDescription("Today").assertDoesNotExist()
        rule.activityRule.scenario.onActivity { activity ->
            val state = ViewModelProvider(activity)[AppViewModel::class.java].uiState
            assertEquals(monday, state.displayedMonday)
            assertEquals(6, state.selectedDayIndex)
            assertTrue(state.isSettingsVisible)
        }
    }
}
