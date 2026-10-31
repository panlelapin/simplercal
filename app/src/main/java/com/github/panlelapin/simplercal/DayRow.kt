package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DayRow(
    state: DayRowState,
    onClick: () -> Unit,
    onShowDetails: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val day = state.day
    val expansionDescription =
        androidx.compose.ui.res.stringResource(
            if (state.isExpanded) R.string.state_expanded else R.string.state_compact,
        )
    val eventDescription =
        androidx.compose.ui.res.pluralStringResource(
            R.plurals.event_count,
            day.events.size,
            day.events.size,
        )
    val spokenDate = day.date.format(DAY_ACCESSIBILITY_DATE_FORMAT)
    val accessibilityDescription =
        androidx.compose.ui.res.stringResource(
            R.string.day_accessibility_description,
            spokenDate,
            eventDescription,
        )
    val allEventsLabel = androidx.compose.ui.res.stringResource(R.string.action_all_events, day.events.size)
    val appearance = dayAppearance(state)
    val borderColor = if (appearance.isHighlighted) MaterialTheme.colorScheme.primary else state.separatorColor
    Surface(
        onClick = onClick,
        enabled = day.isEnabled,
        modifier =
            modifier
                .fillMaxWidth()
                .dayOutline(appearance, borderColor)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = accessibilityDescription
                    stateDescription = expansionDescription
                    selected = state.isSelected
                    if (day.isEnabled && day.events.isNotEmpty()) {
                        customActions = listOf(CustomAccessibilityAction(allEventsLabel) {
                            onShowDetails()
                            true
                        })
                    }
                },
        shape = appearance.combinedShape,
        color = appearance.dayBackground,
    ) {
        DayRowLayout(state, appearance, onShowDetails)
    }
}

private fun Modifier.dayOutline(appearance: DayAppearance, borderColor: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = DAY_SEPARATOR_THICKNESS.toPx()
    if (size.width <= stroke || size.height <= stroke) return@drawWithContent
    val outer = appearance.combinedShape.createOutline(size, layoutDirection, this)
    val corners = (outer as? Outline.Rounded)?.roundRect
    val inset = stroke / 2f
    val tl = ((corners?.topLeftCornerRadius?.x ?: 0f) - inset).coerceAtLeast(0f)
    val tr = ((corners?.topRightCornerRadius?.x ?: 0f) - inset).coerceAtLeast(0f)
    val br = ((corners?.bottomRightCornerRadius?.x ?: 0f) - inset).coerceAtLeast(0f)
    val bl = ((corners?.bottomLeftCornerRadius?.x ?: 0f) - inset).coerceAtLeast(0f)
    val left = inset
    val right = size.width - inset
    val top = inset
    val bottom = size.height - inset
    if (appearance.isHighlighted) {
        val outline = Outline.Rounded(RoundRect(left, top, right, bottom,
            CornerRadius(tl), CornerRadius(tr), CornerRadius(br), CornerRadius(bl)))
        drawOutline(outline, borderColor, style = Stroke(stroke))
    } else {
        // An open contour omits horizontal seams without clipping the vertical
        // sides. Square weekend sides reach their shared edge with no gap.
        val path = Path().apply {
            if (br > 0f) {
                moveTo(right - br, bottom)
                arcTo(Rect(right - br * 2, bottom - br * 2, right, bottom), 90f, -90f, false)
            } else moveTo(right, size.height)
            lineTo(right, if (tr > 0f) top + tr else if (appearance.hasTopBorder) top else 0f)
            if (tr > 0f) arcTo(Rect(right - tr * 2, top, right, top + tr * 2), 0f, -90f, false)
            if (appearance.hasTopBorder) lineTo(left + tl, top) else moveTo(left + tl, top)
            if (tl > 0f) arcTo(Rect(left, top, left + tl * 2, top + tl * 2), 270f, -90f, false)
            else if (!appearance.hasTopBorder) moveTo(left, 0f)
            lineTo(left, if (bl > 0f) bottom - bl else size.height)
            if (bl > 0f) arcTo(Rect(left, bottom - bl * 2, left + bl * 2, bottom), 180f, -90f, false)
        }
        drawPath(path, borderColor, style = Stroke(stroke))
    }

}

