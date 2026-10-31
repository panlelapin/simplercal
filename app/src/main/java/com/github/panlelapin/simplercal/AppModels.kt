package com.github.panlelapin.simplercal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import java.time.LocalDate

internal data class CalendarChoice(
    val id: Long,
    val name: String,
    val accountName: String,
    val ownerAccount: String,
)

internal data class WeekDay(
    val date: LocalDate,
    val abbreviation: String,
    val isWEorBankH: Boolean,
    val isHolidays: Boolean,
    val events: List<CalendarEvent> = emptyList(),
    val isEnabled: Boolean = true,
)

internal data class DayMarkers(val isHolidays: Boolean = false, val isBankHoliday: Boolean = false)

internal enum class DayTemporalState {
    PAST,
    CURRENT,
    FUTURE,
}

internal data class AppUiState(
    val title: AnnotatedString,
    val displayedMonday: LocalDate,
    val referenceDate: LocalDate,
    val highlightedDayIndex: Int?,
    val selectedDayIndex: Int,
    val selectionRequest: Int,
    val eventsByDay: List<List<CalendarEvent>>,
    val loadedEventsMonday: LocalDate?,
    val isCalendarLoading: Boolean,
    val calendarFailure: CalendarFailureReason?,
    val calendars: List<CalendarChoice>,
    val isCalendarListLoading: Boolean,
    val calendarListFailure: CalendarFailureReason?,
    val hasCalendarPermission: Boolean,
    val isSettingsVisible: Boolean,
    val accentTheme: AccentTheme,
    val themeMode: ThemeMode,
    val scrollMode: WeekScrollMode,
    val simulationMode: SimulationMode,
    val debug1OutlineColor: Debug1OutlineColor,
    val scheduleTimes: List<Int>,
    val calendarSources: Map<CalendarRole, CalendarSourceState> = emptyMap(),
    val allowedYear: Int = LocalDate.now().year,
    val referenceZone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    val loadedEventsZone: java.time.ZoneId? = null,
    val dayMarkers: Map<LocalDate, DayMarkers> = emptyMap(),
    val hasCalendarObserverFailure: Boolean = false,
)

internal data class MainScreenActions(
    val onSettings: () -> Unit,
    val onPreviousWeek: () -> Unit,
    val onNextWeek: () -> Unit,
    val onToday: () -> Unit,
)

internal data class WeekViewState(
    val weekMonday: LocalDate,
    val referenceDate: LocalDate,
    val eventsByDay: List<List<CalendarEvent>>,
    val highlightedDayIndex: Int?,
    val isDarkTheme: Boolean,
    val scrollMode: WeekScrollMode,
    val simulationMode: SimulationMode,
    val debug1OutlineColor: Debug1OutlineColor,
    val appBarBackground: Color,
    val requestedDayIndex: Int,
    val selectionRequest: Int,
    val onSelectionChanged: (Int) -> Unit,
    val dayMarkers: Map<LocalDate, DayMarkers> = emptyMap(),
    val allowedYear: Int? = null,
    val scheduleTimes: List<Int> = List(SCHEDULE_SLOT_COUNT) { UNSET_SCHEDULE_TIME },
    val referenceZone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
)

internal data class DayRowState(
    val dayIndex: Int,
    val highlightedDayIndex: Int?,
    val referenceDate: LocalDate,
    val isDarkTheme: Boolean,
    val day: WeekDay,
    val isSelected: Boolean,
    val isExpanded: Boolean,
    val isContentExpanded: Boolean,
    val dayLabelColumnWidth: Dp,
    val separatorColor: Color,
    val schedule: DaySchedule = DaySchedule.empty(),
)

internal data class WeekRowsState(
    val days: List<WeekDay>,
    val selectedDayIndex: Int,
    val contentExpandedDays: Set<Int>,
    val highlightedDayIndex: Int?,
    val referenceDate: LocalDate,
    val isDarkTheme: Boolean,
    val dayLabelColumnWidth: Dp,
    val separatorColor: Color,
    val schedules: List<DaySchedule>,
)

internal data class DayLabelState(
    val day: WeekDay,
    val isExpanded: Boolean,
    val width: Dp,
    val textColor: Color,
)

internal data class DayContentState(
    val isExpanded: Boolean,
    val isWEorBankH: Boolean,
    val isHolidays: Boolean,
    val schedule: DaySchedule,
    val eventCount: Int,
    val accentColor: Color,
    val textColor: Color,
)

internal data class DayAppearance(
    val isHighlighted: Boolean,
    val dayBackground: Color,
    val dayAccentColor: Color,
    val dayTextColor: Color,
    val hasTopBorder: Boolean,
    val combinedShape: androidx.compose.foundation.shape.RoundedCornerShape,
)

internal data class DayColors(
    val background: Color,
    val foreground: Color,
)

internal data class WeekGestureState(
    val scrollMode: WeekScrollMode,
    val geometry: WeekViewportGeometry,
    val rowBounds: () -> WeekRowBounds,
    val contextKey: Any,
    val touchSlopPx: Float,
    val selectedDayIndex: () -> Int,
    val animatedDayWeights: () -> List<Float>,
    val startDrag: () -> Unit,
    val dragToFocus: (Float) -> Unit,
    val endDrag: () -> Unit,
    val cancelDrag: () -> Unit,
    val enabledDayIndices: Set<Int> = (0 until WEEK_DAY_COUNT).toSet(),
    val minimumGroup: Int = 0,
    val maximumGroup: Int = WEEKEND_START_INDEX,
)

internal data class WeekDragState(
    val pointerId: PointerId,
    val initialY: Float,
    val isDragAllowed: Boolean,
)

internal data class SettingsActions(
    val onCalendarPermissionResult: (Boolean) -> Unit,
    val onCalendarSelected: (CalendarRole, Long) -> Unit,
    val onSourceSelected: (CalendarRole, CalendarSourceKind, String) -> Unit,
    val onSourceEnabled: (CalendarRole, Boolean) -> Unit,
    val onRefreshSource: (CalendarRole) -> Unit,
    val onRefreshCalendars: () -> Unit,
    val onScheduleTimeChange: (Int, Int) -> Unit,
    val onAccentThemeChange: (AccentTheme) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onScrollModeChange: (WeekScrollMode) -> Unit,
    val onSimulationModeChange: (SimulationMode) -> Unit,
    val onDebug1OutlineColorChange: (Debug1OutlineColor) -> Unit,
    val onBack: () -> Unit,
)
