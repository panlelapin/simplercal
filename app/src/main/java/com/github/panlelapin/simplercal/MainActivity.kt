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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

private const val PREFERENCES_NAME = "simplercal"
private const val SELECTED_CALENDAR_KEY = "selected_calendar_id"
private const val DAY_ACCENT_COLOR_KEY = "day_accent_color"
private const val THEME_MODE_KEY = "theme_mode"
private const val SCROLL_MODE_KEY = "scroll_mode"
private const val DEBUG1_OUTLINE_COLOR_KEY = "debug1_outline_color"
private const val DEBUG2_RIGHT_BACKGROUND_KEY = "debug2_right_background"
private const val GITHUB_URL = "https://github.com/panlelapin/simplercal"
private const val WEEK_DAY_COUNT = 7
private const val WEEKEND_START_INDEX = 5
private const val COMPACT_DAY_WEIGHT = 6.5f
private const val TOTAL_DAY_WEIGHT = 100f
private const val DAY_STATE_ANIMATION_DURATION_MILLIS = 500
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val DAY_CONTENT_TEXT = "dolor sit amet bla bla truc bigoudi plan plan proutcul"
private const val EXPANDED_CONTENT_LINE_COUNT = 9
private const val COMPACT_CONTENT_LINE_COUNT = 1
private val DAY_LABEL_HORIZONTAL_PADDING = 8.dp
private val DAY_SEPARATOR_THICKNESS = 1.5.dp
private val DAY_SUBCONTAINER_CORNER_RADIUS = 10.dp
private val DAY_ACCENT_STRIPE_WIDTH = 4.dp
private val WEEKEND_SIDE_PILL_WIDTH = 5.dp
private val DAY_ACCENT_STRIPE_SHAPE = RoundedCornerShape(percent = 50)
private val MINIMUM_RIGHT_GESTURE_GUTTER = 24.dp
private const val RIGHT_GESTURE_GUTTER_FRACTION = 0.225f
private const val BOTTOM_GESTURE_GUTTER_FRACTION = 0.72f
private val DAY_SUBCONTAINER_SHAPE = RoundedCornerShape(DAY_SUBCONTAINER_CORNER_RADIUS)

private val DAY_ABBREVIATIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun currentWeekMonday(today: LocalDate = LocalDate.now()): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun weekTitle(monday: LocalDate): AnnotatedString {
    val weekNumber = monday.get(WeekFields.ISO.weekOfWeekBasedYear())
    val month =
        monday.month
            .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            .take(3)
            .uppercase(Locale.ROOT)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 12.sp)) { append("S") }
        append(weekNumber.toString())
        withStyle(SpanStyle(fontSize = 12.sp)) { append(" - ") }
        append(monday.dayOfMonth.toString())
        withStyle(SpanStyle(fontSize = 12.sp)) { append(month) }
    }
}

private data class CalendarChoice(
    val id: Long,
    val name: String,
)

