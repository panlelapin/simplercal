package com.github.panlelapin.simplercal

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.time.LocalTime
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    state: AppUiState,
    actions: SettingsActions,
) {
    var calendarPickerVisible by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var accentPickerVisible by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var selectedScheduleSlot by rememberSaveable {
        androidx.compose.runtime.mutableStateOf<Int?>(null)
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            actions.onCalendarPermissionResult(granted)
        }
    val appBarBackground = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBackground),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            state = state,
            onRequestCalendarPermission = {
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            },
            onOpenCalendarPicker = {
                actions.onRefreshCalendars()
                calendarPickerVisible = true
            },
            onOpenTimePicker = { selectedScheduleSlot = it },
            onOpenAccentPicker = { accentPickerVisible = true },
            actions = actions,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    if (calendarPickerVisible) {
        CalendarPickerDialog(
            state = state,
            onSelect = {
                actions.onCalendarSelected(it)
                calendarPickerVisible = false
            },
            onDismiss = { calendarPickerVisible = false },
        )
    }
    if (accentPickerVisible) {
        AccentPickerDialog(
            selected = state.accentTheme,
            onSelect = {
                actions.onAccentThemeChange(it)
                accentPickerVisible = false
            },
            onDismiss = { accentPickerVisible = false },
        )
    }
    selectedScheduleSlot?.let { slotIndex ->
        ScheduleTimePickerDialog(
            initialMinutes = state.scheduleTimes.getOrElse(slotIndex) { UNSET_SCHEDULE_TIME },
            onDismiss = { selectedScheduleSlot = null },
            onConfirm = { minutes ->
                actions.onScheduleTimeChange(slotIndex, minutes)
                selectedScheduleSlot = null
            },
        )
    }
}

@Composable
private fun SettingsContent(
    state: AppUiState,
    onRequestCalendarPermission: () -> Unit,
    onOpenCalendarPicker: () -> Unit,
    onOpenTimePicker: (Int) -> Unit,
    onOpenAccentPicker: () -> Unit,
    actions: SettingsActions,
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
        CalendarSection(
            state = state,
            onRequestPermission = onRequestCalendarPermission,
            onOpenPicker = onOpenCalendarPicker,
        )
        ScheduleSection(state.scheduleTimes, onOpenTimePicker)
        SingleChoiceSection(
            title = stringResource(R.string.section_theme),
            options = ThemeMode.entries,
            selected = state.themeMode,
            onSelected = actions.onThemeModeChange,
            label = { stringResource(it.labelResource) },
        )
        AccentSection(state.accentTheme, onOpenAccentPicker)
        SingleChoiceSection(
            title = stringResource(R.string.section_scroll_mode),
            options = WeekScrollMode.entries,
            selected = state.scrollMode,
            onSelected = actions.onScrollModeChange,
            label = { stringResource(it.labelResource) },
        )
        SingleChoiceSection(
            title = stringResource(R.string.section_simulation_mode),
            options = SimulationMode.entries,
            selected = state.simulationMode,
            onSelected = actions.onSimulationModeChange,
            label = { stringResource(it.labelResource) },
        )
        SingleChoiceSection(
            title = stringResource(R.string.section_debug1),
            options = Debug1OutlineColor.entries,
            selected = state.debug1OutlineColor,
            onSelected = actions.onDebug1OutlineColorChange,
            label = { stringResource(it.labelResource) },
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.release_version, stringResource(R.string.official_release_version)),
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
                        val intent = Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri())
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.browser_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CalendarSection(
    state: AppUiState,
    onRequestPermission: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    Text(stringResource(R.string.section_calendar), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    if (!state.hasCalendarPermission) {
        Text(stringResource(R.string.calendar_permission_explanation))
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRequestPermission) { Text(stringResource(R.string.action_allow)) }
    } else {
        val selectedCalendar = state.calendars.firstOrNull { it.id == state.selectedCalendarId }
        Button(
            onClick = onOpenPicker,
            enabled = !state.isCalendarListLoading,
        ) {
            Text(selectedCalendar?.name ?: stringResource(R.string.action_choose_calendar))
        }
        if (state.isCalendarListLoading) {
            CircularProgressIndicator()
        } else if (state.calendars.isEmpty() && state.calendarListFailure == null) {
            Text(stringResource(R.string.calendar_none_available))
        }
        calendarFailureMessage(state.calendarListFailure)?.let { Text(it) }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ScheduleSection(
    scheduleTimes: List<Int>,
    onOpenTimePicker: (Int) -> Unit,
) {
    Text(stringResource(R.string.section_schedules), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    scheduleTimes.forEachIndexed { index, minutes ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.schedule_case, index + 1),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onOpenTimePicker(index) }) {
                Text(formatScheduleTime(minutes))
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun AccentSection(
    selected: AccentTheme,
    onOpenPicker: () -> Unit,
) {
    Text(stringResource(R.string.section_accent_color), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Button(onClick = onOpenPicker) { Text(stringResource(selected.labelResource)) }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun <T> SingleChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
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
    state: AppUiState,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.action_choose_calendar)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(state.calendars, key = { it.id }) { calendar ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(calendar.id) },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(calendar.name)
                            if (calendar.accountName.isNotEmpty()) {
                                Text(
                                    calendar.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (
                                calendar.ownerAccount.isNotEmpty() &&
                                    calendar.ownerAccount != calendar.accountName
                            ) {
                                Text(
                                    calendar.ownerAccount,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun AccentPickerDialog(
    selected: AccentTheme,
    onSelect: (AccentTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.section_accent_color)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(AccentTheme.entries, key = { it.preferenceValue }) { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                        Text(stringResource(option.labelResource))
                    }
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScheduleTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val safeMinutes = initialMinutes.takeIf { it in 0 until 24 * 60 }
    val initialTime = safeMinutes?.let { LocalTime.of(it / 60, it % 60) } ?: LocalTime.now()
    val timePickerState =
        rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_choose_time)) },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour * 60 + timePickerState.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun formatScheduleTime(minutes: Int): String =
    if (minutes !in 0 until 24 * 60) {
        stringResource(R.string.action_choose_time)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
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
