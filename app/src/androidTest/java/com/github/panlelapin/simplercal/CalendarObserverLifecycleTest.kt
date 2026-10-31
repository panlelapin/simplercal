package com.github.panlelapin.simplercal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarObserverLifecycleTest {
    @get:Rule val rule = createComposeRule()

    @Test fun observersFollowStartStopPermissionSelectionAndCompositionDisposal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val ids = mutableStateOf(setOf(7L))
        val permission = mutableStateOf(false)
        val visible = mutableStateOf(true)
        var events = 0
        var calendars = 0
        var closed = 0
        var invalidations = 0
        var notify: (() -> Unit)? = null
        val factory: (() -> Unit) -> CalendarObserverRegistration = { changed ->
            notify = changed
            object : CalendarObserverRegistration {
                override fun events() { events++ }
                override fun calendars() { calendars++ }
                override fun close() { closed++ }
            }
        }
        rule.setContent {
            if (visible.value) CalendarChangesEffect(context, ids.value, permission.value,
                { invalidations++ }, { fail("Unexpected revocation") }, owner.lifecycle, {}, factory)
        }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { assertEquals(0, events); permission.value = true }
        rule.runOnIdle { assertEquals(1, events); assertEquals(1, calendars); notify!!.invoke() }
        rule.runOnIdle { assertEquals(1, invalidations); ids.value = setOf(8L) }
        rule.runOnIdle { assertEquals(1, closed); assertEquals(2, events); owner.registry.currentState = Lifecycle.State.CREATED }
        rule.runOnIdle { assertEquals(2, closed); owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { assertEquals(3, events); permission.value = false }
        rule.runOnIdle { assertEquals(3, closed); ids.value = emptySet(); permission.value = true }
        rule.runOnIdle { assertEquals(3, events); ids.value = setOf(7L) }
        rule.runOnIdle { assertEquals(4, events); visible.value = false }
        rule.runOnIdle { assertEquals(4, closed); owner.registry.currentState = Lifecycle.State.DESTROYED }
    }
}
