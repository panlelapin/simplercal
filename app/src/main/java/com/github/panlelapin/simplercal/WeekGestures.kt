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
        val anchorFocus = state.selectedDayIndex().coerceAtMost(WEEKEND_START_INDEX).toFloat()
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
                0
            }
        val isDragAllowed =
            state.scrollMode == WeekScrollMode.PER_DAY ||
                touchDayIndex in expandedDayIndices(anchorFocus.toInt())
        val dragResult =
            consumeWeekDrag(
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
        if (dragResult.isDragged) {
            state.endDrag()
        } else if (dragResult.isTap && isTouchInDayArea) {
            state.selectDay(touchDayIndex)
        }
    }
}

private suspend fun AwaitPointerEventScope.consumeWeekDrag(
    state: WeekGestureState,
    drag: WeekDragState,
): WeekDragResult {
    val progress = WeekDragProgress(initialY = drag.initialY)
    while (true) {
        val change = awaitActivePointerChange(drag.pointerId) ?: break
        progress.process(change = change, state = state, drag = drag)
    }
    return progress.result()
}

private suspend fun AwaitPointerEventScope.awaitActivePointerChange(
    pointerId: PointerId,
): PointerInputChange? =
    awaitPointerEvent().changes.firstOrNull { change -> change.id == pointerId && change.pressed }

private class WeekDragProgress(
    initialY: Float,
) {
    private var accumulatedDrag = 0f
    private var previousY = initialY
    private var hasStartedDrag = false
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
            startDrag(
                change = change,
                state = state,
                drag = drag,
                displacement = displacementFromDown,
            )
        } else if (hasStartedDrag && dragAmount != MIN_FRACTION) {
            continueDrag(change = change, state = state, drag = drag, dragAmount = dragAmount)
        }
    }

    fun result(): WeekDragResult =
        WeekDragResult(
            isDragged = hasStartedDrag,
            isTap = !hasExceededTouchSlop,
        )

    private fun startDrag(
        change: PointerInputChange,
        state: WeekGestureState,
        drag: WeekDragState,
        displacement: Float,
    ) {
        if (!drag.isDragAllowed) return
        val direction = if (displacement < MIN_FRACTION) -1f else 1f
        accumulatedDrag = displacement - direction * state.touchSlopPx
        state.startDrag(drag.anchorFocus.toInt())
        hasStartedDrag = true
        updateDrag(state = state, drag = drag)
        change.consume()
    }

    private fun continueDrag(
        change: PointerInputChange,
        state: WeekGestureState,
        drag: WeekDragState,
        dragAmount: Float,
    ) {
        accumulatedDrag += dragAmount
        updateDrag(state = state, drag = drag)
        change.consume()
    }

    private fun updateDrag(
        state: WeekGestureState,
        drag: WeekDragState,
    ) {
        state.dragToFocus(drag.anchorFocus - accumulatedDrag / drag.transitionDistance)
    }
}

private data class WeekDragResult(
    val isDragged: Boolean,
    val isTap: Boolean,
)

private fun isInsideDayArea(
    position: Offset,
    width: Float,
    dayAreaHeight: Float,
    state: WeekGestureState,
): Boolean =
    position.x >= state.leftGestureInsetPx &&
        position.x < width - state.rightGestureInsetPx &&
        position.y < dayAreaHeight
