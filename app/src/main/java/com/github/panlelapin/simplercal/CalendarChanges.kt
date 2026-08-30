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

/** Refreshes the displayed week when the Android Calendar Provider changes. */
@Composable
internal fun CalendarChangesEffect(
    context: Context,
    calendarId: Long,
    hasPermission: Boolean,
    onCalendarChanged: () -> Unit,
    onPermissionRevoked: () -> Unit,
) {
    val currentOnCalendarChanged = rememberUpdatedState(onCalendarChanged)
    val currentOnPermissionRevoked = rememberUpdatedState(onPermissionRevoked)
    DisposableEffect(context, calendarId, hasPermission) {
        if (calendarId < 0L || !hasPermission) {
            onDispose {}
        } else {
            val observer = CalendarContentObserver {
                currentOnCalendarChanged.value()
            }
            val registered =
                try {
                    context.contentResolver.registerContentObserver(
                        CalendarContract.Events.CONTENT_URI,
                        true,
                        observer,
                    )
                    true
                } catch (error: SecurityException) {
                    Log.e(LOG_TAG, "Calendar observer registration was denied", error)
                    currentOnPermissionRevoked.value()
                    false
                } catch (error: RuntimeException) {
                    Log.e(LOG_TAG, "Calendar observer registration failed", error)
                    false
                }
            onDispose {
                if (registered) {
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
