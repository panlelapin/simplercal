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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
private const val GITHUB_URL = "https://github.com/panlelapin/simplercal"
private const val EXPANDED_DAY_COUNT = 3
private const val EXPANDED_DAY_WEIGHT = 21f
private const val COMPACT_DAY_WEIGHT = 9.25f
private const val DAY_CONTENT_ROW_COUNT = 9
private const val DAY_STATE_ANIMATION_DURATION_MILLIS = 720
private val DAY_LABEL_HORIZONTAL_PADDING = 8.dp

private val DAY_ABBREVIATIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private data class CalendarChoice(
    val id: Long,
    val name: String,
)

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
            HelloScreen(onSettings = { isSettingsVisible = true })
            if (isSettingsVisible) {
                SettingsScreen(onBack = { isSettingsVisible = false })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun HelloScreen(onSettings: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Hello") },
                navigationIcon = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Settings",
                        )
                    }
                },
                actions = {
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
            color = colorResource(R.color.screen_background),
        ) {
            WeekView()
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun WeekView() {
    val days = remember { currentWeek() }
    var expandedStartIndex by remember { mutableStateOf(0) }
    val dayLabelColumnWidth = dayLabelColumnWidth()
    val bottomGestureInset =
        WindowInsets.mandatorySystemGestures.asPaddingValues().calculateBottomPadding()
    Column(modifier = Modifier.fillMaxSize().padding(bottom = bottomGestureInset)) {
        days.forEachIndexed { index, day ->
            val isExpanded = index in expandedStartIndex until expandedStartIndex + EXPANDED_DAY_COUNT
            val animatedWeight by animateFloatAsState(
                targetValue = if (isExpanded) EXPANDED_DAY_WEIGHT else COMPACT_DAY_WEIGHT,
                animationSpec =
                    tween(
                        durationMillis = DAY_STATE_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                label = "day-$index-height",
            )
            DayRow(
                day = day,
                isExpanded = isExpanded,
                weight = animatedWeight,
                dayLabelColumnWidth = dayLabelColumnWidth,
                onClick = {
                    expandedStartIndex = expandedStartIndexFor(index)
                },
            )
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun ColumnScope.DayRow(
    day: WeekDay,
    isExpanded: Boolean,
    weight: Float,
    dayLabelColumnWidth: Dp,
    onClick: () -> Unit,
) {
    val stateDescription = if (isExpanded) "expanded" else "compact"
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(weight)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${day.abbreviation} ${day.dayOfMonth}, $stateDescription"
                },
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
        color =
            if (day.isWeekend) {
                colorResource(R.color.weekend_background)
            } else {
                colorResource(R.color.screen_background)
            },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            DayLabelContainer(
                day = day,
                isExpanded = isExpanded,
                width = dayLabelColumnWidth,
            )
            Spacer(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface),
            )
            DayContentContainer(
                day = day,
                isExpanded = isExpanded,
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
    width: Dp,
) {
    val verticalAlignment = if (isExpanded) Alignment.TopEnd else Alignment.CenterEnd
    Surface(
        modifier = Modifier.width(width).fillMaxHeight(),
        shape = RectangleShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun DayContentContainer(
    day: WeekDay,
    isExpanded: Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RectangleShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            DateNumberRow(modifier = Modifier.fillMaxWidth().weight(1f), day = day)
            if (isExpanded) {
                repeat(DAY_CONTENT_ROW_COUNT - 1) {
                    Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun DateNumberRow(modifier: Modifier, day: WeekDay) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
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

private fun expandedStartIndexFor(dayIndex: Int): Int =
    when (dayIndex) {
        0, 1 -> 0
        2, 3, 4 -> dayIndex - 1
        5, 6 -> 4
        else -> error("Day index must be between 0 and 6")
    }

private fun currentWeek(today: LocalDate = LocalDate.now()): List<WeekDay> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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
private fun SettingsScreen(onBack: () -> Unit) {
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
