package com.github.panlelapin.simplercal

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeekAnimationTest {
    @Test fun animationUses500ElapsedMillisecondsAtEveryAnimatorScale() = runTest {
        listOf(0f, .5f, 1f, 10f).forEach { scale ->
            val clock = BroadcastFrameClock()
            val durationScale = object : MotionDurationScale { override val scaleFactor = scale }
            val interaction = WeekInteraction(0) {}
            interaction.selectDay(4)
            var finished = 0
            val job = launch(clock + durationScale, start = CoroutineStart.UNDISPATCHED) {
                runDayWeightAnimation(interaction, 7, interaction.animationRequest,
                    { weights, _ -> interaction.animatedDayWeights = weights }, { finished++ })
            }
            clock.sendFrame(0L); runCurrent()
            clock.sendFrame(250_000_000L); runCurrent()
            assertEquals(20.125f, interaction.animatedDayWeights[0], .0001f)
            assertTrue(0 in interaction.contentExpandedDays)
            clock.sendFrame(499_000_000L); runCurrent()
            assertEquals(0, finished)
            assertTrue(0 in interaction.contentExpandedDays)
            clock.sendFrame(500_000_000L); runCurrent()
            job.join()
            assertEquals(1, finished)
            assertEquals(dayWeightsFor(4), interaction.animatedDayWeights)
            assertEquals(setOf(4, 5), interaction.contentExpandedDays)
        }
    }

    @Test fun delayedFramesFinishAtTheFirstFrameAfterTheDeadline() = runTest {
        val clock = BroadcastFrameClock()
        val interaction = WeekInteraction(0) {}
        interaction.selectDay(3)
        val job = launch(clock, start = CoroutineStart.UNDISPATCHED) {
            runDayWeightAnimation(interaction, 7, interaction.animationRequest, { weights, _ -> interaction.animatedDayWeights = weights })
        }
        clock.sendFrame(100L); runCurrent()
        clock.sendFrame(800_000_100L); runCurrent()
        job.join()
        assertEquals(dayWeightsFor(3), interaction.animatedDayWeights)
        assertEquals(setOf(3, 4), interaction.contentExpandedDays)
    }

    @Test fun supersededTimerCannotOverwriteADragBeforeItsEffectIsCancelled() = runTest {
        val clock = BroadcastFrameClock()
        val interaction = WeekInteraction(0) {}
        interaction.selectDay(4)
        val request = interaction.animationRequest
        val job = launch(clock, start = CoroutineStart.UNDISPATCHED) {
            runDayWeightAnimation(interaction, 7, request, { weights, _ -> interaction.animatedDayWeights = weights })
        }
        clock.sendFrame(0L); runCurrent()
        clock.sendFrame(200_000_000L); runCurrent()
        interaction.startDrag()
        interaction.dragToFocus(3.8f)
        val dragFrame = interaction.animatedDayWeights
        // Deliberately leave the old job alive: its request guard must protect the drag.
        clock.sendFrame(300_000_000L); runCurrent()
        job.join()
        assertEquals(dragFrame, interaction.animatedDayWeights)
        assertTrue(interaction.isDragging)
    }

    @Test fun cancellationResumesFromTheLastDragFrameWithoutAnImmediateJump() = runTest {
        val interaction = WeekInteraction(4) {}
        interaction.animatedDayWeights = dayWeightsForFocus(1.5f)
        interaction.startDrag()
        interaction.dragToFocus(3.6f)
        val frame = interaction.animatedDayWeights
        interaction.cancelDrag()
        assertEquals(frame, interaction.animatedDayWeights)
        val clock = BroadcastFrameClock()
        val job = launch(clock, start = CoroutineStart.UNDISPATCHED) {
            runDayWeightAnimation(interaction, 7, interaction.animationRequest, { weights, _ -> interaction.animatedDayWeights = weights })
        }
        clock.sendFrame(0L); runCurrent()
        assertEquals(frame, interaction.animatedDayWeights)
        clock.sendFrame(500_000_000L); runCurrent()
        job.join()
        assertEquals(4, interaction.selectedDayIndex)
        assertEquals(dayWeightsFor(4), interaction.animatedDayWeights)
    }
}
