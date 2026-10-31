package com.github.panlelapin.simplercal

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalendarBoundaryTest {
    @Test fun onlyCurrentAndNextYearAreEnabledIncludingLeapDay() {
        assertFalse(allowedDate(LocalDate.of(2025, 12, 31), 2026))
        assertTrue(allowedDate(LocalDate.of(2026, 1, 1), 2026))
        assertTrue(allowedDate(LocalDate.of(2027, 12, 31), 2026))
        assertFalse(allowedDate(LocalDate.of(2028, 1, 1), 2026))
        assertTrue(allowedDate(LocalDate.of(2028, 2, 29), 2027))
    }

    @Test fun boundaryWeeksKeepSevenRowsButHideOutsideEventsAndMarkers() {
        listOf(firstAllowedMonday(2026), lastAllowedMonday(2026)).forEach { monday ->
            val markers = (0..6).associate { monday.plusDays(it.toLong()) to DayMarkers(true, true) }
            val week = currentWeek(monday, markers = markers, allowedYear = 2026)
            assertEquals(7, week.size)
            week.forEach { day ->
                assertEquals(allowedDate(day.date, 2026), day.isEnabled)
                if (!day.isEnabled) {
                    assertTrue(day.events.isEmpty())
                    assertFalse(day.isHolidays)
                }
            }
        }
    }

    @Test fun aBoundedDragCannotSettleOnAnOutsideYearGroup() {
        val path = WeekDragPath(3, dayWeightsFor(3), 1000f, minimumGroup = 3, maximumGroup = 5)
        assertEquals(3f, path.focusAt(-1_000_000f), 0f)
        assertEquals(5f, path.focusAt(1_000_000f), 0f)
        val end = WeekDragPath(3, dayWeightsFor(3), 1000f, minimumGroup = 0, maximumGroup = 3)
        assertEquals(3f, end.focusAt(1_000_000f), 0f)
        assertTrue(end.focusAt(end.bound(1_000_000f) - 10f) < 3f)
    }

    @Test fun timedEventsCrossMidnightButExcludeTheInstantAtTheirEnd() {
        val zone = ZoneId.of("Europe/Paris")
        val monday = LocalDate.of(2026, 3, 23)
        val begin = monday.plusDays(5).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val end = monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val event = CalendarEvent(1, "timed", "DST", begin, end, false)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1), groupEventsByDay(listOf(event), monday, zone).map { it.size })
        assertTrue(groupEventsByDay(listOf(event), monday.plusWeeks(1), zone).all { it.isEmpty() })
    }

    @Test fun sourceIdentitySurvivesDisableButChangesWithSource() {
        val source = CalendarSource(CalendarSourceKind.HTTPS, location = "https://example.invalid/a.ics")
        assertEquals(source.identity, source.copy(enabled = false).identity)
        assertNotEquals(source.identity, source.copy(location = "https://example.invalid/b.ics").identity)
        assertNotEquals(source.identity, source.copy(kind = CalendarSourceKind.LOCAL).identity)
        assertNotEquals(CalendarSource(CalendarSourceKind.ANDROID, calendarId = 1).identity,
            CalendarSource(CalendarSourceKind.ANDROID, calendarId = 2).identity)
    }

    @Test fun httpsRejectsInsecureAmbiguousOrCredentialBearingAddresses() {
        listOf("http://example.invalid/a.ics", "file:///tmp/a.ics", "https:///a.ics",
            "https://user:password@example.invalid/a.ics", "https://example.invalid/a.ics#fragment", "garbage").forEach {
            assertThrows(Exception::class.java) { httpsAddress(it) }
        }
        assertEquals("example.invalid", httpsAddress("  https://example.invalid/a.ics?token=synthetic  ").host)
    }
}
