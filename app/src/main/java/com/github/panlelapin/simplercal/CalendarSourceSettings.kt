package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CalendarSourcesSection(
    state: AppUiState,
    actions: SettingsActions,
    onAndroid: (CalendarRole) -> Unit,
    onLocal: (CalendarRole) -> Unit,
) {
    var editingRole by rememberSaveable { mutableStateOf<String?>(null) }
    var url by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.section_calendar), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.sources_explanation), style = MaterialTheme.typography.bodySmall)
    CalendarRole.entries.forEach { role ->
        val calendar = state.calendarSources.getValue(role)
        val source = calendar.source
        val label = stringResource(role.labelResource)
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = source.enabled, onCheckedChange = { actions.onSourceEnabled(role, it) },
                    modifier = Modifier.semantics { contentDescription = label })
            }
            val description = when (source.kind) {
                CalendarSourceKind.ANDROID -> state.calendars.firstOrNull { it.id == source.calendarId }?.let {
                    val owner = it.accountName.ifEmpty { it.ownerAccount }
                    if (owner.isEmpty()) it.name else "${it.name} ($owner)"
                } ?: stringResource(if (source.calendarId < 0) R.string.calendar_error_missing else R.string.source_android_selected)
                CalendarSourceKind.LOCAL -> android.net.Uri.parse(source.location).lastPathSegment
                    ?.substringAfterLast('/')?.substringAfterLast(':')
                    ?.takeIf { it.endsWith(".ics", true) || it.endsWith(".ical", true) }
                    ?: stringResource(R.string.source_local_selected)
                CalendarSourceKind.HTTPS -> source.location
                CalendarSourceKind.NONE -> stringResource(R.string.source_none)
            }
            Text(if (source.kind == CalendarSourceKind.NONE) description else
                "${stringResource(source.kind.labelResource)}: $description", style = MaterialTheme.typography.bodySmall)
            // Three separate rows stay usable on narrow screens and with large fonts.
            OutlinedButton(onClick = { onAndroid(role) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.source_choose_android))
            }
            OutlinedButton(onClick = { onLocal(role) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.source_choose_local))
            }
            OutlinedButton(onClick = {
                url = if (source.kind == CalendarSourceKind.HTTPS) source.location else ""
                editingRole = role.name
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.source_choose_https)) }
            if (source.isFile) {
                val timestamp = calendar.lastSuccessMillis?.let {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ENGLISH)
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
                } ?: stringResource(R.string.source_never_refreshed)
                Text(stringResource(R.string.source_last_success, timestamp), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { actions.onRefreshSource(role) }, enabled = source.enabled && !calendar.refreshing) {
                    Text(stringResource(if (calendar.refreshing) R.string.source_refreshing else R.string.source_refresh))
                }
            }
            val failure = calendar.failure
            val failureResource = when (failure) {
                CalendarFailureReason.PERMISSION_REVOKED -> R.string.calendar_error_permission
                CalendarFailureReason.CALENDAR_UNAVAILABLE -> R.string.calendar_error_missing
                CalendarFailureReason.PROVIDER_UNAVAILABLE -> R.string.calendar_error_provider
                CalendarFailureReason.INVALID_SOURCE -> R.string.source_error_url
                CalendarFailureReason.ICAL_UNAVAILABLE -> R.string.source_error_ical
                null -> null
            }
            failureResource?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }
    editingRole?.let { name ->
        val validUrl = try { httpsAddress(url); true } catch (_: Exception) { false }
        AlertDialog(onDismissRequest = { editingRole = null },
            title = { Text(stringResource(R.string.source_choose_https)) },
            text = { OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true,
                label = { Text(stringResource(R.string.source_https_url)) },
                isError = url.isNotBlank() && !validUrl) },
            confirmButton = { TextButton(enabled = validUrl, onClick = {
                actions.onSourceSelected(CalendarRole.valueOf(name), CalendarSourceKind.HTTPS, url)
                editingRole = null
            }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { editingRole = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    Spacer(Modifier.height(12.dp))
}
