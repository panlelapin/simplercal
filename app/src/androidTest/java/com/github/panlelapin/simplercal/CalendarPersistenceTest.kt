package com.github.panlelapin.simplercal

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Namespaced storage so the tests neither read nor clear normal app preferences/cache. */
class CalendarPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "test_${UUID.randomUUID()}_"
    private val directory = File(base.cacheDir, prefix).apply { mkdirs() }
    private val context = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            base.getSharedPreferences(prefix + name, mode)
        override fun getFilesDir() = directory
    }

    @After fun cleanup() {
        listOf(PREFERENCES_NAME, "calendar_device").forEach { base.deleteSharedPreferences(prefix + it) }
        directory.deleteRecursively()
    }

    @Test fun legacySelectionMigratesOnThisDeviceWithoutOverwritingANewerDeviceSelection() {
        val portable = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        portable.edit().putLong(SELECTED_CALENDAR_KEY, 7).commit()
        val store = CalendarSourcePreferences(context)
        assertEquals(7L, store.source(CalendarRole.MAIN).calendarId)
        assertEquals(CalendarSourceKind.ANDROID, store.source(CalendarRole.MAIN).kind)
        assertFalse(portable.contains(SELECTED_CALENDAR_KEY))
        portable.edit().putLong(SELECTED_CALENDAR_KEY, 99).commit()
        assertEquals(7L, CalendarSourcePreferences(context).source(CalendarRole.MAIN).calendarId)
    }

    @Test fun roleSelectionsRoundTripWithoutBackingUpProviderIdsOrDocumentUris() {
        val store = CalendarSourcePreferences(context)
        val configurations = mapOf(
            CalendarRole.MAIN to CalendarSource(CalendarSourceKind.ANDROID, calendarId = 12),
            CalendarRole.BANK_HOLIDAYS to CalendarSource(CalendarSourceKind.LOCAL, location = "content://test/bank.ics"),
            CalendarRole.SCHOOL_HOLIDAYS to CalendarSource(CalendarSourceKind.HTTPS, enabled = false, location = "https://example.invalid/school.ics"),
        )
        configurations.forEach { (role, source) -> store.save(role, source) }
        val reloaded = CalendarSourcePreferences(context)
        configurations.forEach { (role, source) -> assertEquals(source, reloaded.source(role)) }
        val portable = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all
        assertFalse(portable.keys.any { it.endsWith("_id") || it.endsWith("_uri") })
        assertFalse(portable.values.contains("content://test/bank.ics"))
    }

    @Test fun snapshotsPersistIndependentlyAndRejectAnotherSourceIdentity() = runBlocking {
        val store = CalendarSourcePreferences(context)
        val source = CalendarSource(CalendarSourceKind.HTTPS, location = "https://example.invalid/a.ics")
        val snapshot = IcalSnapshot(source.identity, "Controlled text", 123456L)
        store.saveSnapshot(CalendarRole.MAIN, snapshot)
        assertEquals(snapshot, CalendarSourcePreferences(context).snapshot(CalendarRole.MAIN, source))
        assertNull(store.snapshot(CalendarRole.BANK_HOLIDAYS, source))
        assertNull(store.snapshot(CalendarRole.MAIN, source.copy(location = "https://example.invalid/b.ics")))
        assertEquals(snapshot, store.snapshot(CalendarRole.MAIN, source.copy(enabled = false)))
    }

    @Test fun corruptSnapshotIsAnErrorAndDoesNotContaminateOtherRoles() = runBlocking {
        val store = CalendarSourcePreferences(context)
        val source = CalendarSource(CalendarSourceKind.HTTPS, location = "https://example.invalid/a.ics")
        val snapshot = IcalSnapshot(source.identity, "Controlled text", 1)
        store.saveSnapshot(CalendarRole.MAIN, snapshot)
        store.saveSnapshot(CalendarRole.BANK_HOLIDAYS, snapshot)
        File(directory, "ical_sources/MAIN.json").writeText("{broken")
        try { store.snapshot(CalendarRole.MAIN, source); fail("Corrupt snapshot accepted") }
        catch (_: org.json.JSONException) { /* Explicit failure, not an empty successful import. */ }
        assertEquals(snapshot, store.snapshot(CalendarRole.BANK_HOLIDAYS, source))
    }

    @Test fun invalidStoredSettingsUseDefaultsAndInvalidSchedulesAreRemoved() {
        val portable = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        portable.edit().putString(THEME_MODE_KEY, "unknown").putString(SCROLL_MODE_KEY, "unknown")
            .putString(SIMULATION_MODE_KEY, "unknown").putString(DAY_ACCENT_COLOR_KEY, "unknown")
            .putString("source_MAIN_kind", "unknown").putInt(scheduleTimeKey(0), -2)
            .putInt(scheduleTimeKey(1), 1440).putInt(scheduleTimeKey(2), 1439).commit()
        val settings = AppPreferences(context)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode())
        assertEquals(WeekScrollMode.DISCRETE, settings.scrollMode())
        assertEquals(SimulationMode.OFF, settings.simulationMode())
        assertEquals(AccentTheme.SYSTEM, settings.accentTheme())
        assertEquals(CalendarSourceKind.NONE, CalendarSourcePreferences(context).source(CalendarRole.MAIN).kind)
        assertEquals(listOf(-1, -1, 1439, -1, -1), settings.scheduleTimes())
        assertFalse(portable.contains(scheduleTimeKey(0)))
        assertFalse(settings.setScheduleTime(-1, 30))
        assertFalse(settings.setScheduleTime(5, 30))
        assertFalse(settings.setScheduleTime(0, 1440))
    }

    @Test fun restoreCleanupClearsAllDeviceReferencesAndPreservesPortableSettings() {
        val portable = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        portable.edit().putLong(SELECTED_CALENDAR_KEY, 8).putString(THEME_MODE_KEY, "dark").commit()
        context.getSharedPreferences("calendar_device", Context.MODE_PRIVATE).edit()
            .putLong("source_MAIN_id", 7).putString("source_BANK_HOLIDAYS_uri", "content://test/a.ics").commit()
        clearRestoredCalendarSelections(context)
        assertTrue(context.getSharedPreferences("calendar_device", Context.MODE_PRIVATE).all.isEmpty())
        assertFalse(portable.contains(SELECTED_CALENDAR_KEY))
        assertEquals("dark", portable.getString(THEME_MODE_KEY, null))
    }
}
