package com.pomodoro.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reschedule session alarms after reboot if a timer was running. */
class SessionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (TimerStore.isSessionPaused(context)) return
        if (TimerStore.phase(context) == Phase.IDLE) return
        SessionAlarms.scheduleNext(context)
    }
}
