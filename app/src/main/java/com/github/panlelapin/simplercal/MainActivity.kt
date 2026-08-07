package com.github.panlelapin.simplercal

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.LocalDate

internal data class CalendarChoice(
    val id: Long,
    val name: String,
)

internal data class WeekDay(
    val abbreviation: String,
    val dayOfMonth: Int,
    val isWeekend: Boolean,
    val isHoliday: Boolean,
    val isBankH: Boolean,
)

internal class AppPreferences(
    val preferences: SharedPreferences,
    initialAccentTheme: AccentTheme,
    initialThemeMode: ThemeMode,
    initialScrollMode: WeekScrollMode,
    initialDebug1OutlineColor: Debug1OutlineColor,
    initialDebug2RightBackground: Debug2RightBackground,
) {
    var accentTheme by mutableStateOf(initialAccentTheme)
    var themeMode by mutableStateOf(initialThemeMode)
    var scrollMode by mutableStateOf(initialScrollMode)
    var debug1OutlineColor by mutableStateOf(initialDebug1OutlineColor)
    var debug2RightBackground by mutableStateOf(initialDebug2RightBackground)
}

internal data class MainScreenState(
    val title: AnnotatedString,
    val displayedMonday: LocalDate,
    val highlightedDayIndex: Int?,
    val scrollMode: WeekScrollMode,
    val debug1OutlineColor: Debug1OutlineColor,
    val todaySelectionRequest: Int,
)

internal data class MainScreenActions(
    val onSettings: () -> Unit,
    val onPreviousWeek: () -> Unit,
    val onNextWeek: () -> Unit,
    val onToday: () -> Unit,
)

internal data class WeekViewState(
    val weekMonday: LocalDate,
    val highlightedDayIndex: Int?,
    val scrollMode: WeekScrollMode,
    val debug1OutlineColor: Debug1OutlineColor,
    val appBarBackground: Color,
    val todaySelectionRequest: Int,
)

internal data class DayRowState(
    val dayIndex: Int,
    val highlightedDayIndex: Int?,
    val day: WeekDay,
    val isExpanded: Boolean,
    val isContentExpanded: Boolean,
    val weight: Float,
    val dayLabelColumnWidth: Dp,
    val separatorColor: Color,
    val appBarBackground: Color,
)

internal data class WeekRowsState(
    val days: List<WeekDay>,
    val selectedDayIndex: Int,
    val contentExpandedDays: Set<Int>,
    val animatedDayWeights: List<Float>,
    val highlightedDayIndex: Int?,
    val dayLabelColumnWidth: Dp,
    val separatorColor: Color,
    val appBarBackground: Color,
)

internal data class DayLabelState(
    val day: WeekDay,
    val isExpanded: Boolean,
    val width: Dp,
    val separatorColor: Color,
    val hasTopBorder: Boolean,
    val hasBottomBorder: Boolean,
    val isHighlighted: Boolean,
    val shape: Shape,
    val textColor: Color,
    val appBarBackground: Color,
)

internal data class DayContentState(
    val isExpanded: Boolean,
    val separatorColor: Color,
    val hasTopBorder: Boolean,
    val hasBottomBorder: Boolean,
    val isHighlighted: Boolean,
    val shape: Shape,
    val background: Color,
    val accentColor: Color,
    val textColor: Color,
    val modifier: Modifier,
)

internal data class DayAppearance(
    val isHighlighted: Boolean,
    val innerContainerBorderColor: Color,
    val dayBackground: Color,
    val dayAccentColor: Color,
    val dayTextColor: Color,
    val hasTopBorder: Boolean,
    val hasBottomBorder: Boolean,
    val highlightBorderInset: Dp,
    val combinedShape: RoundedCornerShape,
)

internal data class WeekGestureState(
    val scrollMode: WeekScrollMode,
    val bottomGestureInsetPx: Float,
    val rightGestureInsetPx: Float,
    val leftGestureInsetPx: Float,
    val selectedDayIndex: () -> Int,
    val animatedDayWeights: () -> List<Float>,
    val selectDay: (Int) -> Unit,
    val startDrag: (Int) -> Unit,
    val dragToFocus: (Float) -> Unit,
    val endDrag: () -> Unit,
)

internal data class WeekDragState(
    val pointerId: PointerId,
    val initialY: Float,
    val anchorFocus: Float,
    val transitionDistance: Float,
    val isDragAllowed: Boolean,
)

internal data class SettingsState(
    val selectedAccentTheme: AccentTheme,
    val selectedThemeMode: ThemeMode,
    val selectedScrollMode: WeekScrollMode,
    val selectedDebug1OutlineColor: Debug1OutlineColor,
    val selectedDebug2RightBackground: Debug2RightBackground,
)

