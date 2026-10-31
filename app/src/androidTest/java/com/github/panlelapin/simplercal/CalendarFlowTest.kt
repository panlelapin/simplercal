package com.github.panlelapin.simplercal

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Full app composition with real persisted settings and deterministic external data. */
class CalendarFlowTest {
    @get:Rule val rule = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "flow_${UUID.randomUUID()}_"
    private val directory = File(base.cacheDir, prefix).apply { mkdirs() }
    private val context = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences(prefix + name, mode)
        override fun getFilesDir() = directory
    }
    private val reads = AtomicInteger()
    private lateinit var model: AppViewModel

    @After fun cleanup() {
        if (::model.isInitialized) rule.runOnUiThread { model.onStop() }
        listOf(PREFERENCES_NAME, "calendar_device").forEach { base.deleteSharedPreferences(prefix + it) }
        directory.deleteRecursively()
    }

    private fun launch(restoration: StateRestorationTester? = null) {
        val gateway = object : CalendarGateway {
            override fun hasPermission() = false
            override suspend fun loadCalendars(): CalendarChoicesResult = error("File flow queried Android calendars")
            override suspend fun loadWeek(calendarId: Long, monday: LocalDate, zoneId: ZoneId): CalendarWeekResult = error("File flow queried Android events")
        }
        rule.runOnUiThread {
            model = AppViewModel(AppEnvironment(AppPreferences(context), CalendarSourcePreferences(context), gateway,
                FileCalendarReader {
                    reads.incrementAndGet()
                    "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Tests//EN\r\n" + (0..11).joinToString("") { index ->
                        "BEGIN:VEVENT\r\nUID:flow-$index\r\nDTSTAMP:20260101T000000Z\r\nDTSTART;VALUE=DATE:20260803\r\nSUMMARY:Event $index with its complete long title\r\nEND:VEVENT\r\n"
                    } + "END:VCALENDAR\r\n"
                }, clock = Clock.fixed(Instant.parse("2026-08-03T12:00:00Z"), ZoneOffset.UTC),
                zone = { ZoneOffset.UTC }, untitled = "Untitled"), SavedStateHandle())
        }
        if (restoration == null) rule.setContent { SimplerCalApp(model) }
        else restoration.setContent { SimplerCalApp(model) }
    }

    private fun chooseHttps() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onAllNodesWithText("Set HTTPS URL")[0].performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("https://example.invalid/calendar.ics")
        rule.onNodeWithText("OK").performClick()
        rule.waitUntil(10_000) { model.uiState.calendarSources.getValue(CalendarRole.MAIN).lastSuccessMillis != null }
        rule.waitUntil(10_000) { model.uiState.eventsByDay[0].size == 12 }
    }

    @Test fun httpsImportWithoutCalendarPermissionShowsTheFullOverflowList() {
        launch(); chooseHttps()
        rule.runOnIdle { assertFalse(model.uiState.hasCalendarPermission); assertEquals(1, reads.get()) }
        rule.onNodeWithContentDescription("Back").performClick()
        val monday = rule.onNodeWithContentDescription("Monday, August 3, 2026, 12 events")
        if (rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()) monday.performScrollTo()
        val actions = monday.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { assertTrue(actions.single().action()) }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(11)
        rule.onNodeWithText("Event 11 with its complete long title").assertIsDisplayed()
        rule.onAllNodesWithText("All day")[0].assertExists()
        rule.onNodeWithText("Vacation").assertDoesNotExist()
        rule.onNodeWithText("Close").performClick()
        rule.runOnIdle { assertEquals(1, reads.get()) }
    }

    @Test fun touchingTheOverflowButtonOpensTheCompleteEventList() {
        launch(); chooseHttps()
        rule.onNodeWithContentDescription("Back").performClick()
        val monday = rule.onNodeWithContentDescription("Monday, August 3, 2026, 12 events")
        if (rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()) monday.performScrollTo()
        rule.onNodeWithTag("all-events", useUnmergedTree = true).performTouchInput { click() }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(11)
        rule.onNodeWithText("Event 11 with its complete long title").assertIsDisplayed()
    }

    @Test fun settingsReplaceWeekAndDisablePreservesSourceWithoutAnotherImport() {
        launch(); chooseHttps()
        rule.onNodeWithContentDescription("Monday, August 3, 2026, 12 events").assertDoesNotExist()
        rule.onNodeWithContentDescription("Main events").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(10_000) { model.uiState.eventsByDay.all { it.isEmpty() } }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("HTTPS iCalendar: https://example.invalid/calendar.ics").assertExists()
        rule.onNodeWithContentDescription("Main events").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(10_000) { model.uiState.eventsByDay[0].size == 12 }
        rule.runOnIdle { assertEquals(1, reads.get()) }
    }

    @Test fun longPressHasNoDetailsOrManualMarkerAction() {
        launch()
        val monday = rule.onNodeWithContentDescription("Monday, August 3, 2026, 0 events")
        if (rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()) monday.performScrollTo()
        monday.performTouchInput { longClick() }
        monday.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        rule.onNodeWithText("Close").assertDoesNotExist()
        rule.onNodeWithText("Vacation").assertDoesNotExist()
    }

    @Test fun httpsDialogDraftSurvivesSavedCompositionRestoration() {
        val restoration = StateRestorationTester(rule)
        launch(restoration)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onAllNodesWithText("Set HTTPS URL")[0].performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("https://example.invalid/draft.ics")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNode(hasSetTextAction()).assertTextContains("https://example.invalid/draft.ics")
        rule.runOnIdle { assertEquals(0, reads.get()) }
    }

}
