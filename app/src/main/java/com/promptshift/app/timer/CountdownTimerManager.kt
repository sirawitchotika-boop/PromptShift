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
    val targetEndMs: Long = 0L,
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

        fun formatPresetLabel(totalSeconds: Int): String =
            formatMmSs(totalSeconds.coerceAtLeast(0) * 1000L)
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
    private val onTimerStarted: (targetEndMs: Long) -> Unit = {},
    private val onTimerCancelled: () -> Unit = {},
    private val onTimerFinished: () -> Unit = {},
    private val onAlertDismissed: () -> Unit = {},
) {
    private val lock = Any()
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

    fun startTotalSeconds(totalSeconds: Int): Boolean {
        val safeSec = totalSeconds.coerceIn(0, 999 * 60 + 59)
        return startDurationMs(safeSec * 1000L)
    }

    fun startDurationMs(durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val targetEndMs: Long
        synchronized(lock) {
            tickJob?.cancel()
            tickJob = null
            lastConfiguredDurationMs = durationMs
            targetEndMs = clockMs() + durationMs
            _state.value = TimerState(
                isRunning = true,
                totalDurationMs = durationMs,
                remainingMs = durationMs,
                isFinished = false,
                targetEndMs = targetEndMs,
            )
        }
        onAlertDismissed()
        onTimerStarted(targetEndMs)

        val job = appScope.launch {
            while (isActive) {
                val remaining = (targetEndMs - clockMs()).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    if (completeIfRunning(expectedTargetEndMs = targetEndMs, durationMs = durationMs)) {
                        onTimerFinished()
                    }
                    break
                } else {
                    synchronized(lock) {
                        val current = _state.value
                        if (current.isRunning && current.targetEndMs == targetEndMs) {
                            _state.value = current.copy(
                                isRunning = true,
                                remainingMs = remaining,
                                isFinished = false,
                                targetEndMs = targetEndMs,
                            )
                        } else {
                            return@launch
                        }
                    }
                }
                delay(tickIntervalMs)
            }
        }
        synchronized(lock) {
            if (_state.value.isRunning && _state.value.targetEndMs == targetEndMs) {
                tickJob = job
            } else {
                job.cancel()
            }
        }
        return true
    }

    /**
     * Called when the Doze-safe AlarmManager wake-up fires.
     * Completes the running timer idempotently if the wall clock has reached [TimerState.targetEndMs]
     * (or if the coroutine tick was paused by Android Doze), without ever double-firing [onTimerFinished].
     */
    fun onAlarmExpired(nowMs: Long = clockMs()): Boolean {
        val shouldFire = synchronized(lock) {
            val current = _state.value
            if (!current.isRunning || current.targetEndMs <= 0L) {
                false
            } else if (nowMs < current.targetEndMs) {
                // Alarm fired slightly early; keep running and let the loop finish cleanly.
                _state.value = current.copy(remainingMs = (current.targetEndMs - nowMs).coerceAtLeast(0L))
                false
            } else {
                tickJob?.cancel()
                tickJob = null
                _state.value = TimerState(
                    isRunning = false,
                    totalDurationMs = current.totalDurationMs,
                    remainingMs = 0L,
                    isFinished = true,
                    targetEndMs = 0L,
                )
                true
            }
        }
        if (shouldFire) {
            onTimerFinished()
        }
        return shouldFire
    }

    fun cancel() {
        synchronized(lock) {
            tickJob?.cancel()
            tickJob = null
            _state.value = TimerState(
                isRunning = false,
                totalDurationMs = lastConfiguredDurationMs,
                remainingMs = 0L,
                isFinished = false,
                targetEndMs = 0L,
            )
        }
        onAlertDismissed()
        onTimerCancelled()
    }

    fun reset(): Boolean {
        val duration = synchronized(lock) {
            lastConfiguredDurationMs.takeIf { it > 0L } ?: _state.value.totalDurationMs
        }
        if (duration <= 0L) {
            cancel()
            return false
        }
        return startDurationMs(duration)
    }

    fun dismissFinishedAlert() {
        onAlertDismissed()
        synchronized(lock) {
            if (_state.value.isFinished) {
                _state.value = _state.value.copy(isFinished = false)
            }
        }
    }

    private fun completeIfRunning(expectedTargetEndMs: Long, durationMs: Long): Boolean {
        return synchronized(lock) {
            val current = _state.value
            if (!current.isRunning || current.targetEndMs != expectedTargetEndMs) {
                false
            } else {
                tickJob = null
                _state.value = TimerState(
                    isRunning = false,
                    totalDurationMs = durationMs,
                    remainingMs = 0L,
                    isFinished = true,
                    targetEndMs = 0L,
                )
                true
            }
        }
    }
}
