package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DayDetailsDialog(
    day: WeekDay,
    markers: DayMarkers,
    isSimulation: Boolean,
    onMarkersChange: (LocalDate, DayMarkers) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH))) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                item {
                    if (isSimulation) {
                        Text(stringResource(R.string.simulation_markers_explanation))
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = markers.isHolidays,
                                onClick = { onMarkersChange(day.date, markers.copy(isHolidays = !markers.isHolidays)) },
                                label = { Text(stringResource(R.string.marker_vacation)) },
                            )
                            FilterChip(
                                selected = markers.isBankHoliday,
                                onClick = { onMarkersChange(day.date, markers.copy(isBankHoliday = !markers.isBankHoliday)) },
                                label = { Text(stringResource(R.string.marker_bank_holiday)) },
                            )
                        }
                    }
                }
                if (day.events.isEmpty()) item { Text(stringResource(R.string.no_events)) }
                items(day.events, key = { it.instanceId }) { event ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(event.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (event.allDay) stringResource(R.string.event_all_day) else eventTimeRange(event),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

internal fun eventTimeRange(event: CalendarEvent, zone: ZoneId = ZoneId.systemDefault()): String {
    val format = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH).withZone(zone)
    return "${format.format(Instant.ofEpochMilli(event.beginMillis))} – ${format.format(Instant.ofEpochMilli(event.endMillis))}"
}
