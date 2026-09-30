package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt

internal class WeekInteraction(
    initialSelectedDayIndex: Int,
    private var onSelectionChanged: (Int) -> Unit,
) {
    var selectedDayIndex by mutableStateOf(initialSelectedDayIndex.coerceIn(0, WEEK_DAY_COUNT - 1))
        private set
    var animatedDayWeights by mutableStateOf(dayWeightsFor(groupIndexFor(selectedDayIndex)))
    var contentExpandedDays by mutableStateOf(expandedDayIndices(selectedDayIndex))
    var animationRequest by mutableStateOf(0)
    var isDragging by mutableStateOf(false)
        private set
    var dragFocusPosition by mutableStateOf(groupIndexFor(selectedDayIndex).toFloat())
        private set

    private var dragOriginFocus = dragFocusPosition
    private var dragOriginWeights = animatedDayWeights
    private var dragOriginSelectedDay = selectedDayIndex
    private var selectedWeekendDay = selectedDayIndex.coerceAtLeast(WEEKEND_START_INDEX)

    val selectedGroupIndex: Int
        get() = groupIndexFor(selectedDayIndex)

    fun updateSelectionListener(listener: (Int) -> Unit) {
        onSelectionChanged = listener
    }

    fun selectDay(dayIndex: Int) {
        if (isDragging) cancelDrag()
        val boundedDay = dayIndex.coerceIn(0, WEEK_DAY_COUNT - 1)
        val oldGroup = selectedGroupIndex
        if (boundedDay >= WEEKEND_START_INDEX) selectedWeekendDay = boundedDay
        selectedDayIndex = boundedDay
        onSelectionChanged(boundedDay)
        val newGroup = selectedGroupIndex
        if (newGroup != oldGroup) {
            contentExpandedDays = contentExpandedDays + expandedDayIndices(boundedDay)
            animationRequest += 1
        } else {
            if (animatedDayWeights == dayWeightsFor(newGroup)) {
                contentExpandedDays = expandedDayIndices(boundedDay)
            }
        }
    }

    fun startDrag() {
        if (isDragging) return
        isDragging = true
        dragOriginFocus = selectedGroupIndex.toFloat()
        dragOriginSelectedDay = selectedDayIndex
        dragFocusPosition = dragOriginFocus
        dragOriginWeights = animatedDayWeights
        animationRequest += 1
    }

    fun dragToFocus(focusPosition: Float) {
        if (!isDragging) return
        val boundedFocus =
            focusPosition.coerceIn(
                minimumValue = MIN_FRACTION,
                maximumValue = WEEKEND_START_INDEX.toFloat(),
            )
        dragFocusPosition = boundedFocus
        animatedDayWeights = dragWeightsFromExactOrigin(boundedFocus)
        val roundedGroup = boundedFocus.roundToInt().coerceIn(0, WEEKEND_START_INDEX)
        selectedDayIndex =
            if (roundedGroup == WEEKEND_START_INDEX) {
                if (selectedGroupIndex != WEEKEND_START_INDEX) {
                    selectedWeekendDay = WEEKEND_START_INDEX
                }
                selectedWeekendDay
            } else {
                roundedGroup
            }
        contentExpandedDays = expandedContentDaysForFocus(boundedFocus)
    }

    fun endDrag() {
        if (!isDragging) return
        val settledGroup =
            dragFocusPosition.roundToInt().coerceIn(
                minimumValue = 0,
                maximumValue = WEEKEND_START_INDEX,
            )
        selectedDayIndex =
            if (settledGroup == WEEKEND_START_INDEX) selectedWeekendDay else settledGroup
        animatedDayWeights = dayWeightsFor(settledGroup)
        contentExpandedDays = expandedDayIndices(selectedDayIndex)
        isDragging = false
        onSelectionChanged(selectedDayIndex)
    }

    fun cancelDrag() {
        if (!isDragging) return
        selectedDayIndex = dragOriginSelectedDay
        val settledGroup = groupIndexFor(dragOriginSelectedDay)
        animatedDayWeights = dayWeightsFor(settledGroup)
        contentExpandedDays = expandedDayIndices(selectedDayIndex)
        dragFocusPosition = settledGroup.toFloat()
        isDragging = false
    }

    private fun dragWeightsFromExactOrigin(focusPosition: Float): List<Float> {
        val delta = focusPosition - dragOriginFocus
        if (delta == MIN_FRACTION) return dragOriginWeights
        if (abs(delta) >= MAX_FRACTION) return dayWeightsForFocus(focusPosition)
        val adjacentFocus =
            (dragOriginFocus + if (delta < MIN_FRACTION) -1f else 1f).coerceIn(
                MIN_FRACTION,
                WEEKEND_START_INDEX.toFloat(),
            )
        val adjacentWeights = dayWeightsForFocus(adjacentFocus)
        return List(WEEK_DAY_COUNT) { dayIndex ->
            interpolateWeight(
                start = dragOriginWeights[dayIndex],
                end = adjacentWeights[dayIndex],
                fraction = abs(delta),
            )
        }
    }
}

