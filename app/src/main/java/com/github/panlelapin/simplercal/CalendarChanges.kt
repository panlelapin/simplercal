package com.github.panlelapin.simplercal

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/** Refreshes the displayed week when the Android Calendar Provider changes. */
@Composable
internal fun CalendarChangesEffect(
    context: Context,
    calendarId: Long,
    hasPermission: Boolean,
    onCalendarChanged: () -> Unit,
    onPermissionRevoked: () -> Unit,
    lifecycle: Lifecycle?,
    onObservationFailed: (Boolean) -> Unit,
) {
    val currentOnCalendarChanged = rememberUpdatedState(onCalendarChanged)
    val currentOnPermissionRevoked = rememberUpdatedState(onPermissionRevoked)
    val currentOnObservationFailed = rememberUpdatedState(onObservationFailed)
    var isStarted by remember(lifecycle) {
        mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true)
    }
    DisposableEffect(lifecycle) {
        val listener = LifecycleEventObserver { _, _ ->
            isStarted = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        }
        lifecycle?.addObserver(listener)
        onDispose { lifecycle?.removeObserver(listener) }
    }
    DisposableEffect(context, calendarId, hasPermission, isStarted) {
        if (calendarId < 0L || !hasPermission || !isStarted) {
            currentOnObservationFailed.value(false)
            onDispose {}
        } else {
            val observer = CalendarContentObserver {
                currentOnCalendarChanged.value()
            }
            var registered = false
            var eventsRegistered = false
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            scope.launch {
                var retryDelay = 1_000L
                while (isActive && !registered) {
                    try {
                        if (!eventsRegistered) {
                            context.contentResolver.registerContentObserver(
                                CalendarContract.Events.CONTENT_URI, true, observer,
                            )
                            eventsRegistered = true
                        }
                        context.contentResolver.registerContentObserver(
                            CalendarContract.Calendars.CONTENT_URI, true, observer,
                        )
                        registered = true
                        currentOnObservationFailed.value(false)
                    } catch (error: SecurityException) {
                        Log.e(LOG_TAG, "Calendar observer registration was denied", error)
                        currentOnPermissionRevoked.value()
                        break
                    } catch (error: RuntimeException) {
                        Log.e(LOG_TAG, "Calendar observer registration failed", error)
                        currentOnObservationFailed.value(true)
                    }
                    if (!registered) {
                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                    }
                }
            }
            onDispose {
                scope.cancel()
                if (eventsRegistered) {
                    try {
                        context.contentResolver.unregisterContentObserver(observer)
                    } catch (error: RuntimeException) {
                        Log.w(LOG_TAG, "Calendar observer cleanup failed", error)
                    }
                }
            }
        }
    }
}

private const val LOG_TAG = "SimplerCalCalendar"

private class CalendarContentObserver(
    private val onChanged: () -> Unit,
) : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        onChanged()
    }
}
