package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeekGestureTest {
    @get:Rule val composeRule = createComposeRule()
    private var selected by mutableStateOf(0)
    private var interaction: WeekInteraction? = null
    private var touchSlop = 0f

    @Test fun discreteTapKeepsCollapsingContentUntil500ms() = exerciseTap(WeekScrollMode.DISCRETE)

    @Test fun linearTapKeepsCollapsingContentUntil500ms() = exerciseTap(WeekScrollMode.LINEAR)

    @Test fun aDragOnACompactDiscreteDayDoesNotBecomeATap() {
        setWeek(WeekScrollMode.DISCRETE)
        composeRule.onNodeWithContentDescription("Friday, August 7, 0 events").performTouchInput {
            down(center)
            moveBy(Offset(0f, touchSlop + 1f))
            up()
        }
        composeRule.runOnIdle { assertEquals(0, selected) }
    }

    @Test fun linearIncludesTheFinalPointerPositionOnRelease() {
        setWeek(WeekScrollMode.LINEAR)
        composeRule.onRoot().performTouchInput {
            val step = dayGroupTransitionDistance(height.toFloat())
            down(Offset(centerX, height * 0.9f))
            moveBy(Offset(0f, -touchSlop - step * 0.1f))
            // This updates the up event, without dispatching another move event.
            updatePointerBy(0, Offset(0f, -step * 0.6f))
            up()
        }
        composeRule.runOnIdle { assertEquals(1, selected) }
    }

    private fun exerciseTap(mode: WeekScrollMode) {
        setWeek(mode)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("Friday, August 7, 0 events").performTouchInput { click() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertEquals(4, selected)
            assertTrue(0 in requireNotNull(interaction).contentExpandedDays)
        }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.runOnIdle { assertTrue(0 in requireNotNull(interaction).contentExpandedDays) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle { assertTrue(0 !in requireNotNull(interaction).contentExpandedDays) }
        composeRule.mainClock.autoAdvance = true
    }

    private fun setWeek(mode: WeekScrollMode) {
        val date = LocalDate.of(2026, 8, 3)
        composeRule.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                val current = rememberWeekInteraction(selected, 2) { selected = it }
                interaction = current
                touchSlop = LocalViewConfiguration.current.touchSlop
                AnimateDayWeights(current, WEEK_DAY_COUNT)
                Box(Modifier.fillMaxSize().weekGestureInput(WeekGestureState(
                    scrollMode = mode, bottomGestureInsetPx = 0f, rightGestureInsetPx = 0f,
                    leftGestureInsetPx = 0f, touchSlopPx = touchSlop,
                    selectedDayIndex = { current.selectedDayIndex }, animatedDayWeights = { current.animatedDayWeights },
                    startDrag = current::startDrag, dragToFocus = current::dragToFocus,
                    endDrag = current::endDrag, cancelDrag = current::cancelDrag,
                ))) {
                    Column(Modifier.fillMaxSize()) {
                        currentWeek(date).forEachIndexed { index, day ->
                            DayRow(
                                DayRowState(index, null, date, false, day,
                                    index == current.selectedDayIndex, index in expandedDayIndices(current.selectedDayIndex),
                                    index in current.contentExpandedDays, current.animatedDayWeights[index],
                                    80.dp, colors.outlineVariant, colors.surfaceContainer),
                                onClick = { current.selectDay(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}
