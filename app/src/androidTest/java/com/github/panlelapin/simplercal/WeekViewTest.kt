package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercise the real WeekView, including its insets and accessibility scroll fallback. */
class WeekViewTest {
    @get:Rule val composeRule = createComposeRule()
    private var visible by mutableStateOf(true)
    private var selected by mutableStateOf(0)

    @Test fun fallbackDiscreteDaysCanBeSelected() {
        setWeek(WeekScrollMode.DISCRETE)
        val friday = composeRule.onNodeWithContentDescription("Friday, August 7, 2026, 0 events")
        friday.performScrollTo().performClick()
        composeRule.waitForIdle()
        friday.assertIsSelected()
        composeRule.runOnIdle { assertEquals(4, selected) }
    }

    @Test fun fallbackLinearDaysCanBeSelected() {
        setWeek(WeekScrollMode.LINEAR)
        val friday = composeRule.onNodeWithContentDescription("Friday, August 7, 2026, 0 events")
        friday.performScrollTo().performClick()
        composeRule.waitForIdle()
        friday.assertIsSelected()
        composeRule.runOnIdle { assertEquals(4, selected) }
    }

    @Test fun returningToTheWeekPreservesItsScrollPosition() {
        setWeek(WeekScrollMode.DISCRETE)
        val scroll = composeRule.onNode(hasScrollAction())
        scroll.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val before = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0f)
        composeRule.runOnIdle { visible = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible = true }
        composeRule.waitForIdle()
        val after = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 1f)
    }

    @Test fun largeFontInALowLandscapeWindowKeepsSundayReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    Box(Modifier.fillMaxWidth().height(220.dp)) {
                        val monday = LocalDate.of(2026, 8, 3)
                        WeekView(WeekViewState(monday, monday, emptyEventDays(), 0, false,
                            WeekScrollMode.LINEAR, SimulationMode.OFF, Debug1OutlineColor.APP_BAR_BACKGROUND,
                            MaterialTheme.colorScheme.surfaceContainer, selected, 1, { selected = it }))
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("Sunday, August 9, 2026, 0 events")
            .performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(6, selected) }
    }

    private fun setWeek(mode: WeekScrollMode) {
        composeRule.setContent {
            MaterialTheme {
                val holder = rememberSaveableStateHolder()
                if (visible) holder.SaveableStateProvider("week") {
                    Box(Modifier.fillMaxWidth().height(400.dp)) {
                        val monday = LocalDate.of(2026, 8, 3)
                        WeekView(WeekViewState(
                            weekMonday = monday,
                            referenceDate = monday,
                            eventsByDay = emptyEventDays(),
                            highlightedDayIndex = 0,
                            isDarkTheme = false,
                            scrollMode = mode,
                            simulationMode = SimulationMode.OFF,
                            debug1OutlineColor = Debug1OutlineColor.APP_BAR_BACKGROUND,
                            appBarBackground = MaterialTheme.colorScheme.surfaceContainer,
                            requestedDayIndex = selected,
                            selectionRequest = 1,
                            onSelectionChanged = { selected = it },
                        ))
                    }
                }
            }
        }
    }
}
