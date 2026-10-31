package com.github.panlelapin.simplercal

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import kotlin.math.ceil

internal data class WeekWindowInsets(val mandatory: WindowInsets, val navigation: WindowInsets, val safe: WindowInsets)

// Production reads the platform objects. Tests supply deterministic nonzero insets.
internal val LocalWeekWindowInsets = staticCompositionLocalOf<WeekWindowInsets?> { null }

@Composable
internal fun weekWindowInsets(): WeekWindowInsets = LocalWeekWindowInsets.current ?: WeekWindowInsets(
    WindowInsets.mandatorySystemGestures, WindowInsets.navigationBars, WindowInsets.safeDrawing,
)

/** ScrollState remains the owner of scroll, accessibility, restoration and user flings. */
internal class WeekScrollCoordinator(val scroll: ScrollState, initialSelection: Int) {
    var geometry: WeekViewportGeometry? = null
    var bounds: WeekRowBounds? = null
    var selectedDayIndex = initialSelection
        private set
    private var anchor: Pair<Int, Int>? = null

    fun select(dayIndex: Int) {
        if (selectedDayIndex == dayIndex) return
        selectedDayIndex = dayIndex
        val viewport = geometry ?: return
        val current = bounds ?: return
        anchor = if (viewport.needsScroll) dayIndex to
            (current.top(dayIndex) - scroll.value).coerceIn((1 - current.height(dayIndex)).coerceAtMost(0),
                (viewport.viewportHeight - 1).coerceAtLeast(0))
        else null
    }

    fun follow(weights: List<Float>, fraction: Float = 1f) {
        val viewport = geometry ?: return
        val active = anchor ?: return
        if (!viewport.needsScroll || scroll.isScrollInProgress) {
            anchor = null // A manual scroll always takes ownership back from the tap animation.
            return
        }
        val rows = weekRowBounds(viewport.contentHeight, weights)
        // A partially clipped row must not snap to y=0 on its first frame.
        val preferredY = if (active.second < 0) (active.second * (1f - fraction)).roundToInt() else active.second
        val anchorY = minOf(preferredY, (viewport.viewportHeight - rows.height(active.first)).coerceAtLeast(0))
        val target = anchoredWeekScroll(rows, active.first, anchorY, viewport.viewportHeight)
        // Called atomically with the weight update, never from composition or measure.
        scroll.dispatchRawDelta((target - scroll.value).toFloat())
    }

    fun finish() { anchor = null }
}

/**
 * Insets and real text/button sizes are resolved in measure. The subcomposed viewport
 * only changes when constraints/content metrics change; animated weights are read by
 * the child Layout, so animation frames do not recompose this viewport or its texts.
 */
@Composable
internal fun WeekViewport(
    insets: WeekWindowInsets,
    label: DayLabelMetrics,
    maximumEventCount: Int,
    coordinator: WeekScrollCoordinator,
    modifier: Modifier = Modifier,
    content: @Composable (WeekViewportGeometry) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bottom = maxOf(
            (insets.mandatory.getBottom(this) * BOTTOM_GESTURE_GUTTER_FRACTION).roundToInt(),
            insets.navigation.getBottom(this), insets.safe.getBottom(this),
        ).coerceIn(0, height)
        val side = (maxOf(MINIMUM_RIGHT_GESTURE_GUTTER.toPx(), insets.mandatory.getRight(this, layoutDirection).toFloat()) *
            RIGHT_GESTURE_GUTTER_FRACTION).roundToInt().coerceIn(0, width / 2)
        val stroke = DAY_SEPARATOR_THICKNESS.roundToPx()
        val textWidth = (width - side * 2 - stroke * 2 - label.width.roundToPx() -
            (DAY_CONTENT_HORIZONTAL_PADDING + DAY_ACCENT_STRIPE_WIDTH).roundToPx() - DAY_CONTENT_HORIZONTAL_PADDING.roundToPx()).coerceAtLeast(0)
        val samples = subcompose(WeekSlot.MinimumContent) { MinimumDayContent(maximumEventCount) }
            .map { it.measure(Constraints(maxWidth = textWidth)) }
        val labelHeight = label.height.roundToPx()
        val compactText = maxOf(samples[0].height, samples[3].height, labelHeight)
        val compact = maxOf(ceil(MINIMUM_DAY_TOUCH_HEIGHT.toPx().toDouble()).toInt(), compactText + stroke * 2)
        val expanded = maxOf(samples[1].height, samples[2].height, samples[4].height,
            samples[5].height, labelHeight + DAY_LABEL_TOP_PADDING.roundToPx()) + stroke * 2
        val viewport = (height - bottom).coerceAtLeast(0)
        val geometry = WeekViewportGeometry(width, height, bottom, side, maxOf(viewport, minimumWeekHeightPx(compact, expanded)))
        coordinator.geometry = geometry
        val child = subcompose(WeekSlot.Viewport) { content(geometry) }.single()
            .measure(Constraints.fixed(width, height))
        layout(width, height) { child.placeRelative(0, 0) }
    }
}

private enum class WeekSlot { MinimumContent, Viewport }

/** Independent unplaced probes: each is measured once, never rendered or interactive. */
@Composable
private fun MinimumDayContent(maximumEventCount: Int) {
    listOf(false, true).forEach { italic ->
        Column(Modifier.clearAndSetSemantics {}) { DayEventTitle("Ag", italic) }
        Column(Modifier.clearAndSetSemantics {}) {
            repeat(SCHEDULE_SLOT_COUNT) { DayEventTitle("Ag", italic) }
        }
        Column(Modifier.clearAndSetSemantics {}) {
            repeat(SCHEDULE_SLOT_COUNT) { DayEventTitle("Ag", italic) }
            AllEventsButton(maxOf(maximumEventCount, 1), {}, Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
internal fun WeekDayLayout(
    geometry: WeekViewportGeometry,
    weights: () -> List<Float>,
    coordinator: WeekScrollCoordinator,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var viewportInitialized by rememberSaveable { mutableStateOf(false) }
    var previousGeometry by remember { mutableStateOf(geometry) }
    LaunchedEffect(geometry) {
        val selected = coordinator.selectedDayIndex
        val oldBounds = weekRowBounds(previousGeometry.contentHeight, weights())
        val wasVisible = oldBounds.top(selected) < coordinator.scroll.value + previousGeometry.viewportHeight &&
            oldBounds.edges[selected + 1] > coordinator.scroll.value
        val shouldReveal = !viewportInitialized || (previousGeometry != geometry && wasVisible)
        if (shouldReveal && geometry.needsScroll && !coordinator.scroll.isScrollInProgress) {
            val bounds = weekRowBounds(geometry.contentHeight, weights())
            coordinator.scroll.scrollTo(visibleWeekScroll(bounds, selected, coordinator.scroll.value, geometry.viewportHeight))
        }
        // A restored composition starts with the current geometry, so returning
        // from settings never runs the resize/reveal policy on its saved viewport.
        viewportInitialized = true
        previousGeometry = geometry
    }
    val scrolling = if (geometry.needsScroll) Modifier.verticalScroll(coordinator.scroll) else Modifier
    Layout(content = content, modifier = modifier.then(scrolling)) { measurables, constraints ->
        val bounds = weekRowBounds(geometry.contentHeight, weights())
        coordinator.bounds = bounds
        val children = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixed(constraints.maxWidth, bounds.height(index)))
        }
        layout(constraints.maxWidth, bounds.height) {
            children.forEachIndexed { index, child -> child.placeRelative(0, bounds.top(index)) }
        }
    }
}

