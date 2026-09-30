package com.github.panlelapin.simplercal

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

internal class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val devicePreferences = context.getSharedPreferences("calendar_device", Context.MODE_PRIVATE)

    init {
        // Forward migration; device-local identifiers are excluded from future backups.
        if (preferences.contains(SELECTED_CALENDAR_KEY)) {
            val legacyId = preferences.getLong(SELECTED_CALENDAR_KEY, NO_CALENDAR_ID)
            if (!devicePreferences.contains(SELECTED_CALENDAR_KEY)) {
                devicePreferences.edit { putLong(SELECTED_CALENDAR_KEY, legacyId) }
            }
            preferences.edit { remove(SELECTED_CALENDAR_KEY) }
        }
    }

    fun selectedCalendarId(): Long =
        devicePreferences.getLong(SELECTED_CALENDAR_KEY, NO_CALENDAR_ID)

    fun setSelectedCalendarId(calendarId: Long) {
        devicePreferences.edit { putLong(SELECTED_CALENDAR_KEY, calendarId) }
    }

    fun clearSelectedCalendar() {
        devicePreferences.edit { remove(SELECTED_CALENDAR_KEY) }
    }

    fun accentTheme(): AccentTheme =
        AccentTheme.fromPreferenceValue(
            preferences.getString(DAY_ACCENT_COLOR_KEY, AccentTheme.SYSTEM.preferenceValue)
                ?: AccentTheme.SYSTEM.preferenceValue,
        )

    fun setAccentTheme(theme: AccentTheme) {
        preferences.edit { putString(DAY_ACCENT_COLOR_KEY, theme.preferenceValue) }
    }

    fun themeMode(): ThemeMode =
        ThemeMode.fromPreferenceValue(
            preferences.getString(THEME_MODE_KEY, ThemeMode.SYSTEM.preferenceValue)
                ?: ThemeMode.SYSTEM.preferenceValue,
        )

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(THEME_MODE_KEY, mode.preferenceValue) }
    }

    fun scrollMode(): WeekScrollMode =
        WeekScrollMode.fromPreferenceValue(
            preferences.getString(SCROLL_MODE_KEY, WeekScrollMode.DISCRETE.preferenceValue)
                ?: WeekScrollMode.DISCRETE.preferenceValue,
        )

    fun setScrollMode(mode: WeekScrollMode) {
        preferences.edit { putString(SCROLL_MODE_KEY, mode.preferenceValue) }
    }

    fun simulationMode(): SimulationMode =
        SimulationMode.fromPreferenceValue(
            preferences.getString(SIMULATION_MODE_KEY, SimulationMode.OFF.preferenceValue)
                ?: SimulationMode.OFF.preferenceValue,
        )

    fun setSimulationMode(mode: SimulationMode) {
        preferences.edit { putString(SIMULATION_MODE_KEY, mode.preferenceValue) }
    }

    fun debug1OutlineColor(): Debug1OutlineColor =
        Debug1OutlineColor.fromPreferenceValue(
            preferences.getString(
                DEBUG1_OUTLINE_COLOR_KEY,
                Debug1OutlineColor.APP_BAR_BACKGROUND.preferenceValue,
            ) ?: Debug1OutlineColor.APP_BAR_BACKGROUND.preferenceValue,
        )

    fun setDebug1OutlineColor(color: Debug1OutlineColor) {
        preferences.edit { putString(DEBUG1_OUTLINE_COLOR_KEY, color.preferenceValue) }
    }

    fun scheduleTimes(): List<Int> =
        List(SCHEDULE_SLOT_COUNT) { index ->
            val key = scheduleTimeKey(index)
            val stored = preferences.getInt(key, UNSET_SCHEDULE_TIME)
            if (stored == UNSET_SCHEDULE_TIME) {
                UNSET_SCHEDULE_TIME
            } else {
                validateScheduleMinutes(stored) ?: run {
                    preferences.edit { remove(key) }
                    UNSET_SCHEDULE_TIME
                }
            }
        }

    fun setScheduleTime(slotIndex: Int, minutes: Int): Boolean {
        if (slotIndex !in 0 until SCHEDULE_SLOT_COUNT || validateScheduleMinutes(minutes) == null) {
            return false
        }
        preferences.edit { putInt(scheduleTimeKey(slotIndex), minutes) }
        return true
    }

    fun dayMarkers(monday: LocalDate): Map<LocalDate, DayMarkers> {
        val holidays = preferences.getStringSet("holiday_dates", emptySet()).orEmpty()
        val bankHolidays = preferences.getStringSet("bank_holiday_dates", emptySet()).orEmpty()
        return (0 until WEEK_DAY_COUNT).associate { index ->
            val date = monday.plusDays(index.toLong())
            date to DayMarkers(date.toString() in holidays, date.toString() in bankHolidays)
        }
    }

    fun setDayMarkers(date: LocalDate, markers: DayMarkers) {
        fun updated(key: String, enabled: Boolean): Set<String> =
            preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet().apply {
                if (enabled) add(date.toString()) else remove(date.toString())
            }
        preferences.edit {
            putStringSet("holiday_dates", updated("holiday_dates", markers.isHolidays))
            putStringSet("bank_holiday_dates", updated("bank_holiday_dates", markers.isBankHoliday))
        }
    }
}
