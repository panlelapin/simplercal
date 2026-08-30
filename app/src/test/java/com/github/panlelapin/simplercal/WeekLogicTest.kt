package com.github.panlelapin.simplercal

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekLogicTest {
    private val monday = LocalDate.of(2026, 8, 3)

    @Test
    fun normalWeekOnlyMarksTheWeekend() {
        val week = currentWeek(monday = monday, simulationMode = SimulationMode.OFF)

        assertEquals(WEEK_DAY_COUNT, week.size)
        assertTrue(week.take(5).none { it.isWEorBankH || it.isHolidays })
        assertTrue(week.drop(5).all { it.isWEorBankH })
        assertTrue(week.none { it.isHolidays })
    }

    @Test
    fun simulationUsesWednesdayAndDocumentedSpecialDaysForEveryWeek() {
        val week = currentWeek(monday = monday, simulationMode = SimulationMode.SIMULATION)

        assertEquals(monday.plusDays(2), referenceDateFor(monday, SimulationMode.SIMULATION))
        assertEquals(listOf(true, false, false, false, false, true, true), week.map { it.isWEorBankH })
        assertEquals(listOf(true, true, true, true, false, false, false), week.map { it.isHolidays })
    }

    @Test
    fun dayIndexIsNullOutsideDisplayedWeek() {
        assertEquals(0, dayIndexInWeek(monday, monday))
        assertEquals(6, dayIndexInWeek(monday.plusDays(6), monday))
        assertNull(dayIndexInWeek(monday.minusDays(1), monday))
        assertNull(dayIndexInWeek(monday.plusDays(7), monday))
    }

    @Test
    fun dayWeightsAlwaysUseOneSevenDaySourceAndSumToOneHundred() {
        (0 until WEEK_DAY_COUNT).forEach { selectedDay ->
            val weights = dayWeightsFor(selectedDay)

            assertEquals(WEEK_DAY_COUNT, weights.size)
            assertEquals(TOTAL_DAY_WEIGHT, weights.sum(), 0.001f)
            assertEquals(2, weights.count { it > COMPACT_DAY_WEIGHT })
        }
    }

    @Test
    fun interruptedDragKeepsItsExactVisualOriginAndSundayIdentity() {
        var reportedSelection = -1
        val interaction = WeekInteraction(6) { reportedSelection = it }
        val interruptedWeights = dayWeightsFor(2).mapIndexed { index, weight -> weight + index / 10f }
        interaction.animatedDayWeights = interruptedWeights

        interaction.startDrag()
        interaction.dragToFocus(WEEKEND_START_INDEX.toFloat())

        assertEquals(interruptedWeights, interaction.animatedDayWeights)
        interaction.endDrag()
        assertEquals(6, interaction.selectedDayIndex)
        assertEquals(6, reportedSelection)
    }

    @Test
    fun scheduleValuesAndTemporalStatesAreValidated() {
        assertEquals(0, validateScheduleMinutes(0))
        assertEquals(1439, validateScheduleMinutes(1439))
        assertNull(validateScheduleMinutes(-1))
        assertNull(validateScheduleMinutes(1440))
        assertEquals(DayTemporalState.PAST, temporalStateFor(monday, monday.plusDays(1)))
        assertEquals(DayTemporalState.CURRENT, temporalStateFor(monday, monday))
        assertEquals(DayTemporalState.FUTURE, temporalStateFor(monday, monday.minusDays(1)))
    }

    @Test
    fun weekTitleUsesIsoWeekAndMondayDate() {
        assertEquals("S32 - 3AUG", weekTitle(monday).text)
    }

    @Test
    fun multiDayCalendarEventIsPlacedOnEveryOverlappingDay() {
        val zone = ZoneId.of("Europe/Paris")
        val begin = monday.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        val end = monday.plusDays(2).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        val event =
            CalendarEvent(
                eventId = 7,
                instanceId = "7:$begin",
                title = "Multi-day",
                beginMillis = begin,
                endMillis = end,
                allDay = false,
            )

        val grouped = groupEventsByDay(listOf(event), monday, zone)

        assertEquals(listOf(1, 1, 1, 0, 0, 0, 0), grouped.map { it.size })
    }
}
