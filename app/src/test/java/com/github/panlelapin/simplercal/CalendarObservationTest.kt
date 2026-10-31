package com.github.panlelapin.simplercal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarObservationTest {
    @Test fun partialRegistrationRetriesWithBoundedBackoffAndClosesExactlyOnce() = runTest {
        var eventCalls = 0
        var calendarCalls = 0
        var closes = 0
        var failRegistration = true
        val times = mutableListOf<Long>()
        val errors = mutableListOf<Boolean>()
        val registration = object : CalendarObserverRegistration {
            override fun events() { eventCalls++ }
            override fun calendars() {
                calendarCalls++
                times += testScheduler.currentTime
                if (failRegistration) error("Controlled registration failure")
            }
            override fun close() { closes++ }
        }
        val observation = CalendarObservation(this, registration, { fail("Unexpected permission failure") }, { errors += it })
        runCurrent()
        advanceTimeBy(91_001); runCurrent()
        assertEquals(listOf(0L, 1000L, 3000L, 7000L, 15000L, 31000L, 61000L, 91000L), times)
        assertEquals(1, eventCalls)
        failRegistration = false
        advanceTimeBy(30_000); runCurrent()
        assertEquals(false, errors.last())
        observation.close(); observation.close()
        assertEquals(1, closes)
        val callsAtClose = calendarCalls
        advanceTimeBy(60_000); runCurrent()
        assertEquals(callsAtClose, calendarCalls)
    }

    @Test fun permissionFailureIsReportedWithoutARetryLoop() = runTest {
        var revocations = 0
        var calls = 0
        val registration = object : CalendarObserverRegistration {
            override fun events() { calls++; throw SecurityException("Controlled denial") }
            override fun calendars() { fail("Calendar registration after denied Events registration") }
            override fun close() { fail("Nothing was registered") }
        }
        val observation = CalendarObservation(this, registration, { revocations++ }, { fail("Permission is not a transient failure") })
        runCurrent(); advanceTimeBy(100_000); runCurrent()
        assertEquals(1, calls)
        assertEquals(1, revocations)
        observation.close()
    }

    @Test fun stopDuringBackoffUnregistersAndPreventsFurtherAttempts() = runTest {
        var calls = 0
        var closed = false
        val registration = object : CalendarObserverRegistration {
            override fun events() {}
            override fun calendars() { calls++; error("Controlled failure") }
            override fun close() { closed = true }
        }
        val observation = CalendarObservation(this, registration, {}, {})
        runCurrent(); observation.close()
        advanceTimeBy(120_000); runCurrent()
        assertTrue(closed)
        assertEquals(1, calls)
    }
}
