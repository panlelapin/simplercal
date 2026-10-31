package com.github.panlelapin.simplercal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DayPanelRenderingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pressedRippleStaysInsideTheRoundedShapeAndBelowTheCurrentDayContour() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    val colors = MaterialTheme.colorScheme
                    Box(Modifier.width(300.dp).height(120.dp).background(colors.error)) {
                        val date = LocalDate.of(2026, 8, 5)
                        DayRow(DayRowState(2, 2, date, false,
                            WeekDay(date, "Wed", false, false, emptyList()), true, true, true,
                            80.dp, colors.outline), {},
                            modifier = Modifier.fillMaxSize().testTag("rendered-day"))
                    }
                }
            }
        }
        val day = rule.onNodeWithTag("rendered-day")
        val before = day.captureToImage().toPixelMap()
        val corner = before[0, 0]
        val contour = before[before.width / 2, 0]
        val inside = before[before.width * 3 / 4, before.height * 3 / 4]
        rule.mainClock.autoAdvance = false
        day.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(400)
        // Android may render the ripple through a native animator, outside the test clock.
        rule.waitUntil(3000) {
            rule.mainClock.advanceTimeByFrame()
            val pixels = day.captureToImage().toPixelMap()
            pixels[pixels.width * 3 / 4, pixels.height * 3 / 4] != inside
        }
        val pressed = day.captureToImage().toPixelMap()
        assertEquals(corner, pressed[0, 0])
        assertEquals(contour, pressed[pressed.width / 2, 0])
        assertNotEquals(inside, pressed[pressed.width * 3 / 4, pressed.height * 3 / 4])
        day.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
    }

    @Test fun frameWeightsRemeasureTheDaysWithoutRecomposingTheirContent() {
        var weights by mutableStateOf(dayWeightsFor(0))
        var compositions = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.75f)) {
                MaterialTheme {
                    val scroll = rememberScrollState()
                    val coordinator = remember { WeekScrollCoordinator(scroll, 0) }
                    val geometry = WeekViewportGeometry(300, 600, 0, 0, 600)
                    Box(Modifier.width(400.dp).height(800.dp)) {
                        WeekDayLayout(geometry, { weights }, coordinator, Modifier.fillMaxSize()) {
                            SideEffect { compositions++ }
                            repeat(7) { Box(Modifier.testTag("measured-day-$it")) }
                        }
                    }
                }
            }
        }
        val beforeCount = compositions
        val before = rule.onNodeWithTag("measured-day-0").fetchSemanticsNode().boundsInRoot.height
        rule.runOnIdle { weights = dayWeightsForFocus(.5f) }
        val after = rule.onNodeWithTag("measured-day-0").fetchSemanticsNode().boundsInRoot.height
        assertTrue(after < before)
        assertEquals(beforeCount, compositions)
    }

    @Test fun weekendSidesStayContinuousWithoutDrawingAHorizontalWeekendSeam() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    val colors = MaterialTheme.colorScheme
                    Column(Modifier.width(300.dp).height(200.dp).testTag("weekend")) {
                        listOf(5, 6).forEach { day ->
                            val date = LocalDate.of(2026, 8, 3).plusDays(day.toLong())
                            DayRow(DayRowState(day, null, date, false,
                                WeekDay(date, if (day == 5) "Sat" else "Sun", true, false, emptyList()),
                                false, true, true, 80.dp, colors.onSurface), {},
                                modifier = Modifier.height(100.dp))
                        }
                    }
                }
            }
        }
        val image = rule.onNodeWithTag("weekend").captureToImage().toPixelMap()
        assertEquals(image[0, 50], image[0, 99])
        assertEquals(image[0, 150], image[0, 100])
        assertEquals(image[250, 50], image[250, 99])
        assertEquals(image[250, 150], image[250, 100])
        assertNotEquals(image[0, 99], image[250, 99])
    }
}
