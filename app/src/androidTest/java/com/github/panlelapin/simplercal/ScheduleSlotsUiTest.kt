package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test

class ScheduleSlotsUiTest {
    @get:Rule val rule = createComposeRule()
    private val monday = LocalDate.of(2026, 8, 3)
    private var times by mutableStateOf(listOf(480, 480, 720, 840, 1080))

    private fun event(beginMinute: Long, endMinute: Long, title: String = "Lesson", id: Long = 1): CalendarEvent {
        val midnight = monday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return CalendarEvent(id, "ui-slot-$id", title, midnight + beginMinute * 60_000, midnight + endMinute * 60_000, false)
    }

    private fun matcher(text: String) = hasText(text) and hasAnyAncestor(
        hasContentDescription("Monday, August 3, 2026", substring = true),
    )

    private fun line(text: String) = rule.onNode(matcher(text), useUnmergedTree = true)

    private fun waitForLine(text: String) {
        // Projection runs off the UI thread; wait for its specific result, not an arbitrary delay.
        rule.waitUntil(5000) { rule.onAllNodes(matcher(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun fiveFixedLinesIncludeTheEmptySlotsAndTheFirstUntilLabel() {
        setWeek(listOf(event(600, 660)))
        waitForLine("From 08:00 · Lesson")
        listOf("Until 08:00", "From 08:00 · Lesson", "From 12:00", "From 14:00", "From 18:00")
            .forEach { line(it).assertIsDisplayed() }
    }

    @Test fun anEventCrossingTwoSlotsAppearsOnlyOnTheStartingLine() {
        setWeek(listOf(event(600, 780)))
        waitForLine("From 08:00 · Lesson")
        line("From 12:00").assertIsDisplayed()
        line("From 12:00 · Lesson").assertDoesNotExist()
    }

    @Test fun anEventCrossingFourSlotsAppearsOnTheFirstThree() {
        setWeek(listOf(event(360, 900)))
        waitForLine("Until 08:00 · Lesson")
        listOf("Until 08:00 · Lesson", "From 08:00 · Lesson", "From 12:00 · Lesson", "From 14:00")
            .forEach { line(it).assertIsDisplayed() }
        line("From 14:00 · Lesson").assertDoesNotExist()
    }

    @Test fun changingTheConfiguredTimesReprojectsTheCurrentCalendar() {
        setWeek(listOf(event(630, 660)))
        waitForLine("From 08:00 · Lesson")
        rule.runOnIdle { times = listOf(480, 480, 600, 840, 1080) }
        waitForLine("From 10:00 · Lesson")
        line("From 08:00").assertIsDisplayed()
        line("From 08:00 · Lesson").assertDoesNotExist()
    }

    @Test fun incompleteSettingsDoNotKeepOldAssignmentsAndTheFullListStaysAvailable() {
        setWeek(listOf(event(600, 660)))
        waitForLine("From 08:00 · Lesson")
        rule.runOnIdle { times = List(5) { -1 } }
        line("From 08:00 · Lesson").assertDoesNotExist()
        rule.onNodeWithTag("all-events", useUnmergedTree = true).performTouchInput { click() }
        rule.onNodeWithText("Lesson").assertIsDisplayed()
    }

    @Test fun simultaneousEventsKeepOneSlotLineAndBothRemainReadableInTheDetail() {
        setWeek(listOf(event(600, 630), event(630, 660, "Second lesson", 2)))
        waitForLine("From 08:00 · Lesson (+1)")
        rule.onNodeWithTag("all-events", useUnmergedTree = true).performTouchInput { click() }
        rule.onNodeWithText("Lesson").assertIsDisplayed()
        rule.onNodeWithText("Second lesson").assertIsDisplayed()
    }

    private fun setWeek(events: List<CalendarEvent>) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.75f),
                LocalWeekWindowInsets provides WeekWindowInsets(WindowInsets(0, 0, 0, 24),
                    WindowInsets(0, 0, 0, 24), WindowInsets(0, 0, 0, 24))) {
                MaterialTheme {
                    Box(Modifier.width(400.dp).height(1000.dp)) {
                        WeekView(WeekViewState(monday, monday, listOf(events) + List(6) { emptyList() }, 0,
                            false, WeekScrollMode.LINEAR, SimulationMode.OFF, Debug1OutlineColor.APP_BAR_BACKGROUND,
                            MaterialTheme.colorScheme.surfaceContainer, 0, 1, {},
                            scheduleTimes = times, referenceZone = ZoneOffset.UTC))
                    }
                }
            }
        }
    }
}