internal data class SettingsActions(
    val onAccentThemeChange: (AccentTheme) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onScrollModeChange: (WeekScrollMode) -> Unit,
    val onDebug1OutlineColorChange: (Debug1OutlineColor) -> Unit,
    val onDebug2RightBackgroundChange: (Debug2RightBackground) -> Unit,
    val onBack: () -> Unit,
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
private fun SimplerCalApp() {
    val context = LocalContext.current
    val componentActivity = context as? ComponentActivity
    val preferences = remember(context) { context.getSharedPreferences(PREFERENCES_NAME, 0) }
    val appPreferences = rememberAppPreferences(preferences)
    var displayedMonday by remember { mutableStateOf(currentWeekMonday()) }
    var topBarTitle by remember { mutableStateOf(weekTitle(displayedMonday)) }
    var highlightedDayIndex by remember { mutableStateOf<Int?>(null) }
    var todaySelectionRequest by remember { mutableStateOf(0) }
    val isDarkTheme =
        when (appPreferences.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    val dynamicColorScheme =
        if (isDarkTheme) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    val colorScheme = appPreferences.accentTheme.applyTo(dynamicColorScheme, isDarkTheme)
    val selectToday: () -> Unit = {
        displayedMonday = currentWeekMonday()
        todaySelectionRequest += 1
    }
    LaunchedEffect(Unit) {
        selectToday()
    }
    val update: () -> Unit = {
        topBarTitle = weekTitle(displayedMonday)
        val today = LocalDate.now()
        highlightedDayIndex =
            if (displayedMonday == currentWeekMonday(today)) today.dayOfWeek.value - 1 else null
    }
    AppUpdateEffects(componentActivity, displayedMonday, update)
    SideEffect { componentActivity?.let { updateSystemBars(it, isDarkTheme) } }
    MaterialTheme(colorScheme = colorScheme) {
        AppSurface(
            preferences = appPreferences,
            mainState =
                MainScreenState(
                    title = topBarTitle,
                    displayedMonday = displayedMonday,
                    highlightedDayIndex = highlightedDayIndex,
                    scrollMode = appPreferences.scrollMode,
                    debug1OutlineColor = appPreferences.debug1OutlineColor,
                    todaySelectionRequest = todaySelectionRequest,
                ),
            onSelectToday = selectToday,
            onPreviousWeek = { displayedMonday = displayedMonday.minusWeeks(1) },
            onNextWeek = { displayedMonday = displayedMonday.plusWeeks(1) },
        )
    }
}

@Composable
private fun AppUpdateEffects(
    componentActivity: ComponentActivity?,
    displayedMonday: LocalDate,
    update: () -> Unit,
) {
    val currentUpdate = rememberUpdatedState(update)
    LaunchedEffect(displayedMonday) { currentUpdate.value() }
    DisposableEffect(componentActivity) {
        val lifecycle = componentActivity?.lifecycle
        if (lifecycle == null) {
            onDispose {}
        } else {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) currentUpdate.value()
                }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }
}

private fun updateSystemBars(
    activity: ComponentActivity,
    isDarkTheme: Boolean,
) {
    val insetsController =
        WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView,
        )
    insetsController.isAppearanceLightStatusBars = !isDarkTheme
    insetsController.isAppearanceLightNavigationBars = !isDarkTheme
}

@Composable
private fun rememberAppPreferences(preferences: SharedPreferences): AppPreferences =
    remember(preferences) {
        AppPreferences(
            preferences = preferences,
            initialAccentTheme =
                AccentTheme.fromPreferenceValue(
                    preferences.getString(
                        DAY_ACCENT_COLOR_KEY,
                        AccentTheme.SYSTEM.preferenceValue,
                    ) ?: AccentTheme.SYSTEM.preferenceValue,
                ),
            initialThemeMode =
                ThemeMode.fromPreferenceValue(
                    preferences.getString(THEME_MODE_KEY, ThemeMode.SYSTEM.preferenceValue)
                        ?: ThemeMode.SYSTEM.preferenceValue,
                ),
            initialScrollMode =
                WeekScrollMode.fromPreferenceValue(
                    preferences.getString(SCROLL_MODE_KEY, WeekScrollMode.SMOOTH.preferenceValue)
                        ?: WeekScrollMode.SMOOTH.preferenceValue,
                ),
            initialDebug1OutlineColor =
                Debug1OutlineColor.fromPreferenceValue(
                    preferences.getString(
                        DEBUG1_OUTLINE_COLOR_KEY,
                        Debug1OutlineColor.APP_BAR_BACKGROUND.preferenceValue,
                    ) ?: Debug1OutlineColor.APP_BAR_BACKGROUND.preferenceValue,
                ),
            initialDebug2RightBackground =
                Debug2RightBackground.fromPreferenceValue(
                    preferences.getString(
                        DEBUG2_RIGHT_BACKGROUND_KEY,
                        Debug2RightBackground.SURFACE.preferenceValue,
                    ) ?: Debug2RightBackground.SURFACE.preferenceValue,
                ),
        )
    }

