package com.github.panlelapin.simplercal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

@Stable
internal class WeekInteraction(
    initialSelectedDayIndex: Int,
    private val onSelectionChanged: (Int) -> Unit,
) {
    var selectedDayIndex by mutableStateOf(initialSelectedDayIndex.coerceIn(0, WEEK_DAY_COUNT - 1))
        private set
    var animatedDayWeights by mutableStateOf(dayWeightsFor(groupIndexFor(selectedDayIndex)))
    var contentExpandedDays by mutableStateOf(expandedDayIndices(selectedDayIndex))
    var animationRequest by mutableStateOf(0)
    var isDragging by mutableStateOf(false)
        private set
    var dragFocusPosition by mutableStateOf(groupIndexFor(selectedDayIndex).toFloat())
        private set

    private var dragOriginFocus = dragFocusPosition
    private var dragOriginWeights = animatedDayWeights
    private var dragOriginSelectedDay = selectedDayIndex
    private var selectedWeekendDay = selectedDayIndex.coerceAtLeast(WEEKEND_START_INDEX)

    val selectedGroupIndex: Int
        get() = groupIndexFor(selectedDayIndex)

    fun selectDay(dayIndex: Int) {
        if (isDragging) cancelDrag()
        val boundedDay = dayIndex.coerceIn(0, WEEK_DAY_COUNT - 1)
        val oldGroup = selectedGroupIndex
        if (boundedDay >= WEEKEND_START_INDEX) selectedWeekendDay = boundedDay
        selectedDayIndex = boundedDay
        onSelectionChanged(boundedDay)
        val newGroup = selectedGroupIndex
        if (newGroup != oldGroup) {
            contentExpandedDays = contentExpandedDays + expandedDayIndices(boundedDay)
            animationRequest += 1
        } else {
            if (animatedDayWeights == dayWeightsFor(newGroup)) {
                contentExpandedDays = expandedDayIndices(boundedDay)
            }
        }
    }

    fun startDrag() {
        if (isDragging) return
        isDragging = true
        dragOriginFocus = selectedGroupIndex.toFloat()
        dragOriginSelectedDay = selectedDayIndex
        selectedWeekendDay = if (selectedDayIndex >= WEEKEND_START_INDEX) selectedDayIndex else WEEKEND_START_INDEX
        dragFocusPosition = dragOriginFocus
        dragOriginWeights = animatedDayWeights
        animationRequest += 1
    }

    fun dragToFocus(focusPosition: Float) {
        if (!isDragging) return
        val boundedFocus =
            focusPosition.coerceIn(
                minimumValue = MIN_FRACTION,
                maximumValue = WEEKEND_START_INDEX.toFloat(),
            )
        dragFocusPosition = boundedFocus
        animatedDayWeights = dragWeightsFromExactOrigin(boundedFocus)
        val roundedGroup = nearestDayGroup(animatedDayWeights, selectedGroupIndex)
        selectedDayIndex =
            if (roundedGroup == WEEKEND_START_INDEX) {
                selectedWeekendDay
            } else {
                roundedGroup
            }
        contentExpandedDays = animatedDayWeights.indices.filter {
            animatedDayWeights[it] > COMPACT_DAY_WEIGHT
        }.toSet()
    }

    fun endDrag() {
        if (!isDragging) return
        val settledGroup = nearestDayGroup(animatedDayWeights, selectedGroupIndex)
        selectedDayIndex =
            if (settledGroup == WEEKEND_START_INDEX) selectedWeekendDay else settledGroup
        animatedDayWeights = dayWeightsFor(settledGroup)
        contentExpandedDays = expandedDayIndices(selectedDayIndex)
        isDragging = false
        onSelectionChanged(selectedDayIndex)
    }

    fun cancelDrag() {
        if (!isDragging) return
        selectedDayIndex = dragOriginSelectedDay
        dragFocusPosition = groupIndexFor(dragOriginSelectedDay).toFloat()
        isDragging = false
        // Keep the displayed geometry. Resume towards the original selection after
        // cancellation, never replace an interrupted frame by its unvisited target.
        contentExpandedDays = contentExpandedDays + expandedDayIndices(selectedDayIndex)
        animationRequest += 1
    }

    private fun dragWeightsFromExactOrigin(focusPosition: Float): List<Float> {
        val delta = focusPosition - dragOriginFocus
        if (delta == MIN_FRACTION) return dragOriginWeights
        if (abs(delta) >= MAX_FRACTION) return dayWeightsForFocus(focusPosition)
        val adjacentFocus =
            (dragOriginFocus + if (delta < MIN_FRACTION) -1f else 1f).coerceIn(
                MIN_FRACTION,
                WEEKEND_START_INDEX.toFloat(),
            )
        val adjacentWeights = dayWeightsForFocus(adjacentFocus)
        return List(WEEK_DAY_COUNT) { dayIndex ->
            interpolateWeight(
                start = dragOriginWeights[dayIndex],
                end = adjacentWeights[dayIndex],
                fraction = abs(delta),
            )
        }
    }
}

