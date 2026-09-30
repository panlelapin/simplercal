package com.github.panlelapin.simplercal

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
internal fun ColumnScope.DayRow(
    state: DayRowState,
    onClick: () -> Unit,
    onShowDetails: () -> Unit = {},
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
    val appearance = dayAppearance(state)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(state.weight)
                .combinedClickable(role = Role.Button, onClick = onClick,
                    onLongClick = onShowDetails,
                    onLongClickLabel = androidx.compose.ui.res.stringResource(R.string.action_day_details))
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityDescription
                    stateDescription = expansionDescription
                    selected = state.isSelected
                },
        shape = appearance.combinedShape,
        color = state.appBarBackground,
    ) {
        DayRowLayout(state, appearance, onShowDetails)
    }
}

@Composable
private fun DayRowLayout(
    state: DayRowState,
    appearance: DayAppearance,
    onShowDetails: () -> Unit,
) {
    val borderColor = if (appearance.isHighlighted) MaterialTheme.colorScheme.primary else state.separatorColor
    Box(modifier = Modifier.fillMaxSize().drawWithContent {
        drawContent()
        val stroke = DAY_SEPARATOR_THICKNESS.toPx()
        val outline = appearance.combinedShape.createOutline(
            Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            layoutDirection, this,
        )
        clipRect(
            top = if (appearance.isHighlighted || appearance.hasTopBorder) 0f else stroke,
            bottom = if (appearance.isHighlighted || appearance.hasBottomBorder) size.height else (size.height - stroke).coerceAtLeast(stroke),
        ) {
            translate(stroke / 2f, stroke / 2f) { drawOutline(outline, borderColor, style = Stroke(stroke)) }
        }
    }) {
        Row(modifier = Modifier.dayRowModifier(appearance)) {
            DayLabelContainer(
                state =
                    DayLabelState(
                        day = state.day,
                        isExpanded = state.isExpanded,
                        width = state.dayLabelColumnWidth,
                        textColor = appearance.dayTextColor,
                        appBarBackground = appearance.dayBackground,
                    ),
            )
            DayContentContainer(
                state =
                    DayContentState(
                        isExpanded = state.isContentExpanded,
                        isWEorBankH = state.day.isWEorBankH,
                        isHolidays = state.day.isHolidays,
                        events = state.day.events,
                        background = appearance.dayBackground,
                        accentColor = appearance.dayAccentColor,
                        textColor = appearance.dayTextColor,
                        modifier = Modifier.fillMaxHeight().weight(1f),
                    ),
                onShowDetails = onShowDetails,
            )
        }
    }
}

@Composable
private fun dayAppearance(state: DayRowState): DayAppearance {
    val colorScheme = MaterialTheme.colorScheme
    val isHighlighted = state.dayIndex == state.highlightedDayIndex
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
        dayBackground = colors.background,
        dayAccentColor =
            if (temporalState == DayTemporalState.PAST) {
                colorScheme.secondary
            } else {
                colorScheme.primary
            },
        dayTextColor = colors.foreground,
        hasTopBorder = state.dayIndex != 0 && state.dayIndex != WEEKEND_START_INDEX + 1 &&
            state.dayIndex - 1 != state.highlightedDayIndex,
        hasBottomBorder = false, // Each ordinary seam belongs to the following row only.
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

@Composable
private fun Modifier.dayRowModifier(
    appearance: DayAppearance,
): Modifier {
    return this
            .fillMaxSize()
            .padding(
                start = DAY_SEPARATOR_THICKNESS,
                end = DAY_SEPARATOR_THICKNESS,
                top = if (appearance.isHighlighted || appearance.hasTopBorder) DAY_SEPARATOR_THICKNESS else 0.dp,
                bottom = if (appearance.isHighlighted || appearance.hasBottomBorder) DAY_SEPARATOR_THICKNESS else 0.dp,
            )
}

@Composable
private fun DayLabelContainer(state: DayLabelState) {
    Surface(
        modifier = Modifier.width(state.width).fillMaxHeight(),
        shape = RectangleShape,
        color = state.appBarBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = DAY_LABEL_HORIZONTAL_PADDING,
                            end = DAY_LABEL_HORIZONTAL_PADDING,
                            top = if (state.isExpanded) 2.dp else 0.dp,
                        ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = if (state.isExpanded) Arrangement.Top else Arrangement.Center,
            ) {
                Text(
                    text = dayLabelText(state.day),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontSize = (MaterialTheme.typography.titleLarge.fontSize.value - 2f).sp,
                        ),
                    color = state.textColor,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun DayContentContainer(state: DayContentState, onShowDetails: () -> Unit) {
    val accentWidth = if (state.isHolidays) DAY_ACCENT_STRIPE_WIDTH else 0.dp
    Surface(
        modifier = state.modifier,
        shape = RectangleShape,
        color = state.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isHolidays) {
                Spacer(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(accentWidth)
                            .background(state.accentColor),
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp + accentWidth, end = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                val displayedEvents =
                    state.events.take(
                        if (state.isExpanded) {
                            if (state.events.size > EXPANDED_CONTENT_LINE_COUNT) EXPANDED_CONTENT_LINE_COUNT - 1 else EXPANDED_CONTENT_LINE_COUNT
                        } else {
                            COMPACT_CONTENT_LINE_COUNT
                        },
                    )
                displayedEvents.forEach { event ->
                    Text(
                        text = event.title,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontStyle =
                                    if (state.isWEorBankH) FontStyle.Italic else FontStyle.Normal,
                                fontSynthesis = FontSynthesis.Style,
                            ),
                        color = state.textColor,
                        textAlign = TextAlign.Start,
                    )
                }
                if (state.isExpanded && state.events.size > EXPANDED_CONTENT_LINE_COUNT) {
                    TextButton(onClick = onShowDetails) {
                        Text(androidx.compose.ui.res.stringResource(R.string.action_all_events, state.events.size))
                    }
                }
            }
        }
    }
}

@Composable
internal fun dayLabelColumnWidth(days: List<WeekDay>): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontSize = (MaterialTheme.typography.titleLarge.fontSize.value - 2f).sp,
        )
    val widestLabelWidth =
        days.maxOf { day ->
            val measuredText =
                textMeasurer.measure(
                    text = dayLabelText(day),
                    style = textStyle,
                )
            val measuredSize = measuredText.size
            measuredSize.width
        }
    return with(density) { widestLabelWidth.toDp() } + DAY_LABEL_HORIZONTAL_PADDING * 2
}

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
): List<WeekDay> {
    return (0 until WEEK_DAY_COUNT).map { dayIndex ->
        val date = monday.plusDays(dayIndex.toLong())
        val isSimulation = simulationMode == SimulationMode.SIMULATION
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
                if (isSimulation) {
                    dayIndex <= 3
                } else {
                    markers[date]?.isHolidays == true
                },
            events = eventsByDay.getOrNull(dayIndex).orEmpty(),
        )
    }
}

private val DAY_ACCESSIBILITY_DATE_FORMAT =
    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)

internal fun temporalStateFor(
    date: LocalDate,
    referenceDate: LocalDate,
): DayTemporalState =
    when {
        date < referenceDate -> DayTemporalState.PAST
        date > referenceDate -> DayTemporalState.FUTURE
        else -> DayTemporalState.CURRENT
    }
