package com.github.panlelapin.simplercal

import android.app.Application
import android.content.Intent
import android.net.Uri
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Boundaries shared by production and deterministic tests; no Android objects in state logic. */
internal interface SettingsStore {
    fun accentTheme(): AccentTheme
    fun setAccentTheme(theme: AccentTheme)
    fun themeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun scrollMode(): WeekScrollMode
    fun setScrollMode(mode: WeekScrollMode)
    fun simulationMode(): SimulationMode
    fun setSimulationMode(mode: SimulationMode)
    fun debug1OutlineColor(): Debug1OutlineColor
    fun setDebug1OutlineColor(color: Debug1OutlineColor)
    fun scheduleTimes(): List<Int>
    fun setScheduleTime(slotIndex: Int, minutes: Int): Boolean
}

internal interface SourceStore {
    fun source(role: CalendarRole): CalendarSource
    fun save(role: CalendarRole, source: CalendarSource)
    suspend fun snapshot(role: CalendarRole, source: CalendarSource): IcalSnapshot?
    suspend fun saveSnapshot(role: CalendarRole, snapshot: IcalSnapshot)
}

internal interface CalendarGateway {
    fun hasPermission(): Boolean
    suspend fun loadWeek(calendarId: Long, monday: LocalDate, zoneId: ZoneId): CalendarWeekResult
    suspend fun loadCalendars(): CalendarChoicesResult
}

internal fun interface FileCalendarReader {
    suspend fun readSource(source: CalendarSource): String
}

internal data class AppEnvironment(
    val preferences: SettingsStore,
    val sources: SourceStore,
    val calendars: CalendarGateway,
    val files: FileCalendarReader,
    val clock: Clock = Clock.systemUTC(),
    val zone: () -> ZoneId = { ZoneId.systemDefault() },
    val io: CoroutineDispatcher = Dispatchers.IO,
    val untitled: String,
    val releaseDocument: (String) -> Unit = {},
) {
    companion object {
        fun android(application: Application): AppEnvironment {
            val context = application.applicationContext
            return AppEnvironment(
                AppPreferences(context), CalendarSourcePreferences(context), CalendarRepository(context),
                IcalRepository(context), untitled = context.getString(R.string.calendar_event_untitled),
                releaseDocument = { location ->
                    try {
                        context.contentResolver.releasePersistableUriPermission(Uri.parse(location), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) { /* Grant may already have been revoked. */ }
                },
            )
        }
    }
}
