package com.github.panlelapin.simplercal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
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
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
internal fun ColumnScope.DayRow(
    state: DayRowState,
    onClick: () -> Unit,
) {
    val day = state.day
    val stateDescription = if (state.isExpanded) "expanded" else "compact"
    val appearance = dayAppearance(state)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(state.weight)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${day.abbreviation}, $stateDescription"
                },
        shape = appearance.combinedShape,
        color = state.appBarBackground,
    ) {
        DayRowLayout(state, appearance)
    }
}

@Composable
private fun DayRowLayout(
    state: DayRowState,
    appearance: DayAppearance,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.dayRowModifier(state, appearance)) {
            DayLabelContainer(
                state =
                    DayLabelState(
                        day = state.day,
                        isExpanded = state.isExpanded,
                        width = state.dayLabelColumnWidth,
                        separatorColor = appearance.innerContainerBorderColor,
                        hasTopBorder = appearance.hasTopBorder,
                        hasBottomBorder = appearance.hasBottomBorder,
                        isHighlighted = appearance.isHighlighted,
                        shape = RectangleShape,
                        textColor = appearance.dayTextColor,
                        appBarBackground = appearance.dayBackground,
                    ),
            )
            DayContentContainer(
                state =
                    DayContentState(
                        isExpanded = state.isContentExpanded,
                        isWEorBankH = state.day.isWEorBankH,
                        separatorColor = appearance.innerContainerBorderColor,
                        hasTopBorder = appearance.hasTopBorder,
                        hasBottomBorder = appearance.hasBottomBorder,
                        isHighlighted = appearance.isHighlighted,
                        shape = RectangleShape,
                        background = appearance.dayBackground,
                        accentColor = appearance.dayAccentColor,
                        textColor = appearance.dayTextColor,
                        modifier = Modifier.fillMaxHeight().weight(1f),
                    ),
            )
        }
        Canvas(modifier = Modifier.matchParentSize().clip(appearance.combinedShape)) {
            drawDayBorder(
                color = state.separatorColor,
                showTop = appearance.hasTopBorder,
                showBottom = appearance.hasBottomBorder,
                showVertical = !appearance.isHighlighted,
            )
        }
    }
}

@Composable
private fun dayAppearance(state: DayRowState): DayAppearance {
    val colorScheme = MaterialTheme.colorScheme
    val isHighlighted = state.dayIndex == state.highlightedDayIndex
    val isPast =
        state.highlightedDayIndex != null && state.dayIndex < state.highlightedDayIndex
    val isFutureOrToday =
        state.highlightedDayIndex != null && state.dayIndex >= state.highlightedDayIndex
    val (dayBackground, dayTextColor) =
        dayColors(
            colorScheme = colorScheme,
            isWEorBankH = state.day.isWEorBankH,
            isPast = isPast,
            isFutureOrToday = isFutureOrToday,
            isDarkTheme = state.isDarkTheme,
        )
    return DayAppearance(
        isHighlighted = isHighlighted,
        innerContainerBorderColor =
            if (isHighlighted) colorScheme.primary else state.separatorColor,
        dayBackground = dayBackground,
        dayAccentColor = if (isPast) colorScheme.secondary else colorScheme.primary,
        dayTextColor = dayTextColor,
        hasTopBorder = state.dayIndex != 0 && state.dayIndex != WEEKEND_START_INDEX + 1,
        hasBottomBorder =
            state.dayIndex != WEEKEND_START_INDEX && state.dayIndex != WEEK_DAY_COUNT - 1,
        highlightBorderInset = if (isHighlighted) DAY_SEPARATOR_THICKNESS else 0.dp,
        combinedShape = dayRowShape(state.dayIndex),
    )
}

private fun dayColors(
    colorScheme: ColorScheme,
    isWEorBankH: Boolean,
    isPast: Boolean,
    isFutureOrToday: Boolean,
    isDarkTheme: Boolean,
): Pair<Color, Color> =
    when {
        isWEorBankH ->
            colorScheme.surface to
                if (isPast) colorScheme.onTertiary else colorScheme.onSurface
        isFutureOrToday ->
            (if (isDarkTheme) Color.Black else Color.White) to colorScheme.onSurface
        else -> colorScheme.surfaceContainer to colorScheme.onSurface
    }

