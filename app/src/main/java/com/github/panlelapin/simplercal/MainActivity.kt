package com.github.panlelapin.simplercal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Hosts the single launcher screen for SimplerCal. */
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

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
        setContent { SimplerCalApp(appViewModel) }
    }
}

@Composable
private fun SimplerCalApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val state = viewModel.uiState
    val isDarkTheme =
        when (state.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    val configuration = LocalConfiguration.current
    val dynamicColorScheme = remember(context, isDarkTheme, configuration) {
        if (isDarkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    }
    val colorScheme = remember(state.accentTheme, dynamicColorScheme, isDarkTheme) {
        state.accentTheme.applyTo(dynamicColorScheme, isDarkTheme)
    }

    CalendarChangesEffect(
        context = context,
        calendarId = state.selectedCalendarId,
        hasPermission = state.hasCalendarPermission,
        onCalendarChanged = viewModel::onCalendarProviderChanged,
        onPermissionRevoked = { viewModel.onCalendarPermissionResult(false) },
        lifecycle = activity?.lifecycle,
        onObservationFailed = viewModel::onCalendarObservationFailed,
    )
    AppLifecycleEffects(activity, context, viewModel)
    SideEffect { activity?.let { updateSystemBars(it, isDarkTheme) } }

    MaterialTheme(colorScheme = colorScheme) {
        AppSurface(
            state = state,
            isDarkTheme = isDarkTheme,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun AppLifecycleEffects(
    activity: ComponentActivity?,
    context: Context,
    viewModel: AppViewModel,
) {
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        if (lifecycle == null) {
            onDispose {}
        } else {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
                    if (event == Lifecycle.Event.ON_STOP) viewModel.onStop()
                }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receiverContext: Context?,
                    intent: Intent?,
                ) {
                    viewModel.onTimeContextChanged()
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
}

private fun updateSystemBars(
    activity: ComponentActivity,
    isDarkTheme: Boolean,
) {
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = !isDarkTheme
        isAppearanceLightNavigationBars = !isDarkTheme
    }
}

@Composable
private fun AppSurface(
    state: AppUiState,
    isDarkTheme: Boolean,
    viewModel: AppViewModel,
) {
    if (state.isSettingsVisible) BackHandler(onBack = viewModel::hideSettings)
    val screenStates = rememberSaveableStateHolder()
    Box(modifier = Modifier.fillMaxSize()) {
        if (!state.isSettingsVisible) screenStates.SaveableStateProvider("week") {
        MainScreen(
            state = state,
            isDarkTheme = isDarkTheme,
            actions =
                MainScreenActions(
                    onSettings = viewModel::showSettings,
                    onPreviousWeek = viewModel::showPreviousWeek,
                    onNextWeek = viewModel::showNextWeek,
                    onToday = viewModel::selectToday,
                ),
            onDaySelected = viewModel::onDaySelected,
            onDayMarkersChange = viewModel::setDayMarkers,
        )
        }
        if (state.isSettingsVisible) {
            screenStates.SaveableStateProvider("settings") {
            SettingsScreen(
                state = state,
                actions =
                    SettingsActions(
                        onCalendarPermissionResult = viewModel::onCalendarPermissionResult,
                        onCalendarSelected = viewModel::selectCalendar,
                        onRefreshCalendars = viewModel::refreshCalendars,
                        onScheduleTimeChange = viewModel::setScheduleTime,
                        onAccentThemeChange = viewModel::setAccentTheme,
                        onThemeModeChange = viewModel::setThemeMode,
                        onScrollModeChange = viewModel::setScrollMode,
                        onSimulationModeChange = viewModel::setSimulationMode,
                        onDebug1OutlineColorChange = viewModel::setDebug1OutlineColor,
                        onBack = viewModel::hideSettings,
                    ),
            )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainScreen(
    state: AppUiState,
    isDarkTheme: Boolean,
    actions: MainScreenActions,
    onDaySelected: (Int) -> Unit,
    onDayMarkersChange: (java.time.LocalDate, DayMarkers) -> Unit,
) {
    val appBarBackground = MaterialTheme.colorScheme.surfaceContainer
    val snackbarHostState = remember { SnackbarHostState() }
    val calendarError = calendarFailureMessage(
        state.calendarFailure ?: if (state.hasCalendarObserverFailure) CalendarFailureReason.PROVIDER_UNAVAILABLE else null,
    )
    LaunchedEffect(calendarError) {
        if (calendarError != null) snackbarHostState.showSnackbar(calendarError)
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = { MainTopBar(state, actions, appBarBackground) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = appBarBackground,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                WeekView(
                    state =
                        WeekViewState(
                            weekMonday = state.displayedMonday,
                            referenceDate = state.referenceDate,
                            eventsByDay =
                                if (
                                    state.loadedEventsMonday == state.displayedMonday &&
                                        state.loadedEventsCalendarId == state.selectedCalendarId
                                ) {
                                    state.eventsByDay
                                } else {
                                    emptyEventDays()
                                },
                            highlightedDayIndex = state.highlightedDayIndex,
                            isDarkTheme = isDarkTheme,
                            scrollMode = state.scrollMode,
                            simulationMode = state.simulationMode,
                            debug1OutlineColor = state.debug1OutlineColor,
                            appBarBackground = appBarBackground,
                            requestedDayIndex = state.selectedDayIndex,
                            selectionRequest = state.selectionRequest,
                            onSelectionChanged = onDaySelected,
                            dayMarkers = state.dayMarkers,
                            onDayMarkersChange = onDayMarkersChange,
                        ),
                )
                if (state.isCalendarLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainTopBar(
    state: AppUiState,
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
                        contentDescription = stringResource(R.string.action_settings),
                    )
                }
                IconButton(onClick = actions.onPreviousWeek) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_previous_week),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = actions.onNextWeek) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = stringResource(R.string.action_next_week),
                )
            }
            IconButton(onClick = actions.onToday) {
                Icon(
                    painter = painterResource(R.drawable.ic_today),
                    contentDescription = stringResource(R.string.action_today),
                )
            }
        },
    )
}

@Composable
private fun calendarFailureMessage(reason: CalendarFailureReason?): String? =
    when (reason) {
        CalendarFailureReason.PERMISSION_REVOKED ->
            stringResource(R.string.calendar_error_permission)
        CalendarFailureReason.PROVIDER_UNAVAILABLE ->
            stringResource(R.string.calendar_error_provider)
        CalendarFailureReason.CALENDAR_UNAVAILABLE ->
            stringResource(R.string.calendar_error_missing)
        null -> null
    }
