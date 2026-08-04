package com.github.panlelapin.simplercal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters

private const val SELECTED_CALENDAR_KEY = "selected_calendar_id"
private const val PREFERENCES_NAME = "simplercal"
private const val DAYS_IN_WEEK = 7L
private const val LAST_DAY_OFFSET = 6L

private data class CalendarChoice(
    val id: Long,
    val name: String,
    val color: Int,
)

private data class CalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

/** Hosts the single launcher screen for SimplerCal. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        var isSettingsVisible by remember { mutableStateOf(false) }
        var isAgendaVisible by remember { mutableStateOf(false) }
        Surface(modifier = Modifier.fillMaxSize()) {
            if (isSettingsVisible) {
                SettingsScreen(onBack = { isSettingsVisible = false })
            } else {
                CalendarScreen(
                    isAgendaVisible = isAgendaVisible,
                    onSettings = { isSettingsVisible = true },
                    onChangeView = { isAgendaVisible = !isAgendaVisible },
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun CalendarScreen(
    isAgendaVisible: Boolean,
    onSettings: () -> Unit,
    onChangeView: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasPermission = it
        }
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val selectedCalendarId =
        remember {
            context.getSharedPreferences(PREFERENCES_NAME, 0).getLong(SELECTED_CALENDAR_KEY, -1L)
        }
    val events =
        remember(hasPermission, weekStart) {
            if (hasPermission) {
                loadEvents(context, weekStart, selectedCalendarId)
            } else {
                emptyList()
            }
        }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SimplerCal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row {
                TextButton(
                    onClick = onSettings,
                    modifier = Modifier.semantics { contentDescription = "Réglages" },
                ) { Text("⚙", fontSize = 24.sp) }
                TextButton(
                    onClick = onChangeView,
                    modifier = Modifier.semantics { contentDescription = "Changer de vue" },
                ) { Text(if (isAgendaVisible) "▦" else "☷", fontSize = 24.sp) }
            }
        }
        if (!hasPermission) {
            Text("Autorisez l’accès au calendrier pour afficher vos événements.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }) { Text("Autoriser") }
        } else if (isAgendaVisible) {
            AgendaView(events)
        } else {
            WeekView(weekStart, events)
        }
    }
}

@Composable
@Suppress("FunctionName", "LongMethod", "ktlint:standard:function-naming")
private fun WeekView(
    weekStart: LocalDate,
    events: List<CalendarEvent>,
) {
    val dayFormatter = DateTimeFormatter.ofPattern("EEE d")
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${weekStart.format(
                DateTimeFormatter.ofPattern("d MMM"),
            )} – ${weekStart.plusDays(LAST_DAY_OFFSET).format(
                DateTimeFormatter.ofPattern("d MMM yyyy"),
            )}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            for (offset in 0L until DAYS_IN_WEEK) {
                val day = weekStart.plusDays(offset)
                val dayLabel = day.format(dayFormatter).replaceFirstChar { it.uppercase() }
                Text(
                    text = dayLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0L until DAYS_IN_WEEK).toList()) { offset ->
                val day = weekStart.plusDays(offset)
                val dayEvents = events.filter { it.start.toLocalDate() == day }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        text = day.format(DateTimeFormatter.ofPattern("dd")),
                        modifier = Modifier.width(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        if (dayEvents.isEmpty()) {
                            Text(
                                "Aucun événement",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        dayEvents.forEach { event ->
                            Text(event.title, fontWeight = FontWeight.Medium)
                            Text(
                                "${event.start.toLocalTime().format(
                                    DateTimeFormatter.ofPattern("HH:mm"),
                                )} – ${event.end.toLocalTime().format(
                                    DateTimeFormatter.ofPattern("HH:mm"),
                                )}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun AgendaView(events: List<CalendarEvent>) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Agenda",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (events.isEmpty()) {
            Text(
                "Aucun événement cette semaine",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        events.forEach { event ->
            Text(
                event.start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                fontWeight = FontWeight.Bold,
            )
            Text(event.title)
            Text(
                "${event.start.toLocalTime()} – ${event.end.toLocalTime()}",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var calendars by remember { mutableStateOf(emptyList<CalendarChoice>()) }
    var selectedId by remember {
        mutableStateOf(
            context.getSharedPreferences("simplercal", 0).getLong(SELECTED_CALENDAR_KEY, -1L),
        )
    }
    LaunchedEffect(Unit) { calendars = loadCalendars(context) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Retour") }
            Text(
                "Réglages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Calendrier utilisé", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (calendars.isEmpty()) {
            Text(
                "Aucun calendrier disponible ou accès non accordé.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            calendars.forEach { calendar ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selectedId ==
                            calendar.id
                        ) {
                            "●"
                        } else {
                            "○"
                        },
                        modifier = Modifier.width(32.dp),
                    )
                    TextButton(onClick = {
                        selectedId = calendar.id
                        val preferences =
                            context
                                .getSharedPreferences(
                                    PREFERENCES_NAME,
                                    0,
                                )
                        preferences.edit {
                            putLong(SELECTED_CALENDAR_KEY, calendar.id)
                        }
                    }) { Text(calendar.name) }
                }
            }
        }
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
                    CalendarContract.Calendars.CALENDAR_COLOR,
                ),
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )
        val calendars =
            cursor
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                CalendarChoice(
                                    id = cursor.getLong(0),
                                    name = cursor.getString(1),
                                    color = cursor.getInt(2),
                                ),
                            )
                        }
                    }
                }
        calendars.orEmpty()
    }.getOrDefault(emptyList())

@Suppress("MissingUseCall")
private fun loadEvents(
    context: Context,
    weekStart: LocalDate,
    calendarId: Long,
): List<CalendarEvent> =
    runCatching {
        val zone = ZoneId.systemDefault()
        val startDate = weekStart.atStartOfDay(zone)
        val startMillis = startDate.toInstant().toEpochMilli()
        val endDate = weekStart.plusDays(DAYS_IN_WEEK).atStartOfDay(zone)
        val endMillis = endDate.toInstant().toEpochMilli()
        val builder =
            CalendarContract.Instances.CONTENT_URI
                .buildUpon()
                .appendPath(startMillis.toString())
                .appendPath(endMillis.toString())
        val selection =
            if (calendarId >=
                0
            ) {
                "${CalendarContract.Instances.CALENDAR_ID} = ?"
            } else {
                null
            }
        val selectionArgs = if (calendarId >= 0) arrayOf(calendarId.toString()) else null
        val cursor =
            context.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                ),
                selection,
                selectionArgs,
                CalendarContract.Instances.BEGIN,
            )
        val events =
            cursor
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                CalendarEvent(
                                    title = cursor.getString(0) ?: "Sans titre",
                                    start =
                                        LocalDateTime.ofInstant(
                                            Instant.ofEpochMilli(cursor.getLong(1)),
                                            zone,
                                        ),
                                    end =
                                        LocalDateTime.ofInstant(
                                            Instant.ofEpochMilli(cursor.getLong(2)),
                                            zone,
                                        ),
                                ),
                            )
                        }
                    }
                }
        events.orEmpty()
    }.getOrDefault(emptyList())
