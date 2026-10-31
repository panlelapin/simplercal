package com.github.panlelapin.simplercal

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AppViewModel(
    private val environment: AppEnvironment,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    constructor(application: Application, savedStateHandle: SavedStateHandle) :
        this(AppEnvironment.android(application), savedStateHandle)

    private val preferences = environment.preferences
    private val sources = environment.sources
    private val calendarRepository = environment.calendars
    private val icalEvents = IcalEventCache()
    private val icalRepository = environment.files
    private fun today(): LocalDate = LocalDate.now(environment.clock.withZone(environment.zone()))
    private var calendarRefreshJob: Job? = null
    private var calendarListJob: Job? = null
    private var midnightJob: Job? = null
    private val pendingImports = mutableSetOf<CalendarRole>()
    private val fileJobs = mutableMapOf<CalendarRole, Job>()
    private var refreshRevision = 0L
    private var isForeground = false
    private var providerPermissionDenied = false
    private val hasRestoredNavigation = savedStateHandle.contains(STATE_DISPLAYED_MONDAY)

    var uiState by androidx.compose.runtime.mutableStateOf(createInitialState())
        private set

    init { if (hasRestoredNavigation) update() else selectToday() }

    fun update() {
        val year = today().year
        val monday = uiState.displayedMonday.coerceIn(firstAllowedMonday(year), lastAllowedMonday(year))
        val selected = boundedSelection(monday, uiState.selectedDayIndex, year)
        val reference = referenceDateFor(monday, uiState.simulationMode, today())
        savedStateHandle[STATE_DISPLAYED_MONDAY] = monday.toEpochDay()
        savedStateHandle[STATE_SELECTED_DAY] = selected
        uiState = uiState.copy(
            title = weekTitle(monday), displayedMonday = monday, selectedDayIndex = selected,
            allowedYear = year, referenceDate = reference, highlightedDayIndex = dayIndexInWeek(reference, monday)?.takeIf { allowedDate(reference, year) },
            hasCalendarPermission = calendarRepository.hasPermission() && !providerPermissionDenied,
            selectionRequest = uiState.selectionRequest + if (selected != uiState.selectedDayIndex) 1 else 0,
        )
        combineSources()
        requestCalendarRefresh()
    }

    fun onResume() {
        isForeground = true
        providerPermissionDenied = false
        restartMidnightTimer()
        update()
        refreshCalendars()
        // Only a document choice returned while stopped may queue an explicit initial import.
        pendingImports.toList().forEach { role ->
            pendingImports.remove(role)
            importFileSource(role, allowDisabled = true)
        }
        // Otherwise file/HTTPS sources use ONLY their successful private snapshot here.
    }

    fun onStop() {
        isForeground = false
        refreshRevision += 1
        midnightJob?.cancel()
        calendarRefreshJob?.cancel()
        calendarListJob?.cancel()
        fileJobs.values.forEach { it.cancel() }
        uiState = uiState.copy(isCalendarLoading = false, isCalendarListLoading = false,
            calendarSources = uiState.calendarSources.mapValues { it.value.copy(refreshing = false) })
    }

    fun onCalendarObservationFailed(failed: Boolean) {
        uiState = uiState.copy(hasCalendarObserverFailure = failed)
    }

    fun onTimeContextChanged() {
        if (isForeground) { restartMidnightTimer(); update() }
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
        val today = today()
        val selected = if (uiState.simulationMode == SimulationMode.SIMULATION) 2 else today.dayOfWeek.value - 1
        uiState = uiState.copy(displayedMonday = currentWeekMonday(today), selectedDayIndex = selected,
            selectionRequest = uiState.selectionRequest + 1)
        update()
    }

    fun onDaySelected(dayIndex: Int) {
        if (dayIndex !in 0 until WEEK_DAY_COUNT || !allowedDate(uiState.displayedMonday.plusDays(dayIndex.toLong()), uiState.allowedYear)) return
        savedStateHandle[STATE_SELECTED_DAY] = dayIndex
        uiState = uiState.copy(selectedDayIndex = dayIndex)
    }

    fun onCalendarProviderChanged() { refreshCalendars(); update() }

    fun onCalendarPermissionResult(granted: Boolean) {
        providerPermissionDenied = !granted
        uiState = uiState.copy(hasCalendarPermission = granted && calendarRepository.hasPermission(),
            calendarSources = uiState.calendarSources.mapValues { (_, state) ->
                if (state.source.kind == CalendarSourceKind.ANDROID) state.copy(
                    failure = if (granted) null else CalendarFailureReason.PERMISSION_REVOKED) else state
            })
        calendarRefreshJob?.cancel()
        calendarListJob?.cancel()
        refreshCalendars()
        update() // Rebuild the week, including independent iCalendar sources even after denial.
    }

    fun selectCalendar(role: CalendarRole, calendarId: Long) {
        configureSource(role, CalendarSource(CalendarSourceKind.ANDROID,
            uiState.calendarSources.getValue(role).source.enabled, calendarId))
    }

    fun selectFileSource(role: CalendarRole, kind: CalendarSourceKind, location: String) {
        require(kind == CalendarSourceKind.LOCAL || kind == CalendarSourceKind.HTTPS)
        if (kind == CalendarSourceKind.HTTPS) {
            try { httpsAddress(location) } catch (_: Exception) {
                replaceSource(role) { it.copy(failure = CalendarFailureReason.INVALID_SOURCE) }
                return
            }
        }
        configureSource(role, CalendarSource(kind, uiState.calendarSources.getValue(role).source.enabled,
            location = location.trim()))
        if (isForeground) importFileSource(role, allowDisabled = true) else pendingImports.add(role)
    }

    private fun configureSource(role: CalendarRole, source: CalendarSource) {
        val previous = uiState.calendarSources.getValue(role).source
        fileJobs.remove(role)?.cancel()
        pendingImports.remove(role)
        sources.save(role, source)
        replaceSource(role) { CalendarSourceState(source) }
        if (previous.kind == CalendarSourceKind.LOCAL && previous.location.isNotBlank() &&
            uiState.calendarSources.values.none { it.source.kind == CalendarSourceKind.LOCAL && it.source.location == previous.location }) {
            environment.releaseDocument(previous.location)
        }
        update()
    }

    fun setSourceEnabled(role: CalendarRole, enabled: Boolean) {
        val source = uiState.calendarSources.getValue(role).source.copy(enabled = enabled)
        fileJobs.remove(role)?.cancel()
        sources.save(role, source)
        replaceSource(role) { it.copy(source = source, refreshing = false) }
        update()
    }

    fun refreshFileSource(role: CalendarRole) = importFileSource(role, allowDisabled = false)

    private fun importFileSource(role: CalendarRole, allowDisabled: Boolean) {
        val source = uiState.calendarSources.getValue(role).source
        if (!source.isFile || (!source.enabled && !allowDisabled) || !isForeground) return
        fileJobs.remove(role)?.cancel()
        replaceSource(role) { it.copy(refreshing = true, failure = null) }
        fileJobs[role] = viewModelScope.launch {
            try {
                val text = icalRepository.readSource(source)
                val year = uiState.allowedYear
                val zone = environment.zone()
                // Validate and expand the whole allowed range before replacing the last good copy.
                withContext(environment.io) {
                    parseIcalEvents(text, year, zone, environment.untitled)
                }
                val snapshot = IcalSnapshot(source.identity, text, environment.clock.millis())
                withContext(environment.io) { sources.saveSnapshot(role, snapshot) }
                if (uiState.calendarSources.getValue(role).source != source) return@launch
                replaceSource(role) { it.copy(refreshing = false, lastSuccessMillis = snapshot.successMillis, failure = null) }
                update()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (uiState.calendarSources.getValue(role).source == source) {
                    replaceSource(role) { it.copy(refreshing = false, failure = CalendarFailureReason.ICAL_UNAVAILABLE) }
                    combineSources()
                }
            }
        }
    }

    fun refreshCalendars() {
        calendarListJob?.cancel()
        if (!isForeground) return
        if (!calendarRepository.hasPermission() || providerPermissionDenied) {
            uiState = uiState.copy(hasCalendarPermission = false, calendars = emptyList(), isCalendarListLoading = false)
            return
        }
        uiState = uiState.copy(hasCalendarPermission = true, isCalendarListLoading = true, calendarListFailure = null)
        calendarListJob = viewModelScope.launch {
            when (val result = calendarRepository.loadCalendars()) {
                is CalendarChoicesResult.Success -> uiState = uiState.copy(calendars = result.calendars, isCalendarListLoading = false)
                CalendarChoicesResult.PermissionRequired -> onCalendarPermissionResult(false)
                is CalendarChoicesResult.Failure -> {
                    if (result.reason == CalendarFailureReason.PERMISSION_REVOKED) onCalendarPermissionResult(false)
                    uiState = uiState.copy(isCalendarListLoading = false, calendarListFailure = result.reason)
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
        val year = today().year
        val monday = (savedStateHandle.get<Long>(STATE_DISPLAYED_MONDAY)?.let { LocalDate.ofEpochDay(it) }
            ?: currentWeekMonday(today())).coerceIn(firstAllowedMonday(year), lastAllowedMonday(year))
        val simulation = preferences.simulationMode()
        val selected = boundedSelection(monday, savedStateHandle.get<Int>(STATE_SELECTED_DAY)
            ?: if (simulation == SimulationMode.SIMULATION) 2 else today().dayOfWeek.value - 1, year)
        val reference = referenceDateFor(monday, simulation, today())
        return AppUiState(title = weekTitle(monday), displayedMonday = monday, referenceDate = reference,
            highlightedDayIndex = dayIndexInWeek(reference, monday)?.takeIf { allowedDate(reference, year) }, selectedDayIndex = selected, selectionRequest = 1,
            eventsByDay = emptyEventDays(), loadedEventsMonday = null, isCalendarLoading = false, calendarFailure = null,
            calendars = emptyList(), isCalendarListLoading = false, calendarListFailure = null,
            hasCalendarPermission = calendarRepository.hasPermission(),
            isSettingsVisible = savedStateHandle[STATE_SETTINGS_VISIBLE] ?: false,
            accentTheme = preferences.accentTheme(), themeMode = preferences.themeMode(), scrollMode = preferences.scrollMode(),
            simulationMode = simulation, debug1OutlineColor = preferences.debug1OutlineColor(), scheduleTimes = preferences.scheduleTimes(),
            calendarSources = CalendarRole.entries.associateWith { CalendarSourceState(sources.source(it)) }, allowedYear = year)
    }

    private fun setDisplayedMonday(monday: LocalDate) {
        if (monday < firstAllowedMonday(uiState.allowedYear) || monday > lastAllowedMonday(uiState.allowedYear)) return
        uiState = uiState.copy(displayedMonday = monday)
        update()
    }

    private fun requestCalendarRefresh() {
        val revision = ++refreshRevision
        calendarRefreshJob?.cancel()
        if (!isForeground) return
        calendarRefreshJob = viewModelScope.launch {
            delay(150L)
            val monday = uiState.displayedMonday
            val zone = environment.zone()
            val year = uiState.allowedYear
            uiState = uiState.copy(isCalendarLoading = true)
            coroutineScope {
                CalendarRole.entries.map { role -> async {
                    val previous = uiState.calendarSources.getValue(role)
                    val source = previous.source
                    if (source.kind == CalendarSourceKind.NONE || (!source.enabled && !source.isFile)) return@async
                    var successMillis = previous.lastSuccessMillis
                    val result = if (source.kind == CalendarSourceKind.ANDROID) {
                        if (source.calendarId < 0) CalendarWeekResult.Failure(CalendarFailureReason.CALENDAR_UNAVAILABLE)
                        else if (uiState.hasCalendarPermission) calendarRepository.loadWeek(source.calendarId, monday, zone)
                        else CalendarWeekResult.PermissionRequired
                    } else {
                        try {
                            withContext(environment.io) {
                                val snapshot = sources.snapshot(role, source)
                                successMillis = snapshot?.successMillis
                                val events = snapshot?.takeIf { source.enabled }?.let { icalEvents.events(role, it, year, zone,
                                    environment.untitled) }.orEmpty()
                                CalendarWeekResult.Success(groupEventsByDay(events, monday, zone))
                            }
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { CalendarWeekResult.Failure(CalendarFailureReason.ICAL_UNAVAILABLE) }
                    }
                    if (revision != refreshRevision || monday != uiState.displayedMonday || zone != environment.zone() ||
                        source != uiState.calendarSources.getValue(role).source || year != uiState.allowedYear) return@async
                    when (result) {
                        is CalendarWeekResult.Success -> replaceSource(role) {
                            it.copy(days = result.eventsByDay, monday = monday, zone = zone, loadedIdentity = source.identity,
                                lastSuccessMillis = successMillis,
                                // Reading the old cache must not hide a failed explicit Refresh.
                                failure = if (source.isFile) it.failure else null)
                        }
                        CalendarWeekResult.PermissionRequired -> {
                            if (uiState.hasCalendarPermission) onCalendarPermissionResult(false)
                            replaceSource(role) { it.copy(failure = CalendarFailureReason.PERMISSION_REVOKED) }
                        }
                        is CalendarWeekResult.Failure -> {
                            if (result.reason == CalendarFailureReason.PERMISSION_REVOKED && uiState.hasCalendarPermission) onCalendarPermissionResult(false)
                            replaceSource(role) { it.copy(failure = result.reason) }
                        }
                    }
                    combineSources()
                } }.awaitAll()
            }
            if (revision == refreshRevision) uiState = uiState.copy(isCalendarLoading = false)
        }
    }

    private fun replaceSource(role: CalendarRole, transform: (CalendarSourceState) -> CalendarSourceState) {
        uiState = uiState.copy(calendarSources = uiState.calendarSources + (role to transform(uiState.calendarSources.getValue(role))))
    }

    private fun combineSources() {
        val monday = uiState.displayedMonday
        val zone = environment.zone()
        fun days(role: CalendarRole): List<List<CalendarEvent>> {
            val state = uiState.calendarSources.getValue(role)
            return if (state.source.enabled && state.loadedIdentity == state.source.identity && state.monday == monday &&
                state.zone == zone && (state.source.kind != CalendarSourceKind.ANDROID || uiState.hasCalendarPermission)) state.days
                else emptyEventDays()
        }
        val main = days(CalendarRole.MAIN)
        val banks = days(CalendarRole.BANK_HOLIDAYS)
        val holidays = days(CalendarRole.SCHOOL_HOLIDAYS)
        val markers = (0 until WEEK_DAY_COUNT).associate { index ->
            val date = monday.plusDays(index.toLong())
            date to DayMarkers(allowedDate(date, uiState.allowedYear) && holidays[index].isNotEmpty(),
                allowedDate(date, uiState.allowedYear) && banks[index].isNotEmpty())
        }
        uiState = uiState.copy(eventsByDay = main.mapIndexed { i, events ->
            if (allowedDate(monday.plusDays(i.toLong()), uiState.allowedYear)) events else emptyList()
        }, dayMarkers = markers, loadedEventsMonday = monday, loadedEventsZone = zone,
            calendarFailure = uiState.calendarSources.values.firstOrNull { it.source.enabled && it.source.kind != CalendarSourceKind.NONE && it.failure != null }?.failure)
    }

    private fun restartMidnightTimer() {
        midnightJob?.cancel()
        midnightJob = viewModelScope.launch {
            while (isActive) {
                val now = ZonedDateTime.now(environment.clock.withZone(environment.zone()))
                delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1000L))
                update()
            }
        }
    }

    private companion object {
        const val STATE_DISPLAYED_MONDAY = "displayed_monday"
        const val STATE_SELECTED_DAY = "selected_day"
        const val STATE_SETTINGS_VISIBLE = "settings_visible"
    }
}

private fun boundedSelection(monday: LocalDate, selected: Int, year: Int): Int =
    (0 until WEEK_DAY_COUNT).filter { allowedDate(monday.plusDays(it.toLong()), year) }
        .minBy { kotlin.math.abs(it - selected) }

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
