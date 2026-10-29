package com.pomodoro.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        if (TimerStore.isSessionPaused(app)) return
        if (TimerStore.phase(app) == Phase.IDLE) return
        if (TimerStore.remainingSeconds(app) > 0 && !TimerStore.isSessionActive(app)) return
        SessionAlarms.deliverPhaseEnd(app)
    }
}
