package com.github.panlelapin.simplercal

import android.database.MatrixCursor
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real Android cursors/signals, controlled queries: never reads an account on the device. */
class CalendarRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val monday = LocalDate.of(2026, 8, 3)
    private fun exists() = MatrixCursor(arrayOf("_id")).apply { addRow(arrayOf(7L)) }

    @Test fun nullCursorIsFailureWhereAnEmptyCursorIsSuccessful() = runBlocking {
        val nulls = CalendarRepository(context, CalendarQuery { _, _, _, _, _, _ -> null }, { true })
        assertEquals(CalendarWeekResult.Failure(CalendarFailureReason.PROVIDER_UNAVAILABLE), nulls.loadWeek(7, monday, ZoneOffset.UTC))
        assertEquals(CalendarChoicesResult.Failure(CalendarFailureReason.PROVIDER_UNAVAILABLE), nulls.loadCalendars())
        val empty = CalendarRepository(context, CalendarQuery { uri, columns, _, _, _, _ ->
            if (uri == CalendarContract.Calendars.CONTENT_URI) exists() else MatrixCursor(columns)
        }, { true })
        assertEquals(CalendarWeekResult.Success(emptyEventDays()), empty.loadWeek(7, monday, ZoneOffset.UTC))
    }

    @Test fun hiddenCalendarRemainsValidAndDeletedCalendarIsExplicitlyUnavailable() = runBlocking {
        var present = true
        val repository = CalendarRepository(context, CalendarQuery { uri, columns, selection, _, _, _ ->
            assertFalse(selection.orEmpty().contains(CalendarContract.Calendars.VISIBLE, ignoreCase = true))
            if (uri == CalendarContract.Calendars.CONTENT_URI) {
                MatrixCursor(columns).apply { if (present) addRow(arrayOf(7L)) }
            } else MatrixCursor(columns)
        }, { true })
        assertTrue(repository.loadWeek(7, monday, ZoneOffset.UTC) is CalendarWeekResult.Success)
        present = false
        assertEquals(CalendarWeekResult.Failure(CalendarFailureReason.CALENDAR_UNAVAILABLE), repository.loadWeek(7, monday, ZoneOffset.UTC))
    }

    @Test fun deniedPermissionAvoidsQueriesAndSecurityExceptionReportsRevocation() = runBlocking {
        var permitted = false
        var calls = 0
        val repository = CalendarRepository(context, CalendarQuery { _, _, _, _, _, _ ->
            calls++; throw SecurityException("Controlled revocation")
        }, { permitted })
        assertEquals(CalendarWeekResult.PermissionRequired, repository.loadWeek(7, monday, ZoneOffset.UTC))
        assertEquals(CalendarChoicesResult.PermissionRequired, repository.loadCalendars())
        assertEquals(0, calls)
        permitted = true
        assertEquals(CalendarWeekResult.Failure(CalendarFailureReason.PERMISSION_REVOKED), repository.loadWeek(7, monday, ZoneOffset.UTC))
    }

    @Test fun queryCancellationReachesTheProviderSignal() = runBlocking {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val repository = CalendarRepository(context, CalendarQuery { _, _, _, _, _, signal ->
            signal.setOnCancelListener { cancelled.countDown() }
            entered.countDown()
            check(cancelled.await(5, TimeUnit.SECONDS)) { "Cancellation did not reach provider" }
            signal.throwIfCanceled()
            null
        }, { true })
        val read = async(Dispatchers.Default) { repository.loadWeek(7, monday, ZoneOffset.UTC) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            read.cancelAndJoin()
            assertEquals(0L, cancelled.count)
            assertTrue(read.isCancelled)
        } finally { read.cancelAndJoin() }
    }

    @Test fun cancellationDuringCursorIterationClosesTheCursor() = runBlocking {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        var closed = false
        val repository = CalendarRepository(context, CalendarQuery { uri, columns, _, _, _, signal ->
            if (uri == CalendarContract.Calendars.CONTENT_URI) exists() else {
                signal.setOnCancelListener { cancelled.countDown() }
                object : MatrixCursor(columns) {
                    override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
                        entered.countDown()
                        check(cancelled.await(5, TimeUnit.SECONDS))
                        return true
                    }
                    override fun close() { closed = true; super.close() }
                }.apply { addRow(arrayOf<Any>(1L, "Controlled event", 1L, 2L, 0)) }
            }
        }, { true })
        val read = async(Dispatchers.Default) { repository.loadWeek(7, monday, ZoneOffset.UTC) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            read.cancelAndJoin()
            assertTrue(closed)
            assertTrue(read.isCancelled)
        } finally { read.cancelAndJoin() }
    }

    @Test fun instancesUseOneCapturedZoneAndPreserveIdsTitlesAndOrderingRequest() = runBlocking {
        val start = monday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        var closed = false
        val repository = CalendarRepository(context, CalendarQuery { uri, columns, selection, arguments, order, _ ->
            if (uri == CalendarContract.Calendars.CONTENT_URI) exists() else {
                assertEquals(listOf("7"), arguments!!.toList())
                assertTrue(selection!!.contains(CalendarContract.Instances.CALENDAR_ID))
                assertTrue(order!!.endsWith("event_id ASC"))
                assertEquals(start.toString(), uri.pathSegments[uri.pathSegments.lastIndex - 1])
                object : MatrixCursor(columns) {
                    override fun close() { closed = true; super.close() }
                }.apply { addRow(arrayOf<Any>(42L, "  Full title  ", start, start + 3_600_000, 0)) }
            }
        }, { true })
        val result = repository.loadWeek(7, monday, ZoneOffset.UTC) as CalendarWeekResult.Success
        assertEquals("42:$start", result.eventsByDay[0].single().instanceId)
        assertEquals("Full title", result.eventsByDay[0].single().title)
        assertTrue(closed)
    }
}
