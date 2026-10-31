package com.github.panlelapin.simplercal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal interface CalendarObserverRegistration {
    fun events()
    fun calendars()
    fun close()
}

/** Own partial registration and retry timing, including cancellation during backoff. */
internal class CalendarObservation(
    scope: CoroutineScope,
    private val registration: CalendarObserverRegistration,
    onPermissionRevoked: () -> Unit,
    onFailure: (Boolean) -> Unit,
) {
    private var eventsRegistered = false
    private val job: Job = scope.launch {
        var retryDelay = 1_000L
        while (isActive) {
            try {
                if (!eventsRegistered) {
                    registration.events()
                    eventsRegistered = true
                }
                registration.calendars()
                onFailure(false)
                break
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                onPermissionRevoked()
                break
            } catch (_: RuntimeException) {
                onFailure(true)
            }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
        }
    }

    fun close() {
        job.cancel()
        if (eventsRegistered) {
            eventsRegistered = false
            registration.close()
        }
    }
}
