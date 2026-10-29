package com.pomodoro.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object SessionAlarms {
    private const val REQUEST_CODE = 42_001

    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        if (TimerStore.isSessionPaused(app)) {
            cancel(app)
            return
        }
        if (TimerStore.remainingSeconds(app) <= 0) {
            if (TimerStore.phase(app) != Phase.IDLE) {
                deliverPhaseEnd(app)
            }
            return
        }
        if (!TimerStore.isSessionActive(app)) {
            cancel(app)
            return
        }

        val triggerAt = ProgressRefresh.nextTriggerAtMillis(app)
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(app)
        alarmManager.cancel(pending)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java).cancel(pendingIntent(app))
    }

    internal fun deliverPhaseEnd(app: Context) {
        val intent = Intent(app, PomodoroService::class.java)
            .setAction(PomodoroService.ACTION_SESSION_ALARM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SessionAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