@Composable
private fun DayRowLayout(
    state: DayRowState,
    appearance: DayAppearance,
    onShowDetails: () -> Unit,
) {
    Row(modifier = Modifier.dayRowModifier(appearance)) {
        DayLabelContainer(
            state =
                DayLabelState(
                    day = state.day,
                    isExpanded = state.isExpanded,
                    width = state.dayLabelColumnWidth,
                    textColor = appearance.dayTextColor,
                ),
        )
        DayContentContainer(
            state =
                DayContentState(
                    isExpanded = state.isContentExpanded,
                    isWEorBankH = state.day.isWEorBankH,
                    isHolidays = state.day.isHolidays,
                    schedule = state.schedule,
                    eventCount = state.day.events.size,
                    accentColor = appearance.dayAccentColor,
                    textColor = appearance.dayTextColor,
                ),
            onShowDetails = onShowDetails,
            modifier = Modifier.fillMaxHeight().weight(1f),
        )
    }
}

@Composable
private fun dayAppearance(state: DayRowState): DayAppearance {
    val colorScheme = MaterialTheme.colorScheme
    val isHighlighted = state.day.isEnabled && state.dayIndex == state.highlightedDayIndex
    val temporalState = temporalStateFor(state.day.date, state.referenceDate)
    val colors =
        dayColors(
            colorScheme = colorScheme,
            isWEorBankH = state.day.isWEorBankH,
            temporalState = temporalState,
            isDarkTheme = state.isDarkTheme,
        )
    return DayAppearance(
        isHighlighted = isHighlighted,
        dayBackground = if (state.day.isEnabled) colors.background else colorScheme.surfaceContainer,
        dayAccentColor =
            if (temporalState == DayTemporalState.PAST) {
                colorScheme.secondary
            } else {
                colorScheme.primary
            },
        dayTextColor = if (state.day.isEnabled) colors.foreground else colorScheme.onSurface.copy(alpha = 0.38f),
        hasTopBorder = state.dayIndex != 0 && state.dayIndex != WEEKEND_START_INDEX + 1 &&
            state.dayIndex - 1 != state.highlightedDayIndex,
        combinedShape = dayRowShape(state.dayIndex),
    )
}

private fun dayColors(
    colorScheme: ColorScheme,
    isWEorBankH: Boolean,
    temporalState: DayTemporalState,
    isDarkTheme: Boolean,
): DayColors =
    when {
        isWEorBankH -> DayColors(colorScheme.surface, colorScheme.onSurface)
        temporalState != DayTemporalState.PAST ->
            DayColors(
                background = if (isDarkTheme) Color.Black else Color.White,
                foreground = colorScheme.onSurface,
            )
        else -> DayColors(colorScheme.surfaceContainer, colorScheme.onSurfaceVariant)
    }

private fun Modifier.dayRowModifier(
    appearance: DayAppearance,
): Modifier {
    return this
            .fillMaxSize()
            .padding(
                start = DAY_SEPARATOR_THICKNESS,
                end = DAY_SEPARATOR_THICKNESS,
                top = if (appearance.isHighlighted || appearance.hasTopBorder) DAY_SEPARATOR_THICKNESS else 0.dp,
                bottom = if (appearance.isHighlighted) DAY_SEPARATOR_THICKNESS else 0.dp,
            )
}

