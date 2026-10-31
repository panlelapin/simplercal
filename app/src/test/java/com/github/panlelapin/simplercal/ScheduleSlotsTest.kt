package com.github.panlelapin.simplercal

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ScheduleSlotsTest {
    private val monday = LocalDate.of(2026, 8, 3)
    private val times = listOf(480, 480, 720, 840, 1080)

    private fun event(from: String, until: String, id: Long = 1): CalendarEvent = CalendarEvent(
        id, "slot-$id", "Event $id", Instant.parse(from).toEpochMilli(), Instant.parse(until).toEpochMilli(), false,
    )

    private fun slots(event: CalendarEvent, date: LocalDate = monday, hours: List<Int> = times,
        zone: ZoneId = ZoneOffset.UTC): List<Int> = ScheduleProjector(hours, zone).day(date, listOf(event))
        .lines.filter { it.events.isNotEmpty() }.map { it.index }

    @Test fun firstUntilCanEqualSecondFromAndTheOtherStartsMustIncrease() {
        assertEquals(ScheduleStatus.READY, scheduleStatus(times))
        assertEquals(ScheduleStatus.READY, scheduleStatus(listOf(420, 480, 720, 840, 1080)))
        assertEquals(ScheduleStatus.INVALID, scheduleStatus(listOf(540, 480, 720, 840, 1080)))
        assertEquals(ScheduleStatus.INVALID, scheduleStatus(listOf(480, 480, 480, 840, 1080)))
    }

    @Test fun configurationRejectsEmptyFirstRangeOutOfBoundsAndMissingFields() {
        assertEquals(ScheduleStatus.INVALID, scheduleStatus(listOf(1, 480, 720, 840, 1080)))
        assertEquals(ScheduleStatus.INVALID, scheduleStatus(listOf(0, 480, 720, 840, 1080)))
        assertEquals(ScheduleStatus.INVALID, scheduleStatus(listOf(480, 480, 720, 840, 1440)))
        assertEquals(ScheduleStatus.INVALID, scheduleStatus(emptyList()))
        assertEquals(ScheduleStatus.INCOMPLETE, scheduleStatus(List(5) { -1 }))
        assertEquals(ScheduleStatus.INCOMPLETE, scheduleStatus(listOf(480, -1, 720, -1, 1080)))
    }

    @Test fun oneSlotEventOccupiesOnlyItsOwnFixedLine() {
        assertEquals(listOf(1), slots(event("2026-08-03T10:00:00Z", "2026-08-03T11:00:00Z")))
    }

    @Test fun crossingTwoSlotsKeepsOnlyTheStartingSlot() {
        assertEquals(listOf(1), slots(event("2026-08-03T10:00:00Z", "2026-08-03T13:00:00Z")))
    }

    @Test fun crossingThreeSlotsKeepsTheFirstTwo() {
        assertEquals(listOf(1, 2), slots(event("2026-08-03T10:00:00Z", "2026-08-03T15:00:00Z")))
    }

    @Test fun crossingFourSlotsKeepsTheFirstThreeAsRequested() {
        assertEquals(listOf(0, 1, 2), slots(event("2026-08-03T06:00:00Z", "2026-08-03T15:00:00Z")))
    }

    @Test fun crossingFiveSlotsOmitsOnlyTheFifth() {
        assertEquals(listOf(0, 1, 2, 3), slots(event("2026-08-03T06:00:00Z", "2026-08-03T19:00:00Z")))
    }

    @Test fun exactEndBoundaryDoesNotTouchTheFollowingSlot() {
        assertEquals(listOf(1), slots(event("2026-08-03T10:00:00Z", "2026-08-03T12:00:00Z")))
        assertEquals(listOf(2), slots(event("2026-08-03T12:00:00Z", "2026-08-03T13:00:00Z")))
        assertEquals(listOf(1), slots(event("2026-08-03T10:00:00Z", "2026-08-03T14:00:00Z")))
    }

    @Test fun firstLineStartsAt0001AndTheLastLineEndsAtMidnight() {
        assertEquals(emptyList<Int>(), slots(event("2026-08-03T00:00:00Z", "2026-08-03T00:01:00Z")))
        assertEquals(listOf(0), slots(event("2026-08-03T00:01:00Z", "2026-08-03T00:02:00Z")))
        assertEquals(listOf(4), slots(event("2026-08-03T23:00:00Z", "2026-08-04T00:00:00Z")))
    }

    @Test fun anOptionalPauseBetweenFirstAndSecondDoesNotInventAnEventPlacement() {
        assertEquals(emptyList<Int>(), slots(event("2026-08-03T07:10:00Z", "2026-08-03T07:50:00Z"),
            hours = listOf(420, 480, 720, 840, 1080)))
    }

    @Test fun multipleEventsKeepTheirOrderAndEmptySlotsKeepTheirPosition() {
        val a = event("2026-08-03T09:00:00Z", "2026-08-03T10:00:00Z", 1)
        val b = event("2026-08-03T10:00:00Z", "2026-08-03T11:00:00Z", 2)
        val projected = ScheduleProjector(times, ZoneOffset.UTC).day(monday, listOf(a, b))
        assertEquals(listOf(0, 1, 2, 3, 4), projected.lines.map { it.index })
        assertEquals(listOf(a, b), projected.lines[1].events)
        assertTrue(projected.lines.filter { it.index != 1 }.all { it.events.isEmpty() })
    }

    @Test fun incompleteOrUnorderedSettingsKeepTheRawTimesAndDoNotGuessSlots() {
        listOf(listOf(480, -1, 720, -1, 1080), listOf(540, 480, 720, 840, 1080)).forEach { hours ->
            val result = ScheduleProjector(hours, ZoneOffset.UTC).day(monday,
                listOf(event("2026-08-03T10:00:00Z", "2026-08-03T11:00:00Z")))
            assertEquals(hours, result.lines.map { it.minutes })
            assertTrue(result.lines.all { it.events.isEmpty() })
        }
    }

    @Test fun changingTheTimesReassignsTheSameEventToTheNewLine() {
        val event = event("2026-08-03T10:30:00Z", "2026-08-03T11:00:00Z")
        assertEquals(listOf(1), slots(event))
        assertEquals(listOf(2), slots(event, hours = listOf(480, 480, 600, 840, 1080)))
    }

    @Test fun multiDayEventsOmitOnlyTheirGlobalLastSlot() {
        val event = event("2026-08-03T20:00:00Z", "2026-08-04T15:00:00Z")
        assertEquals(listOf(4), slots(event))
        assertEquals(listOf(0, 1, 2), slots(event, monday.plusDays(1)))
    }

    @Test fun allDayFloatingDatesRemainIndependentOfTheDisplayZone() {
        val event = event("2026-08-03T00:00:00Z", "2026-08-04T00:00:00Z").copy(allDay = true)
        listOf("Pacific/Honolulu", "Pacific/Kiritimati").forEach { zone ->
            assertEquals(listOf(0, 1, 2, 3), slots(event, zone = ZoneId.of(zone)))
        }
    }

    @Test fun zeroDurationIsAPointWhileReversedIntervalsAreNotProjected() {
        assertEquals(listOf(2), slots(event("2026-08-03T12:00:00Z", "2026-08-03T12:00:00Z")))
        assertEquals(emptyList<Int>(), slots(event("2026-08-03T13:00:00Z", "2026-08-03T12:00:00Z")))
    }

    @Test fun timedEventsUseTheReferenceZoneRatherThanUtcClockHours() {
        val event = event("2026-08-03T08:30:00Z", "2026-08-03T09:00:00Z")
        assertEquals(listOf(1), slots(event, zone = ZoneId.of("Europe/Paris")))
        assertEquals(listOf(0), slots(event, zone = ZoneId.of("America/New_York")))
    }

    @Test fun repeatedAutumnHoursUseTheActualOffsetAndDoNotDuplicateAnEvent() {
        val date = LocalDate.of(2026, 10, 25)
        val hours = listOf(120, 120, 150, 180, 240)
        val zone = ZoneId.of("Europe/Paris")
        assertEquals(listOf(2), slots(event("2026-10-25T01:35:00Z", "2026-10-25T01:45:00Z"), date, hours, zone))
        assertEquals(listOf(2), slots(event("2026-10-25T00:45:00Z", "2026-10-25T01:15:00Z"), date, hours, zone))
        val event = event("2026-10-25T00:15:00Z", "2026-10-25T01:15:00Z")
        val result = ScheduleProjector(hours, zone).day(date, listOf(event))
        assertEquals(listOf(1, 2), result.lines.filter { it.events.isNotEmpty() }.map { it.index })
        assertTrue(result.lines.all { it.events.size <= 1 })
    }

    @Test fun nonexistentSpringSlotsAreNotInvented() {
        val date = LocalDate.of(2026, 3, 29)
        val event = event("2026-03-29T00:50:00Z", "2026-03-29T01:10:00Z")
        assertEquals(listOf(0), slots(event, date, listOf(120, 120, 150, 180, 240), ZoneId.of("Europe/Paris")))
    }

    @Test fun aLongEventKeepsAllSlotsOnAnIntermediateDay() {
        assertEquals(listOf(0, 1, 2, 3, 4), slots(event("2016-01-01T00:00:00Z", "2036-01-01T00:00:00Z")))
    }

    @Test fun projectorRetainsAnImmutableSnapshotOfTheChosenTimes() {
        val mutable = times.toMutableList()
        val projector = ScheduleProjector(mutable, ZoneOffset.UTC)
        mutable.fill(-1)
        val result = projector.day(monday, listOf(event("2026-08-03T10:00:00Z", "2026-08-03T11:00:00Z")))
        assertEquals(times, result.lines.map { it.minutes })
        assertEquals(1, result.lines[1].events.size)
    }

    @Test fun supersededProjectionChecksCancellationInsteadOfCompletingOldWork() {
        val projector = ScheduleProjector(times, ZoneOffset.UTC) { throw CancellationException("Superseded") }
        assertThrows(CancellationException::class.java) {
            projector.day(monday, listOf(event("2026-08-03T10:00:00Z", "2026-08-03T11:00:00Z")))
        }
    }
}
