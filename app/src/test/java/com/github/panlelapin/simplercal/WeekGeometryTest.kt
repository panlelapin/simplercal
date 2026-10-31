package com.github.panlelapin.simplercal

import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WeekGeometryTest {
    @Test fun everyMeasuredPixelBelongsToExactlyThePlacedRow() {
        listOf(739, 1108, 1477, 1933).forEach { height ->
            listOf(0f, .13f, .5f, 1.8f, 4.7f, 5f).forEach { focus ->
                val bounds = weekRowBounds(height, dayWeightsForFocus(focus))
                assertEquals(0, bounds.edges.first())
                assertEquals(height, bounds.edges.last())
                (0 until 7).forEach { day ->
                    (bounds.top(day) until bounds.edges[day + 1]).forEach { y -> assertEquals(day, bounds.dayAt(y.toFloat())) }
                }
                assertEquals(-1, bounds.dayAt(-.1f))
                assertEquals(-1, bounds.dayAt(height.toFloat()))
            }
        }
    }

    @Test fun measuredMinimumFitsEveryCompactAndExpandedRowAtEverySelection() {
        listOf(48 to 249, 63 to 420, 96 to 830).forEach { (compact, expanded) ->
            val height = minimumWeekHeightPx(compact, expanded)
            (0..6).forEach { selected ->
                val bounds = weekRowBounds(height, dayWeightsFor(selected))
                (0..6).forEach { day -> assertTrue(bounds.height(day) >= if (day in expandedDayIndices(selected)) expanded else compact) }
            }
        }
    }

    @Test fun guttersAreLinearTargetsButTheProtectedBottomIsNeverAGestureTarget() {
        val geometry = WeekViewportGeometry(400, 1000, 40, 12, 960)
        assertTrue(geometry.acceptsDrag(1f, 900f))
        assertTrue(geometry.acceptsDrag(399f, 900f))
        assertFalse(geometry.containsDay(1f))
        assertTrue(geometry.containsDay(12f))
        assertFalse(geometry.acceptsDrag(200f, 960f))
        assertFalse(geometry.acceptsDrag(200f, 999f))
        assertFalse(geometry.acceptsDrag(200f, -1f))
        assertFalse(geometry.acceptsDrag(400f, 200f))
    }

    @Test fun fridayStaysAnchoredThroughoutTheFormerScrollJumpScenario() {
        val scroll = ScrollState(360)
        val coordinator = WeekScrollCoordinator(scroll, 0)
        val height = 739
        coordinator.geometry = WeekViewportGeometry(400, 300, 0, 5, height)
        val start = dayWeightsFor(0)
        val target = dayWeightsFor(4)
        coordinator.bounds = weekRowBounds(height, start)
        val originalY = coordinator.bounds!!.top(4) - scroll.value
        coordinator.select(4)
        var previousY = originalY
        (0..100).forEach { frame ->
            val weights = List(7) { interpolateWeight(start[it], target[it], frame / 100f) }
            coordinator.follow(weights)
            val rows = weekRowBounds(height, weights)
            val y = rows.top(4) - scroll.value
            assertTrue(y >= 0 && y + rows.height(4) <= 300)
            assertTrue(kotlin.math.abs(y - previousY) <= 5)
            if (frame == 0) assertEquals(originalY, y)
            previousY = y
        }
        coordinator.finish()
    }

    @Test fun roundedDragPathReachesEveryNeighbourAtItsMeasuredBoundaryDistance() {
        listOf(739, 1000, 1477).forEach { height ->
            (0..4).forEach { group ->
                val origin = weekRowBounds(height, dayWeightsFor(group))
                val target = weekRowBounds(height, dayWeightsFor(group + 1))
                val pixels = origin.edges.indices.maxOf { kotlin.math.abs(origin.edges[it] - target.edges[it]) }
                assertEquals((group + 1).toFloat(), WeekDragPath(group, dayWeightsFor(group), height.toFloat()).focusAt(pixels.toFloat()), .0001f)
            }
        }
    }

    @Test fun cancellingAnInterruptedDragKeepsTheCurrentGeometryAndRequestsAResume() {
        var businessSelection = 4
        val interaction = WeekInteraction(4) { businessSelection = it }
        interaction.animatedDayWeights = dayWeightsForFocus(1.6f)
        interaction.startDrag()
        interaction.dragToFocus(4.2f)
        val visible = interaction.animatedDayWeights
        val request = interaction.animationRequest
        interaction.cancelDrag()
        assertFalse(interaction.isDragging)
        assertEquals(4, interaction.selectedDayIndex)
        assertEquals(4, businessSelection)
        assertEquals(visible, interaction.animatedDayWeights)
        assertEquals(request + 1, interaction.animationRequest)
    }

    @Test fun aManualScrollTakesOwnershipBackFromAnActiveTapAnchor() = runTest {
        val scroll = ScrollState(360)
        val coordinator = WeekScrollCoordinator(scroll, 0)
        coordinator.geometry = WeekViewportGeometry(400, 300, 0, 5, 739)
        coordinator.bounds = weekRowBounds(739, dayWeightsFor(0))
        coordinator.select(4)
        scroll.scroll { coordinator.follow(dayWeightsFor(4)) }
        assertEquals(360, scroll.value)
        coordinator.follow(dayWeightsFor(4))
        assertEquals(360, scroll.value)
    }

    @Test fun anchoringSundayCannotScrollPastTheEndOfTheWeek() {
        val bounds = weekRowBounds(739, dayWeightsFor(6))
        assertEquals(439, anchoredWeekScroll(bounds, 6, 0, 300))
        assertEquals(0, anchoredWeekScroll(bounds, 0, 300, 300))
        assertEquals(0, anchoredWeekScroll(bounds, 6, 0, 1000))
    }

    @Test fun rapidTapsKeepTheVisibleOriginAndOnlyTheLatestTargetIsSelected() {
        val interaction = WeekInteraction(0) {}
        interaction.selectDay(2)
        val frame = List(7) { interpolateWeight(dayWeightsFor(0)[it], dayWeightsFor(2)[it], .4f) }
        interaction.animatedDayWeights = frame
        interaction.selectDay(5)
        interaction.selectDay(3)
        assertEquals(frame, interaction.animatedDayWeights)
        assertEquals(3, interaction.selectedDayIndex)
        assertEquals(3, interaction.animationRequest)
        assertTrue(interaction.contentExpandedDays.containsAll(setOf(0, 1, 2, 3, 4, 5, 6)))
    }

    @Test fun sundayIdentitySurvivesLeavingAndReenteringItsGroupDuringOneDrag() {
        var reported = -1
        val interaction = WeekInteraction(6) { reported = it }
        interaction.startDrag()
        interaction.dragToFocus(4f)
        assertEquals(4, interaction.selectedDayIndex)
        interaction.dragToFocus(5f)
        interaction.endDrag()
        assertEquals(6, interaction.selectedDayIndex)
        assertEquals(6, reported)
    }

    @Test fun aPartiallyClippedDayDoesNotSnapItsHeaderOnTheFirstFrame() {
        val start = dayWeightsFor(0)
        val target = dayWeightsFor(2)
        val original = weekRowBounds(739, start)
        // A 200px viewport can legitimately show just the lower portion of Wednesday.
        val scroll = ScrollState(original.top(2) + 20)
        val coordinator = WeekScrollCoordinator(scroll, 0)
        coordinator.geometry = WeekViewportGeometry(400, 200, 0, 5, 739)
        coordinator.bounds = original
        coordinator.select(2)
        coordinator.follow(start, 0f)
        assertEquals(-20, original.top(2) - scroll.value)
        val middle = List(7) { interpolateWeight(start[it], target[it], .5f) }
        coordinator.follow(middle, .5f)
        assertEquals(-10, weekRowBounds(739, middle).top(2) - scroll.value)
        coordinator.follow(target, 1f)
        assertEquals(0, weekRowBounds(739, target).top(2) - scroll.value)
    }

    @Test fun revealingAfterResizeShowsTheDayWithoutUnnecessaryScrollWhenItAlreadyFits() {
        val bounds = weekRowBounds(739, dayWeightsFor(6))
        val scroll = visibleWeekScroll(bounds, 6, 0, 200)
        assertEquals(bounds.top(6), scroll)
        assertEquals(scroll + 10, visibleWeekScroll(bounds, 6, scroll + 10, 200))
        val fitted = visibleWeekScroll(bounds, 6, 0, 400)
        assertEquals(339, fitted)
        assertEquals(fitted, visibleWeekScroll(bounds, 6, fitted, 400))
    }
}
