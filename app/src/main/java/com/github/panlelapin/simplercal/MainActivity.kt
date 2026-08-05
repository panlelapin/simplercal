package com.github.panlelapin.simplercal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val PREFERENCES_NAME = "simplercal"
private const val SELECTED_CALENDAR_KEY = "selected_calendar_id"
private const val DAY_ACCENT_COLOR_KEY = "day_accent_color"
private const val SCROLL_MODE_KEY = "scroll_mode"
private const val GITHUB_URL = "https://github.com/panlelapin/simplercal"
private const val TOP_BAR_TITLE = "S52 31\u2002juin"
private const val WEEK_DAY_COUNT = 7
private const val WEEKEND_START_INDEX = 5
private const val COMPACT_DAY_WEIGHT = 6.5f
private const val TOTAL_DAY_WEIGHT = 100f
private const val DAY_STATE_ANIMATION_DURATION_MILLIS = 700
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val DAY_CONTENT_TEXT = "dolor sit amet bla bla truc bigoudi plan plan proutcul"
private const val EXPANDED_CONTENT_LINE_COUNT = 9
private const val COMPACT_CONTENT_LINE_COUNT = 1
private val DAY_LABEL_HORIZONTAL_PADDING = 8.dp
private val DAY_SEPARATOR_THICKNESS = 1.5.dp
private val DAY_SUBCONTAINER_CORNER_RADIUS = 24.dp
private val DAY_ACCENT_STRIPE_HEIGHT = 12.dp
private val MINIMUM_RIGHT_GESTURE_GUTTER = 24.dp
private const val GESTURE_GUTTER_FRACTION = 0.6f
private val DAY_SUBCONTAINER_SHAPE = RoundedCornerShape(DAY_SUBCONTAINER_CORNER_RADIUS)

private val DAY_ABBREVIATIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private data class CalendarChoice(
    val id: Long,
    val name: String,
)

private enum class DayAccentColorRole(
    val preferenceValue: String,
    val label: String,
) {
    PRIMARY("primary", "Primary"),
    SECONDARY("secondary", "Secondary"),
    TERTIARY("tertiary", "Tertiary"),
    ;

    fun color(colorScheme: ColorScheme) =
        when (this) {
            PRIMARY -> colorScheme.primary
            SECONDARY -> colorScheme.secondary
            TERTIARY -> colorScheme.tertiary
        }

    companion object {
        fun fromPreferenceValue(value: String): DayAccentColorRole =
            entries.firstOrNull { it.preferenceValue == value } ?: PRIMARY
    }
}

private enum class WeekScrollMode(
    val preferenceValue: String,
    val label: String,
    val activationOffset: Float,
) {
    MODE_1("mode_1", "Mode 1", 0.5f),
    MODE_2("mode_2", "Mode 2", 0f),
    ;

    companion object {
        fun fromPreferenceValue(value: String): WeekScrollMode =
            entries.firstOrNull { it.preferenceValue == value } ?: MODE_1
    }
}

private data class WeekDay(
    val abbreviation: String,
    val isWeekend: Boolean,
)