@Composable
private fun Modifier.dayRowModifier(
    state: DayRowState,
    appearance: DayAppearance,
): Modifier {
    val baseModifier =
        this
            .fillMaxSize()
            .padding(
                start = 2.dp,
                end = 2.dp,
                top = appearance.highlightBorderInset,
                bottom = appearance.highlightBorderInset,
            )
    val borderedModifier =
        baseModifier
            .clip(appearance.combinedShape)
            .background(state.appBarBackground)
            .then(
                if (appearance.isHighlighted || state.day.isWEorBankH) {
                    Modifier.border(
                        border =
                            BorderStroke(
                                DAY_SEPARATOR_THICKNESS,
                                if (appearance.isHighlighted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            ),
                        shape = appearance.combinedShape,
                    )
                } else {
                    Modifier
                },
            )
    return borderedModifier.padding(
        start = 0.dp,
        end = 0.dp,
        top = if (state.dayIndex == WEEKEND_START_INDEX + 1) 0.dp else 2.dp,
        bottom = if (state.dayIndex == WEEKEND_START_INDEX) 0.dp else 2.dp,
    )
}

@Composable
private fun DayLabelContainer(state: DayLabelState) {
    Surface(
        modifier = Modifier.width(state.width).fillMaxHeight(),
        shape = state.shape,
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
                            top = 2.dp,
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
            if (!state.isHighlighted) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawDayBorder(
                        color = state.separatorColor,
                        showTop = state.hasTopBorder,
                        showBottom = state.hasBottomBorder,
                        showVertical = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayContentContainer(state: DayContentState) {
    Surface(
        modifier = state.modifier,
        shape = state.shape,
        color = state.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!state.isHighlighted) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawDayBorder(
                        color = state.separatorColor,
                        showTop = state.hasTopBorder,
                        showBottom = state.hasBottomBorder,
                        showVertical = false,
                    )
                }
            }
            Spacer(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(DAY_ACCENT_STRIPE_WIDTH)
                        .background(state.accentColor),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(state.shape)
                        .padding(start = 8.dp + DAY_ACCENT_STRIPE_WIDTH, end = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                val lineCount =
                    if (state.isExpanded) {
                        EXPANDED_CONTENT_LINE_COUNT
                    } else {
                        COMPACT_CONTENT_LINE_COUNT
                    }
                repeat(lineCount) { lineIndex ->
                    Text(
                        text = dayContentText(lineIndex, state.isWEorBankH),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = state.textColor,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

private fun dayContentText(
    lineIndex: Int,
    isWEorBankH: Boolean,
): AnnotatedString =
    buildAnnotatedString {
        val line = "${lineIndex + 1} $DAY_CONTENT_TEXT"
        if (isWEorBankH) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(line) }
        } else {
            append(line)
        }
    }

private fun DrawScope.drawDayBorder(
    color: Color,
    showTop: Boolean,
    showBottom: Boolean,
    showVertical: Boolean = true,
) {
    val strokeWidth = DAY_SEPARATOR_THICKNESS.toPx()
    val halfStroke = strokeWidth / 2f
    if (showTop) {
        drawLine(
            color = color,
            start = Offset(x = halfStroke, y = halfStroke),
            end = Offset(x = size.width - halfStroke, y = halfStroke),
            strokeWidth = strokeWidth,
        )
    }
    if (showBottom) {
        drawLine(
            color = color,
            start = Offset(x = halfStroke, y = size.height - halfStroke),
            end = Offset(x = size.width - halfStroke, y = size.height - halfStroke),
            strokeWidth = strokeWidth,
        )
    }
    if (showVertical) {
        drawLine(
            color = color,
            start = Offset(x = halfStroke, y = halfStroke),
            end = Offset(x = halfStroke, y = size.height - halfStroke),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(x = size.width - halfStroke, y = halfStroke),
            end = Offset(x = size.width - halfStroke, y = size.height - halfStroke),
            strokeWidth = strokeWidth,
        )
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
        append(day.dayOfMonth.toString())
    }

internal fun currentWeek(
    today: LocalDate = LocalDate.now(),
    simulationMode: SimulationMode = SimulationMode.OFF,
): List<WeekDay> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0 until WEEK_DAY_COUNT).map { dayIndex ->
        val date = monday.plusDays(dayIndex.toLong())
        val isSimulation = simulationMode == SimulationMode.SIMULATION
        WeekDay(
            abbreviation = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            dayOfMonth = date.dayOfMonth,
            isWEorBankH =
                if (isSimulation) {
                    dayIndex == 0 ||
                        date.dayOfWeek == DayOfWeek.SATURDAY ||
                        date.dayOfWeek == DayOfWeek.SUNDAY
                } else {
                    date.dayOfWeek == DayOfWeek.SATURDAY ||
                        date.dayOfWeek == DayOfWeek.SUNDAY ||
                        date.dayOfWeek == DayOfWeek.TUESDAY
                },
            isHolidays =
                if (isSimulation) {
                    dayIndex <= 3
                } else {
                    date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.TUESDAY
                },
        )
    }
}