@Composable
private fun AppSurface(
    preferences: AppPreferences,
    mainState: MainScreenState,
    onSelectToday: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    var isSettingsVisible by remember { mutableStateOf(false) }
    if (isSettingsVisible) {
        BackHandler(onBack = { isSettingsVisible = false })
    }
    Box(modifier = Modifier.fillMaxSize()) {
        HelloScreen(
            state = mainState,
            actions =
                MainScreenActions(
                    onSettings = { isSettingsVisible = true },
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onToday = onSelectToday,
                ),
        )
        if (isSettingsVisible) {
            SettingsScreen(
                state =
                    SettingsState(
                        selectedAccentTheme = preferences.accentTheme,
                        selectedThemeMode = preferences.themeMode,
                        selectedScrollMode = preferences.scrollMode,
                        selectedDebug1OutlineColor = preferences.debug1OutlineColor,
                        selectedDebug2RightBackground = preferences.debug2RightBackground,
                    ),
                actions = settingsActions(preferences) { isSettingsVisible = false },
            )
        }
    }
}

private fun settingsActions(
    preferences: AppPreferences,
    onBack: () -> Unit,
): SettingsActions =
    SettingsActions(
        onAccentThemeChange = { theme ->
            preferences.preferences.edit { putString(DAY_ACCENT_COLOR_KEY, theme.preferenceValue) }
            preferences.accentTheme = theme
        },
        onThemeModeChange = { mode ->
            preferences.preferences.edit { putString(THEME_MODE_KEY, mode.preferenceValue) }
            preferences.themeMode = mode
        },
        onScrollModeChange = { mode ->
            preferences.preferences.edit { putString(SCROLL_MODE_KEY, mode.preferenceValue) }
            preferences.scrollMode = mode
        },
        onDebug1OutlineColorChange = { color ->
            preferences.preferences.edit {
                putString(
                    DEBUG1_OUTLINE_COLOR_KEY,
                    color.preferenceValue,
                )
            }
            preferences.debug1OutlineColor = color
        },
        onDebug2RightBackgroundChange = { background ->
            preferences.preferences.edit {
                putString(DEBUG2_RIGHT_BACKGROUND_KEY, background.preferenceValue)
            }
            preferences.debug2RightBackground = background
        },
        onBack = onBack,
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HelloScreen(
    state: MainScreenState,
    actions: MainScreenActions,
) {
    val appBarBackground = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = { MainTopBar(state, actions, appBarBackground) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = appBarBackground,
        ) {
            WeekView(
                state =
                    WeekViewState(
                        weekMonday = state.displayedMonday,
                        highlightedDayIndex = state.highlightedDayIndex,
                        scrollMode = state.scrollMode,
                        debug1OutlineColor = state.debug1OutlineColor,
                        appBarBackground = appBarBackground,
                        todaySelectionRequest = state.todaySelectionRequest,
                    ),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainTopBar(
    state: MainScreenState,
    actions: MainScreenActions,
    appBarBackground: Color,
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBackground),
        title = { Text(text = state.title, maxLines = 1) },
        navigationIcon = {
            Row {
                IconButton(onClick = actions.onSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = "Settings",
                    )
                }
                IconButton(onClick = actions.onPreviousWeek) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Previous week",
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = actions.onNextWeek) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = "Next week",
                )
            }
            IconButton(onClick = actions.onToday) {
                Icon(
                    painter = painterResource(R.drawable.ic_today),
                    contentDescription = "Today",
                )
            }
        },
    )
}

/* Legacy settings implementation replaced by SettingsScreen.kt.
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun settingsScreen(
    state: SettingsState,
    actions: SettingsActions,
) {
    val selectedAccentTheme = state.selectedAccentTheme
    val onAccentThemeChange = actions.onAccentThemeChange
    val selectedThemeMode = state.selectedThemeMode
    val onThemeModeChange = actions.onThemeModeChange
    val selectedScrollMode = state.selectedScrollMode
    val onScrollModeChange = actions.onScrollModeChange
    val selectedDebug1OutlineColor = state.selectedDebug1OutlineColor
    val onDebug1OutlineColorChange = actions.onDebug1OutlineColorChange
    val selectedDebug2RightBackground = state.selectedDebug2RightBackground
    val onDebug2RightBackgroundChange = actions.onDebug2RightBackgroundChange
    val onBack = actions.onBack
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
*/
