package com.github.panlelapin.simplercal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput

internal fun dayGroupTransitionDistance(height: Float): Float {
    val expandedWeight = dayWeightsFor(0).first()
    return height * (expandedWeight - COMPACT_DAY_WEIGHT) / TOTAL_DAY_WEIGHT
}

internal fun Modifier.weekGestureInput(state: WeekGestureState): Modifier =
    pointerInput(
        state.scrollMode,
        state.bottomGestureInsetPx,
        state.rightGestureInsetPx,
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
            state.scrollMode != WeekScrollMode.PER_DAY ||
                touchDayIndex in expandedDayIndices(anchorFocus.toInt())
        val hasMoved =
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
        if (hasMoved) {
            state.endDrag()
        } else if (isTouchInDayArea) {
            state.selectDay(touchDayIndex)
        }
    }
}

private suspend fun AwaitPointerEventScope.consumeWeekDrag(
    state: WeekGestureState,
    drag: WeekDragState,
): Boolean {
    var accumulatedDrag = 0f
    var previousY = drag.initialY
    var hasMoved = false
    var isActive = true
    while (isActive) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == drag.pointerId }
        if (change == null || !change.pressed) {
            isActive = false
        } else {
            val dragAmount = change.position.y - previousY
            previousY = change.position.y
            if (dragAmount != MIN_FRACTION && drag.isDragAllowed) {
                if (!hasMoved) {
                    state.startDrag(drag.anchorFocus.toInt())
                    hasMoved = true
                }
                accumulatedDrag += dragAmount
                state.dragToFocus(drag.anchorFocus - accumulatedDrag / drag.transitionDistance)
                change.consume()
            }
        }
    }
    return hasMoved
}

private fun isInsideDayArea(
    position: Offset,
    width: Float,
    dayAreaHeight: Float,
    state: WeekGestureState,
): Boolean =
    position.x >= state.leftGestureInsetPx &&
        position.x < width - state.rightGestureInsetPx &&
        position.y < dayAreaHeight
