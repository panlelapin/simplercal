package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import java.time.LocalDate
import kotlin.math.roundToInt

internal class WeekInteraction {
    var selectedDayIndex by mutableStateOf(0)
    var animatedDayWeights by mutableStateOf(dayWeightsFor(0))
    var contentExpandedDays by mutableStateOf(expandedDayIndices(0))
    var animationRequest by mutableStateOf(0)
    var isDragging by mutableStateOf(false)
    var dragFocusPosition by mutableStateOf(0f)

    fun selectDay(dayIndex: Int) {
        if (dayIndex != selectedDayIndex) {
            contentExpandedDays = contentExpandedDays + expandedDayIndices(dayIndex)
            selectedDayIndex = dayIndex
            animationRequest += 1
        }
    }

    fun startDrag(dayIndex: Int) {
        if (!isDragging) {
            isDragging = true
            dragFocusPosition = dayIndex.toFloat()
            animationRequest += 1
        }
    }

    fun dragToFocus(focusPosition: Float) {
        val boundedFocusPosition =
            focusPosition.coerceIn(
                minimumValue = MIN_FRACTION,
                maximumValue = WEEKEND_START_INDEX.toFloat(),
            )
        val lowerDayIndex = boundedFocusPosition.toInt()
        val upperDayIndex = (lowerDayIndex + 1).coerceAtMost(WEEKEND_START_INDEX)
        selectedDayIndex =
            boundedFocusPosition.roundToInt().coerceIn(
                minimumValue = 0,
                maximumValue = WEEKEND_START_INDEX,
            )
        dragFocusPosition = boundedFocusPosition
        contentExpandedDays = expandedDayIndices(lowerDayIndex) + expandedDayIndices(upperDayIndex)
        animatedDayWeights = dayWeightsForFocus(boundedFocusPosition)
    }

    fun endDrag() {
        selectedDayIndex =
            dragFocusPosition.roundToInt().coerceIn(
                minimumValue = 0,
                maximumValue = WEEKEND_START_INDEX,
            )
        animatedDayWeights = dayWeightsFor(selectedDayIndex)
        contentExpandedDays = expandedDayIndices(selectedDayIndex)
        isDragging = false
    }
}

@Composable
internal fun rememberWeekInteraction(todaySelectionRequest: Int): WeekInteraction {
    val interaction = remember { WeekInteraction() }
    LaunchedEffect(todaySelectionRequest) {
        if (todaySelectionRequest > 0) {
            interaction.selectDay(LocalDate.now().dayOfWeek.value - 1)
        }
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
    val mandatoryGesturePadding = WindowInsets.mandatorySystemGestures.asPaddingValues()
    val bottom = mandatoryGesturePadding.calculateBottomPadding() * BOTTOM_GESTURE_GUTTER_FRACTION
    val right =
        maxOf(
            a = MINIMUM_RIGHT_GESTURE_GUTTER,
            b = mandatoryGesturePadding.calculateRightPadding(LocalLayoutDirection.current),
        ) *
            RIGHT_GESTURE_GUTTER_FRACTION
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
            val targetWeights = dayWeightsFor(interaction.selectedDayIndex)
            if (startWeights != targetWeights) {
                val durationNanos = DAY_STATE_ANIMATION_DURATION_MILLIS * NANOS_PER_MILLISECOND
                val startNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                var fraction = MIN_FRACTION
                while (fraction < MAX_FRACTION) {
                    val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                    fraction =
                        ((frameNanos - startNanos).toDouble() / durationNanos)
                            .toFloat()
                            .coerceIn(
                                minimumValue = MIN_FRACTION,
                                maximumValue = MAX_FRACTION,
                            )
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
