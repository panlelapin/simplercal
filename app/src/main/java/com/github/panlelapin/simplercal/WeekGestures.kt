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
        state.touchSlopPx,
    ) {
        handleWeekGesture(state)
    }

private suspend fun PointerInputScope.handleWeekGesture(state: WeekGestureState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val dayAreaHeight = (size.height - state.bottomGestureInsetPx).coerceAtLeast(1f)
        val anchorFocus = groupIndexFor(state.selectedDayIndex()).toFloat()
        val transitionDistance = dayGroupTransitionDistance(dayAreaHeight).coerceAtLeast(1f)
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
        val progress = WeekDragProgress(initialY = down.position.y)
        var completedNormally = false
        try {
            consumeWeekDrag(
                progress = progress,
                state = state,
                drag =
                    WeekDragState(
                        pointerId = down.id,
                        initialY = down.position.y,
                        anchorFocus = anchorFocus,
                        transitionDistance = transitionDistance,
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
) {
    var hasStartedDrag = false
        private set

    private var accumulatedDrag = 0f
    private var previousY = initialY
    private var hasExceededTouchSlop = false

    fun process(
        change: PointerInputChange,
        state: WeekGestureState,
        drag: WeekDragState,
    ) {
        val dragAmount = change.position.y - previousY
        previousY = change.position.y
        val displacementFromDown = change.position.y - drag.initialY
        if (!hasExceededTouchSlop && abs(displacementFromDown) > state.touchSlopPx) {
            hasExceededTouchSlop = true
            if (drag.isDragAllowed) {
                val direction = if (displacementFromDown < MIN_FRACTION) -1f else 1f
                accumulatedDrag = displacementFromDown - direction * state.touchSlopPx
                state.startDrag()
                hasStartedDrag = true
                updateDrag(state = state, drag = drag)
                change.consume()
            }
        } else if (hasStartedDrag && dragAmount != MIN_FRACTION) {
            accumulatedDrag += dragAmount
            updateDrag(state = state, drag = drag)
            change.consume()
        }
    }

    private fun updateDrag(
        state: WeekGestureState,
        drag: WeekDragState,
    ) {
        state.dragToFocus(drag.anchorFocus - accumulatedDrag / drag.transitionDistance)
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
    }
}

private suspend fun AwaitPointerEventScope.awaitActivePointerChange(
    pointerId: PointerId,
): PointerInputChange? =
    awaitPointerEvent().changes.firstOrNull { change -> change.id == pointerId && change.pressed }

private fun isInsideDayArea(
    position: Offset,
    width: Float,
    dayAreaHeight: Float,
    state: WeekGestureState,
): Boolean =
    position.x >= state.leftGestureInsetPx &&
        position.x < width - state.rightGestureInsetPx &&
        position.y < dayAreaHeight
