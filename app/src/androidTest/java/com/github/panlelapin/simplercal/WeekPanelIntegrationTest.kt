package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real viewport, measured content, rounded day boundaries and nonzero system insets. */
class WeekPanelIntegrationTest {
    @get:Rule val rule = createComposeRule()
    private var selected by mutableIntStateOf(0)
    private var height by mutableIntStateOf(1000)
    private var monday by mutableStateOf(LocalDate.of(2026, 8, 3))

    @Test fun linearDragUsesTheRealWeekAndBothPeripheralStrips() {
        setWeek(WeekScrollMode.LINEAR)
        rule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        val viewport = rule.onNodeWithTag("week-viewport")
        listOf(true, false).forEach { left ->
            val before = selected
            viewport.performTouchInput {
                val step = dayGroupTransitionDistance((height - BOTTOM_PIXELS).toFloat())
                down(Offset(if (left) 2f else width - 2f, height * .9f))
                moveBy(Offset(0f, -step * .8f - 30f))
                up()
            }
            rule.runOnIdle { assertEquals(before + 1, selected) }
        }
    }

    @Test fun discreteDragUsesTheMeasuredMidpointOfTheExpandedRow() {
        setWeek(WeekScrollMode.DISCRETE)
        rule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        rule.onNodeWithTag("week-viewport").performTouchInput {
            val available = (height - BOTTOM_PIXELS).toFloat()
            down(Offset(centerX, 10f))
            moveBy(Offset(0f, available * .16875f + dayGroupTransitionDistance(available) * .8f + 30f))
            up()
        }
        rule.runOnIdle { assertEquals(1, selected) }
        rule.onNodeWithContentDescription("Tuesday, August 4, 2026, 0 events").assertIsSelected()
    }

    @Test fun dragStartingInTheProtectedBottomNeverSelectsADay() {
        setWeek(WeekScrollMode.LINEAR)
        rule.onNodeWithTag("week-viewport").performTouchInput {
            down(Offset(centerX, height - BOTTOM_PIXELS / 2f))
            moveBy(Offset(0f, -height * .6f))
            up()
        }
        rule.runOnIdle { assertEquals(0, selected) }
    }

