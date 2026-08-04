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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit

private const val PREFERENCES_NAME = "simplercal"
private const val SELECTED_CALENDAR_KEY = "selected_calendar_id"
private const val GITHUB_URL = "https://github.com/panlelapin/simplercal"

private data class CalendarChoice(
    val id: Long,
    val name: String,
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
    MaterialTheme(colorScheme = colorScheme) {
        var isSettingsVisible by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxSize()) {
            HelloScreen(onSettings = { isSettingsVisible = true })
            if (isSettingsVisible) {
                SettingsScreen(onBack = { isSettingsVisible = false })
            }
        }
    }
}

@Composable
@Suppress("FunctionName", "ktlint:standard:function-naming")
private fun HelloScreen(onSettings: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onSettings,
                    modifier = Modifier.semantics { contentDescription = "Settings" },
                ) { Text("⚙", fontSize = 24.sp) }
                Text(
                    "Hello",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = {},
                    modifier = Modifier.semantics { contentDescription = "Change view" },
                ) { Text("▦", fontSize = 24.sp) }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Hello", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
@Suppress("CognitiveComplexMethod", "FunctionName", "LongMethod", "ktlint:standard:function-naming")
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val releaseVersion =
        remember {
            val packageInfo =
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            packageInfo.versionName ?: "---"
        }
    var calendars by remember { mutableStateOf(emptyList<CalendarChoice>()) }
    var selectedId by remember {
        mutableStateOf(
            context
                .getSharedPreferences(PREFERENCES_NAME, 0)
                .getLong(SELECTED_CALENDAR_KEY, -1L),
        )
    }
    val hasPermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { if (it) calendars = loadCalendars(context) }
    LaunchedEffect(hasPermission) {
        if (hasPermission) calendars = loadCalendars(context)
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
            Text("Calendar", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (!hasPermission) {
                Text("Allow access to choose an Android calendar.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text("Allow")
                }
            } else if (calendars.isEmpty()) {
                Text("No visible calendars available.")
            } else {
                calendars.forEach { calendar ->
                    TextButton(onClick = {
                        selectedId = calendar.id
                        context.getSharedPreferences(PREFERENCES_NAME, 0).edit {
                            putLong(SELECTED_CALENDAR_KEY, calendar.id)
                        }
                    }) {
                        Text(
                            if (selectedId ==
                                calendar.id
                            ) {
                                "● ${calendar.name}"
                            } else {
                                "○ ${calendar.name}"
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = "SimplerCal v.$releaseVersion",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = GITHUB_URL,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
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
