package com.pomodoro.timer

import android.content.Context
import kotlin.math.floor
import kotlin.math.min

/** Throttles island ring/bar, notification art, and widget to progress steps (not every second). */
object ProgressRefresh {
    /** Island ring steps every 2% of phase time; notification buckets use the same step. */
    const val PROGRESS_STEP = 0.02f

    private var lastProgressBucket = Int.MIN_VALUE
    private var lastMinuteBucket = Int.MIN_VALUE
    private var lastNotificationProgressBucket = Int.MIN_VALUE
    private var lastNotificationMinuteBucket = Int.MIN_VALUE

    data class RefreshPlan(val notification: Boolean, val islandOrWidget: Boolean)

    fun reset() {
        lastProgressBucket = Int.MIN_VALUE
        lastMinuteBucket = Int.MIN_VALUE
        lastNotificationProgressBucket = Int.MIN_VALUE
        lastNotificationMinuteBucket = Int.MIN_VALUE
    }

    /** Island ring / bar: refresh every 2% of phase progress. */
    fun stepFraction(@Suppress("UNUSED_PARAMETER") context: Context): Float = PROGRESS_STEP

    /** Ring display: add 2% each time that much phase time has elapsed. */
    fun islandRingProgress(context: Context): Float {
        val p = TimerStore.phaseProgress(context)
        return (floor(p / PROGRESS_STEP) * PROGRESS_STEP).toFloat().coerceIn(0f, 1f)
    }

    /** Ms until the next 2% boundary (for island timer ticks). */
    fun msUntilNextProgressStep(context: Context): Long {
        val totalSec = TimerStore.runningPhaseTotalSeconds(context)
        if (totalSec <= 0) return 1000L
        if (TimerStore.isSessionPaused(context)) return 500L
        val totalMs = totalSec * 1000L
        val end = TimerStore.endAtMillis(context)
        if (end <= 0L) return 1000L
        val msLeft = (end - System.currentTimeMillis()).coerceAtLeast(0L)
        val elapsedMs = totalMs - msLeft
        val stepMs = (PROGRESS_STEP * totalMs).toLong().coerceAtLeast(1L)
        val nextBoundary = ((elapsedMs / stepMs) + 1) * stepMs
        return (nextBoundary - elapsedMs).coerceIn(1L, stepMs)
    }

    fun progressBucket(context: Context): Int {
        val step = PROGRESS_STEP
        val progress = TimerStore.phaseProgress(context)
        val maxBucket = (1f / step).toInt() - 1
        return (progress / step).toInt().coerceIn(0, maxBucket.coerceAtLeast(0))
    }

    /** Minute label bucket: every 1 or 5 minutes left (settings). */
    private fun minuteBucket(context: Context): Int {
        val remaining = TimerStore.remainingSeconds(context)
        if (remaining <= 0) return 0
        val minuteLeft = (remaining + 59) / 60
        val step = TimerStore.uiRefreshMinuteStep(context)
        return minuteLeft / step
    }

    fun shouldRefreshUi(context: Context): Boolean = planRefresh(context).let {
        it.notification || it.islandOrWidget
    }

    /** Notification: progress / Now Bar art + minute label in shade. Time also ticks via chronometer. */
    fun planRefresh(context: Context): RefreshPlan {
        val progress = progressBucket(context)
        val minute = minuteBucket(context)
        val notification = progress != lastNotificationProgressBucket ||
            minute != lastNotificationMinuteBucket
        val islandOrWidget = progress != lastProgressBucket || minute != lastMinuteBucket
        if (notification) {
            lastNotificationProgressBucket = progress
            lastNotificationMinuteBucket = minute
        }
        if (islandOrWidget) {
            lastProgressBucket = progress
            lastMinuteBucket = minute
        }
        return RefreshPlan(notification, islandOrWidget)
    }

    fun delayMsUntilNextCheck(context: Context): Long {
        val remainingSec = TimerStore.remainingSeconds(context)
        if (remainingSec <= 0) return 1000L
        val totalSec = TimerStore.runningPhaseTotalSeconds(context)
        if (totalSec <= 0) return 1000L

        val progress = TimerStore.phaseProgress(context)
        val step = PROGRESS_STEP
        val nextProgress = (progressBucket(context) + 1) * step
        val msToProgress = if (nextProgress > 1f) {
            remainingSec * 1000L
        } else {
            ((nextProgress - progress).coerceAtLeast(0f) * totalSec * 1000f).toLong()
        }

        val msToMinute = msUntilMinuteBucket(context, remainingSec)
        val minGap = (step * totalSec * 1000f).toLong().coerceIn(50L, 500L)

        return min(min(msToProgress, msToMinute), remainingSec * 1000L).coerceAtLeast(minGap)
    }

    /** Alarm schedule: always include phase end; UI wake at progress or minute milestone. */
    fun nextTriggerAtMillis(context: Context): Long {
        val end = TimerStore.endAtMillis(context)
        val ui = System.currentTimeMillis() + delayMsUntilNextCheck(context)
        return if (end > 0) min(end, ui) else ui
    }

    private fun msUntilMinuteBucket(context: Context, remainingSec: Int): Long {
        val stepMin = TimerStore.uiRefreshMinuteStep(context)
        val minuteLeft = (remainingSec + 59) / 60
        if (minuteLeft <= 0) return 60_000L
        val targetMinute = if (minuteLeft % stepMin == 0) {
            (minuteLeft - stepMin).coerceAtLeast(0)
        } else {
            minuteLeft - (minuteLeft % stepMin)
        }
        val targetSec = targetMinute * 60
        return ((remainingSec - targetSec).coerceAtLeast(1)) * 1000L
    }
}
