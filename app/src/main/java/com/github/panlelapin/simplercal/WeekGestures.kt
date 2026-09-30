package com.github.panlelapin.simplercal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

internal fun dayGroupTransitionDistance(height: Float): Float {
    val expandedWeight = dayWeightsFor(0).first()
    return height * (expandedWeight - COMPACT_DAY_WEIGHT) / TOTAL_DAY_WEIGHT
}

internal fun Modifier.weekGestureInput(state: WeekGestureState): Modifier =
    pointerInput(
        state.scrollMode,
        state.bottomGestureInsetPx,
        state.rightGestureInsetPx,
        state.leftGestureInsetPx,
        state.touchSlopPx,
    ) {
        handleWeekGesture(state)
    }

private suspend fun PointerInputScope.handleWeekGesture(state: WeekGestureState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val dayAreaHeight = (size.height - state.bottomGestureInsetPx).coerceAtLeast(1f)
        val isTouchInDayArea =
            isInsideDayArea(
                position = down.position,
                width = size.width.toFloat(),
                dayAreaHeight = dayAreaHeight,
                state = state,
            )
        val touchDayIndex =
            if (isTouchInDayArea) {
                dayIndexAtPosition(
                    y = down.position.y,
                    height = dayAreaHeight.toInt(),
                    weights = state.animatedDayWeights(),
                )
            } else {
                -1
            }
        val isDragAllowed =
            state.scrollMode == WeekScrollMode.LINEAR ||
                touchDayIndex in expandedDayIndices(state.selectedDayIndex())
        val progress = WeekDragProgress(
            initialY = down.position.y,
            dayAreaHeight = dayAreaHeight,
            touchDayIndex = touchDayIndex,
            downWeights = state.animatedDayWeights(),
        )
        var completedNormally = false
        try {
            consumeWeekDrag(
                progress = progress,
                state = state,
                drag =
                    WeekDragState(
                        pointerId = down.id,
                        initialY = down.position.y,
                        isDragAllowed = isDragAllowed,
                    ),
            )
            if (progress.hasStartedDrag) state.endDrag()
            completedNormally = true
        } finally {
            if (!completedNormally && progress.hasStartedDrag) state.cancelDrag()
        }
    }
}

private class WeekDragProgress(
    initialY: Float,
    private val dayAreaHeight: Float,
    private val touchDayIndex: Int,
    private val downWeights: List<Float>,
) {
    var hasStartedDrag = false
        private set

    private var accumulatedDrag = 0f
    private var previousY = initialY
    private var hasExceededTouchSlop = false
    private var path: WeekDragPath? = null

    fun process(
        change: PointerInputChange,
        state: WeekGestureState,
        drag: WeekDragState,
    ) {
        if (!change.pressed && !hasStartedDrag) return
        val dragAmount = change.position.y - previousY
        previousY = change.position.y
        val displacementFromDown = change.position.y - drag.initialY
        if (change.isConsumed && change.pressed) return
        if (!hasExceededTouchSlop && abs(displacementFromDown) > state.touchSlopPx) {
            hasExceededTouchSlop = true
        }
        if (hasExceededTouchSlop && !hasStartedDrag) {
            // Cancel the child's tap even when Discrete disallows starting a drag here.
            change.consume()
            if (drag.isDragAllowed) {
                val direction = if (displacementFromDown < 0f) -1f else 1f
                val threshold = if (state.scrollMode == WeekScrollMode.DISCRETE) {
                    discreteDragThreshold(drag.initialY, touchDayIndex, downWeights,
                        dayAreaHeight, direction, state.touchSlopPx)
                } else state.touchSlopPx
                if (abs(displacementFromDown) <= threshold) return
                state.startDrag()
                path = WeekDragPath(groupIndexFor(state.selectedDayIndex()), state.animatedDayWeights(), dayAreaHeight)
                hasStartedDrag = true
                accumulatedDrag = mapDirection(state, displacementFromDown - direction * threshold)
                updateDrag(state)
            }
        } else if (hasStartedDrag && dragAmount != MIN_FRACTION) {
            accumulatedDrag += mapDirection(state, dragAmount)
            updateDrag(state)
            change.consume()
        }
    }

    private fun mapDirection(state: WeekGestureState, pixels: Float): Float =
        if (state.scrollMode == WeekScrollMode.LINEAR) -pixels else pixels

    private fun updateDrag(state: WeekGestureState) {
        val currentPath = path ?: return
        accumulatedDrag = currentPath.bound(accumulatedDrag)
        state.dragToFocus(currentPath.focusAt(accumulatedDrag))
    }
}

private suspend fun AwaitPointerEventScope.consumeWeekDrag(
    progress: WeekDragProgress,
    state: WeekGestureState,
    drag: WeekDragState,
) {
    while (true) {
        val change = awaitActivePointerChange(drag.pointerId) ?: break
        progress.process(change = change, state = state, drag = drag)
        if (!change.pressed) break
    }
}

private suspend fun AwaitPointerEventScope.awaitActivePointerChange(
    pointerId: PointerId,
): PointerInputChange? =
    awaitPointerEvent().changes.firstOrNull { change -> change.id == pointerId }

private fun isInsideDayArea(
    position: Offset,
    width: Float,
    dayAreaHeight: Float,
    state: WeekGestureState,
): Boolean =
    position.x >= state.leftGestureInsetPx &&
        position.x < width - state.rightGestureInsetPx &&
        position.y < dayAreaHeight
