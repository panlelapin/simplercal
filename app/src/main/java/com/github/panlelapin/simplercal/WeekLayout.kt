package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
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
internal fun ColumnScope.WeekRows(
    state: WeekRowsState,
    onSelectDay: (Int) -> Unit,
    onShowDetails: (LocalDate) -> Unit,
) {
    state.days.forEachIndexed { index, day ->
        DayRow(
            state =
                DayRowState(
                    dayIndex = index,
                    highlightedDayIndex = state.highlightedDayIndex,
                    referenceDate = state.referenceDate,
                    isDarkTheme = state.isDarkTheme,
                    day = day,
                    isSelected = index == state.selectedDayIndex,
                    isExpanded = index in expandedDayIndices(state.selectedDayIndex),
                    isContentExpanded = index in state.contentExpandedDays,
                    weight = state.animatedDayWeights[index],
                    dayLabelColumnWidth = state.dayLabelColumnWidth,
                    separatorColor = state.separatorColor,
                    appBarBackground = state.appBarBackground,
                ),
            onClick = { onSelectDay(index) },
            onShowDetails = { onShowDetails(day.date) },
        )
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
        ) {
            currentWeek(
                monday = state.weekMonday,
                simulationMode = state.simulationMode,
                eventsByDay = state.eventsByDay,
                markers = state.dayMarkers,
            )
        }
    val interaction =
        rememberWeekInteraction(
            requestedDayIndex = state.requestedDayIndex,
            selectionRequest = state.selectionRequest,
            onSelectionChanged = state.onSelectionChanged,
        )
    val currentAnimatedDayWeights = rememberUpdatedState(interaction.animatedDayWeights)
    val currentSelectedDayIndex = rememberUpdatedState(interaction.selectedDayIndex)
    val currentStartDrag = rememberUpdatedState(interaction::startDrag)
    val currentDragToFocus = rememberUpdatedState(interaction::dragToFocus)
    val currentEndDrag = rememberUpdatedState(interaction::endDrag)
    val dayLabelColumnWidth = dayLabelColumnWidth(days)
    val separatorColor =
        state.debug1OutlineColor.resolve(MaterialTheme.colorScheme, state.appBarBackground)
    val insets = rememberWeekInsets()
    var detailEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val detailDay = days.firstOrNull { it.date.toEpochDay() == detailEpochDay }
    detailDay?.let { day ->
        DayDetailsDialog(
            day = day,
            markers = state.dayMarkers[day.date] ?: DayMarkers(),
            isSimulation = state.simulationMode == SimulationMode.SIMULATION,
            onMarkersChange = state.onDayMarkersChange,
            onDismiss = { detailEpochDay = null },
        )
    }
    val density = LocalDensity.current
    val bodyLineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    val minimumWeekHeight = with(density) {
        minimumWeekHeightDp(bodyLineHeight.toDp().value).dp
    }
    val weekDescription = stringResource(R.string.week_accessibility_description)
    val fallbackScroll = rememberScrollState()
    AnimateDayWeights(interaction, days.size)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeHeight = (maxHeight - insets.bottom).coerceAtLeast(0.dp)
        val needsScroll = safeHeight < minimumWeekHeight
        val weekHeight = maxOf(safeHeight, minimumWeekHeight)
        LaunchedEffect(needsScroll, interaction.selectedDayIndex, weekHeight) {
            if (needsScroll) {
                val topWeight = dayWeightsFor(interaction.selectedDayIndex).take(interaction.selectedDayIndex).sum()
                val targetTop = with(density) { weekHeight.toPx() } * topWeight / TOTAL_DAY_WEIGHT
                fallbackScroll.scrollTo(targetTop.toInt())
            }
        }
        val gestureModifier = if (needsScroll) Modifier else Modifier.weekGestureInput(
            WeekGestureState(
                scrollMode = state.scrollMode,
                bottomGestureInsetPx = insets.bottomPx,
                rightGestureInsetPx = insets.rightPx,
                leftGestureInsetPx = insets.rightPx,
                touchSlopPx = LocalViewConfiguration.current.touchSlop,
                selectedDayIndex = { currentSelectedDayIndex.value },
                animatedDayWeights = { currentAnimatedDayWeights.value },
                startDrag = { currentStartDrag.value() },
                dragToFocus = { focus -> currentDragToFocus.value(focus) },
                endDrag = { currentEndDrag.value() },
                cancelDrag = interaction::cancelDrag,
            ),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(gestureModifier)
                    .semantics { contentDescription = weekDescription },
        ) {
            Spacer(modifier = Modifier.fillMaxHeight().width(insets.right))
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(bottom = insets.bottom),
            ) {
                val scrollModifier = if (needsScroll) Modifier.verticalScroll(fallbackScroll) else Modifier
                Column(Modifier.fillMaxSize().then(scrollModifier).height(weekHeight)) {
                    WeekRows(
                        state =
                            WeekRowsState(
                                days = days,
                                selectedDayIndex = interaction.selectedDayIndex,
                                contentExpandedDays = interaction.contentExpandedDays,
                                animatedDayWeights = interaction.animatedDayWeights,
                                highlightedDayIndex = state.highlightedDayIndex,
                                referenceDate = state.referenceDate,
                                isDarkTheme = state.isDarkTheme,
                                dayLabelColumnWidth = dayLabelColumnWidth,
                                separatorColor = separatorColor,
                                appBarBackground = state.appBarBackground,
                            ),
                        onSelectDay = interaction::selectDay,
                        onShowDetails = { detailEpochDay = it.toEpochDay() },
                    )
                }
            }
            Spacer(modifier = Modifier.fillMaxHeight().width(insets.right))
        }
    }
}

internal fun minimumWeekHeightDp(lineHeight: Float): Float = maxOf(
    48f * TOTAL_DAY_WEIGHT / COMPACT_DAY_WEIGHT,
    maxOf(lineHeight * EXPANDED_CONTENT_LINE_COUNT + 4f, lineHeight * (EXPANDED_CONTENT_LINE_COUNT - 1) + 52f) *
        TOTAL_DAY_WEIGHT / dayWeightsFor(0).first(),
)

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

internal fun dayIndexAtPosition(
    y: Float,
    height: Int,
    weights: List<Float>,
): Int {
    if (height <= 0 || weights.isEmpty()) return 0
    val boundedY =
        y.coerceIn(
            minimumValue = MIN_FRACTION,
            maximumValue = height.toFloat(),
        )
    val totalWeight = weights.sum()
    var bottom = 0f
    val matchingDayIndex =
        weights.indices.firstOrNull { dayIndex ->
            bottom += height * weights[dayIndex] / totalWeight
            boundedY < bottom
        }
    return matchingDayIndex ?: weights.lastIndex
}
