package com.github.panlelapin.simplercal

import biweekly.component.VEvent
import biweekly.io.text.ICalReader
import biweekly.util.ICalDate
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Expand a snapshot only in the two permitted years; never access its original source here. */
internal suspend fun parseIcalEvents(text: String, year: Int, zone: ZoneId, untitled: String): List<CalendarEvent> {
    val context = currentCoroutineContext()
    val calendars = ICalReader(text).use { reader ->
        reader.defaultTimezone = TimeZone.getTimeZone(zone)
        buildList {
            while (true) {
                context.ensureActive()
                val calendar = reader.readNext() ?: break
                require(reader.warnings.isEmpty()) { "Invalid iCalendar" }
                add(calendar)
            }
        }.also { require(it.isNotEmpty()) { "Empty iCalendar input" } }
    }
    val lowerDate = LocalDate.of(year, 1, 1)
    val upperDate = LocalDate.of(year + 2, 1, 1)
    val result = mutableListOf<CalendarEvent>()
    calendars.forEach { calendar ->
        val groups = calendar.events.groupBy { requireNotNull(it.uid?.value) { "Missing UID" } }
        groups.forEach { (uid, versions) ->
            context.ensureActive()
            require(uid.isNotBlank()) { "Missing UID" }
            // Keep the most recent revision of each master/exception.
            val events = versions.groupBy { it.recurrenceId?.value?.let(::dateMillis) }
                .mapValues { (_, revisions) -> revisions.maxWith(compareBy<VEvent> { it.sequence?.value ?: 0 }
                    .thenBy { it.lastModified?.value?.time ?: it.dateTimeStamp?.value?.time ?: 0L }) }
            val master = events[null]
            val overrides = events.filterKeys { it != null }
            events.values.forEach { event ->
                require(event.recurrenceId?.range == null) { "Recurrence RANGE is not supported" }
                require(event.recurrenceDates.all { it.periods.isEmpty() }) { "RDATE periods are not supported" }
            }
            if (master != null && master.status?.isCancelled != true) {
                val start = requireNotNull(master.dateStart?.value) { "Missing DTSTART" }
                val allDay = !start.hasTime()
                val startMillis = dateMillis(start)
                val duration = eventDuration(master, startMillis, allDay)
                val lower = lowerDate.atStartOfDay(if (allDay) ZoneOffset.UTC else zone).toInstant().toEpochMilli()
                val upper = upperDate.atStartOfDay(if (allDay) ZoneOffset.UTC else zone).toInstant().toEpochMilli()
                val timezone = calendar.timezoneInfo.getTimezone(master.dateStart)?.timeZone ?: TimeZone.getTimeZone(zone)
                // Biweekly forces the process default zone for DATE iterators. Iterate a
                // private UTC date-time copy to keep DATE occurrences and EXDATEs stable.
                val iterator = (if (allDay) utcDateIteratorCopy(master) else master).getDateIterator(
                    if (allDay) TimeZone.getTimeZone("UTC") else timezone)
                // Include long events beginning before the permitted years.
                iterator.advanceTo(Date(lower - duration - 2 * 86_400_000L))
                var iterations = 0
                while (iterator.hasNext()) {
                    context.ensureActive()
                    require(++iterations <= 200_000) { "Too many occurrences" }
                    val begin = iterator.next().time
                    if (begin >= upper) break
                    if (begin !in overrides) addInstance(result, master, uid, begin, duration, allDay, lower, upper, untitled, timezone = timezone)
                }
            }
            overrides.values.forEach exception@{ event ->
                context.ensureActive()
                if (event.status?.isCancelled == true || master?.status?.isCancelled == true) return@exception
                val start = requireNotNull(event.dateStart?.value) { "Missing exception DTSTART" }
                val allDay = !start.hasTime()
                val begin = dateMillis(start)
                val lower = lowerDate.atStartOfDay(if (allDay) ZoneOffset.UTC else zone).toInstant().toEpochMilli()
                val upper = upperDate.atStartOfDay(if (allDay) ZoneOffset.UTC else zone).toInstant().toEpochMilli()
                val inheritedDuration = master?.dateStart?.value?.let { eventDuration(master, dateMillis(it), !it.hasTime()) }
                addInstance(result, event, uid, begin, eventDuration(event, begin, allDay, inheritedDuration), allDay,
                    lower, upper, event.summary?.value ?: master?.summary?.value ?: untitled,
                    requireNotNull(event.recurrenceId?.value).let(::dateMillis),
                    calendar.timezoneInfo.getTimezone(event.dateStart)?.timeZone ?: TimeZone.getTimeZone(zone),
                    if (event.dateEnd == null && event.duration == null) master else event)
            }
        }
    }
    return result.distinctBy { it.instanceId }.sortedWith(compareBy<CalendarEvent> { it.beginMillis }
        .thenBy { it.endMillis }.thenBy { it.eventId })
}