@Composable
private fun DayLabelContainer(state: DayLabelState) {
    Box(
        modifier = Modifier.width(state.width).fillMaxHeight()
            .padding(horizontal = DAY_LABEL_HORIZONTAL_PADDING)
            .padding(top = if (state.isExpanded) DAY_LABEL_TOP_PADDING else 0.dp),
        contentAlignment = if (state.isExpanded) Alignment.TopEnd else Alignment.CenterEnd,
    ) {
        Text(
            text = dayLabelText(state.day),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = dayLabelStyle(),
            color = state.textColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DayContentContainer(state: DayContentState, onShowDetails: () -> Unit, modifier: Modifier) {
    val accentWidth = if (state.isHolidays) DAY_ACCENT_STRIPE_WIDTH else 0.dp
    Column(
        modifier = modifier.drawBehind {
            if (state.isHolidays) drawRect(state.accentColor, Offset.Zero, Size(accentWidth.toPx(), size.height))
        }.padding(start = DAY_CONTENT_HORIZONTAL_PADDING + accentWidth, end = DAY_CONTENT_HORIZONTAL_PADDING),
        verticalArrangement = Arrangement.Center,
    ) {
        val lines = if (state.isExpanded) state.schedule.lines else listOf(
            state.schedule.lines.firstOrNull { it.events.isNotEmpty() } ?: state.schedule.lines.first(),
        )
        lines.forEach { line ->
            DayEventTitle(scheduleLineText(line), state.isWEorBankH, state.textColor)
        }
        if (state.isExpanded && state.eventCount > 0) {
            AllEventsButton(state.eventCount, onShowDetails)
        }
    }
}

@Composable
private fun scheduleLineText(line: ScheduleLine): String {
    val clock = if (line.minutes in 0..1439) String.format(Locale.ROOT, "%02d:%02d", line.minutes / 60, line.minutes % 60) else "—"
    val time = androidx.compose.ui.res.stringResource(if (line.index == 0) R.string.schedule_until else R.string.schedule_from, clock)
    val first = line.events.firstOrNull() ?: return time
    val title = if (line.events.size == 1) first.title else androidx.compose.ui.res.stringResource(
        R.string.schedule_more_events, first.title, line.events.size - 1,
    )
    return androidx.compose.ui.res.stringResource(R.string.schedule_line_content, time, title)
}

/** Shared by the visible content and the unplaced minimum-height measurement probes. */
@Composable
internal fun DayEventTitle(title: String, italic: Boolean, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontSynthesis = FontSynthesis.Style,
        ),
        color = color,
        textAlign = TextAlign.Start,
    )
}

@Composable
internal fun AllEventsButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier.clearAndSetSemantics { testTag = "all-events" }) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(androidx.compose.ui.res.stringResource(R.string.action_all_events, count))
    }
}

internal data class DayLabelMetrics(val width: Dp, val height: Dp)

@Composable
internal fun rememberDayLabelMetrics(days: List<WeekDay>): DayLabelMetrics {
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val style = dayLabelStyle()
    val labels = remember(days) { days.map(::dayLabelText) }
    return remember(labels, style, density, direction, configuration, measurer) {
        val sizes = labels.map { measurer.measure(it, style = style, maxLines = 1).size }
        with(density) {
            DayLabelMetrics((sizes.maxOf { it.width } + DAY_LABEL_HORIZONTAL_PADDING.roundToPx() * 2).toDp(),
                sizes.maxOf { it.height }.toDp())
        }
    }
}

@Composable
private fun dayLabelStyle() = MaterialTheme.typography.titleLarge.copy(
    fontSize = (MaterialTheme.typography.titleLarge.fontSize.value - 2f).sp,
)

private fun dayLabelText(day: WeekDay): AnnotatedString =
    buildAnnotatedString {
        withStyle(
            SpanStyle(fontSize = 12.sp),
        ) { append(day.abbreviation.take(2).uppercase(Locale.ROOT)) }
        append(day.date.dayOfMonth.toString())
    }

internal fun currentWeek(
    monday: LocalDate = currentWeekMonday(),
    simulationMode: SimulationMode = SimulationMode.OFF,
    eventsByDay: List<List<CalendarEvent>> = emptyList(),
    markers: Map<LocalDate, DayMarkers> = emptyMap(),
    allowedYear: Int? = null,
): List<WeekDay> {
    return (0 until WEEK_DAY_COUNT).map { dayIndex ->
        val date = monday.plusDays(dayIndex.toLong())
        val isSimulation = simulationMode == SimulationMode.SIMULATION
        val enabled = allowedYear == null || allowedDate(date, allowedYear)
        WeekDay(
            date = date,
            abbreviation = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            isWEorBankH =
                if (isSimulation) {
                    dayIndex == 0 ||
                        date.dayOfWeek == DayOfWeek.SATURDAY ||
                        date.dayOfWeek == DayOfWeek.SUNDAY
                } else {
                    markers[date]?.isBankHoliday == true || date.dayOfWeek == DayOfWeek.SATURDAY ||
                        date.dayOfWeek == DayOfWeek.SUNDAY
                },
            isHolidays =
                if (!enabled) false else if (isSimulation) {
                    dayIndex <= 3
                } else {
                    markers[date]?.isHolidays == true
                },
            events = if (enabled) eventsByDay.getOrNull(dayIndex).orEmpty() else emptyList(),
            isEnabled = enabled,
        )
    }
}

private val DAY_ACCESSIBILITY_DATE_FORMAT =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)

internal fun temporalStateFor(
    date: LocalDate,
    referenceDate: LocalDate,
): DayTemporalState =
    when {
        date < referenceDate -> DayTemporalState.PAST
        date > referenceDate -> DayTemporalState.FUTURE
        else -> DayTemporalState.CURRENT
    }
