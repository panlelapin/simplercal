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

/** Refreshes the displayed week when the Android Calendar Provider changes. */
@Composable
internal fun CalendarChangesEffect(
    context: Context,
    calendarIds: Set<Long>,
    hasPermission: Boolean,
    onCalendarChanged: () -> Unit,
    onPermissionRevoked: () -> Unit,
    lifecycle: Lifecycle?,
    onObservationFailed: (Boolean) -> Unit,
    registrationFactory: ((() -> Unit) -> CalendarObserverRegistration)? = null,
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
    DisposableEffect(context, calendarIds, hasPermission, isStarted, registrationFactory) {
        if (calendarIds.isEmpty() || !hasPermission || !isStarted) {
            currentOnObservationFailed.value(false)
            onDispose {}
        } else {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val changed = { currentOnCalendarChanged.value() }
            val registration = registrationFactory?.invoke(changed) ?: object : CalendarObserverRegistration {
                private val observer = CalendarContentObserver(changed)
                override fun events() = context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
                override fun calendars() = context.contentResolver.registerContentObserver(CalendarContract.Calendars.CONTENT_URI, true, observer)
                override fun close() {
                    try { context.contentResolver.unregisterContentObserver(observer) }
                    catch (error: RuntimeException) { Log.w(LOG_TAG, "Calendar observer cleanup failed", error) }
                }
            }
            val observation = CalendarObservation(scope, registration,
                { currentOnPermissionRevoked.value() }, { currentOnObservationFailed.value(it) })
            onDispose {
                observation.close()
                scope.cancel()
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
