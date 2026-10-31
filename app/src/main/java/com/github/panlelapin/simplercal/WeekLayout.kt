package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal fun dayRowShape(dayIndex: Int): RoundedCornerShape =
    when (dayIndex) {
        WEEKEND_START_INDEX -> {
            RoundedCornerShape(
                topStart = DAY_SUBCONTAINER_CORNER_RADIUS,
                topEnd = DAY_SUBCONTAINER_CORNER_RADIUS,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            )
        }

        WEEKEND_START_INDEX + 1 -> {
            RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = DAY_SUBCONTAINER_CORNER_RADIUS,
                bottomEnd = DAY_SUBCONTAINER_CORNER_RADIUS,
            )
        }

        else -> {
            DAY_SUBCONTAINER_SHAPE
        }
    }

internal fun currentWeekMonday(today: LocalDate = LocalDate.now()): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun weekTitle(monday: LocalDate): AnnotatedString {
    val weekNumber = monday.get(WeekFields.ISO.weekOfWeekBasedYear())
    val month =
        monday.month
            .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            .take(3)
            .uppercase(Locale.ROOT)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 12.sp)) { append("S") }
        append(weekNumber.toString())
        withStyle(SpanStyle(fontSize = 12.sp)) { append(" - ") }
        append(monday.dayOfMonth.toString())
        withStyle(SpanStyle(fontSize = 12.sp)) { append(month) }
    }
}

@Composable
internal fun WeekRows(
    state: WeekRowsState,
    onSelectDay: (Int) -> Unit,
    onShowDetails: (LocalDate) -> Unit,
) {
    val expanded = expandedDayIndices(state.selectedDayIndex)
    state.days.forEachIndexed { index, day ->
        key(day.date) {
            DayRow(
                state =
                    DayRowState(
                        dayIndex = index,
                        highlightedDayIndex = state.highlightedDayIndex,
                        referenceDate = state.referenceDate,
                        isDarkTheme = state.isDarkTheme,
                        day = day,
                        isSelected = index == state.selectedDayIndex,
                        isExpanded = index in expanded,
                        isContentExpanded = index in state.contentExpandedDays,
                        dayLabelColumnWidth = state.dayLabelColumnWidth,
                        separatorColor = state.separatorColor,
                        schedule = state.schedules[index],
                    ),
                onClick = { onSelectDay(index) },
                onShowDetails = { onShowDetails(day.date) },
            )
        }
    }
}

