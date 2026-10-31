package com.github.panlelapin.simplercal

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal class MemorySettings : SettingsStore {
    var accent = AccentTheme.SYSTEM
    var theme = ThemeMode.SYSTEM
    var scroll = WeekScrollMode.DISCRETE
    var simulation = SimulationMode.OFF
    var outline = Debug1OutlineColor.APP_BAR_BACKGROUND
    private val schedules = MutableList(SCHEDULE_SLOT_COUNT) { UNSET_SCHEDULE_TIME }
    override fun accentTheme() = accent
    override fun setAccentTheme(theme: AccentTheme) { accent = theme }
    override fun themeMode() = theme
    override fun setThemeMode(mode: ThemeMode) { theme = mode }
    override fun scrollMode() = scroll
    override fun setScrollMode(mode: WeekScrollMode) { scroll = mode }
    override fun simulationMode() = simulation
    override fun setSimulationMode(mode: SimulationMode) { simulation = mode }
    override fun debug1OutlineColor() = outline
    override fun setDebug1OutlineColor(color: Debug1OutlineColor) { outline = color }
    override fun scheduleTimes() = schedules.toList()
    override fun setScheduleTime(slotIndex: Int, minutes: Int): Boolean {
        if (slotIndex !in schedules.indices || validateScheduleMinutes(minutes) == null) return false
        schedules[slotIndex] = minutes
        return true
    }
}

internal class MemorySources : SourceStore {
    val configurations = CalendarRole.entries.associateWith { CalendarSource() }.toMutableMap()
    val snapshots = mutableMapOf<CalendarRole, IcalSnapshot>()
    var saves = 0
    var failSave = false
    override fun source(role: CalendarRole) = configurations.getValue(role)
    override fun save(role: CalendarRole, source: CalendarSource) { configurations[role] = source }
    override suspend fun snapshot(role: CalendarRole, source: CalendarSource) =
        snapshots[role]?.takeIf { it.identity == source.identity }
    override suspend fun saveSnapshot(role: CalendarRole, snapshot: IcalSnapshot) {
        check(!failSave) { "Controlled storage failure" }
        snapshots[role] = snapshot
        saves++
    }
}

internal class ControlledCalendars : CalendarGateway {
    data class Request(val id: Long, val monday: LocalDate, val zone: ZoneId)
    var permission = true
    val requests = mutableListOf<Request>()
    var cancellations = 0
    var read: suspend (Request) -> CalendarWeekResult = { CalendarWeekResult.Success(emptyEventDays()) }
    override fun hasPermission() = permission
    override suspend fun loadCalendars() = CalendarChoicesResult.Success(emptyList())
    override suspend fun loadWeek(calendarId: Long, monday: LocalDate, zoneId: ZoneId): CalendarWeekResult {
        val request = Request(calendarId, monday, zoneId)
        requests += request
        try { return read(request) } catch (cancelled: kotlinx.coroutines.CancellationException) {
            cancellations++
            throw cancelled
        }
    }
}

internal class MutableClock(var instantValue: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = fixed(instantValue, zone)
    override fun instant(): Instant = instantValue
}

internal fun ical(vararg events: String): String =
    (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//SimplerCal//Tests//EN") +
        events.map { "BEGIN:VEVENT\n${it.trimIndent()}\nEND:VEVENT" } + "END:VCALENDAR")
        .joinToString("\n").replace("\n", "\r\n") + "\r\n"

internal fun datedEvent(uid: String = "test", date: String = "20260803", title: String = "Event"): String =
    "UID:$uid\nDTSTAMP:20260101T000000Z\nDTSTART;VALUE=DATE:$date\nSUMMARY:$title"

internal fun weekWithTitle(title: String, monday: LocalDate): CalendarWeekResult.Success {
    val begin = monday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return CalendarWeekResult.Success(listOf(listOf(CalendarEvent(1, "1:$begin", title, begin, begin + 86_400_000, true))) +
        List(6) { emptyList() })
}
