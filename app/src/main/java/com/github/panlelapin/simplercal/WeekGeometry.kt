package com.github.panlelapin.simplercal

import kotlin.math.ceil
import kotlin.math.roundToInt

/** All coordinates are integer pixels, in the week viewport's coordinate system. */
internal data class WeekViewportGeometry(
    val width: Int,
    val height: Int,
    val bottom: Int,
    val side: Int,
    val contentHeight: Int,
) {
    val viewportHeight: Int get() = (height - bottom).coerceAtLeast(0)
    val dayWidth: Int get() = (width - side * 2).coerceAtLeast(0)
    val needsScroll: Boolean get() = contentHeight > viewportHeight

    fun acceptsDrag(x: Float, y: Float): Boolean =
        x >= 0 && x < width && y >= 0 && y < viewportHeight

    fun containsDay(x: Float): Boolean = x >= side && x < width - side
}

/** Cumulative rounding gives placement and hit testing exactly the same seams. */
internal data class WeekRowBounds(val edges: List<Int>) {
    val height: Int get() = edges.last()
    fun top(index: Int): Int = edges[index]
    fun height(index: Int): Int = edges[index + 1] - edges[index]
    fun dayAt(y: Float): Int =
        if (y < 0 || y >= height) -1 else edges.indices.drop(1).first { y < edges[it] } - 1
}

internal fun weekRowBounds(height: Int, weights: List<Float>): WeekRowBounds {
    require(height >= 0 && weights.isNotEmpty() && weights.all { it.isFinite() && it > 0f })
    val total = weights.sum().toDouble()
    var cumulative = 0.0
    val edges = buildList {
        add(0)
        weights.forEachIndexed { index, weight ->
            cumulative += weight
            add(if (index == weights.lastIndex) height else (height * cumulative / total).roundToInt().coerceIn(0, height))
        }
    }
    return WeekRowBounds(edges)
}

/** Measured content includes the real Material button; no guessed button/padding heights. */
internal fun minimumWeekHeightPx(compactHeight: Int, expandedHeight: Int): Int = ceil(
    maxOf(
        compactHeight.toDouble() * TOTAL_DAY_WEIGHT / COMPACT_DAY_WEIGHT,
        expandedHeight.toDouble() * TOTAL_DAY_WEIGHT / dayWeightsFor(0).first(),
    ),
).toInt()

internal fun anchoredWeekScroll(bounds: WeekRowBounds, dayIndex: Int, anchorY: Int, viewportHeight: Int): Int =
    (bounds.top(dayIndex) - anchorY).coerceIn(0, (bounds.height - viewportHeight).coerceAtLeast(0))

internal fun visibleWeekScroll(bounds: WeekRowBounds, dayIndex: Int, currentScroll: Int, viewportHeight: Int): Int {
    val top = bounds.top(dayIndex)
    val bottom = top + bounds.height(dayIndex)
    val lower = minOf(top, bottom - viewportHeight)
    val upper = maxOf(top, bottom - viewportHeight)
    return currentScroll.coerceIn(lower, upper).coerceIn(0, (bounds.height - viewportHeight).coerceAtLeast(0))
}
