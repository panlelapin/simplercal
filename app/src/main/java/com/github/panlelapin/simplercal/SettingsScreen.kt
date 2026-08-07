package com.github.panlelapin.simplercal

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri

private data class CalendarSettingsController(
    val calendars: List<CalendarChoice>,
    val selectedId: Long,
    val selectedCalendarName: String?,
    val hasPermission: Boolean,
    val requestPermission: () -> Unit,
    val selectCalendar: (Long) -> Unit,
)

private data class SettingsContentActions(
    val settings: SettingsActions,
    val onOpenCalendarPicker: () -> Unit,
    val onOpenAccentPicker: () -> Unit,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
) {
    val context = LocalContext.current
    val controller = rememberCalendarController(context)
    var isCalendarPickerVisible by remember { mutableStateOf(false) }
    var isAccentThemePickerVisible by remember { mutableStateOf(false) }
    val appBarBackground = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBackground),
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            state = state,
            controller = controller,
            actions =
                SettingsContentActions(
                    settings = actions,
                    onOpenCalendarPicker = { isCalendarPickerVisible = true },
                    onOpenAccentPicker = { isAccentThemePickerVisible = true },
                ),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
    if (isCalendarPickerVisible) {
        CalendarPickerDialog(
            controller = controller,
            onDismiss = { isCalendarPickerVisible = false },
        )
    }
    if (isAccentThemePickerVisible) {
        AccentPickerDialog(
            actions = actions,
            onDismiss = { isAccentThemePickerVisible = false },
        )
    }
}

@Composable
private fun rememberCalendarController(context: Context): CalendarSettingsController {
    var calendars by remember { mutableStateOf(emptyList<CalendarChoice>()) }
    var selectedId by remember {
        mutableStateOf(
            context.getSharedPreferences(PREFERENCES_NAME, 0).getLong(SELECTED_CALENDAR_KEY, -1L),
        )
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
            if (granted) calendars = loadCalendars(context)
        }
    LaunchedEffect(hasPermission) {
        if (hasPermission) calendars = loadCalendars(context)
    }
    return CalendarSettingsController(
        calendars = calendars,
        selectedId = selectedId,
        selectedCalendarName = calendars.firstOrNull { it.id == selectedId }?.name,
        hasPermission = hasPermission,
        requestPermission = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
        selectCalendar = { id ->
            selectedId = id
            context.getSharedPreferences(PREFERENCES_NAME, 0).edit {
                putLong(SELECTED_CALENDAR_KEY, id)
            }
        },
    )
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    controller: CalendarSettingsController,
    actions: SettingsContentActions,
    modifier: Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        CalendarSection(controller, actions.onOpenCalendarPicker)
        AccentSection(state.selectedAccentTheme, actions.onOpenAccentPicker)
        SingleChoiceSection(
            title = "Theme",
            options = ThemeMode.entries,
            selected = state.selectedThemeMode,
            onSelected = actions.settings.onThemeModeChange,
            label = { it.label },
        )
        SingleChoiceSection(
            title = "Scroll mode",
            options = WeekScrollMode.entries,
            selected = state.selectedScrollMode,
            onSelected = actions.settings.onScrollModeChange,
            label = { it.label },
        )
        SingleChoiceSection(
            title = "Simulation mode",
            options = SimulationMode.entries,
            selected = state.selectedSimulationMode,
            onSelected = actions.settings.onSimulationModeChange,
            label = { it.label },
        )
        SingleChoiceSection(
            title = "Debug1",
            options = Debug1OutlineColor.entries,
            selected = state.selectedDebug1OutlineColor,
            onSelected = actions.settings.onDebug1OutlineColorChange,
            label = { it.label },
        )
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

@Composable
private fun CalendarSection(
    controller: CalendarSettingsController,
    onOpenPicker: () -> Unit,
) {
    Text("Calendar", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    if (!controller.hasPermission) {
        Text("Allow access to choose an Android calendar.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = controller.requestPermission) { Text("Allow") }
    } else {
        Button(
            onClick = onOpenPicker,
            enabled = controller.calendars.isNotEmpty(),
        ) {
            Text(controller.selectedCalendarName ?: "Choose calendar")
        }
        if (controller.calendars.isEmpty()) Text("No visible calendars available.")
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun AccentSection(
    selected: AccentTheme,
    onOpenPicker: () -> Unit,
) {
    Text("Seed color", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Button(onClick = onOpenPicker) { Text(selected.label) }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun <T> SingleChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
) where T : Enum<T> {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label(option))
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun CalendarPickerDialog(
    controller: CalendarSettingsController,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Choose calendar") },
        text = {
            Column {
                controller.calendars.forEach { calendar ->
                    TextButton(onClick = {
                        controller.selectCalendar(calendar.id)
                        onDismiss()
                    }) {
                        Text(calendar.name)
                    }
                }
            }
        },
    )
}

@Composable
private fun AccentPickerDialog(
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Seed color") },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                AccentTheme.entries.forEach { option ->
                    TextButton(onClick = {
                        actions.onAccentThemeChange(option)
                        onDismiss()
                    }) {
                        Text(option.label)
                    }
                }
            }
        },
    )
}

private fun loadCalendars(context: Context): List<CalendarChoice> =
    runCatching { readCalendars(context.contentResolver) }.getOrDefault(emptyList())

private fun readCalendars(resolver: ContentResolver): List<CalendarChoice> {
    val uri = CalendarContract.Calendars.CONTENT_URI
    val columns =
        arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )
    val visible = "${CalendarContract.Calendars.VISIBLE} = 1"
    val nameSort = CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
    return resolver.query(uri, columns, visible, null, nameSort).use { cursor ->
        if (cursor == null) {
            emptyList()
        } else {
            buildList {
                while (cursor.moveToNext()) {
                    add(CalendarChoice(id = cursor.getLong(0), name = cursor.getString(1)))
                }
            }
        }
    }
}