@Composable
internal fun rememberWeekInteraction(
    requestedDayIndex: Int,
    selectionRequest: Int,
    onSelectionChanged: (Int) -> Unit,
): WeekInteraction {
    val currentListener = rememberUpdatedState(onSelectionChanged)
    val interaction =
        remember {
            WeekInteraction(requestedDayIndex) { dayIndex -> currentListener.value(dayIndex) }
        }
    interaction.updateSelectionListener { dayIndex -> currentListener.value(dayIndex) }
    LaunchedEffect(selectionRequest) {
        if (selectionRequest > 1) interaction.selectDay(requestedDayIndex)
    }
    return interaction
}

internal data class WeekInsets(
    val bottom: Dp,
    val right: Dp,
    val bottomPx: Float,
    val rightPx: Float,
)

@Composable
internal fun rememberWeekInsets(): WeekInsets {
    val layoutDirection = LocalLayoutDirection.current
    val mandatory = WindowInsets.mandatorySystemGestures.asPaddingValues()
    val navigation = WindowInsets.navigationBars.asPaddingValues()
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val requestedBottom =
        mandatory.calculateBottomPadding() * BOTTOM_GESTURE_GUTTER_FRACTION
    val bottom =
        maxOf(
            requestedBottom,
            navigation.calculateBottomPadding(),
            safeDrawing.calculateBottomPadding(),
        )
    val requestedSide =
        maxOf(
            MINIMUM_RIGHT_GESTURE_GUTTER,
            mandatory.calculateRightPadding(layoutDirection),
        ) * RIGHT_GESTURE_GUTTER_FRACTION
    // Scaffold owns horizontal safeDrawing insets; these are visual gutters only.
    val right = requestedSide
    val density = LocalDensity.current
    return WeekInsets(
        bottom = bottom,
        right = right,
        bottomPx = with(density) { bottom.toPx() },
        rightPx = with(density) { right.toPx() },
    )
}

@Composable
internal fun AnimateDayWeights(
    interaction: WeekInteraction,
    dayCount: Int,
) {
    LaunchedEffect(interaction.animationRequest) {
        if (!interaction.isDragging) {
            val startWeights = interaction.animatedDayWeights
            val targetWeights = dayWeightsFor(interaction.selectedGroupIndex)
            if (startWeights != targetWeights) {
                val durationNanos = DAY_STATE_ANIMATION_DURATION_MILLIS * NANOS_PER_MILLISECOND
                val startNanos = withFrameNanos { it }
                var fraction = MIN_FRACTION
                while (fraction < MAX_FRACTION) {
                    val frameNanos = withFrameNanos { it }
                    fraction =
                        ((frameNanos - startNanos).toDouble() / durationNanos)
                            .toFloat()
                            .coerceIn(MIN_FRACTION, MAX_FRACTION)
                    interaction.animatedDayWeights =
                        List(dayCount) { dayIndex ->
                            interpolateWeight(
                                start = startWeights[dayIndex],
                                end = targetWeights[dayIndex],
                                fraction = fraction,
                            )
                        }
                }
                interaction.animatedDayWeights = targetWeights
            }
            interaction.contentExpandedDays = expandedDayIndices(interaction.selectedDayIndex)
        }
    }
}

private fun expandedContentDaysForFocus(focusPosition: Float): Set<Int> {
    val lowerGroup = focusPosition.toInt().coerceIn(0, WEEKEND_START_INDEX)
    val upperGroup = (lowerGroup + 1).coerceAtMost(WEEKEND_START_INDEX)
    return expandedDayIndices(lowerGroup) + expandedDayIndices(upperGroup)
}
