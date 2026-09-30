package com.github.panlelapin.simplercal

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RegressionTest {
    @Test
    fun unavailableProviderDoesNotBecomeAnEmptySuccessfulResult() {
        assertThrows(IllegalStateException::class.java) { requireCalendarCursor(null) }
    }

    @Test
    fun repeatedSelectionKeepsCollapsingContentUntilTheAnimationFinishes() {
        val interaction = WeekInteraction(0) {}
        interaction.selectDay(3)
        val contentDuringAnimation = interaction.contentExpandedDays
        interaction.selectDay(3)
        assertEquals(setOf(0, 1, 3, 4), contentDuringAnimation)
        assertEquals(contentDuringAnimation, interaction.contentExpandedDays)
        assertEquals(1, interaction.animationRequest)
    }

    @Test
    fun overscrollIsDiscardedSoReversingImmediatelyMovesTheLayout() {
        val path = WeekDragPath(0, dayWeightsFor(0), 1000f)
        var distance = path.bound(-1000f)
        assertEquals(0f, distance, 0f)
        distance = path.bound(distance + 10f)
        assertTrue(path.focusAt(distance) > 0f)
        assertEquals(5f, path.focusAt(1_000_000f), 0f)
    }

    @Test
    fun interruptedAnimationUsesItsActualBoundaryDistance() {
        val origin = dayWeightsForFocus(0.5f)
        val path = WeekDragPath(1, origin, 1000f)
        val interaction = WeekInteraction(1) {}
        interaction.animatedDayWeights = origin
        interaction.startDrag()
        interaction.dragToFocus(path.focusAt(10f))
        var movedBoundary = 0f
        var largestMovement = 0f
        origin.indices.forEach { index ->
            movedBoundary += (interaction.animatedDayWeights[index] - origin[index]) * 10f
            largestMovement = maxOf(largestMovement, kotlin.math.abs(movedBoundary))
        }
        assertEquals(10f, largestMovement, 0.001f)
    }

    @Test
    fun discreteWaitsForTheTouchedContainersMidpoint() {
        val threshold = discreteDragThreshold(10f, 0, dayWeightsFor(0), 1000f, 1f, 8f)
        assertEquals(158.75f, threshold, 0.001f)
        assertEquals(8f, discreteDragThreshold(200f, 0, dayWeightsFor(0), 1000f, 1f, 8f), 0f)
    }

    @Test
    fun realMarkersAreExplicitPerDateAndSimulationIgnoresThem() {
        val monday = LocalDate.of(2026, 8, 3)
        val markers = mapOf(monday.plusDays(4) to DayMarkers(isHolidays = true, isBankHoliday = true))
        val normal = currentWeek(monday, markers = markers)
        assertTrue(normal[4].isHolidays && normal[4].isWEorBankH)
        val simulation = currentWeek(monday, SimulationMode.SIMULATION, markers = markers)
        assertTrue(!simulation[4].isHolidays && !simulation[4].isWEorBankH)
    }

    @Test
    fun allDayEventsKeepTheirUtcDatesAcrossTimeZonesAndDoNotIncludeTheExclusiveEnd() {
        val monday = LocalDate.of(2026, 8, 3)
        val start = monday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = monday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val event = CalendarEvent(1, "1:$start", "All day", start, end, true)
        listOf("Pacific/Honolulu", "Pacific/Kiritimati").forEach { zone ->
            val result = groupEventsByDay(listOf(event), monday, ZoneId.of(zone))
            assertEquals(listOf(1, 0, 0, 0, 0, 0, 0), result.map { it.size })
        }
    }

    @Test
    fun isoTitleUsesTheWeekBasedYearAtTheYearBoundary() {
        assertEquals("S1 - 30DEC", weekTitle(LocalDate.of(2024, 12, 30)).text)
    }

    @Test
    fun fallbackHeightPreservesTouchTargetsAndScaledContent() {
        val height = minimumWeekHeightDp(40f)
        assertTrue(height * COMPACT_DAY_WEIGHT / TOTAL_DAY_WEIGHT >= 47.99f)
        assertTrue(height * dayWeightsFor(0).first() / TOTAL_DAY_WEIGHT >= 40f * 9)
    }
}