    @Test fun resizingDuringADragCancelsItsOldPixelScale() {
        setWeek(WeekScrollMode.LINEAR)
        val viewport = rule.onNodeWithTag("week-viewport")
        val originalHeight = viewport.fetchSemanticsNode().boundsInRoot.height
        viewport.performTouchInput {
            down(Offset(centerX, height * .9f))
            moveBy(Offset(0f, -dayGroupTransitionDistance((height - BOTTOM_PIXELS).toFloat()) * .8f - 30f))
        }
        // Both 1000 dp and 900 dp exceed the small CI display: the parent clamps
        // them to the same size. Resize from the measured pixels instead, keeping
        // this case in the non-scrolling branch (the fallback has its own test).
        rule.runOnIdle { height = (originalHeight / TEST_DENSITY).toInt() - 8 }
        assertTrue("The viewport must actually shrink before releasing the pointer",
            viewport.fetchSemanticsNode().boundsInRoot.height < originalHeight)
        viewport.performTouchInput { up() }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, selected) }
        rule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        rule.onNodeWithContentDescription("Monday, August 3, 2026, 0 events").assertIsSelected()
    }

    @Test fun changingWeekDuringADragCannotCommitThePreviousGesture() {
        setWeek(WeekScrollMode.LINEAR)
        val viewport = rule.onNodeWithTag("week-viewport")
        viewport.performTouchInput {
            down(Offset(centerX, height * .9f))
            moveBy(Offset(0f, -height * .25f))
        }
        rule.runOnIdle { monday = monday.plusWeeks(1) }
        viewport.performTouchInput { up() }
        rule.runOnIdle { assertEquals(0, selected) }
        rule.onNodeWithContentDescription("Monday, August 10, 2026, 0 events").assertIsSelected()
    }

    @Test fun tappingFridayInAScrolledWeekKeepsItVisibleOnEveryAnimationFrame() {
        height = 300
        setWeek(WeekScrollMode.LINEAR)
        val friday = rule.onNodeWithContentDescription("Friday, August 7, 2026, 0 events")
        friday.performScrollTo()
        rule.mainClock.autoAdvance = false
        friday.performTouchInput { click() }
        repeat(36) {
            rule.mainClock.advanceTimeByFrame()
            friday.assertIsDisplayed()
            val day = friday.fetchSemanticsNode().boundsInRoot
            val viewport = rule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            assertTrue(day.height > 0f && day.top >= viewport.top - 1f)
        }
        rule.mainClock.autoAdvance = true
        friday.assertIsSelected()
    }

    @Test fun largeFontFiveSlotsUseTheMeasuredExpandedHeight() {
        height = 500
        setWeek(WeekScrollMode.DISCRETE, eventCount = 5, fontScale = 2f)
        repeat(5) { index -> rule.onNodeWithText("Meeting $index", substring = true, useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test fun largeFontOverflowButtonRemainsTouchableAndOpensTheFullList() {
        height = 500
        setWeek(WeekScrollMode.LINEAR, eventCount = 100, fontScale = 2f)
        rule.onNodeWithTag("all-events", useUnmergedTree = true).performScrollTo().performTouchInput { click() }
        rule.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex))
            .performScrollToIndex(99)
        rule.onNodeWithText("Meeting 99").assertIsDisplayed()
    }

    @Test fun sundayKeepsItsSelectedIdentityInTheRealViewport() {
        selected = 6
        setWeek(WeekScrollMode.LINEAR)
        rule.onNodeWithContentDescription("Sunday, August 9, 2026, 0 events").assertIsSelected().assertIsDisplayed()
        rule.onNodeWithContentDescription("Saturday, August 8, 2026, 0 events").assertIsNotSelected()
    }

    @Test fun shrinkingIntoTheScrollingFallbackKeepsTheSelectedSundayVisible() {
        selected = 6
        setWeek(WeekScrollMode.LINEAR)
        rule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        rule.runOnIdle { height = 300 }
        rule.onNodeWithContentDescription("Sunday, August 9, 2026, 0 events").assertIsSelected().assertIsDisplayed()
        rule.onAllNodes(hasScrollAction()).assertCountEquals(1)
    }

    @Test fun boundaryDaysCannotStartADragOrBecomeASelection() {
        selected = 3
        monday = LocalDate.of(2025, 12, 29)
        setWeek(WeekScrollMode.LINEAR, allowedYear = 2026)
        rule.onNodeWithContentDescription("Monday, December 29, 2025, 0 events").assertIsNotEnabled()
            .performTouchInput { down(center); moveBy(Offset(0f, -100f)); up() }
        rule.runOnIdle { assertEquals(3, selected) }
        rule.onNodeWithTag("week-viewport").performTouchInput {
            down(Offset(2f, height * .5f))
            moveBy(Offset(0f, height * .4f))
            up()
        }
        rule.runOnIdle { assertTrue(selected >= 3) }
    }

    private fun setWeek(mode: WeekScrollMode, eventCount: Int = 0, fontScale: Float = 1f, allowedYear: Int? = null) {
        val starts = listOf(60L, 540L, 780L, 900L, 1140L)
        val events = List(eventCount) {
            val begin = monday.atStartOfDay(java.time.ZoneOffset.UTC).plusMinutes(starts[it % starts.size]).toInstant().toEpochMilli()
            CalendarEvent(it.toLong(), "panel-$it", "Meeting $it", begin, begin + 900_000L, false)
        }
        rule.setContent {
            // Keep a genuinely non-scrolling branch available even on small CI displays.
            CompositionLocalProvider(LocalDensity provides Density(TEST_DENSITY, fontScale),
                LocalWeekWindowInsets provides WeekWindowInsets(WindowInsets(0, 0, 40, BOTTOM_PIXELS),
                    WindowInsets(0, 0, 0, BOTTOM_PIXELS), WindowInsets(0, 0, 0, BOTTOM_PIXELS))) {
                MaterialTheme {
                    Box(Modifier.width(400.dp).height(height.dp)) {
                        WeekView(WeekViewState(monday, monday, List(7) { if (it == 0) events else emptyList() },
                            0, false, mode, SimulationMode.OFF, Debug1OutlineColor.APP_BAR_BACKGROUND,
                            MaterialTheme.colorScheme.surfaceContainer, selected, 1, { selected = it }, allowedYear = allowedYear,
                            scheduleTimes = listOf(480, 480, 720, 840, 1080), referenceZone = java.time.ZoneOffset.UTC))
                    }
                }
            }
        }
        if (eventCount > 0) rule.waitUntil(5000) {
            rule.onAllNodesWithText("Meeting 0", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    companion object {
        private const val BOTTOM_PIXELS = 24
        private const val TEST_DENSITY = .75f
    }
}