@Composable
internal fun rememberWeekInteraction(
    requestedDayIndex: Int,
    selectionRequest: Int,
    contextKey: Any = Unit,
    onSelectionChanged: (Int) -> Unit,
): WeekInteraction {
    val currentListener = rememberUpdatedState(onSelectionChanged)
    val interaction =
        remember(contextKey) {
            WeekInteraction(requestedDayIndex) { dayIndex -> currentListener.value(dayIndex) }
        }
    LaunchedEffect(interaction, selectionRequest) {
        if (selectionRequest > 1) interaction.selectDay(requestedDayIndex)
    }
    return interaction
}

@Composable
internal fun AnimateDayWeights(
    interaction: WeekInteraction,
    dayCount: Int,
    onFrame: (List<Float>, Float) -> Unit = { weights, _ -> interaction.animatedDayWeights = weights },
    onFinished: () -> Unit = {},
) {
    val currentFrame = rememberUpdatedState(onFrame)
    val currentFinished = rememberUpdatedState(onFinished)
    val request = interaction.animationRequest
    LaunchedEffect(interaction, request) {
        runDayWeightAnimation(interaction, dayCount, request, { weights, fraction -> currentFrame.value(weights, fraction) }, { currentFinished.value() })
    }
}

/** Shared with frame-clock tests; only the current request may publish a frame. */
internal suspend fun runDayWeightAnimation(
    interaction: WeekInteraction,
    dayCount: Int,
    request: Int,
    onFrame: (List<Float>, Float) -> Unit,
    onFinished: () -> Unit = {},
) {
    if (interaction.isDragging || interaction.animationRequest != request) return
    val startWeights = interaction.animatedDayWeights
    val targetWeights = dayWeightsFor(interaction.selectedGroupIndex)
    if (startWeights != targetWeights) {
        val durationNanos = DAY_STATE_ANIMATION_DURATION_MILLIS * NANOS_PER_MILLISECOND
        val startNanos = withFrameNanos { it }
        var fraction = MIN_FRACTION
        while (fraction < MAX_FRACTION) {
            val frameNanos = withFrameNanos { it }
            // Pointer input may invalidate this request before recomposition cancels
            // LaunchedEffect. A superseded timer must never overwrite a drag or tap.
            if (interaction.isDragging || interaction.animationRequest != request) return
            fraction = ((frameNanos - startNanos).toDouble() / durationNanos).toFloat().coerceIn(MIN_FRACTION, MAX_FRACTION)
            onFrame(List(dayCount) { index -> interpolateWeight(startWeights[index], targetWeights[index], fraction) }, fraction)
        }
    }
    if (interaction.isDragging || interaction.animationRequest != request) return
    onFrame(targetWeights, MAX_FRACTION)
    interaction.contentExpandedDays = expandedDayIndices(interaction.selectedDayIndex)
    onFinished()
}

/** Compare actual row boundaries, including layouts interrupted between non-adjacent groups. */
internal fun nearestDayGroup(weights: List<Float>, preferredGroup: Int): Int =
    (0..WEEKEND_START_INDEX).minWith(
        compareBy<Int> { group ->
            val target = dayWeightsFor(group)
            var boundary = 0f
            weights.indices.sumOf { index ->
                boundary += weights[index] - target[index]
                (boundary * boundary).toDouble()
            }
        }.thenBy { if (it == preferredGroup) 0 else 1 },
    )
