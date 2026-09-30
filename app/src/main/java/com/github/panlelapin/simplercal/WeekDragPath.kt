package com.github.panlelapin.simplercal

import kotlin.math.abs
import kotlin.math.max

/** Pixel-to-layout mapping, including a tap animation interrupted between frames. */
internal class WeekDragPath(
    val originGroup: Int,
    originWeights: List<Float>,
    height: Float,
) {
    private val normalStep = dayGroupTransitionDistance(height).coerceAtLeast(1f)
    private val previousStep = distanceTo(originWeights, originGroup - 1, height)
    private val nextStep = distanceTo(originWeights, originGroup + 1, height)
    val minimumDistance = if (originGroup == 0) 0f else -(previousStep + normalStep * (originGroup - 1))
    val maximumDistance = if (originGroup == WEEKEND_START_INDEX) 0f else
        nextStep + normalStep * (WEEKEND_START_INDEX - originGroup - 1)

    fun bound(distance: Float): Float = distance.coerceIn(minimumDistance, maximumDistance)

    fun focusAt(distance: Float): Float {
        val bounded = bound(distance)
        if (bounded == 0f) return originGroup.toFloat()
        val direction = if (bounded < 0f) -1f else 1f
        val firstStep = if (bounded < 0f) previousStep else nextStep
        val travelled = abs(bounded)
        val groups = if (travelled <= firstStep) travelled / firstStep else
            1f + (travelled - firstStep) / normalStep
        return originGroup + direction * groups
    }

    private fun distanceTo(origin: List<Float>, group: Int, height: Float): Float {
        if (group !in 0..WEEKEND_START_INDEX) return 1f
        val target = dayWeightsFor(group)
        var boundaryDelta = 0f
        var largestDelta = 0f
        origin.indices.forEach { index ->
            boundaryDelta += target[index] - origin[index]
            largestDelta = max(largestDelta, abs(boundaryDelta))
        }
        return (height * largestDelta / TOTAL_DAY_WEIGHT).coerceAtLeast(1f)
    }
}

internal fun discreteDragThreshold(
    initialY: Float,
    dayIndex: Int,
    weights: List<Float>,
    height: Float,
    direction: Float,
    touchSlop: Float,
): Float {
    if (dayIndex !in weights.indices) return Float.POSITIVE_INFINITY
    val top = height * weights.take(dayIndex).sum() / weights.sum()
    val middle = top + height * weights[dayIndex] / weights.sum() / 2f
    return max(touchSlop, (middle - initialY) * direction)
}