@Composable
internal fun WeekView(state: WeekViewState) {
    val days =
        remember(
            state.weekMonday,
            state.referenceDate,
            state.simulationMode,
            state.eventsByDay,
            state.dayMarkers,
            state.allowedYear,
        ) {
            currentWeek(
                monday = state.weekMonday,
                simulationMode = state.simulationMode,
                eventsByDay = state.eventsByDay,
                markers = state.dayMarkers,
                allowedYear = state.allowedYear,
            )
        }
    val enabled = remember(days) { days.indices.filter { days[it].isEnabled }.toSet() }
    val scheduleInput = remember(days, state.scheduleTimes, state.referenceZone) {
        WeekScheduleInput(state.weekMonday, days.map { it.events }, state.scheduleTimes.toList(), state.referenceZone)
    }
    val projection by produceState<WeekScheduleProjection?>(null, scheduleInput) {
        value = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            val projector = ScheduleProjector(scheduleInput.times, scheduleInput.zone) { context.ensureActive() }
            val projected = scheduleInput.days.mapIndexed { index, events ->
                context.ensureActive()
                projector.day(scheduleInput.monday.plusDays(index.toLong()), events)
            }
            WeekScheduleProjection(scheduleInput, projected)
        }
    }
    // A cancelled/superseded projection must not display another week, source, zone or schedule.
    val schedules = projection?.takeIf { it.input == scheduleInput }?.days
        ?: remember(scheduleInput) { List(WEEK_DAY_COUNT) { DaySchedule.empty(scheduleInput.times) } }
    val groups = remember(enabled) { enabled.map(::groupIndexFor) }
    val initialSelection = if (state.requestedDayIndex in enabled) state.requestedDayIndex else
        enabled.minBy { kotlin.math.abs(it - state.requestedDayIndex) }
    val scroll = rememberScrollState()
    val coordinator = remember(scroll, state.weekMonday) { WeekScrollCoordinator(scroll, initialSelection) }
    val interaction = rememberWeekInteraction(
        requestedDayIndex = initialSelection,
        selectionRequest = state.selectionRequest,
        contextKey = state.weekMonday,
        onSelectionChanged = { index ->
            if (index in enabled) {
                coordinator.select(index)
                state.onSelectionChanged(index)
            }
        },
    )
    LaunchedEffect(interaction, enabled) {
        if (interaction.selectedDayIndex !in enabled) {
            interaction.selectDay(enabled.minBy { kotlin.math.abs(it - interaction.selectedDayIndex) })
        }
    }
    val label = rememberDayLabelMetrics(days)
    val separatorColor = state.debug1OutlineColor.resolve(MaterialTheme.colorScheme, state.appBarBackground)
    var detailEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    days.firstOrNull { it.isEnabled && it.date.toEpochDay() == detailEpochDay }?.let { day ->
        DayDetailsDialog(day = day, onDismiss = { detailEpochDay = null }, zone = state.referenceZone)
    }
    AnimateDayWeights(interaction, days.size,
        onFrame = { weights, fraction ->
            Snapshot.withMutableSnapshot {
                coordinator.follow(weights, fraction)
                interaction.animatedDayWeights = weights
            }
        },
        onFinished = coordinator::finish,
    )
    val description = stringResource(R.string.week_accessibility_description)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    WeekViewport(weekWindowInsets(), label, days.maxOf { it.events.size }, coordinator, Modifier.fillMaxSize()) { geometry ->
        val gestures = if (geometry.needsScroll) Modifier else Modifier.weekGestureInput(
            WeekGestureState(
                scrollMode = state.scrollMode,
                geometry = geometry,
                rowBounds = { coordinator.bounds ?: weekRowBounds(geometry.contentHeight, interaction.animatedDayWeights) },
                contextKey = state.weekMonday,
                touchSlopPx = touchSlop,
                selectedDayIndex = { interaction.selectedDayIndex },
                animatedDayWeights = { interaction.animatedDayWeights },
                startDrag = interaction::startDrag,
                dragToFocus = { interaction.dragToFocus(it.coerceIn(groups.min().toFloat(), groups.max().toFloat())) },
                endDrag = {
                    interaction.endDrag()
                    if (interaction.selectedDayIndex !in enabled) {
                        interaction.selectDay(enabled.minBy { kotlin.math.abs(it - interaction.selectedDayIndex) })
                    }
                },
                cancelDrag = interaction::cancelDrag,
                enabledDayIndices = enabled,
                minimumGroup = groups.min(),
                maximumGroup = groups.max(),
            ),
        )
        Box(Modifier.fillMaxSize().then(gestures).testTag("week-viewport")
            .semantics { contentDescription = description }) {
            val side = with(density) { geometry.side.toDp() }
            val bottom = with(density) { geometry.bottom.toDp() }
            WeekDayLayout(geometry, { interaction.animatedDayWeights }, coordinator,
                Modifier.fillMaxSize().padding(start = side, end = side, bottom = bottom)) {
                WeekRows(
                    WeekRowsState(days, interaction.selectedDayIndex, interaction.contentExpandedDays,
                        state.highlightedDayIndex, state.referenceDate, state.isDarkTheme, label.width,
                        separatorColor, schedules),
                    onSelectDay = { if (it in enabled) interaction.selectDay(it) },
                    onShowDetails = { detailEpochDay = it.toEpochDay() },
                )
            }
        }
    }
}

internal fun dayWeightsFor(selectedDayIndex: Int): List<Float> {
    val expandedDays = expandedDayIndices(selectedDayIndex)
    val compactDayCount = WEEK_DAY_COUNT - expandedDays.size
    val expandedDayWeight =
        (TOTAL_DAY_WEIGHT - COMPACT_DAY_WEIGHT * compactDayCount) / expandedDays.size
    return List(WEEK_DAY_COUNT) { dayIndex ->
        if (dayIndex in expandedDays) expandedDayWeight else COMPACT_DAY_WEIGHT
    }
}

internal fun dayWeightsForFocus(focusPosition: Float): List<Float> {
    val boundedFocus =
        focusPosition.coerceIn(
            minimumValue = MIN_FRACTION,
            maximumValue = WEEKEND_START_INDEX.toFloat(),
        )
    val lowerDayIndex = boundedFocus.toInt()
    val upperDayIndex = (lowerDayIndex + 1).coerceAtMost(WEEKEND_START_INDEX)
    val fraction = boundedFocus - lowerDayIndex
    val lowerWeights = dayWeightsFor(lowerDayIndex)
    val upperWeights = dayWeightsFor(upperDayIndex)
    return List(WEEK_DAY_COUNT) { dayIndex ->
        interpolateWeight(
            start = lowerWeights[dayIndex],
            end = upperWeights[dayIndex],
            fraction = fraction,
        )
    }
}

internal fun expandedDayIndices(selectedDayIndex: Int): Set<Int> =
    if (groupIndexFor(selectedDayIndex) < WEEKEND_START_INDEX) {
        val groupIndex = groupIndexFor(selectedDayIndex)
        setOf(groupIndex, groupIndex + 1)
    } else {
        setOf(WEEKEND_START_INDEX, WEEKEND_START_INDEX + 1)
    }

internal fun groupIndexFor(selectedDayIndex: Int): Int =
    selectedDayIndex.coerceIn(0, WEEKEND_START_INDEX)

internal fun interpolateWeight(
    start: Float,
    end: Float,
    fraction: Float,
): Float = start + (end - start) * fraction

