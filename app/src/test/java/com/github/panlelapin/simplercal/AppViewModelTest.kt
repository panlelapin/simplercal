package com.github.panlelapin.simplercal

import androidx.lifecycle.SavedStateHandle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = MemorySettings()
    private val sources = MemorySources()
    private val calendars = ControlledCalendars()
    private val clock = MutableClock(Instant.parse("2026-08-03T12:00:00Z"))
    private var zone = ZoneId.of("UTC")
    private var fileReads = 0
    private var fileRead: suspend (CalendarSource) -> String = { ical(datedEvent()) }
    private lateinit var model: AppViewModel

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        if (::model.isInitialized) model.onStop()
        Dispatchers.resetMain()
    }

    private fun create(handle: SavedStateHandle = SavedStateHandle()): AppViewModel {
        model = AppViewModel(AppEnvironment(settings, sources, calendars,
            FileCalendarReader { fileReads++; fileRead(it) }, clock, { zone }, dispatcher, "Untitled"), handle)
        return model
    }

    private fun withModel(test: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { test() } finally {
            // runTest drains foreground work before JUnit @After; stop the recurring
            // midnight timer first, otherwise virtual time can advance indefinitely.
            if (::model.isInitialized) model.onStop()
            runCurrent()
        }
    }

    private fun TestScope.reload() { advanceTimeBy(151); runCurrent() }
    private fun mainAndroid(id: Long = 1) {
        sources.save(CalendarRole.MAIN, CalendarSource(CalendarSourceKind.ANDROID, calendarId = id))
    }
    private fun cached(role: CalendarRole, title: String = "Cached") {
        val source = CalendarSource(CalendarSourceKind.HTTPS, location = "https://example.invalid/${role.name}.ics")
        sources.save(role, source)
        sources.snapshots[role] = IcalSnapshot(source.identity, ical(datedEvent(title = title)), 123L)
    }

    @Test fun resumeNavigationAndRecreationReadOnlyTheSnapshot() = withModel {
        cached(CalendarRole.MAIN)
        create().onResume(); reload()
        assertEquals("Cached", model.uiState.eventsByDay[0].single().title)
        model.showNextWeek(); reload()
        model.onStop(); model.onResume(); reload()
        model.onStop(); create().onResume(); reload()
        assertEquals(0, fileReads)
        assertEquals(123L, model.uiState.calendarSources.getValue(CalendarRole.MAIN).lastSuccessMillis)
    }

    @Test fun fiveScheduleTimesUpdateTheUiAndSurviveRecreationWithoutImportingCalendars() = withModel {
        create()
        val times = listOf(480, 480, 720, 840, 1080)
        times.forEachIndexed { index, minutes -> model.setScheduleTime(index, minutes) }
        assertEquals(times, model.uiState.scheduleTimes)
        create()
        assertEquals(times, model.uiState.scheduleTimes)
        assertEquals(0, fileReads)
    }

    @Test fun threeRolesContributeTitlesAndMarkersIndependently() = withModel {
        CalendarRole.entries.forEach { cached(it, it.name) }
        create().onResume(); reload()
        val monday = model.uiState.displayedMonday
        assertEquals(listOf("MAIN"), model.uiState.eventsByDay[0].map { it.title })
        assertEquals(DayMarkers(true, true), model.uiState.dayMarkers[monday])
        model.setSourceEnabled(CalendarRole.BANK_HOLIDAYS, false)
        assertEquals(DayMarkers(true, false), model.uiState.dayMarkers[monday])
        model.setSourceEnabled(CalendarRole.MAIN, false)
        assertTrue(model.uiState.eventsByDay.all { it.isEmpty() })
        assertTrue(model.uiState.dayMarkers.getValue(monday).isHolidays)
        model.setSourceEnabled(CalendarRole.BANK_HOLIDAYS, true); reload()
        assertTrue(model.uiState.dayMarkers.getValue(monday).isBankHoliday)
        assertEquals(0, fileReads)
    }

    @Test fun manualRefreshOnlyReadsItsOwnSourceAndPersistsSuccessTime() = withModel {
        CalendarRole.entries.forEach { cached(it) }
        create().onResume(); reload()
        fileRead = { ical(datedEvent(title = "New bank marker")) }
        model.refreshFileSource(CalendarRole.BANK_HOLIDAYS); runCurrent(); reload()
        assertEquals(1, fileReads)
        assertEquals(1, sources.saves)
        assertEquals(clock.millis(), model.uiState.calendarSources.getValue(CalendarRole.BANK_HOLIDAYS).lastSuccessMillis)
        assertEquals(123L, model.uiState.calendarSources.getValue(CalendarRole.MAIN).lastSuccessMillis)
    }

    @Test fun malformedRefreshKeepsGoodDataAndTimestampAndVisibleFailure() = withModel {
        cached(CalendarRole.MAIN)
        create().onResume(); reload()
        fileRead = { "not an iCalendar" }
        model.refreshFileSource(CalendarRole.MAIN); runCurrent(); reload()
        assertEquals("Cached", model.uiState.eventsByDay[0].single().title)
        assertEquals(123L, model.uiState.calendarSources.getValue(CalendarRole.MAIN).lastSuccessMillis)
        assertEquals(CalendarFailureReason.ICAL_UNAVAILABLE, model.uiState.calendarFailure)
        assertEquals(0, sources.saves)
    }

    @Test fun failedSnapshotWriteDoesNotPublishAnUnpersistedSuccess() = withModel {
        cached(CalendarRole.MAIN)
        create().onResume(); reload(); sources.failSave = true
        model.refreshFileSource(CalendarRole.MAIN); runCurrent(); reload()
        assertEquals("Cached", model.uiState.eventsByDay[0].single().title)
        assertEquals(123L, model.uiState.calendarSources.getValue(CalendarRole.MAIN).lastSuccessMillis)
        assertEquals(CalendarFailureReason.ICAL_UNAVAILABLE, model.uiState.calendarFailure)
    }

    @Test fun changingSourceCannotReuseThePreviousSnapshot() = withModel {
        cached(CalendarRole.MAIN)
        create().onResume(); reload()
        fileRead = { throw java.io.IOException("Controlled failure") }
        model.selectFileSource(CalendarRole.MAIN, CalendarSourceKind.HTTPS, "https://example.invalid/new.ics")
        assertTrue(model.uiState.eventsByDay.all { it.isEmpty() })
        runCurrent(); reload()
        assertTrue(model.uiState.eventsByDay.all { it.isEmpty() })
        assertNull(model.uiState.calendarSources.getValue(CalendarRole.MAIN).lastSuccessMillis)
    }

    @Test fun disablingAFileSourcePreventsRefreshButKeepsItsTimestamp() = withModel {
        cached(CalendarRole.MAIN)
        create().onResume(); reload()
        model.setSourceEnabled(CalendarRole.MAIN, false)
        model.refreshFileSource(CalendarRole.MAIN); reload()
        assertEquals(0, fileReads)
        assertEquals(123L, model.uiState.calendarSources.getValue(CalendarRole.MAIN).lastSuccessMillis)
    }

    @Test fun sourceChoiceWhileStoppedQueuesExactlyOneInitialImport() = withModel {
        create()
        model.selectFileSource(CalendarRole.MAIN, CalendarSourceKind.HTTPS, "https://example.invalid/new.ics")
        runCurrent(); assertEquals(0, fileReads)
        model.onResume(); runCurrent(); reload()
        assertEquals(1, fileReads)
        model.onStop(); model.onResume(); reload()
        assertEquals(1, fileReads)
    }

    @Test fun replacementAndStopCancelPendingReads() = withModel {
        mainAndroid()
        calendars.read = { CompletableDeferred<CalendarWeekResult>().await() }
        create().onResume(); reload()
        model.showNextWeek(); reload()
        assertEquals(1, calendars.cancellations)
        model.onStop(); runCurrent()
        assertEquals(2, calendars.cancellations)
        assertFalse(model.uiState.isCalendarLoading)
    }

    @Test fun anUncooperativeOldWeekResponseCannotReplaceTheNewWeek() = withModel {
        mainAndroid()
        val pending = mutableListOf<Pair<ControlledCalendars.Request, Continuation<CalendarWeekResult>>>()
        calendars.read = { request -> suspendCoroutine { pending += request to it } }
        create().onResume(); reload()
        model.showNextWeek(); reload()
        val old = pending[0]; val current = pending[1]
        current.second.resume(weekWithTitle("Current", current.first.monday)); runCurrent()
        old.second.resume(weekWithTitle("Stale", old.first.monday)); runCurrent()
        assertEquals("Current", model.uiState.eventsByDay[0].single().title)
        assertEquals(current.first.monday, model.uiState.loadedEventsMonday)
    }

    @Test fun anUncooperativeOldCalendarResponseCannotReplaceTheNewSelection() = withModel {
        mainAndroid()
        val pending = mutableListOf<Continuation<CalendarWeekResult>>()
        calendars.read = { suspendCoroutine { pending += it } }
        create().onResume(); reload()
        model.selectCalendar(CalendarRole.MAIN, 2); reload()
        pending[1].resume(weekWithTitle("Calendar 2", model.uiState.displayedMonday)); runCurrent()
        pending[0].resume(weekWithTitle("Calendar 1", model.uiState.displayedMonday)); runCurrent()
        assertEquals("Calendar 2", model.uiState.eventsByDay[0].single().title)
    }

    @Test fun transientFailurePreservesDataOnlyInTheSameWeek() = withModel {
        mainAndroid()
        calendars.read = { weekWithTitle("Good", it.monday) }
        create().onResume(); reload()
        calendars.read = { CalendarWeekResult.Failure(CalendarFailureReason.PROVIDER_UNAVAILABLE) }
        model.update(); reload()
        assertEquals("Good", model.uiState.eventsByDay[0].single().title)
        model.showNextWeek()
        assertTrue(model.uiState.eventsByDay.all { it.isEmpty() })
        reload(); assertTrue(model.uiState.eventsByDay.all { it.isEmpty() })
    }

    @Test fun permissionRevocationCancelsAndroidReadsAndRetainsIndependentFiles() = withModel {
        mainAndroid()
        sources.save(CalendarRole.BANK_HOLIDAYS, CalendarSource(CalendarSourceKind.ANDROID, calendarId = 2))
        cached(CalendarRole.SCHOOL_HOLIDAYS)
        calendars.read = { CompletableDeferred<CalendarWeekResult>().await() }
        create().onResume(); reload()
        calendars.permission = false
        model.onCalendarPermissionResult(false); reload()
        assertEquals(2, calendars.cancellations)
        assertFalse(model.uiState.hasCalendarPermission)
        assertFalse(model.uiState.isCalendarLoading)
        assertTrue(model.uiState.dayMarkers.getValue(model.uiState.displayedMonday).isHolidays)
        assertEquals(0, fileReads)
    }

    @Test fun stopCancelsAnExplicitFileImportWithoutOverwritingItsCache() = withModel {
        cached(CalendarRole.MAIN)
        var cancelled = false
        fileRead = { try { CompletableDeferred<String>().await() } finally { cancelled = true } }
        create().onResume(); reload()
        model.refreshFileSource(CalendarRole.MAIN); runCurrent()
        model.onStop(); runCurrent()
        assertTrue(cancelled)
        assertEquals(0, sources.saves)
        assertFalse(model.uiState.calendarSources.getValue(CalendarRole.MAIN).refreshing)
        assertEquals(123L, model.uiState.calendarSources.getValue(CalendarRole.MAIN).lastSuccessMillis)
    }

    @Test fun androidPermissionRevocationDoesNotCancelAnIndependentHttpsRefresh() = withModel {
        mainAndroid()
        cached(CalendarRole.SCHOOL_HOLIDAYS)
        val download = CompletableDeferred<String>()
        fileRead = { download.await() }
        create().onResume(); reload()
        model.refreshFileSource(CalendarRole.SCHOOL_HOLIDAYS); runCurrent()
        calendars.permission = false
        model.onCalendarPermissionResult(false); reload()
        assertTrue(model.uiState.calendarSources.getValue(CalendarRole.SCHOOL_HOLIDAYS).refreshing)
        download.complete(ical(datedEvent(title = "Updated holidays")))
        runCurrent(); reload()
        val source = model.uiState.calendarSources.getValue(CalendarRole.SCHOOL_HOLIDAYS)
        assertFalse(model.uiState.hasCalendarPermission)
        assertFalse(source.refreshing)
        assertNull(source.failure)
        assertEquals(clock.millis(), source.lastSuccessMillis)
        assertEquals(1, fileReads)
    }

    @Test fun aZoneChangeOnTheSameDateInvalidatesPreviousGrouping() = withModel {
        mainAndroid()
        calendars.read = { weekWithTitle(it.zone.id, it.monday) }
        create().onResume(); reload()
        val date = model.uiState.referenceDate
        zone = ZoneId.of("Europe/Paris"); model.onTimeContextChanged()
        assertTrue(model.uiState.eventsByDay.all { it.isEmpty() })
        reload()
        assertEquals(date, model.uiState.referenceDate)
        assertEquals(zone, model.uiState.referenceZone)
        assertEquals(zone, model.uiState.loadedEventsZone)
        assertEquals("Europe/Paris", model.uiState.eventsByDay[0].single().title)
    }

    @Test fun permissionCanBeGrantedAgainWithoutLosingTheSelectedAndroidCalendar() = withModel {
        mainAndroid(7)
        calendars.permission = false
        calendars.read = { weekWithTitle("Restored access", it.monday) }
        create().onResume(); reload()
        assertTrue(calendars.requests.isEmpty())
        assertFalse(model.uiState.hasCalendarPermission)
        calendars.permission = true
        model.onCalendarPermissionResult(true); reload()
        assertEquals(7L, sources.source(CalendarRole.MAIN).calendarId)
        assertEquals("Restored access", model.uiState.eventsByDay[0].single().title)
    }

    @Test fun oldRevisionCannotWinEvenWhenCalendarMondayAndZoneAreIdentical() = withModel {
        mainAndroid()
        val pending = mutableListOf<Continuation<CalendarWeekResult>>()
        calendars.read = { suspendCoroutine { pending += it } }
        create().onResume(); reload()
        model.onCalendarProviderChanged(); reload()
        pending[1].resume(weekWithTitle("New revision", model.uiState.displayedMonday)); runCurrent()
        pending[0].resume(weekWithTitle("Old revision", model.uiState.displayedMonday)); runCurrent()
        assertEquals("New revision", model.uiState.eventsByDay[0].single().title)
    }

    @Test fun nextMidnightUsesLocalDaysOf23And25Hours() = withModel {
        zone = ZoneId.of("Europe/Paris")
        listOf(LocalDate.of(2026, 3, 29) to 23L, LocalDate.of(2026, 10, 25) to 25L).forEach { (date, hours) ->
            clock.instantValue = date.atStartOfDay(zone).toInstant()
            create().onResume(); runCurrent()
            advanceTimeBy(hours * 3_600_000L - 1); runCurrent()
            assertEquals(date, model.uiState.referenceDate)
            clock.instantValue = date.plusDays(1).atStartOfDay(zone).toInstant()
            advanceTimeBy(2); runCurrent()
            assertEquals(date.plusDays(1), model.uiState.referenceDate)
            model.onStop(); runCurrent()
        }
    }

    @Test fun foregroundMidnightRollsTheAllowedYearsAndStopSuspendsTheTimer() = withModel {
        clock.instantValue = Instant.parse("2026-12-31T23:59:59Z")
        create().onResume(); runCurrent()
        clock.instantValue = Instant.parse("2027-01-01T00:00:00Z")
        advanceTimeBy(1001); runCurrent()
        assertEquals(2027, model.uiState.allowedYear)
        assertEquals(LocalDate.of(2027, 1, 1), model.uiState.referenceDate)
        model.onStop()
        clock.instantValue = Instant.parse("2028-01-01T00:00:00Z")
        advanceTimeBy(86_400_001); runCurrent()
        assertEquals(2027, model.uiState.allowedYear)
        model.onResume(); runCurrent()
        assertEquals(2028, model.uiState.allowedYear)
    }

    @Test fun restorationKeepsSundaySettingsAndBoundsNavigationAndSelection() = withModel {
        val first = firstAllowedMonday(2026)
        val handle = SavedStateHandle(mapOf("displayed_monday" to first.toEpochDay(), "selected_day" to 6,
            "settings_visible" to true))
        create(handle)
        assertEquals(6, model.uiState.selectedDayIndex)
        assertTrue(model.uiState.isSettingsVisible)
        model.showPreviousWeek(); assertEquals(first, model.uiState.displayedMonday)
        model.onDaySelected(0); assertEquals(6, model.uiState.selectedDayIndex)
        model.onStop(); create(handle)
        assertEquals(6, model.uiState.selectedDayIndex)
        assertTrue(model.uiState.isSettingsVisible)
    }

    @Test fun lastWeekCannotAdvanceAndRestoredSelectionCannotUseTheFollowingYear() = withModel {
        val last = lastAllowedMonday(2026)
        create(SavedStateHandle(mapOf<String, Any>("displayed_monday" to last.toEpochDay(), "selected_day" to 6)))
        assertEquals(4, model.uiState.selectedDayIndex)
        model.showNextWeek()
        assertEquals(last, model.uiState.displayedMonday)
        model.onDaySelected(5)
        assertEquals(4, model.uiState.selectedDayIndex)
    }

    @Test fun simulationTodayUsesWednesdayWithoutChangingRealSnapshots() = withModel {
        cached(CalendarRole.MAIN)
        val original = sources.snapshots.toMap()
        create().onResume(); reload()
        model.setSimulationMode(SimulationMode.SIMULATION)
        model.showNextWeek(); model.selectToday()
        assertEquals(2, model.uiState.selectedDayIndex)
        assertEquals(currentWeekMonday(LocalDate.of(2026, 8, 3)), model.uiState.displayedMonday)
        assertEquals(original, sources.snapshots)
    }
}
