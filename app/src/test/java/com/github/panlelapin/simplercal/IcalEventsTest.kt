package com.github.panlelapin.simplercal

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IcalEventsTest {
    private val utc = ZoneOffset.UTC
    private suspend fun parse(text: String, zone: ZoneId = utc, year: Int = 2026) =
        parseIcalEvents(text, year, zone, "Untitled")

    @Test fun allDayDatesAndExclusiveEndAreIndependentOfTheDeviceZone() = runBlocking {
        val input = ical(datedEvent() + "\nDTEND;VALUE=DATE:20260805")
        val original = java.util.TimeZone.getDefault()
        try {
            listOf("Pacific/Honolulu", "Pacific/Kiritimati").forEach { id ->
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(id))
                val zone = ZoneId.of(id)
                val event = parse(input, zone).single()
                assertEquals(Instant.parse("2026-08-03T00:00:00Z").toEpochMilli(), event.beginMillis)
                assertEquals(listOf(1, 1, 0, 0, 0, 0, 0),
                    groupEventsByDay(listOf(event), LocalDate.of(2026, 8, 3), zone).map { it.size })
            }
        } finally { java.util.TimeZone.setDefault(original) }
    }

    @Test fun recurringExceptionsReplaceAndCancelOccurrencesWithoutChangingTheirIdentity() = runBlocking {
        val master = "UID:series\nDTSTAMP:20260101T000000Z\nDTSTART:20260803T100000Z\nDURATION:PT1H\nRRULE:FREQ=DAILY;COUNT=4\nSUMMARY:Original"
        val moved = "UID:series\nDTSTAMP:20260101T000000Z\nRECURRENCE-ID:20260804T100000Z\nDTSTART:20260804T140000Z\nSUMMARY:Moved"
        val cancelled = "UID:series\nDTSTAMP:20260101T000000Z\nRECURRENCE-ID:20260805T100000Z\nSTATUS:CANCELLED"
        val events = parse(ical(master, moved, cancelled))
        assertEquals(listOf("Original", "Moved", "Original"), events.map { it.title })
        val changed = events[1]
        assertEquals("series:${Instant.parse("2026-08-04T10:00:00Z").toEpochMilli()}", changed.instanceId)
        assertEquals(Instant.parse("2026-08-04T14:00:00Z").toEpochMilli(), changed.beginMillis)
        assertEquals(3_600_000L, changed.endMillis - changed.beginMillis)
    }

    @Test fun exdateAndRdateModifyTheRecurrenceSet() = runBlocking {
        val events = parse(ical(datedEvent() + "\nRRULE:FREQ=DAILY;COUNT=3\nEXDATE;VALUE=DATE:20260804\nRDATE;VALUE=DATE:20260807"))
        assertEquals(listOf("2026-08-03", "2026-08-05", "2026-08-07"),
            events.map { Instant.ofEpochMilli(it.beginMillis).atZone(utc).toLocalDate().toString() })
    }

    @Test fun newestSequenceWinsAndCancelledMasterHidesItsOverrides() = runBlocking {
        val old = datedEvent(title = "Old") + "\nSEQUENCE:1"
        val new = datedEvent(title = "New") + "\nSEQUENCE:2"
        assertEquals("New", parse(ical(new, old)).single().title)
        val cancelled = new + "\nSTATUS:CANCELLED"
        val override = "UID:test\nDTSTAMP:20260101T000000Z\nRECURRENCE-ID;VALUE=DATE:20260803\nDTSTART;VALUE=DATE:20260804"
        assertTrue(parse(ical(cancelled, override)).isEmpty())
    }

    @Test fun sameSequenceUsesTheLatestTimestamp() = runBlocking {
        val a = datedEvent(title = "Old") + "\nLAST-MODIFIED:20260101T000000Z"
        val b = datedEvent(title = "New") + "\nLAST-MODIFIED:20260102T000000Z"
        assertEquals("New", parse(ical(b, a)).single().title)
    }

    @Test fun recurringFloatingTimesRemainAtTheSameLocalTimeAcrossDst() = runBlocking {
        val zone = ZoneId.of("Europe/Paris")
        val events = parse(ical("UID:dst\nDTSTAMP:20260101T000000Z\nDTSTART:20260328T090000\nDURATION:PT1H\nRRULE:FREQ=DAILY;COUNT=3"), zone)
        assertEquals(listOf(9, 9, 9), events.map { Instant.ofEpochMilli(it.beginMillis).atZone(zone).hour })
        assertEquals(23 * 3_600_000L, events[1].beginMillis - events[0].beginMillis)
        assertEquals(24 * 3_600_000L, events[2].beginMillis - events[1].beginMillis)
    }

    @Test fun nominalOneDayDurationFollowsBothDstTransitions() = runBlocking {
        val zone = ZoneId.of("Europe/Paris")
        listOf("20260328T120000" to 23L, "20261024T120000" to 25L).forEach { (start, hours) ->
            val event = parse(ical("UID:nominal\nDTSTAMP:20260101T000000Z\nDTSTART:$start\nDURATION:P1D"), zone).single()
            assertEquals(hours * 3_600_000L, event.endMillis - event.beginMillis)
        }
    }

    @Test fun embeddedTimezoneControlsRecurrencesEvenWithADifferentDeviceZone() = runBlocking {
        val timezone = """
            BEGIN:VTIMEZONE
            TZID:Test/Paris
            BEGIN:STANDARD
            DTSTART:19701025T030000
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
            END:STANDARD
            BEGIN:DAYLIGHT
            DTSTART:19700329T020000
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0200
            RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
            END:DAYLIGHT
            END:VTIMEZONE
        """.trimIndent().replace("\n", "\r\n")
        val input = ical("UID:embedded\nDTSTAMP:20260101T000000Z\nDTSTART;TZID=Test/Paris:20260328T090000\nDURATION:PT1H\nRRULE:FREQ=DAILY;COUNT=2")
            .replace("BEGIN:VEVENT", "$timezone\r\nBEGIN:VEVENT")
        val events = parse(input, ZoneId.of("Pacific/Honolulu"))
        assertEquals(listOf(Instant.parse("2026-03-28T08:00:00Z").toEpochMilli(),
            Instant.parse("2026-03-29T07:00:00Z").toEpochMilli()), events.map { it.beginMillis })
    }

    @Test fun exactDtendDurationRemainsExactAcrossRecurrences() = runBlocking {
        val events = parse(ical("UID:exact\nDTSTAMP:20260101T000000Z\nDTSTART:20260328T120000\nDTEND:20260329T120000\nRRULE:FREQ=DAILY;COUNT=2"), ZoneId.of("Europe/Paris"))
        assertEquals(listOf(23 * 3_600_000L, 23 * 3_600_000L), events.map { it.endMillis - it.beginMillis })
    }

    @Test fun onlyOverlappingInstancesInTheTwoYearsAreRetained() = runBlocking {
        val events = parse(ical(
            datedEvent("past", "20251230"),
            datedEvent("overlap", "20251231") + "\nDTEND;VALUE=DATE:20260102",
            datedEvent("first", "20260101"), datedEvent("last", "20271231"),
            datedEvent("future", "20280101"),
        ))
        assertEquals(setOf("overlap", "first", "last"), events.map { it.instanceId.substringBefore(':') }.toSet())
    }

    @Test fun anOldUnboundedSeriesIsExpandedOnlyWithinTheActiveYears() = runBlocking {
        val events = parse(ical(datedEvent(date = "20000101") + "\nRRULE:FREQ=DAILY"))
        assertEquals(730, events.size)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), events.first().beginMillis)
        assertEquals(Instant.parse("2027-12-31T00:00:00Z").toEpochMilli(), events.last().beginMillis)
    }

    @Test fun foldedEscapedTitlesAndUntitledFallbackArePreserved() = runBlocking {
        val text = ical("UID:title\nDTSTAMP:20260101T000000Z\nDTSTART;VALUE=DATE:20260803\nSUMMARY:Very long \\, title\\nsecond\n  half",
            "UID:blank\nDTSTAMP:20260101T000000Z\nDTSTART;VALUE=DATE:20260804")
        val events = parse(text)
        assertEquals("Very long , title\nsecond half", events[0].title)
        assertEquals("Untitled", events[1].title)
    }

    @Test fun invalidImportsNeverBecomePartiallySuccessfulCalendars() {
        val invalid = listOf(
            "not an iCalendar",
            ical("DTSTART;VALUE=DATE:20260803"),
            ical(datedEvent() + "\nDTEND;VALUE=DATE:20260802"),
            ical(datedEvent() + "\nDTEND:20260804T000000Z"),
            ical(datedEvent() + "\nDURATION:PT1H"),
            ical(datedEvent() + "\nDURATION:P3661D"),
            ical(datedEvent() + "\nRECURRENCE-ID;VALUE=DATE;RANGE=THISANDFUTURE:20260803"),
            ical("UID:period\nDTSTAMP:20260101T000000Z\nDTSTART:20260803T100000Z\nRDATE;VALUE=PERIOD:20260804T100000Z/PT1H"),
        )
        invalid.forEachIndexed { index, text ->
            assertThrows("Invalid fixture $index", IllegalArgumentException::class.java) {
                runBlocking { parse(text) }
            }
        }
    }

    @Test fun cacheReusesOnlyMatchingIdentityTextYearAndZone() = runBlocking {
        val cache = IcalEventCache()
        val snapshot = IcalSnapshot("one", ical("UID:cache\nDTSTAMP:20260101T000000Z\nDTSTART:20260803T090000"), 1)
        val first = cache.events(CalendarRole.MAIN, snapshot, 2026, utc, "Untitled")
        assertSame(first, cache.events(CalendarRole.MAIN, snapshot.copy(successMillis = 2), 2026, utc, "Untitled"))
        val paris = cache.events(CalendarRole.MAIN, snapshot, 2026, ZoneId.of("Europe/Paris"), "Untitled")
        assertNotEquals(first.single().beginMillis, paris.single().beginMillis)
        val changed = snapshot.copy(text = ical(datedEvent(title = "Changed")))
        assertEquals("Changed", cache.events(CalendarRole.MAIN, changed, 2026, utc, "Untitled").single().title)
        assertTrue(cache.events(CalendarRole.MAIN, changed, 2027, utc, "Untitled").isEmpty())
        assertNotSame(first, cache.events(CalendarRole.MAIN, snapshot.copy(identity = "two"), 2026, utc, "Untitled"))
    }
}
