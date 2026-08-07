package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
) {
    state.days.forEachIndexed { index, day ->
        DayRow(
            state =
                DayRowState(
                    dayIndex = index,
                    highlightedDayIndex = state.highlightedDayIndex,
                    day = day,
                    isExpanded = index in expandedDayIndices(state.selectedDayIndex),
                    isContentExpanded = index in state.contentExpandedDays,
                    weight = state.animatedDayWeights[index],
                    dayLabelColumnWidth = state.dayLabelColumnWidth,
                    separatorColor = state.separatorColor,
                    appBarBackground = state.appBarBackground,
                ),
            onClick = { onSelectDay(index) },
        )
    }
}

@Composable
internal fun WeekView(state: WeekViewState) {
    val days = remember(state.weekMonday) { currentWeek(state.weekMonday) }
    val interaction = rememberWeekInteraction(state.todaySelectionRequest)
    val currentAnimatedDayWeights = rememberUpdatedState(interaction.animatedDayWeights)
    val currentSelectedDayIndex = rememberUpdatedState(interaction.selectedDayIndex)
    val currentSelectDay = rememberUpdatedState(interaction::selectDay)
    val currentStartDrag = rememberUpdatedState(interaction::startDrag)
    val currentDragToFocus = rememberUpdatedState(interaction::dragToFocus)
    val currentEndDrag = rememberUpdatedState(interaction::endDrag)
    val dayLabelColumnWidth = dayLabelColumnWidth(days)
    val separatorColor =
        state.debug1OutlineColor.resolve(MaterialTheme.colorScheme, state.appBarBackground)
    val insets = rememberWeekInsets()
    AnimateDayWeights(interaction, days.size)
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .weekGestureInput(
                    WeekGestureState(
                        scrollMode = state.scrollMode,
                        bottomGestureInsetPx = insets.bottomPx,
                        rightGestureInsetPx = insets.rightPx,
                        leftGestureInsetPx = insets.rightPx,
                        selectedDayIndex = { currentSelectedDayIndex.value },
                        animatedDayWeights = { currentAnimatedDayWeights.value },
                        selectDay = { dayIndex -> currentSelectDay.value(dayIndex) },
                        startDrag = { dayIndex -> currentStartDrag.value(dayIndex) },
                        dragToFocus = { focus -> currentDragToFocus.value(focus) },
                        endDrag = { currentEndDrag.value() },
                    ),
                ),
    ) {
        Spacer(modifier = Modifier.fillMaxHeight().width(insets.right))
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(bottom = insets.bottom),
        ) {
            WeekRows(
                state =
                    WeekRowsState(
                        days = days,
                        selectedDayIndex = interaction.selectedDayIndex,
                        contentExpandedDays = interaction.contentExpandedDays,
                        animatedDayWeights = interaction.animatedDayWeights,
                        highlightedDayIndex = state.highlightedDayIndex,
                        dayLabelColumnWidth = dayLabelColumnWidth,
                        separatorColor = separatorColor,
                        appBarBackground = state.appBarBackground,
                    ),
                onSelectDay = interaction::selectDay,
            )
        }
        Spacer(modifier = Modifier.fillMaxHeight().width(insets.right))
    }
}

internal fun dayWeightsFor(selectedDayIndex: Int): List<Float> {
    val expandedDays = expandedDayIndices(selectedDayIndex)
    val compactDayCount = WEEK_DAY_COUNT - expandedDays.size
    val expandedDayWeight =
        (TOTAL_DAY_WEIGHT - COMPACT_DAY_WEIGHT * compactDayCount) / expandedDays.size
    return List(DAY_ABBREVIATIONS.size) { dayIndex ->
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
    return List(DAY_ABBREVIATIONS.size) { dayIndex ->
        interpolateWeight(
            start = lowerWeights[dayIndex],
            end = upperWeights[dayIndex],
            fraction = fraction,
        )
    }
}

internal fun expandedDayIndices(selectedDayIndex: Int): Set<Int> =
    if (selectedDayIndex < WEEKEND_START_INDEX) {
        setOf(selectedDayIndex, selectedDayIndex + 1)
    } else {
        setOf(WEEKEND_START_INDEX, WEEKEND_START_INDEX + 1)
    }

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