/** Hosts the single launcher screen for SimplerCal. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
            val isNightMode =
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
        setContent { SimplerCalApp() }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun SimplerCalApp() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(PREFERENCES_NAME, 0) }
    var dayAccentColorRole by remember {
        mutableStateOf(
            DayAccentColorRole.fromPreferenceValue(
                preferences.getString(
                    DAY_ACCENT_COLOR_KEY,
                    DayAccentColorRole.PRIMARY.preferenceValue,
                ) ?: DayAccentColorRole.PRIMARY.preferenceValue,
            ),
        )
    }
    var scrollMode by remember {
        mutableStateOf(
            WeekScrollMode.fromPreferenceValue(
                preferences.getString(SCROLL_MODE_KEY, WeekScrollMode.MODE_1.preferenceValue)
                    ?: WeekScrollMode.MODE_1.preferenceValue,
            ),
        )
    }
    val colorScheme =
        if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    MaterialTheme(colorScheme = colorScheme) {
        var isSettingsVisible by remember { mutableStateOf(false) }
        if (isSettingsVisible) {
            BackHandler(onBack = { isSettingsVisible = false })
        }
        Box(modifier = Modifier.fillMaxSize()) {
            HelloScreen(
                dayAccentColorRole = dayAccentColorRole,
                scrollMode = scrollMode,
                onSettings = { isSettingsVisible = true },
            )
            if (isSettingsVisible) {
                SettingsScreen(
                    selectedDayAccentColorRole = dayAccentColorRole,
                    onDayAccentColorChange = { role ->
                        preferences.edit { putString(DAY_ACCENT_COLOR_KEY, role.preferenceValue) }
                        dayAccentColorRole = role
                    },
                    selectedScrollMode = scrollMode,
                    onScrollModeChange = { mode ->
                        preferences.edit { putString(SCROLL_MODE_KEY, mode.preferenceValue) }
                        scrollMode = mode
                    },
                    onBack = { isSettingsVisible = false },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun HelloScreen(
    dayAccentColorRole: DayAccentColorRole,
    scrollMode: WeekScrollMode,
    onSettings: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.height(56.dp),
                title = {
                    Text(
                        text = TOP_BAR_TITLE,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onSettings) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = "Settings",
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Previous week",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = "Next week",
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            painter = painterResource(R.drawable.ic_today),
                            contentDescription = "Today",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            WeekView(
                dayAccentColorRole = dayAccentColorRole,
                scrollMode = scrollMode,
            )
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun WeekView(
    dayAccentColorRole: DayAccentColorRole,
    scrollMode: WeekScrollMode,
) {
    val days = remember { currentWeek() }
    var selectedDayIndex by remember { mutableStateOf(0) }
    var animatedDayWeights by remember { mutableStateOf(dayWeightsFor(selectedDayIndex)) }
    var contentExpandedDays by remember { mutableStateOf(expandedDayIndices(selectedDayIndex)) }
    var animationRequest by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    val selectDay: (Int) -> Unit = { dayIndex ->
        if (dayIndex != selectedDayIndex) {
            contentExpandedDays = contentExpandedDays + expandedDayIndices(dayIndex)
            selectedDayIndex = dayIndex
            animationRequest += 1
        }
    }
    val startDrag: () -> Unit = {
        if (!isDragging) {
            isDragging = true
            animationRequest += 1
        }
    }
    val dragToFocus: (Float) -> Unit = { focusPosition ->
        val focusedDayIndex =
            (focusPosition + scrollMode.activationOffset).toInt().coerceIn(0, days.lastIndex)
        selectedDayIndex = focusedDayIndex
        contentExpandedDays = expandedDayIndices(focusedDayIndex)
        animatedDayWeights = dayWeightsForFocus(focusPosition)
    }
    val endDrag: () -> Unit = {
        animatedDayWeights = dayWeightsFor(selectedDayIndex)
        contentExpandedDays = expandedDayIndices(selectedDayIndex)
        isDragging = false
    }
    val currentAnimatedDayWeights = rememberUpdatedState(animatedDayWeights)
    val currentSelectedDayIndex = rememberUpdatedState(selectedDayIndex)
    val currentStartDrag = rememberUpdatedState(startDrag)
    val currentDragToFocus = rememberUpdatedState(dragToFocus)
    val currentEndDrag = rememberUpdatedState(endDrag)
    val dayLabelColumnWidth = dayLabelColumnWidth()
    val mandatoryGesturePadding = WindowInsets.mandatorySystemGestures.asPaddingValues()
    val bottomGestureInset =
        mandatoryGesturePadding.calculateBottomPadding() * GESTURE_GUTTER_FRACTION
    val rightGestureInset =
        maxOf(
                MINIMUM_RIGHT_GESTURE_GUTTER,
                mandatoryGesturePadding.calculateRightPadding(LocalLayoutDirection.current),
            ) * GESTURE_GUTTER_FRACTION
    val density = LocalDensity.current
    val bottomGestureInsetPx = with(density) { bottomGestureInset.toPx() }
    val rightGestureInsetPx = with(density) { rightGestureInset.toPx() }
    LaunchedEffect(animationRequest) {
        if (isDragging) return@LaunchedEffect
        val startWeights = animatedDayWeights
        val targetWeights = dayWeightsFor(selectedDayIndex)
        if (startWeights != targetWeights) {
            val durationNanos = DAY_STATE_ANIMATION_DURATION_MILLIS * NANOS_PER_MILLISECOND
            val startNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
            var fraction = 0f
            while (fraction < 1f) {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                fraction =
                    ((frameNanos - startNanos).toDouble() / durationNanos)
                        .toFloat()
                        .coerceIn(0f, 1f)
                animatedDayWeights =
                    List(days.size) { dayIndex ->
                        interpolateWeight(
                            start = startWeights[dayIndex],
                            end = targetWeights[dayIndex],
                            fraction = fraction,
                        )
                    }
            }
            animatedDayWeights = targetWeights
        }
        contentExpandedDays = expandedDayIndices(selectedDayIndex)
    }
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(scrollMode, bottomGestureInset, rightGestureInset) {
                    var isDragEnabled = false
                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            val dayAreaHeight =
                                (size.height.toFloat() - bottomGestureInsetPx).coerceAtLeast(1f)
                            val dayY = position.y.coerceIn(0f, dayAreaHeight)
                            val dayIndex =
                                dayIndexAtPosition(
                                    y = dayY,
                                    height = dayAreaHeight.toInt(),
                                    weights = currentAnimatedDayWeights.value,
                                )
                            isDragEnabled =
                                scrollMode == WeekScrollMode.MODE_2 ||
                                    (
                                        position.x < size.width.toFloat() - rightGestureInsetPx &&
                                            position.y < dayAreaHeight &&
                                            dayIndex in
                                            expandedDayIndices(currentSelectedDayIndex.value)
                                    )
                            if (isDragEnabled) {
                                currentStartDrag.value()
                                if (scrollMode == WeekScrollMode.MODE_2) {
                                    currentDragToFocus.value(
                                        dayFocusAtPosition(
                                            y = dayY,
                                            height = dayAreaHeight.toInt(),
                                            weights = currentAnimatedDayWeights.value,
                                            activationOffset = scrollMode.activationOffset,
                                        ),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            if (isDragEnabled) currentEndDrag.value()
                            isDragEnabled = false
                        },
                        onDragEnd = {
                            if (isDragEnabled) currentEndDrag.value()
                            isDragEnabled = false
                        },
                        onVerticalDrag = { change, _ ->
                            if (isDragEnabled) {
                                change.consume()
                                val dayAreaHeight =
                                    (size.height.toFloat() - bottomGestureInsetPx).coerceAtLeast(1f)
                                currentDragToFocus.value(
                                    dayFocusAtPosition(
                                        y = change.position.y.coerceIn(0f, dayAreaHeight),
                                        height = dayAreaHeight.toInt(),
                                        weights = currentAnimatedDayWeights.value,
                                        activationOffset = scrollMode.activationOffset,
                                    ),
                                )
                            }
                        },
                    )
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(bottom = bottomGestureInset)
        ) {
            days.forEachIndexed { index, day ->
                val isExpanded = index in expandedDayIndices(selectedDayIndex)
                val isContentExpanded = index in contentExpandedDays
                DayRow(
                    day = day,
                    isExpanded = isExpanded,
                isContentExpanded = isContentExpanded,
                weight = animatedDayWeights[index],
                dayAccentColorRole = dayAccentColorRole,
                dayLabelColumnWidth = dayLabelColumnWidth,
                    onClick = { selectDay(index) },
                )
            }
        }
        Spacer(modifier = Modifier.fillMaxHeight().width(rightGestureInset))
    }
}

private fun dayWeightsFor(selectedDayIndex: Int): List<Float> {
    val expandedDays = expandedDayIndices(selectedDayIndex)
    val compactDayCount = WEEK_DAY_COUNT - expandedDays.size
    val expandedDayWeight =
        (TOTAL_DAY_WEIGHT - COMPACT_DAY_WEIGHT * compactDayCount) / expandedDays.size
    return List(DAY_ABBREVIATIONS.size) { dayIndex ->
        if (dayIndex in expandedDays) {
            expandedDayWeight
        } else {
            COMPACT_DAY_WEIGHT
        }
    }
}

private fun dayWeightsForFocus(focusPosition: Float): List<Float> {
    val boundedFocus = focusPosition.coerceIn(0f, DAY_ABBREVIATIONS.lastIndex.toFloat())
    val lowerDayIndex = boundedFocus.toInt()
    val upperDayIndex = (lowerDayIndex + 1).coerceAtMost(DAY_ABBREVIATIONS.lastIndex)
    val fraction = boundedFocus - lowerDayIndex
    val lowerWeights = dayWeightsFor(lowerDayIndex)
    val upperWeights = dayWeightsFor(upperDayIndex)
    return List(DAY_ABBREVIATIONS.size) { dayIndex ->
        interpolateWeight(
            start = lowerWeights[dayIndex],
            end = upperWeights[dayIndex],
            fraction = fraction,
        )
    }
}

private fun expandedDayIndices(selectedDayIndex: Int): Set<Int> =
    if (selectedDayIndex < WEEKEND_START_INDEX) {
        setOf(selectedDayIndex, selectedDayIndex + 1)
    } else {
        setOf(WEEKEND_START_INDEX, WEEKEND_START_INDEX + 1)
    }

private fun interpolateWeight(
    start: Float,
    end: Float,
    fraction: Float,
): Float = start + (end - start) * fraction

private fun dayIndexAtPosition(
    y: Float,
    height: Int,
    weights: List<Float>,
): Int {
    if (height <= 0 || weights.isEmpty()) return 0
    val boundedY = y.coerceIn(0f, height.toFloat())
    val totalWeight = weights.sum()
    var bottom = 0f
    weights.forEachIndexed { dayIndex, weight ->
        bottom += height * weight / totalWeight
        if (boundedY < bottom) return dayIndex
    }
    return weights.lastIndex
}

private fun dayFocusAtPosition(
    y: Float,
    height: Int,
    weights: List<Float>,
    activationOffset: Float,
): Float {
    if (height <= 0 || weights.isEmpty()) return 0f
    val boundedY = y.coerceIn(0f, height.toFloat())
    val totalWeight = weights.sum()
    var top = 0f
    weights.forEachIndexed { dayIndex, weight ->
        val bottom = top + height * weight / totalWeight
        if (boundedY < bottom) {
            val positionWithinDay =
                if (bottom > top) {
                    (boundedY - top) / (bottom - top)
                } else {
                    0.5f
                }
            return (dayIndex + positionWithinDay - activationOffset)
                .coerceIn(0f, weights.lastIndex.toFloat())
        }
        top = bottom
    }
    return weights.lastIndex.toFloat()
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun ColumnScope.DayRow(
    day: WeekDay,
    isExpanded: Boolean,
    isContentExpanded: Boolean,
    weight: Float,
    dayAccentColorRole: DayAccentColorRole,
    dayLabelColumnWidth: Dp,
    onClick: () -> Unit,
) {
    val stateDescription = if (isExpanded) "expanded" else "compact"
    val separatorColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(weight)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${day.abbreviation}, $stateDescription"
        },
        shape = RectangleShape,
        border = BorderStroke(DAY_SEPARATOR_THICKNESS, separatorColor),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(2.dp),
        ) {
            DayLabelContainer(
                day = day,
                isExpanded = isExpanded,
                dayAccentColorRole = dayAccentColorRole,
                width = dayLabelColumnWidth,
                separatorColor = separatorColor,
            )
            DayContentContainer(
                isExpanded = isContentExpanded,
                dayAccentColorRole = dayAccentColorRole,
                separatorColor = separatorColor,
                modifier = Modifier.fillMaxHeight().weight(1f),
            )
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun DayLabelContainer(
    day: WeekDay,
    isExpanded: Boolean,
    dayAccentColorRole: DayAccentColorRole,
    width: Dp,
    separatorColor: Color,
) {
    val verticalAlignment = if (isExpanded) Alignment.TopEnd else Alignment.CenterEnd
    Surface(
        modifier = Modifier.width(width).fillMaxHeight(),
        shape = DAY_SUBCONTAINER_SHAPE,
        border = BorderStroke(DAY_SEPARATOR_THICKNESS, separatorColor),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(DAY_ACCENT_STRIPE_HEIGHT)
                        .background(dayAccentColorRole.color(MaterialTheme.colorScheme)),
            )
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = verticalAlignment,
            ) {
                Text(
                    text = day.abbreviation.uppercase(Locale.ROOT),
                    modifier =
                        if (isExpanded) {
                            Modifier.padding(end = DAY_LABEL_HORIZONTAL_PADDING, top = 8.dp)
                        } else {
                            Modifier.padding(end = DAY_LABEL_HORIZONTAL_PADDING)
                        },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun DayContentContainer(
    isExpanded: Boolean,
    dayAccentColorRole: DayAccentColorRole,
    separatorColor: Color,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = DAY_SUBCONTAINER_SHAPE,
        border = BorderStroke(DAY_SEPARATOR_THICKNESS, separatorColor),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(DAY_ACCENT_STRIPE_HEIGHT)
                        .background(dayAccentColorRole.color(MaterialTheme.colorScheme)),
            )
            Column(
                modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                repeat(if (isExpanded) EXPANDED_CONTENT_LINE_COUNT else COMPACT_CONTENT_LINE_COUNT) {
                    lineIndex ->
                    Text(
                        text = "${lineIndex + 1} $DAY_CONTENT_TEXT",
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun dayLabelColumnWidth(): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.titleMedium
    val widestLabelWidth =
        DAY_ABBREVIATIONS.maxOf { abbreviation ->
            textMeasurer.measure(
                text = AnnotatedString(abbreviation.uppercase(Locale.ROOT)),
                style = textStyle,
            ).size.width
        }
    return with(density) { widestLabelWidth.toDp() } + DAY_LABEL_HORIZONTAL_PADDING * 2
}

private fun currentWeek(today: LocalDate = LocalDate.now()): List<WeekDay> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return DAY_ABBREVIATIONS.mapIndexed { dayIndex, abbreviation ->
        val date = monday.plusDays(dayIndex.toLong())
        WeekDay(
            abbreviation = abbreviation,
            isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CognitiveComplexMethod", "FunctionName", "LongMethod", "ktlint:standard:function-naming")
private fun SettingsScreen(
    selectedDayAccentColorRole: DayAccentColorRole,
    onDayAccentColorChange: (DayAccentColorRole) -> Unit,
    selectedScrollMode: WeekScrollMode,
    onScrollModeChange: (WeekScrollMode) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var calendars by remember { mutableStateOf(emptyList<CalendarChoice>()) }
    var selectedId by remember {
        mutableStateOf(
            context
                .getSharedPreferences(PREFERENCES_NAME, 0)
                .getLong(SELECTED_CALENDAR_KEY, -1L),
        )
    }
    var isCalendarPickerVisible by remember { mutableStateOf(false) }
    var isDayAccentColorPickerVisible by remember { mutableStateOf(false) }
    var isScrollModePickerVisible by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasPermission = isGranted
            if (isGranted) calendars = loadCalendars(context)
        }
    LaunchedEffect(hasPermission) {
        if (hasPermission) calendars = loadCalendars(context)
    }
    val selectedCalendarName = calendars.firstOrNull { it.id == selectedId }?.name
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Calendar", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (!hasPermission) {
                Text("Allow access to choose an Android calendar.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text("Allow")
                }
            } else {
                Button(
                    onClick = { isCalendarPickerVisible = true },
                    enabled = calendars.isNotEmpty(),
                ) {
                    Text(selectedCalendarName ?: "Choose calendar")
                }
                if (calendars.isEmpty()) Text("No visible calendars available.")
            }
            Spacer(Modifier.height(24.dp))
            Text("Day accent color", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { isDayAccentColorPickerVisible = true }) {
                Text(
                    selectedDayAccentColorRole.label,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Scroll mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { isScrollModePickerVisible = true }) {
                Text(selectedScrollMode.label)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = "SimplerCal v${BuildConfig.OFFICIAL_RELEASE_VERSION}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = GITHUB_URL,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri()))
                        },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (isCalendarPickerVisible) {
        AlertDialog(
            onDismissRequest = { isCalendarPickerVisible = false },
            confirmButton = {
                TextButton(onClick = { isCalendarPickerVisible = false }) { Text("Cancel") }
            },
            title = { Text("Choose calendar") },
            text = {
                Column {
                    calendars.forEach { calendar ->
                        TextButton(onClick = {
                            selectedId = calendar.id
                            context.getSharedPreferences(PREFERENCES_NAME, 0).edit {
                                putLong(SELECTED_CALENDAR_KEY, calendar.id)
                            }
                            isCalendarPickerVisible = false
                        }) {
                            Text(calendar.name)
                        }
                    }
                }
            },
        )
    }
    if (isDayAccentColorPickerVisible) {
        AlertDialog(
            onDismissRequest = { isDayAccentColorPickerVisible = false },
            confirmButton = {
                TextButton(onClick = { isDayAccentColorPickerVisible = false }) { Text("Cancel") }
            },
            title = { Text("Day accent color") },
            text = {
                Column {
                    DayAccentColorRole.entries.forEach { option ->
                        TextButton(onClick = {
                            onDayAccentColorChange(option)
                            isDayAccentColorPickerVisible = false
                        }) {
                            Text(option.label)
                        }
                    }
                }
            },
        )
    }
    if (isScrollModePickerVisible) {
        AlertDialog(
            onDismissRequest = { isScrollModePickerVisible = false },
            confirmButton = {
                TextButton(onClick = { isScrollModePickerVisible = false }) { Text("Cancel") }
            },
            title = { Text("Scroll mode") },
            text = {
                Column {
                    WeekScrollMode.entries.forEach { mode ->
                        TextButton(onClick = {
                            onScrollModeChange(mode)
                            isScrollModePickerVisible = false
                        }) {
                            Text(mode.label)
                        }
                    }
                }
            },
        )
    }
}

@Suppress("MissingUseCall")
private fun loadCalendars(context: Context): List<CalendarChoice> =
    runCatching {
        val cursor =
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                ),
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )
        val calendars =
            cursor?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(CalendarChoice(id = cursor.getLong(0), name = cursor.getString(1)))
                    }
                }
            }
        calendars.orEmpty()
    }.getOrDefault(emptyList())
