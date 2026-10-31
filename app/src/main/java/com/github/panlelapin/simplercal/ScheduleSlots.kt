package com.github.panlelapin.simplercal

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal enum class ScheduleStatus { INCOMPLETE, INVALID, READY }

internal fun scheduleStatus(times: List<Int>): ScheduleStatus {
    if (times.size != SCHEDULE_SLOT_COUNT || times.any { it != UNSET_SCHEDULE_TIME && it !in 0..1439 }) {
        return ScheduleStatus.INVALID
    }
    if (times[0] != UNSET_SCHEDULE_TIME && times[0] <= FIRST_SLOT_START_MINUTE) return ScheduleStatus.INVALID
    val configured = times.indices.filter { times[it] != UNSET_SCHEDULE_TIME }
    if (configured.zipWithNext().any { (before, after) ->
            if (before == 0) times[before] > times[after] else times[before] >= times[after]
        }) return ScheduleStatus.INVALID
    return if (times.any { it == UNSET_SCHEDULE_TIME }) ScheduleStatus.INCOMPLETE else ScheduleStatus.READY
}

internal data class ScheduleLine(val index: Int, val minutes: Int, val events: List<CalendarEvent> = emptyList())

internal data class DaySchedule(val lines: List<ScheduleLine>) {
    init { require(lines.size == SCHEDULE_SLOT_COUNT && lines.indices.all { lines[it].index == it }) }
    companion object {
        fun empty(times: List<Int> = emptyList()): DaySchedule = DaySchedule(
            List(SCHEDULE_SLOT_COUNT) { index -> ScheduleLine(index, times.getOrElse(index) { UNSET_SCHEDULE_TIME }) },
        )
    }
}

internal data class WeekScheduleInput(
    val monday: LocalDate,
    val days: List<List<CalendarEvent>>,
    val times: List<Int>,
    val zone: ZoneId,
)

internal data class WeekScheduleProjection(val input: WeekScheduleInput, val days: List<DaySchedule>)

private const val FIRST_SLOT_START_MINUTE = 1
private const val END_OF_DAY_MINUTE = 24 * 60

/** A fresh projector per displayed week keeps the date/window cache bounded. */
internal class ScheduleProjector(times: List<Int>, private val zone: ZoneId, private val checkCancellation: () -> Unit = {}) {
    private val times = times.toList()
    private val ready = scheduleStatus(this.times) == ScheduleStatus.READY
    private val cache = mutableMapOf<Pair<LocalDate, ZoneId>, List<SlotWindow>>()

    fun day(date: LocalDate, events: List<CalendarEvent>): DaySchedule {
        val empty = DaySchedule.empty(times)
        if (!ready || events.isEmpty()) return empty
        val assigned = List(SCHEDULE_SLOT_COUNT) { mutableListOf<CalendarEvent>() }
        events.forEach { event ->
            checkCancellation()
            // All-day dates remain floating UTC dates, as in the provider/iCalendar repositories.
            val eventZone = if (event.allDay) ZoneOffset.UTC else zone
            val begin = Instant.ofEpochMilli(event.beginMillis)
            val rawEnd = Instant.ofEpochMilli(event.endMillis)
            if (rawEnd < begin) return@forEach
            val end = if (rawEnd == begin) begin.plusNanos(1) else rawEnd
            val firstDate = begin.atZone(eventZone).toLocalDate()
            val lastDate = end.minusNanos(1).atZone(eventZone).toLocalDate()
            val first = endpoint(firstDate, begin, end, eventZone, first = true) ?: return@forEach
            val last = endpoint(lastDate, begin, end, eventZone, first = false) ?: return@forEach
            // Two touched slots -> the first only. Three or more -> all except the last.
            // Compare full windows, so crossing midnight does not drop a slot on every day.
            windows(date, eventZone).asSequence()
                .filter { it.overlaps(begin, end) && (first == last || it != last) }
                .map { it.index }.distinct().forEach { assigned[it].add(event) }
        }
        return DaySchedule(empty.lines.map { it.copy(events = assigned[it.index].toList()) })
    }

    private fun endpoint(date: LocalDate, begin: Instant, end: Instant, zone: ZoneId, first: Boolean): SlotWindow? {
        // The only unassigned daily gaps are before 00:01 and the optional first/second pause.
        // Search the endpoint day and its neighbour, never every day of a long event.
        listOf(date, date.plusDays(if (first) 1L else -1L)).forEach { candidate ->
            val hits = windows(candidate, zone).filter { it.overlaps(begin, end) }
            (if (first) hits.firstOrNull() else hits.lastOrNull())?.let { return it }
        }
        return null
    }

    private fun windows(date: LocalDate, zone: ZoneId): List<SlotWindow> = cache.getOrPut(date to zone) {
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        val bySlot = List(SCHEDULE_SLOT_COUNT) { mutableListOf<SlotWindow>() }
        var segmentStart = startOfDay
        while (segmentStart < endOfDay) {
            checkCancellation()
            val next = zone.rules.nextTransition(segmentStart)?.instant
            val segmentEnd = if (next != null && next < endOfDay) next else endOfDay
            val offset = zone.rules.getOffset(segmentStart)
            times.indices.forEach { index ->
                val from = if (index == 0) FIRST_SLOT_START_MINUTE else times[index]
                val until = if (index == 0) times[0] else times.getOrElse(index + 1) { END_OF_DAY_MINUTE }
                val begin = maxOf(segmentStart, date.atStartOfDay().plusMinutes(from.toLong()).toInstant(offset))
                val end = minOf(segmentEnd, date.atStartOfDay().plusMinutes(until.toLong()).toInstant(offset))
                if (begin < end) {
                    val previous = bySlot[index].lastOrNull()
                    if (previous?.end == begin) {
                        bySlot[index][bySlot[index].lastIndex] = previous.copy(end = end)
                    } else {
                        bySlot[index].add(SlotWindow(index, begin, end))
                    }
                }
            }
            segmentStart = segmentEnd
        }
        bySlot.flatten().sortedBy { it.begin }
    }

    private data class SlotWindow(val index: Int, val begin: Instant, val end: Instant) {
        fun overlaps(eventBegin: Instant, eventEnd: Instant): Boolean = eventBegin < end && eventEnd > begin
    }
}
