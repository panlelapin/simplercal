package com.github.panlelapin.simplercal

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class CalendarEvent(
    val eventId: Long,
    val instanceId: String,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

internal sealed interface CalendarWeekResult {
    data class Success(
        val eventsByDay: List<List<CalendarEvent>>,
    ) : CalendarWeekResult

    data object PermissionRequired : CalendarWeekResult

    data class Failure(
        val reason: CalendarFailureReason,
    ) : CalendarWeekResult
}

internal sealed interface CalendarChoicesResult {
    data class Success(
        val calendars: List<CalendarChoice>,
    ) : CalendarChoicesResult

    data object PermissionRequired : CalendarChoicesResult

    data class Failure(
        val reason: CalendarFailureReason,
    ) : CalendarChoicesResult
}

internal enum class CalendarFailureReason {
    PERMISSION_REVOKED,
    PROVIDER_UNAVAILABLE,
    CALENDAR_UNAVAILABLE,
}

internal class CalendarRepository(
    private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadWeek(
        calendarId: Long,
        monday: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): CalendarWeekResult =
        withContext(Dispatchers.IO) {
            if (calendarId < 0L) return@withContext CalendarWeekResult.Success(emptyEventDays())
            if (!hasPermission()) return@withContext CalendarWeekResult.PermissionRequired
            try {
                val weekStart = monday.atStartOfDay(zoneId)
                val weekEnd = monday.plusDays(WEEK_DAY_COUNT.toLong()).atStartOfDay(zoneId)
                val instances = cancellableProviderRead { cancellation ->
                    if (!calendarExists(context.contentResolver, calendarId, cancellation)) {
                        return@cancellableProviderRead null
                    }
                    readCalendarInstances(
                        resolver = context.contentResolver,
                        calendarId = calendarId,
                        beginMillis = weekStart.toInstant().toEpochMilli(),
                        endMillis = weekEnd.toInstant().toEpochMilli(),
                        untitledEventName = context.getString(R.string.calendar_event_untitled),
                        cancellation = cancellation,
                    )
                } ?: return@withContext CalendarWeekResult.Failure(CalendarFailureReason.CALENDAR_UNAVAILABLE)
                CalendarWeekResult.Success(
                    groupEventsByDay(instances, monday, zoneId),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                Log.e(LOG_TAG, "Calendar week query was denied", error)
                CalendarWeekResult.Failure(CalendarFailureReason.PERMISSION_REVOKED)
            } catch (error: RuntimeException) {
                Log.e(LOG_TAG, "Calendar week query failed", error)
                CalendarWeekResult.Failure(CalendarFailureReason.PROVIDER_UNAVAILABLE)
            }
        }

    suspend fun loadCalendars(): CalendarChoicesResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext CalendarChoicesResult.PermissionRequired
            try {
                CalendarChoicesResult.Success(
                    cancellableProviderRead { cancellation -> readCalendars(
                        resolver = context.contentResolver,
                        unnamedCalendarName = context.getString(R.string.calendar_unnamed),
                        cancellation = cancellation,
                    ) },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                Log.e(LOG_TAG, "Calendar list query was denied", error)
                CalendarChoicesResult.Failure(CalendarFailureReason.PERMISSION_REVOKED)
            } catch (error: RuntimeException) {
                Log.e(LOG_TAG, "Calendar list query failed", error)
                CalendarChoicesResult.Failure(CalendarFailureReason.PROVIDER_UNAVAILABLE)
            }
        }
}

// Register cancellation before entering a blocking provider call on Dispatchers.IO.
private suspend fun <T> cancellableProviderRead(read: (CancellationSignal) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        continuation.invokeOnCancellation { cancellation.cancel() }
        try {
            continuation.resume(read(cancellation))
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

internal fun requireCalendarCursor(cursor: Cursor?): Cursor =
    checkNotNull(cursor) { "Calendar Provider returned no cursor" }

private fun calendarExists(resolver: ContentResolver, id: Long, cancellation: CancellationSignal): Boolean =
    resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars._ID} = ?",
        arrayOf(id.toString()),
        null,
        cancellation,
    ).use { cursor ->
        requireCalendarCursor(cursor).moveToFirst()
    }

private fun readCalendarInstances(
    resolver: ContentResolver,
    calendarId: Long,
    beginMillis: Long,
    endMillis: Long,
    untitledEventName: String,
    cancellation: CancellationSignal,
): List<CalendarEvent> {
    val uri =
        CalendarContract.Instances.CONTENT_URI
            .buildUpon()
            .appendPath(beginMillis.toString())
            .appendPath(endMillis.toString())
            .build()
    val projection =
        arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
    val selection = "${CalendarContract.Instances.CALENDAR_ID} = ?"
    val sortOrder =
        "${CalendarContract.Instances.BEGIN} ASC, " +
            "${CalendarContract.Instances.END} ASC, " +
            "${CalendarContract.Instances.EVENT_ID} ASC"
    return resolver.query(
        uri,
        projection,
        selection,
        arrayOf(calendarId.toString()),
        sortOrder,
        cancellation,
    ).use { cursor ->
        requireCalendarCursor(cursor).readEvents(untitledEventName, cancellation)
    }
}

private fun Cursor.readEvents(untitledEventName: String, cancellation: CancellationSignal): List<CalendarEvent> =
    buildList {
        while (moveToNext()) {
            cancellation.throwIfCanceled()
            val eventId = getLong(0)
            val title = getString(1)?.trim().orEmpty().ifEmpty { untitledEventName }
            val begin = getLong(2)
            val end = getLong(3).coerceAtLeast(begin + 1L)
            add(
                CalendarEvent(
                    eventId = eventId,
                    instanceId = "$eventId:$begin",
                    title = title,
                    beginMillis = begin,
                    endMillis = end,
                    allDay = getInt(4) != 0,
                ),
            )
        }
    }

private fun readCalendars(
    resolver: ContentResolver,
    unnamedCalendarName: String,
    cancellation: CancellationSignal,
): List<CalendarChoice> {
    val projection =
        arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
        )
    val sortOrder =
        "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC, " +
            "${CalendarContract.Calendars.ACCOUNT_NAME} COLLATE NOCASE ASC"
    return resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        null,
        null,
        sortOrder,
        cancellation,
    ).use { cursor ->
        val readableCursor = requireCalendarCursor(cursor)
        buildList {
            while (readableCursor.moveToNext()) {
                cancellation.throwIfCanceled()
                add(
                    CalendarChoice(
                        id = readableCursor.getLong(0),
                        name = readableCursor.getString(1)?.trim().orEmpty().ifEmpty { unnamedCalendarName },
                        accountName = readableCursor.getString(2)?.trim().orEmpty(),
                        ownerAccount = readableCursor.getString(3)?.trim().orEmpty(),
                    ),
                )
            }
        }
    }
}

internal fun groupEventsByDay(
    events: List<CalendarEvent>,
    monday: LocalDate,
    zoneId: ZoneId,
): List<List<CalendarEvent>> =
    List(WEEK_DAY_COUNT) { dayIndex ->
        val date = monday.plusDays(dayIndex.toLong())
        events.filter { event -> event.occursOn(date, zoneId) }
    }

private fun CalendarEvent.occursOn(date: LocalDate, zoneId: ZoneId): Boolean {
    if (allDay) {
        val startDate = Instant.ofEpochMilli(beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return date >= startDate && date < endDate
    }
    val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    return beginMillis < dayEnd && endMillis > dayStart
}

internal fun emptyEventDays(): List<List<CalendarEvent>> =
    List(WEEK_DAY_COUNT) { emptyList() }

private const val LOG_TAG = "SimplerCalCalendar"
