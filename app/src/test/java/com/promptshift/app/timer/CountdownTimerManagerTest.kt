package com.promptshift.app.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CountdownTimerManagerTest {

    @Test
    fun `formats MM SS cleanly and triggers finish callback once countdown hits zero`() = runTest {
        assertEquals("00:00", TimerState.formatMmSs(0L))
        assertEquals("00:01", TimerState.formatMmSs(150L))
        assertEquals("01:45", TimerState.formatMmSs(105_000L))

        var fakeNow = 1_000L
        var finishCount = 0
        var dismissCount = 0

        val manager = CountdownTimerManager(
            appScope = backgroundScope,
            clockMs = { fakeNow },
            tickIntervalMs = 250L,
            onTimerFinished = { finishCount++ },
            onAlertDismissed = { dismissCount++ },
        )

        assertFalse(manager.start(0, 0))
        assertTrue(manager.start(0, 2))
        runCurrent()

        assertTrue(manager.state.value.isRunning)
        assertEquals("00:02", manager.state.value.formattedRemaining())
        assertEquals("00:02", manager.state.value.chipLabel())

        // Advance 1 second
        fakeNow += 1_000L
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(manager.state.value.isRunning)
        assertEquals("00:01", manager.state.value.formattedRemaining())

        // Advance final 1 second -> finishes
        fakeNow += 1_000L
        advanceTimeBy(1_000L)
        runCurrent()
        assertFalse(manager.state.value.isRunning)
        assertTrue(manager.state.value.isFinished)
        assertEquals("Done!", manager.state.value.chipLabel())
        assertEquals(1, finishCount)

        // Dismiss clears finished alert
        manager.dismissFinishedAlert()
        assertFalse(manager.state.value.isFinished)
        assertEquals("⏱", manager.state.value.chipLabel())
        assertTrue(dismissCount >= 1)
    }

    @Test
    fun `cancel and reset work cleanly on running timer`() = runTest {
        var fakeNow = 5_000L
        val manager = CountdownTimerManager(
            appScope = backgroundScope,
            clockMs = { fakeNow },
            tickIntervalMs = 250L,
        )

        assertTrue(manager.start(1, 0))
        runCurrent()
        fakeNow += 20_000L
        advanceTimeBy(500L)
        runCurrent()
        assertEquals("00:40", manager.state.value.formattedRemaining())

        // Reset restores full 01:00
        assertTrue(manager.reset())
        runCurrent()
        assertTrue(manager.state.value.isRunning)
        assertEquals("01:00", manager.state.value.formattedRemaining())

        // Cancel stops timer cleanly
        manager.cancel()
        assertFalse(manager.state.value.isRunning)
        assertEquals(0L, manager.state.value.remainingMs)
    }

    @Test
    fun `startTotalSeconds formats preset labels and notifies start and cancel callbacks`() = runTest {
        assertEquals("00:30", TimerState.formatPresetLabel(30))
        assertEquals("05:00", TimerState.formatPresetLabel(300))

        var fakeNow = 10_000L
        val startedTargets = mutableListOf<Long>()
        var cancelCount = 0

        val manager = CountdownTimerManager(
            appScope = backgroundScope,
            clockMs = { fakeNow },
            tickIntervalMs = 250L,
            onTimerStarted = { target -> startedTargets.add(target) },
            onTimerCancelled = { cancelCount++ },
        )

        assertFalse(manager.startTotalSeconds(0))
        assertTrue(manager.startTotalSeconds(90))
        runCurrent()

        assertTrue(manager.state.value.isRunning)
        assertEquals("01:30", manager.state.value.formattedRemaining())
        assertEquals(100_000L, manager.state.value.targetEndMs)
        assertEquals(listOf(100_000L), startedTargets)

        manager.cancel()
        assertFalse(manager.state.value.isRunning)
        assertEquals(0L, manager.state.value.targetEndMs)
        assertEquals(1, cancelCount)
    }
}
