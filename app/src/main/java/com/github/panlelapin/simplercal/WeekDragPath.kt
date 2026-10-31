package com.github.panlelapin.simplercal

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Pixel-to-layout mapping, including a tap animation interrupted between frames. */
internal class WeekDragPath(
    val originGroup: Int,
    originWeights: List<Float>,
    height: Float,
    private val minimumGroup: Int = 0,
    private val maximumGroup: Int = WEEKEND_START_INDEX,
) {
    private val pixelHeight = height.roundToInt().coerceAtLeast(0)
    private val forward = steps(originWeights, (originGroup + 1..maximumGroup).toList())
    private val backward = steps(originWeights, (originGroup - 1 downTo minimumGroup).toList())
    val minimumDistance = -backward.sum()
    val maximumDistance = forward.sum()

    fun bound(distance: Float): Float = distance.coerceIn(minimumDistance, maximumDistance)

    fun focusAt(distance: Float): Float {
        val bounded = bound(distance)
        val steps = if (bounded < 0f) backward else forward
        val direction = if (bounded < 0f) -1f else 1f
        var remaining = abs(bounded)
        steps.forEachIndexed { index, length ->
            if (remaining <= length) return originGroup + direction * (index + remaining / length)
            remaining -= length
        }
        return originGroup + direction * steps.size
    }

    private fun steps(origin: List<Float>, groups: List<Int>): List<Float> {
        var previous = weekRowBounds(pixelHeight, origin)
        return groups.map { group ->
            val target = weekRowBounds(pixelHeight, dayWeightsFor(group))
            val distance = previous.edges.indices.maxOf { abs(target.edges[it] - previous.edges[it]) }.toFloat().coerceAtLeast(1f)
            previous = target
            distance
        }
    }

}

internal fun discreteDragThreshold(
    initialY: Float,
    dayIndex: Int,
    bounds: WeekRowBounds,
    direction: Float,
    touchSlop: Float,
): Float {
    if (dayIndex !in 0 until bounds.edges.lastIndex) return Float.POSITIVE_INFINITY
    val middle = bounds.top(dayIndex) + bounds.height(dayIndex) / 2f
    return max(touchSlop, (middle - initialY) * direction)
}