private fun utcDateIteratorCopy(event: VEvent): VEvent = VEvent(event).apply {
    fun timed(date: ICalDate): ICalDate = ICalDate(Date(dateMillis(date)), true)
    dateStart.value = timed(dateStart.value)
    recurrenceDates.forEach { property -> property.dates.replaceAll { timed(it) } }
    exceptionDates.forEach { property -> property.values.replaceAll { timed(it) } }
}

private fun dateMillis(date: ICalDate): Long {
    if (date.hasTime()) return date.time
    val raw = requireNotNull(date.rawComponents)
    return LocalDate.of(raw.year, raw.month, raw.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun eventDuration(event: VEvent, begin: Long, allDay: Boolean, inherited: Long? = null): Long {
    val endDate = event.dateEnd?.value
    require(endDate == null || endDate.hasTime() != allDay) { "Mismatched date types" }
    val end = endDate?.let(::dateMillis)
    require(!allDay || end == null || end > begin) { "Invalid all-day end" }
    require(!allDay || event.duration?.value?.hasTime() != true) { "Invalid all-day duration" }
    val duration = end?.minus(begin) ?: event.duration?.value?.toMillis() ?: inherited ?: if (allDay) 86_400_000L else 1L
    require(duration >= 0L && duration <= 3660L * 86_400_000L && (!allDay || duration >= 86_400_000L)) { "Invalid duration" }
    return duration.coerceAtLeast(1L)
}

private fun addInstance(
    events: MutableList<CalendarEvent>, event: VEvent, uid: String, begin: Long, duration: Long,
    allDay: Boolean, lower: Long, upper: Long, untitled: String, occurrence: Long = begin,
    timezone: TimeZone = TimeZone.getTimeZone("UTC"), durationTemplate: VEvent? = event,
) {
    // DURATION days/weeks are nominal calendar units, unlike the exact duration defined by DTEND.
    val nominal = durationTemplate?.takeIf { it.dateEnd == null }?.duration?.value
    val end = if (nominal != null) {
        val calendar = java.util.Calendar.getInstance(if (allDay) TimeZone.getTimeZone("UTC") else timezone)
        calendar.timeInMillis = begin
        calendar.add(java.util.Calendar.DAY_OF_MONTH, (nominal.weeks ?: 0) * 7 + (nominal.days ?: 0))
        calendar.add(java.util.Calendar.HOUR_OF_DAY, nominal.hours ?: 0)
        calendar.add(java.util.Calendar.MINUTE, nominal.minutes ?: 0)
        calendar.add(java.util.Calendar.SECOND, nominal.seconds ?: 0)
        calendar.timeInMillis.coerceAtLeast(begin + 1L)
    } else begin + duration
    if (begin >= upper || end <= lower) return
    require(events.size < 100_000) { "Too many events" }
    events.add(CalendarEvent(uid.hashCode().toLong(), "$uid:$occurrence", event.summary?.value?.trim().orEmpty()
        .ifEmpty { untitled }, begin, end, allDay))
}

/** One parsed generation per role, so week navigation does not expand the same feed repeatedly. */
internal class IcalEventCache {
    private data class Entry(val identity: String, val text: String, val year: Int, val zone: ZoneId,
        val events: List<CalendarEvent>)
    private val entries = java.util.concurrent.ConcurrentHashMap<CalendarRole, Entry>()

    suspend fun events(role: CalendarRole, snapshot: IcalSnapshot, year: Int, zone: ZoneId, untitled: String): List<CalendarEvent> {
        val cached = entries[role]
        if (cached != null && cached.identity == snapshot.identity && cached.text == snapshot.text &&
            cached.year == year && cached.zone == zone) return cached.events
        val result = parseIcalEvents(snapshot.text, year, zone, untitled)
        entries[role] = Entry(snapshot.identity, snapshot.text, year, zone, result)
        return result
    }
}