private enum class ThemeMode(
    val preferenceValue: String,
    val label: String,
) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System"),
    ;

    companion object {
        fun fromPreferenceValue(value: String): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

private enum class AccentTheme(
    val preferenceValue: String,
    val label: String,
    private val argb: Long?,
) {
    SYSTEM("system", "System", null),
    ROYAL_BLUE("royal_blue", "Royal blue", 0xFF005AC1),
    INDIGO("indigo", "Indigo", 0xFF3F51B5),
    TEAL("teal", "Teal", 0xFF006B5F),
    MATERIAL_VIOLET("material_violet", "Material violet", 0xFF6750A4),
    PLUM("plum", "Plum", 0xFF7D3C98),
    RASPBERRY("raspberry", "Raspberry", 0xFFA7355C),
    MANDARIN("mandarin", "Mandarin", 0xFFF57C00),
    EMERALD_GREEN("emerald_green", "Emerald green", 0xFF2E7D32),
    TEAL_SECOND("teal_second", "Teal", 0xFF006B5F),
    ;

    fun applyTo(base: ColorScheme, isDark: Boolean): ColorScheme {
        val accent = argb?.let(::Color) ?: return base
        val surfaceFraction = if (isDark) 0.10f else 0.06f
        val secondaryFraction = if (isDark) 0.60f else 0.35f
        val primaryContainer = accent.blendedOver(base.surfaceContainer, 0.30f)
        val secondary = accent.blendedOver(base.surface, secondaryFraction)
        val secondaryContainer = accent.blendedOver(base.surfaceContainer, 0.22f)
        val tertiary = accent.blendedOver(base.surface, if (isDark) 0.72f else 0.62f)
        val surface = accent.blendedOver(base.surface, surfaceFraction)
        val surfaceContainer = accent.blendedOver(base.surfaceContainer, surfaceFraction)
        return base.copy(
            primary = accent,
            onPrimary = readableContentColor(accent, base),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableContentColor(primaryContainer, base),
            secondary = secondary,
            onSecondary = readableContentColor(secondary, base),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableContentColor(secondaryContainer, base),
            tertiary = tertiary,
            onTertiary = readableContentColor(tertiary, base),
            surface = surface,
            surfaceContainer = surfaceContainer,
            surfaceTint = accent,
            outline = accent.blendedOver(base.outline, 0.35f),
            inversePrimary = accent,
        )
    }

    companion object {
        fun fromPreferenceValue(value: String): AccentTheme =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

private fun Color.blendedOver(background: Color, foregroundFraction: Float): Color {
    val fraction = foregroundFraction.coerceIn(0f, 1f)
    return Color(
        red = red * fraction + background.red * (1f - fraction),
        green = green * fraction + background.green * (1f - fraction),
        blue = blue * fraction + background.blue * (1f - fraction),
        alpha = 1f,
    )
}

private fun readableContentColor(background: Color, colorScheme: ColorScheme): Color {
    val surfaceContrast = contrastRatio(background, colorScheme.surface)
    val onSurfaceContrast = contrastRatio(background, colorScheme.onSurface)
    return if (surfaceContrast > onSurfaceContrast) colorScheme.surface else colorScheme.onSurface
}

private fun contrastRatio(first: Color, second: Color): Float =
    (maxOf(first.luminance(), second.luminance()) + 0.05f) /
        (minOf(first.luminance(), second.luminance()) + 0.05f)

private enum class WeekScrollMode(
    val preferenceValue: String,
    val label: String,
    val activationOffset: Float,
) {
    SMOOTH("mode_1", "Discrete", 0.5f),
    PER_DAY("mode_2", "Linear", 0f),
    ;

    companion object {
        fun fromPreferenceValue(value: String): WeekScrollMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SMOOTH
    }
}

private enum class Debug1OutlineColor(
    val preferenceValue: String,
    val label: String,
) {
    BLACK("black", "Black"),
    APP_BAR_BACKGROUND("app_bar_background", "App bar background"),
    ;

    fun resolve(colorScheme: ColorScheme, appBarBackground: Color): Color =
        when (this) {
            BLACK -> colorScheme.onSurface
            APP_BAR_BACKGROUND -> appBarBackground
        }

    companion object {
        fun fromPreferenceValue(value: String): Debug1OutlineColor =
            entries.firstOrNull { it.preferenceValue == value } ?: APP_BAR_BACKGROUND
    }
}

private enum class Debug2RightBackground(
    val preferenceValue: String,
    val label: String,
) {
    SURFACE("surface", "Surface"),
    APP_BAR_BACKGROUND("app_bar_background", "App bar background"),
    ;

    fun resolve(colorScheme: ColorScheme, appBarBackground: Color): Color =
        when (this) {
            SURFACE -> colorScheme.surface
            APP_BAR_BACKGROUND -> appBarBackground
        }

    companion object {
        fun fromPreferenceValue(value: String): Debug2RightBackground =
            entries.firstOrNull { it.preferenceValue == value } ?: SURFACE
    }
}

private data class WeekDay(
    val abbreviation: String,
    val dayOfMonth: Int,
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
    val componentActivity = context as? ComponentActivity
    val preferences = remember(context) { context.getSharedPreferences(PREFERENCES_NAME, 0) }
    var displayedMonday by remember { mutableStateOf(currentWeekMonday()) }
    var topBarTitle by remember { mutableStateOf(weekTitle(displayedMonday)) }
    var highlightedDayIndex by remember { mutableStateOf<Int?>(null) }
    var todaySelectionRequest by remember { mutableStateOf(0) }
    var accentTheme by remember {
        mutableStateOf(
            AccentTheme.fromPreferenceValue(
                preferences.getString(
                    DAY_ACCENT_COLOR_KEY,
                    AccentTheme.SYSTEM.preferenceValue,
                ) ?: AccentTheme.SYSTEM.preferenceValue,
            ),
        )
    }
    var themeMode by remember {
        mutableStateOf(
            ThemeMode.fromPreferenceValue(
                preferences.getString(THEME_MODE_KEY, ThemeMode.SYSTEM.preferenceValue)
                    ?: ThemeMode.SYSTEM.preferenceValue,
            ),
        )
    }
    var scrollMode by remember {
        mutableStateOf(
            WeekScrollMode.fromPreferenceValue(
                preferences.getString(SCROLL_MODE_KEY, WeekScrollMode.SMOOTH.preferenceValue)
                    ?: WeekScrollMode.SMOOTH.preferenceValue,
            ),
        )
    }
    var debug1OutlineColor by remember {
        mutableStateOf(
            Debug1OutlineColor.fromPreferenceValue(
                preferences.getString(
                    DEBUG1_OUTLINE_COLOR_KEY,
                    Debug1OutlineColor.APP_BAR_BACKGROUND.preferenceValue,
                ) ?: Debug1OutlineColor.APP_BAR_BACKGROUND.preferenceValue,
            ),
        )
    }
    var debug2RightBackground by remember {
        mutableStateOf(
            Debug2RightBackground.fromPreferenceValue(
                preferences.getString(
                    DEBUG2_RIGHT_BACKGROUND_KEY,
                    Debug2RightBackground.SURFACE.preferenceValue,
                ) ?: Debug2RightBackground.SURFACE.preferenceValue,
            ),
        )
    }
    val useDarkTheme =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    val dynamicColorScheme =
        if (useDarkTheme) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    val colorScheme = accentTheme.applyTo(dynamicColorScheme, useDarkTheme)
    val update: () -> Unit = {
        topBarTitle = weekTitle(displayedMonday)
        val today = LocalDate.now()
        highlightedDayIndex =
            if (displayedMonday == currentWeekMonday(today)) today.dayOfWeek.value - 1 else null
    }
    val currentUpdate = rememberUpdatedState(update)
    LaunchedEffect(displayedMonday) {
        currentUpdate.value()
    }
    DisposableEffect(componentActivity) {
        val lifecycle = componentActivity?.lifecycle
        if (lifecycle == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) currentUpdate.value()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }
    SideEffect {
        componentActivity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme) {
        var isSettingsVisible by remember { mutableStateOf(false) }
        if (isSettingsVisible) {
            BackHandler(onBack = { isSettingsVisible = false })
        }
        Box(modifier = Modifier.fillMaxSize()) {
            HelloScreen(
                title = topBarTitle,
                displayedMonday = displayedMonday,
                highlightedDayIndex = highlightedDayIndex,
                scrollMode = scrollMode,
                debug1OutlineColor = debug1OutlineColor,
                todaySelectionRequest = todaySelectionRequest,
                onSettings = { isSettingsVisible = true },
                onPreviousWeek = { displayedMonday = displayedMonday.minusWeeks(1) },
                onNextWeek = { displayedMonday = displayedMonday.plusWeeks(1) },
                onToday = {
                    displayedMonday = currentWeekMonday()
                    todaySelectionRequest += 1
                },
            )
            if (isSettingsVisible) {
                SettingsScreen(
                    selectedAccentTheme = accentTheme,
                    onAccentThemeChange = { theme ->
                        preferences.edit { putString(DAY_ACCENT_COLOR_KEY, theme.preferenceValue) }
                        accentTheme = theme
                    },
                    selectedThemeMode = themeMode,
                    onThemeModeChange = { mode ->
                        preferences.edit { putString(THEME_MODE_KEY, mode.preferenceValue) }
                        themeMode = mode
                    },
                    selectedScrollMode = scrollMode,
                    onScrollModeChange = { mode ->
                        preferences.edit { putString(SCROLL_MODE_KEY, mode.preferenceValue) }
                        scrollMode = mode
                    },
                    selectedDebug1OutlineColor = debug1OutlineColor,
                    onDebug1OutlineColorChange = { color ->
                        preferences.edit {
                            putString(DEBUG1_OUTLINE_COLOR_KEY, color.preferenceValue)
                        }
                        debug1OutlineColor = color
                    },
                    selectedDebug2RightBackground = debug2RightBackground,
                    onDebug2RightBackgroundChange = { background ->
                        preferences.edit {
                            putString(DEBUG2_RIGHT_BACKGROUND_KEY, background.preferenceValue)
                        }
                        debug2RightBackground = background
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
    title: AnnotatedString,
    displayedMonday: LocalDate,
    highlightedDayIndex: Int?,
    scrollMode: WeekScrollMode,
    debug1OutlineColor: Debug1OutlineColor,
    todaySelectionRequest: Int,
    onSettings: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
) {
    val appBarBackground = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBackground),
                title = {
                    Text(
                        text = title,
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
                        IconButton(onClick = onPreviousWeek) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Previous week",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNextWeek) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = "Next week",
                        )
                    }
                    IconButton(onClick = onToday) {
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
            color = appBarBackground,
        ) {
            WeekView(
                weekMonday = displayedMonday,
                highlightedDayIndex = highlightedDayIndex,
                scrollMode = scrollMode,
                debug1OutlineColor = debug1OutlineColor,
                appBarBackground = appBarBackground,
                todaySelectionRequest = todaySelectionRequest,
            )
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun WeekView(
    weekMonday: LocalDate,
    highlightedDayIndex: Int?,
    scrollMode: WeekScrollMode,
    debug1OutlineColor: Debug1OutlineColor,
    appBarBackground: Color,
    todaySelectionRequest: Int,
) {
    val days = remember(weekMonday) { currentWeek(weekMonday) }
    var selectedDayIndex by remember { mutableStateOf(0) }
    var animatedDayWeights by remember { mutableStateOf(dayWeightsFor(selectedDayIndex)) }
    var contentExpandedDays by remember { mutableStateOf(expandedDayIndices(selectedDayIndex)) }
    var animationRequest by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFocusPosition by remember { mutableStateOf(selectedDayIndex.toFloat()) }
    val selectDay: (Int) -> Unit = { dayIndex ->
        if (dayIndex != selectedDayIndex) {
            contentExpandedDays = contentExpandedDays + expandedDayIndices(dayIndex)
            selectedDayIndex = dayIndex
            animationRequest += 1
        }
    }
    LaunchedEffect(todaySelectionRequest) {
        if (todaySelectionRequest > 0) {
            selectDay(LocalDate.now().dayOfWeek.value - 1)
        }
    }
    val startDrag: (Int) -> Unit = { dayIndex ->
        if (!isDragging) {
            isDragging = true
            dragFocusPosition = dayIndex.toFloat()
            animationRequest += 1
        }
    }
    val dragToFocus: (Float) -> Unit = { focusPosition ->
        val boundedFocusPosition = focusPosition.coerceIn(0f, WEEKEND_START_INDEX.toFloat())
        val lowerDayIndex = boundedFocusPosition.toInt()
        val upperDayIndex = (lowerDayIndex + 1).coerceAtMost(WEEKEND_START_INDEX)
        val focusedDayIndex = boundedFocusPosition.roundToInt().coerceIn(0, WEEKEND_START_INDEX)
        dragFocusPosition = boundedFocusPosition
        selectedDayIndex = focusedDayIndex
        contentExpandedDays = expandedDayIndices(lowerDayIndex) + expandedDayIndices(upperDayIndex)
        animatedDayWeights = dayWeightsForFocus(boundedFocusPosition)
    }
    val endDrag: () -> Unit = {
        selectedDayIndex = dragFocusPosition.roundToInt().coerceIn(0, WEEKEND_START_INDEX)
        animatedDayWeights = dayWeightsFor(selectedDayIndex)
        contentExpandedDays = expandedDayIndices(selectedDayIndex)
        isDragging = false
    }
    val currentAnimatedDayWeights = rememberUpdatedState(animatedDayWeights)
    val currentSelectedDayIndex = rememberUpdatedState(selectedDayIndex)
    val currentSelectDay = rememberUpdatedState(selectDay)
    val currentStartDrag = rememberUpdatedState(startDrag)
    val currentDragToFocus = rememberUpdatedState(dragToFocus)
    val currentEndDrag = rememberUpdatedState(endDrag)
    val dayLabelColumnWidth = dayLabelColumnWidth(days)
    val separatorColor = debug1OutlineColor.resolve(MaterialTheme.colorScheme, appBarBackground)
    val mandatoryGesturePadding = WindowInsets.mandatorySystemGestures.asPaddingValues()
    val bottomGestureInset =
        mandatoryGesturePadding.calculateBottomPadding() * BOTTOM_GESTURE_GUTTER_FRACTION
    val rightGestureInset =
        maxOf(
                MINIMUM_RIGHT_GESTURE_GUTTER,
                mandatoryGesturePadding.calculateRightPadding(LocalLayoutDirection.current),
            ) * RIGHT_GESTURE_GUTTER_FRACTION
    val density = LocalDensity.current
    val bottomGestureInsetPx = with(density) { bottomGestureInset.toPx() }
    val rightGestureInsetPx = with(density) { rightGestureInset.toPx() }
    val leftGestureInsetPx = rightGestureInsetPx
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
                    if (scrollMode == WeekScrollMode.PER_DAY) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val dayAreaHeight =
                                (size.height.toFloat() - bottomGestureInsetPx).coerceAtLeast(1f)
                            val anchorFocus =
                                currentSelectedDayIndex.value
                                    .coerceAtMost(WEEKEND_START_INDEX)
                                    .toFloat()
                            val transitionDistance =
                                dayGroupTransitionDistance(dayAreaHeight).coerceAtLeast(1f)
                            var accumulatedDrag = 0f
                            var previousY = down.position.y
                            var hasMoved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dragAmount = change.position.y - previousY
                                previousY = change.position.y
                                if (dragAmount != 0f) {
                                    if (!hasMoved) {
                                        currentStartDrag.value(anchorFocus.toInt())
                                        hasMoved = true
                                    }
                                    accumulatedDrag += dragAmount
                                    currentDragToFocus.value(
                                        anchorFocus - accumulatedDrag / transitionDistance,
                                    )
                                    change.consume()
                                }
                            }
                            if (hasMoved) {
                                currentEndDrag.value()
                            } else if (
                                down.position.x >= leftGestureInsetPx &&
                                    down.position.x < size.width.toFloat() - rightGestureInsetPx &&
                                    down.position.y < dayAreaHeight
                            ) {
                                currentSelectDay.value(
                                    dayIndexAtPosition(
                                        y = down.position.y,
                                        height = dayAreaHeight.toInt(),
                                        weights = currentAnimatedDayWeights.value,
                                    ),
                                )
                            }
                        }
                    } else {
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
                                    position.x >= leftGestureInsetPx &&
                                        position.x < size.width.toFloat() - rightGestureInsetPx &&
                                        position.y < dayAreaHeight &&
                                        dayIndex in expandedDayIndices(currentSelectedDayIndex.value)
                                if (isDragEnabled) {
                                    currentStartDrag.value(currentSelectedDayIndex.value)
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
                                        (size.height.toFloat() - bottomGestureInsetPx)
                                            .coerceAtLeast(1f)
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
                    }
                },
    ) {
        Spacer(modifier = Modifier.fillMaxHeight().width(rightGestureInset))
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
                    dayIndex = index,
                    highlightedDayIndex = highlightedDayIndex,
                    day = day,
                    isExpanded = isExpanded,
                    isContentExpanded = isContentExpanded,
                    weight = animatedDayWeights[index],
                    dayLabelColumnWidth = dayLabelColumnWidth,
                    separatorColor = separatorColor,
                    appBarBackground = appBarBackground,
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
    val boundedFocus = focusPosition.coerceIn(0f, WEEKEND_START_INDEX.toFloat())
    val lowerDayIndex = boundedFocus.toInt()
    val upperDayIndex = (lowerDayIndex + 1).coerceAtMost(WEEKEND_START_INDEX)
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

private fun dayGroupTransitionDistance(height: Float): Float {
    val expandedWeight = dayWeightsFor(0).first()
    return height * (expandedWeight - COMPACT_DAY_WEIGHT) / TOTAL_DAY_WEIGHT
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun ColumnScope.DayRow(
    dayIndex: Int,
    highlightedDayIndex: Int?,
    day: WeekDay,
    isExpanded: Boolean,
    isContentExpanded: Boolean,
    weight: Float,
    dayLabelColumnWidth: Dp,
    separatorColor: Color,
    appBarBackground: Color,
    onClick: () -> Unit,
) {
    val stateDescription = if (isExpanded) "expanded" else "compact"
    val innerContainerBorderColor =
        if (dayIndex == highlightedDayIndex) MaterialTheme.colorScheme.primary else separatorColor
    val isHighlighted = dayIndex == highlightedDayIndex
    val isPast = highlightedDayIndex != null && dayIndex < highlightedDayIndex
    val dayBackground =
        if (isPast) {
            appBarBackground
        } else {
            MaterialTheme.colorScheme.surface
        }
    val dayAccentColor =
        if (isPast) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        }
    val dayTextColor =
        if (isPast) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val showTopBorder = dayIndex != 0 && dayIndex != WEEKEND_START_INDEX + 1
    val showBottomBorder = dayIndex != WEEKEND_START_INDEX && dayIndex != WEEK_DAY_COUNT - 1
    val isWeekend = dayIndex >= WEEKEND_START_INDEX
    val highlightBorderInset = if (isHighlighted) DAY_SEPARATOR_THICKNESS else 0.dp
    val weekendPillWidth = if (isWeekend) WEEKEND_SIDE_PILL_WIDTH else 0.dp
    val combinedShape = DAY_SUBCONTAINER_SHAPE
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
        color = appBarBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = weekendPillWidth,
                            end = weekendPillWidth,
                            top = highlightBorderInset,
                            bottom = highlightBorderInset,
                        )
                        .clip(combinedShape)
                        .background(appBarBackground)
                        .then(
                            if (isHighlighted) {
                                Modifier.border(
                                    border =
                                        BorderStroke(
                                            DAY_SEPARATOR_THICKNESS,
                                            MaterialTheme.colorScheme.primary,
                                        ),
                                    shape = combinedShape,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(
                            start = if (isWeekend) 0.dp else 2.dp,
                            end = if (isWeekend) 0.dp else 2.dp,
                            top = if (dayIndex == WEEKEND_START_INDEX + 1) 0.dp else 2.dp,
                            bottom = if (dayIndex == WEEKEND_START_INDEX) 0.dp else 2.dp,
                        ),
            ) {
                DayLabelContainer(
                    day = day,
                    isExpanded = isExpanded,
                    width = dayLabelColumnWidth,
                    separatorColor = innerContainerBorderColor,
                    showTopBorder = showTopBorder,
                    showBottomBorder = showBottomBorder,
                    isHighlighted = isHighlighted,
                    shape = RectangleShape,
                    textColor = dayTextColor,
                    appBarBackground = dayBackground,
                )
                DayContentContainer(
                    isExpanded = isContentExpanded,
                    separatorColor = innerContainerBorderColor,
                    showTopBorder = showTopBorder,
                    showBottomBorder = showBottomBorder,
                    isHighlighted = isHighlighted,
                    shape = RectangleShape,
                    background = dayBackground,
                    accentColor = dayAccentColor,
                    textColor = dayTextColor,
                    modifier = Modifier.fillMaxHeight().weight(1f),
                )
            }
            if (isWeekend) {
                Spacer(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(WEEKEND_SIDE_PILL_WIDTH)
                            .clip(DAY_ACCENT_STRIPE_SHAPE)
                            .background(MaterialTheme.colorScheme.secondary),
                )
                Spacer(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(WEEKEND_SIDE_PILL_WIDTH)
                            .clip(DAY_ACCENT_STRIPE_SHAPE)
                            .background(MaterialTheme.colorScheme.secondary),
                )
            }
            Canvas(modifier = Modifier.matchParentSize()) {
                drawDayBorder(
                    color = separatorColor,
                    showTop = showTopBorder,
                    showBottom = showBottomBorder,
                    showVertical = !isHighlighted,
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun DayLabelContainer(
    day: WeekDay,
    isExpanded: Boolean,
    width: Dp,
    separatorColor: Color,
    showTopBorder: Boolean,
    showBottomBorder: Boolean,
    isHighlighted: Boolean,
    shape: Shape,
    textColor: Color,
    appBarBackground: Color,
) {
    Surface(
        modifier = Modifier.width(width).fillMaxHeight(),
        shape = shape,
        color = appBarBackground,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
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
                verticalArrangement = if (isExpanded) Arrangement.Top else Arrangement.Center,
            ) {
                Text(
                    text = dayLabelText(day),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontSize =
                                (MaterialTheme.typography.titleLarge.fontSize.value - 2f).sp,
                        ),
                    color = textColor,
                    textAlign = TextAlign.End,
                )
            }
            if (!isHighlighted) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawDayBorder(
                        color = separatorColor,
                        showTop = showTopBorder,
                        showBottom = showBottomBorder,
                        showVertical = false,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun DayContentContainer(
    isExpanded: Boolean,
    separatorColor: Color,
    showTopBorder: Boolean,
    showBottomBorder: Boolean,
    isHighlighted: Boolean,
    shape: Shape,
    background: Color,
    accentColor: Color,
    textColor: Color,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Spacer(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(DAY_ACCENT_STRIPE_WIDTH)
                        .clip(DAY_ACCENT_STRIPE_SHAPE)
                        .background(accentColor),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .padding(
                            start = 16.dp + DAY_ACCENT_STRIPE_WIDTH + 2.dp,
                            end = 16.dp,
                        ),
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
                        color = textColor,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            if (!isHighlighted) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawDayBorder(
                        color = separatorColor,
                        showTop = showTopBorder,
                        showBottom = showBottomBorder,
                        showVertical = false,
                    )
                }
            }
        }
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
            start = Offset(halfStroke, halfStroke),
            end = Offset(size.width - halfStroke, halfStroke),
            strokeWidth = strokeWidth,
        )
    }
    if (showBottom) {
        drawLine(
            color = color,
            start = Offset(halfStroke, size.height - halfStroke),
            end = Offset(size.width - halfStroke, size.height - halfStroke),
            strokeWidth = strokeWidth,
        )
    }
    if (showVertical) {
        drawLine(
            color = color,
            start = Offset(halfStroke, halfStroke),
            end = Offset(halfStroke, size.height - halfStroke),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(size.width - halfStroke, halfStroke),
            end = Offset(size.width - halfStroke, size.height - halfStroke),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun dayLabelColumnWidth(days: List<WeekDay>): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontSize =
                (MaterialTheme.typography.titleLarge.fontSize.value - 2f).sp,
        )
    val widestLabelWidth =
        days.maxOf { day ->
            textMeasurer.measure(
                text = dayLabelText(day),
                style = textStyle,
            ).size.width
        }
    return with(density) { widestLabelWidth.toDp() } + DAY_LABEL_HORIZONTAL_PADDING * 2
}

private fun dayLabelText(day: WeekDay): AnnotatedString =
    buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontSize = 12.sp,
            ),
        ) {
            append(day.abbreviation.uppercase(Locale.ROOT).take(2))
        }
        append(day.dayOfMonth.toString())
    }

private fun currentWeek(today: LocalDate = LocalDate.now()): List<WeekDay> {
    val monday = currentWeekMonday(today)
    return DAY_ABBREVIATIONS.mapIndexed { dayIndex, abbreviation ->
        val date = monday.plusDays(dayIndex.toLong())
        WeekDay(
            abbreviation = abbreviation,
            dayOfMonth = date.dayOfMonth,
            isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CognitiveComplexMethod", "FunctionName", "LongMethod", "ktlint:standard:function-naming")
private fun SettingsScreen(
    selectedAccentTheme: AccentTheme,
    onAccentThemeChange: (AccentTheme) -> Unit,
    selectedThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    selectedScrollMode: WeekScrollMode,
    onScrollModeChange: (WeekScrollMode) -> Unit,
    selectedDebug1OutlineColor: Debug1OutlineColor,
    onDebug1OutlineColorChange: (Debug1OutlineColor) -> Unit,
    selectedDebug2RightBackground: Debug2RightBackground,
    onDebug2RightBackgroundChange: (Debug2RightBackground) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appBarBackground = MaterialTheme.colorScheme.surfaceContainer
    var calendars by remember { mutableStateOf(emptyList<CalendarChoice>()) }
    var selectedId by remember {
        mutableStateOf(
            context
                .getSharedPreferences(PREFERENCES_NAME, 0)
                .getLong(SELECTED_CALENDAR_KEY, -1L),
        )
    }
    var isCalendarPickerVisible by remember { mutableStateOf(false) }
    var isAccentThemePickerVisible by remember { mutableStateOf(false) }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBackground),
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
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedThemeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                    ) {
                        Text(mode.label)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Accent color", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { isAccentThemePickerVisible = true }) {
                Text(selectedAccentTheme.label)
            }
            Spacer(Modifier.height(24.dp))
            Text("Scroll mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WeekScrollMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedScrollMode == mode,
                        onClick = { onScrollModeChange(mode) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = WeekScrollMode.entries.size,
                            ),
                    ) {
                        Text(mode.label)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Debug1", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Debug1OutlineColor.entries.forEachIndexed { index, color ->
                    SegmentedButton(
                        selected = selectedDebug1OutlineColor == color,
                        onClick = { onDebug1OutlineColorChange(color) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = Debug1OutlineColor.entries.size,
                            ),
                    ) {
                        Text(color.label)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Debug2", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Debug2RightBackground.entries.forEachIndexed { index, background ->
                    SegmentedButton(
                        selected = selectedDebug2RightBackground == background,
                        onClick = { onDebug2RightBackgroundChange(background) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = Debug2RightBackground.entries.size,
                            ),
                    ) {
                        Text(background.label)
                    }
                }
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
    if (isAccentThemePickerVisible) {
        AlertDialog(
            onDismissRequest = { isAccentThemePickerVisible = false },
            confirmButton = {
                TextButton(onClick = { isAccentThemePickerVisible = false }) { Text("Cancel") }
            },
            title = { Text("Accent color") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    AccentTheme.entries.forEach { option ->
                        TextButton(onClick = {
                            onAccentThemeChange(option)
                            isAccentThemePickerVisible = false
                        }) {
                            Text(option.label)
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
