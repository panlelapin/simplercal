package com.github.panlelapin.simplercal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
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
        state.geometry,
        state.contextKey,
        state.touchSlopPx,
        state.enabledDayIndices,
        state.minimumGroup,
        state.maximumGroup,
    ) {
        handleWeekGesture(state)
    }

private suspend fun PointerInputScope.handleWeekGesture(state: WeekGestureState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val geometry = state.geometry
        // The protected bottom is not a peripheral drag strip. Do not consume its events.
        if (!geometry.acceptsDrag(down.position.x, down.position.y)) return@awaitEachGesture
        val downBounds = state.rowBounds()
        val touchDayIndex = if (geometry.containsDay(down.position.x)) downBounds.dayAt(down.position.y) else -1
        val isDragAllowed =
            (touchDayIndex < 0 || touchDayIndex in state.enabledDayIndices) &&
                (state.scrollMode == WeekScrollMode.LINEAR || touchDayIndex in expandedDayIndices(state.selectedDayIndex()))
        val progress = WeekDragProgress(down.position, touchDayIndex, downBounds)
        var completedNormally = false
        try {
            val released = consumeWeekDrag(
                progress = progress,
                state = state,
                drag =
                    WeekDragState(
                        pointerId = down.id,
                        initialY = down.position.y,
                        isDragAllowed = isDragAllowed,
                    ),
            )
            if (progress.hasStartedDrag) {
                if (released) state.endDrag() else state.cancelDrag()
            }
            completedNormally = true
        } finally {
            if (!completedNormally && progress.hasStartedDrag) state.cancelDrag()
        }
    }
}

private class WeekDragProgress(
    private val initialPosition: Offset,
    private val touchDayIndex: Int,
    private val downBounds: WeekRowBounds,
) {
    var hasStartedDrag = false
        private set

    private var accumulatedDrag = 0f
    private var previousY = initialPosition.y
    private var hasExceededTouchSlop = false
    private var path: WeekDragPath? = null

    fun process(
        change: PointerInputChange,
        state: WeekGestureState,
        drag: WeekDragState,
    ) {
        val dragAmount = change.position.y - previousY
        previousY = change.position.y
        val displacementFromDown = change.position.y - drag.initialY
        if (change.isConsumed && change.pressed) return
        if (!hasExceededTouchSlop && (change.position - initialPosition).getDistance() > state.touchSlopPx) {
            hasExceededTouchSlop = true
        }
        if (hasExceededTouchSlop && !hasStartedDrag) {
            // Cancel the child's tap even when Discrete disallows starting a drag here.
            change.consume()
            if (drag.isDragAllowed) {
                val direction = if (displacementFromDown < 0f) -1f else 1f
                val threshold = if (state.scrollMode == WeekScrollMode.DISCRETE) {
                    discreteDragThreshold(drag.initialY, touchDayIndex, downBounds, direction, state.touchSlopPx)
                } else state.touchSlopPx
                if (abs(displacementFromDown) <= threshold) return
                state.startDrag()
                path = WeekDragPath(groupIndexFor(state.selectedDayIndex()), state.animatedDayWeights(), downBounds.height.toFloat(),
                    state.minimumGroup, state.maximumGroup)
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
): Boolean {
    while (true) {
        val change = awaitActivePointerChange(drag.pointerId) ?: return false
        progress.process(change = change, state = state, drag = drag)
        if (!change.pressed) return true
    }
}

private suspend fun AwaitPointerEventScope.awaitActivePointerChange(
    pointerId: PointerId,
): PointerInputChange? =
    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { change -> change.id == pointerId }

