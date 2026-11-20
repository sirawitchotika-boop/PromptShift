package com.promptshift.app.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TimerState(
    val isRunning: Boolean = false,
    val totalDurationMs: Long = 0L,
    val remainingMs: Long = 0L,
    val isFinished: Boolean = false,
) {
    fun formattedRemaining(): String = formatMmSs(remainingMs)

    fun chipLabel(): String = when {
        isRunning -> formattedRemaining()
        isFinished -> "Done!"
        else -> "⏱"
    }

    companion object {
        fun formatMmSs(ms: Long): String {
            val safeMs = ms.coerceAtLeast(0L)
            val totalSeconds = (safeMs + 999L) / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "%02d:%02d".format(minutes, seconds)
        }
    }
}

/**
 * Simple wall-clock-accurate countdown timer manager.
 * Pure-Kotlin core so countdown ticking, cancellation, reset, and completion callbacks are JVM unit-testable.
 */
class CountdownTimerManager(
    private val appScope: CoroutineScope,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val tickIntervalMs: Long = 250L,
    private val onTimerFinished: () -> Unit = {},
    private val onAlertDismissed: () -> Unit = {},
) {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var lastConfiguredDurationMs: Long = 0L

    fun start(minutes: Int, seconds: Int): Boolean {
        val clampedMin = minutes.coerceIn(0, 999)
        val clampedSec = seconds.coerceIn(0, 59)
        val durationMs = (clampedMin * 60L + clampedSec) * 1000L
        return startDurationMs(durationMs)
    }

    fun startDurationMs(durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        tickJob?.cancel()
        onAlertDismissed()

        lastConfiguredDurationMs = durationMs
        val targetEndMs = clockMs() + durationMs
        _state.value = TimerState(
            isRunning = true,
            totalDurationMs = durationMs,
            remainingMs = durationMs,
            isFinished = false,
        )

        tickJob = appScope.launch {
            while (isActive) {
                val remaining = (targetEndMs - clockMs()).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    _state.value = TimerState(
                        isRunning = false,
                        totalDurationMs = durationMs,
                        remainingMs = 0L,
                        isFinished = true,
                    )
                    onTimerFinished()
                    break
                } else {
                    _state.value = _state.value.copy(
                        isRunning = true,
                        remainingMs = remaining,
                        isFinished = false,
                    )
                }
                delay(tickIntervalMs)
            }
        }
        return true
    }

    fun cancel() {
        tickJob?.cancel()
        tickJob = null
        onAlertDismissed()
        _state.value = TimerState(
            isRunning = false,
            totalDurationMs = lastConfiguredDurationMs,
            remainingMs = 0L,
            isFinished = false,
        )
    }

    fun reset(): Boolean {
        val duration = lastConfiguredDurationMs.takeIf { it > 0L } ?: _state.value.totalDurationMs
        if (duration <= 0L) {
            cancel()
            return false
        }
        return startDurationMs(duration)
    }

    fun dismissFinishedAlert() {
        onAlertDismissed()
        if (_state.value.isFinished) {
            _state.value = _state.value.copy(isFinished = false)
        }
    }
}
