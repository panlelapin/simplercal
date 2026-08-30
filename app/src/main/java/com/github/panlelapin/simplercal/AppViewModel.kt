package com.github.panlelapin.simplercal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class AppViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application.applicationContext)
    private val calendarRepository = CalendarRepository(application.applicationContext)
    private val calendarRefreshes = Channel<Unit>(Channel.CONFLATED)
    private val hasRestoredNavigation = savedStateHandle.contains(STATE_DISPLAYED_MONDAY)
    private var calendarListJob: Job? = null
    private var midnightJob: Job? = null

    var uiState by androidx.compose.runtime.mutableStateOf(createInitialState())
        private set

    init {
        viewModelScope.launch {
            for (ignored in calendarRefreshes) {
                delay(CALENDAR_REFRESH_DEBOUNCE_MILLIS)
                while (calendarRefreshes.tryReceive().isSuccess) {
                    // Collapse a provider burst into one query of the newest state.
                }
                loadDisplayedWeek()
            }
        }
        restartMidnightTimer()
        if (hasRestoredNavigation) update() else selectToday()
        refreshCalendars()
    }

    fun update() {
        val monday = uiState.displayedMonday
        val referenceDate = referenceDateFor(monday, uiState.simulationMode)
        val highlightedIndex = dayIndexInWeek(referenceDate, monday)
        uiState =
            uiState.copy(
                title = weekTitle(monday),
                referenceDate = referenceDate,
                highlightedDayIndex = highlightedIndex,
                hasCalendarPermission = calendarRepository.hasPermission(),
            )
        requestCalendarRefresh()
    }

    fun onResume() {
        update()
        if (uiState.isSettingsVisible) refreshCalendars()
    }

    fun onTimeContextChanged() {
        restartMidnightTimer()
        update()
    }

    fun showSettings() {
        savedStateHandle[STATE_SETTINGS_VISIBLE] = true
        uiState = uiState.copy(isSettingsVisible = true)
        refreshCalendars()
    }

    fun hideSettings() {
        savedStateHandle[STATE_SETTINGS_VISIBLE] = false
        uiState = uiState.copy(isSettingsVisible = false)
    }

    fun showPreviousWeek() = setDisplayedMonday(uiState.displayedMonday.minusWeeks(1))

    fun showNextWeek() = setDisplayedMonday(uiState.displayedMonday.plusWeeks(1))

    fun selectToday() {
        val monday = currentWeekMonday()
        val selectedIndex =
            if (uiState.simulationMode == SimulationMode.SIMULATION) {
                SIMULATION_TODAY_INDEX
            } else {
                LocalDate.now().dayOfWeek.value - 1
            }
        savedStateHandle[STATE_DISPLAYED_MONDAY] = monday.toEpochDay()
        savedStateHandle[STATE_SELECTED_DAY] = selectedIndex
        uiState =
            uiState.copy(
                displayedMonday = monday,
                selectedDayIndex = selectedIndex,
                selectionRequest = uiState.selectionRequest + 1,
            )
        update()
    }

    fun onDaySelected(dayIndex: Int) {
        val bounded = dayIndex.coerceIn(0, WEEK_DAY_COUNT - 1)
        savedStateHandle[STATE_SELECTED_DAY] = bounded
        if (bounded != uiState.selectedDayIndex) {
            uiState = uiState.copy(selectedDayIndex = bounded)
        }
    }

    fun onCalendarProviderChanged() {
        update()
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        uiState =
            uiState.copy(
                hasCalendarPermission = granted && calendarRepository.hasPermission(),
                calendarFailure = null,
                calendarListFailure = null,
            )
        refreshCalendars()
        update()
    }

    fun selectCalendar(calendarId: Long) {
        preferences.setSelectedCalendarId(calendarId)
        uiState =
            uiState.copy(
                selectedCalendarId = calendarId,
                calendarFailure = null,
            )
        requestCalendarRefresh()
    }

    fun refreshCalendars() {
        calendarListJob?.cancel()
        if (!calendarRepository.hasPermission()) {
            uiState =
                uiState.copy(
                    hasCalendarPermission = false,
                    calendars = emptyList(),
                    isCalendarListLoading = false,
                    calendarListFailure = null,
                )
            return
        }
        uiState =
            uiState.copy(
                hasCalendarPermission = true,
                isCalendarListLoading = true,
                calendarListFailure = null,
            )
        calendarListJob =
            viewModelScope.launch {
                when (val result = calendarRepository.loadCalendars()) {
                    is CalendarChoicesResult.Success -> applyCalendarChoices(result.calendars)
                    CalendarChoicesResult.PermissionRequired -> {
                        uiState =
                            uiState.copy(
                                hasCalendarPermission = false,
                                calendars = emptyList(),
                                isCalendarListLoading = false,
                            )
                    }

                    is CalendarChoicesResult.Failure -> {
                        uiState =
                            uiState.copy(
                                isCalendarListLoading = false,
                                calendarListFailure = result.reason,
                            )
                    }
                }
            }
    }

    fun setScheduleTime(
        slotIndex: Int,
        minutes: Int,
    ) {
        if (!preferences.setScheduleTime(slotIndex, minutes)) return
        val updated = uiState.scheduleTimes.toMutableList().also { it[slotIndex] = minutes }
        uiState = uiState.copy(scheduleTimes = updated)
    }

    fun setAccentTheme(theme: AccentTheme) {
        preferences.setAccentTheme(theme)
        uiState = uiState.copy(accentTheme = theme)
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
        uiState = uiState.copy(themeMode = mode)
    }

    fun setScrollMode(mode: WeekScrollMode) {
        preferences.setScrollMode(mode)
        uiState = uiState.copy(scrollMode = mode)
    }

    fun setSimulationMode(mode: SimulationMode) {
        preferences.setSimulationMode(mode)
        uiState = uiState.copy(simulationMode = mode)
        selectToday()
    }

    fun setDebug1OutlineColor(color: Debug1OutlineColor) {
        preferences.setDebug1OutlineColor(color)
        uiState = uiState.copy(debug1OutlineColor = color)
    }

    private fun createInitialState(): AppUiState {
        val simulationMode = preferences.simulationMode()
        val defaultMonday = currentWeekMonday()
        val restoredMonday =
            savedStateHandle.get<Long>(STATE_DISPLAYED_MONDAY)?.let { LocalDate.ofEpochDay(it) }
                ?: defaultMonday
        val defaultSelectedDay =
            if (simulationMode == SimulationMode.SIMULATION) {
                SIMULATION_TODAY_INDEX
            } else {
                LocalDate.now().dayOfWeek.value - 1
            }
        val selectedDay =
            savedStateHandle.get<Int>(STATE_SELECTED_DAY)
                ?.takeIf { it in 0 until WEEK_DAY_COUNT }
                ?: defaultSelectedDay
        val referenceDate = referenceDateFor(restoredMonday, simulationMode)
        return AppUiState(
            title = weekTitle(restoredMonday),
            displayedMonday = restoredMonday,
            referenceDate = referenceDate,
            highlightedDayIndex = dayIndexInWeek(referenceDate, restoredMonday),
            selectedDayIndex = selectedDay,
            selectionRequest = 1,
            eventsByDay = emptyEventDays(),
            loadedEventsMonday = null,
            loadedEventsCalendarId = NO_CALENDAR_ID,
            isCalendarLoading = false,
            calendarFailure = null,
            selectedCalendarId = preferences.selectedCalendarId(),
            calendars = emptyList(),
            isCalendarListLoading = false,
            calendarListFailure = null,
            hasCalendarPermission = calendarRepository.hasPermission(),
            isSettingsVisible = savedStateHandle[STATE_SETTINGS_VISIBLE] ?: false,
            accentTheme = preferences.accentTheme(),
            themeMode = preferences.themeMode(),
            scrollMode = preferences.scrollMode(),
            simulationMode = simulationMode,
            debug1OutlineColor = preferences.debug1OutlineColor(),
            scheduleTimes = preferences.scheduleTimes(),
        )
    }

    private fun setDisplayedMonday(monday: LocalDate) {
        savedStateHandle[STATE_DISPLAYED_MONDAY] = monday.toEpochDay()
        uiState = uiState.copy(displayedMonday = monday)
        update()
    }

    private fun requestCalendarRefresh() {
        calendarRefreshes.trySend(Unit)
    }

    private suspend fun loadDisplayedWeek() {
        val requestedMonday = uiState.displayedMonday
        val requestedCalendarId = uiState.selectedCalendarId
        if (requestedCalendarId < 0L) {
            uiState =
                uiState.copy(
                    eventsByDay = emptyEventDays(),
                    loadedEventsMonday = null,
                    loadedEventsCalendarId = NO_CALENDAR_ID,
                    isCalendarLoading = false,
                    calendarFailure = null,
                )
            return
        }
        uiState = uiState.copy(isCalendarLoading = true, calendarFailure = null)
        val result = calendarRepository.loadWeek(requestedCalendarId, requestedMonday)
        if (
            requestedMonday != uiState.displayedMonday ||
                requestedCalendarId != uiState.selectedCalendarId
        ) {
            requestCalendarRefresh()
            return
        }
        when (result) {
            is CalendarWeekResult.Success -> {
                uiState =
                    uiState.copy(
                        eventsByDay = result.eventsByDay,
                        loadedEventsMonday = requestedMonday,
                        loadedEventsCalendarId = requestedCalendarId,
                        isCalendarLoading = false,
                        calendarFailure = null,
                    )
            }

            CalendarWeekResult.PermissionRequired -> {
                uiState =
                    uiState.copy(
                        hasCalendarPermission = false,
                        isCalendarLoading = false,
                        calendarFailure = CalendarFailureReason.PERMISSION_REVOKED,
                    )
            }

            is CalendarWeekResult.Failure -> {
                uiState =
                    uiState.copy(
                        isCalendarLoading = false,
                        calendarFailure = result.reason,
                    )
            }
        }
    }

    private fun applyCalendarChoices(calendars: List<CalendarChoice>) {
        val selectedStillExists = calendars.any { it.id == uiState.selectedCalendarId }
        if (uiState.selectedCalendarId >= 0L && !selectedStillExists) {
            preferences.clearSelectedCalendar()
            uiState =
                uiState.copy(
                    selectedCalendarId = NO_CALENDAR_ID,
                    eventsByDay = emptyEventDays(),
                    loadedEventsMonday = null,
                    loadedEventsCalendarId = NO_CALENDAR_ID,
                    calendarFailure = null,
                )
        }
        uiState =
            uiState.copy(
                calendars = calendars,
                isCalendarListLoading = false,
                calendarListFailure = null,
            )
    }

    private fun restartMidnightTimer() {
        midnightJob?.cancel()
        midnightJob =
            viewModelScope.launch {
                while (isActive) {
                    val now = ZonedDateTime.now()
                    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                    delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L))
                    update()
                }
            }
    }

    private companion object {
        const val STATE_DISPLAYED_MONDAY = "displayed_monday"
        const val STATE_SELECTED_DAY = "selected_day"
        const val STATE_SETTINGS_VISIBLE = "settings_visible"
        const val CALENDAR_REFRESH_DEBOUNCE_MILLIS = 150L
        const val SIMULATION_TODAY_INDEX = 2
    }
}

internal fun scheduleTimeKey(index: Int): String = "$SCHEDULE_TIME_KEY_PREFIX$index"

internal fun validateScheduleMinutes(minutes: Int): Int? =
    minutes.takeIf { it in 0 until 24 * 60 }

internal fun referenceDateFor(
    monday: LocalDate,
    simulationMode: SimulationMode,
    actualToday: LocalDate = LocalDate.now(),
): LocalDate =
    if (simulationMode == SimulationMode.SIMULATION) monday.plusDays(2) else actualToday

internal fun dayIndexInWeek(
    date: LocalDate,
    monday: LocalDate,
): Int? {
    val dayOffset = java.time.temporal.ChronoUnit.DAYS.between(monday, date).toInt()
    return dayOffset.takeIf { it in 0 until WEEK_DAY_COUNT }
}
