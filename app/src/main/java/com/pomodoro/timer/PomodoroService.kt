package com.pomodoro.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.pomodoro.timer.widget.PomodoroWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PomodoroService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var tickJob: Job? = null
    private lateinit var ambient: AmbientPlayer

    override fun onCreate() {
        super.onCreate()
        ambient = AmbientPlayer(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> startPhase(Phase.FOCUS, TimerStore.focusMinutes(this))
            ACTION_START_BREAK -> startPhase(Phase.BREAK, TimerStore.breakMinutes(this))
            ACTION_HOLD_PAUSE -> holdPause()
            ACTION_REFRESH_NOTIFICATION -> {
                if (TimerStore.isSessionActive(this)) updateNotification()
                return START_STICKY
            }
            ACTION_STOP -> {
                stopAll()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startPhase(phase: Phase, minutes: Int) {
        TimerStore.clearSessionPaused(this)
        TimerStore.setPhase(this, phase)
        TimerStore.setEndAtMillis(this, System.currentTimeMillis() + minutes * 60_000L)
        ambient.sync(phase)
        if (phase == Phase.FOCUS) {
            AmbientArt.loadAmbientSelection(this) { updateNotification() }
        }
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        SessionNotificationBuilder.logPromotionState(this, notification)
        startTicking()
        IslandOverlay.ensureVisible(this)
        PomodoroWidget.updateAll(this)
    }

    private fun holdPause() {
        if (TimerStore.isSessionPaused(this)) {
            resumeSession()
            return
        }
        tickJob?.cancel()
        TimerStore.setSessionPaused(
            this,
            paused = true,
            phase = TimerStore.phase(this),
            remainingSec = TimerStore.remainingSeconds(this)
        )
        ambient.release()
        updateNotification()
        IslandOverlay.refresh(this)
        PomodoroWidget.updateAll(this)
    }

    private fun resumeSession() {
        val phase = TimerStore.pausedPhase(this)
        val remaining = TimerStore.pausedRemainingSeconds(this)
        if (remaining <= 0) {
            stopAll()
            return
        }
        TimerStore.clearSessionPaused(this)
        TimerStore.setPhase(this, phase)
        TimerStore.setEndAtMillis(this, System.currentTimeMillis() + remaining * 1000L)
        ambient.sync(phase)
        startTicking()
        updateNotification()
        IslandOverlay.refresh(this)
        PomodoroWidget.updateAll(this)
    }

    private fun stopAll() {
        tickJob?.cancel()
        TimerStore.clearSessionPaused(this)
        TimerStore.setPhase(this, Phase.IDLE)
        TimerStore.setEndAtMillis(this, 0L)
        ambient.release()
        IslandOverlay.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        PomodoroWidget.updateAll(this)
    }

    private fun pause() = stopAll()

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val remaining = TimerStore.remainingSeconds(this@PomodoroService)
                if (remaining <= 0) {
                    onPhaseFinished()
                    break
                }
                updateNotification()
                IslandOverlay.ensureVisible(this@PomodoroService)
                IslandOverlay.refresh(this@PomodoroService)
                PomodoroWidget.updateAll(this@PomodoroService)
                delay(1000)
            }
        }
    }

    private fun onPhaseFinished() {
        when (TimerStore.phase(this)) {
            Phase.FOCUS -> startPhase(Phase.BREAK, TimerStore.breakMinutes(this))
            Phase.BREAK, Phase.IDLE -> pause()
        }
    }

    private fun buildNotification(): Notification {
        val open = pendingActivity()
        val pause = pendingService(ACTION_HOLD_PAUSE, 1)
        val stop = pendingService(ACTION_STOP, 2)
        return SessionNotificationBuilder.build(this, CHANNEL_ID, open, pause, stop)
    }

    private fun pendingActivity(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun pendingService(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, PomodoroService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = if (SessionNotificationBuilder.canUseLiveUpdate(this@PomodoroService)) {
                "Live timer for Now Bar and lock screen"
            } else {
                "Timer while a focus or break session runs"
            }
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        tickJob?.cancel()
        ambient.release()
        IslandOverlay.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_FOCUS = "start_focus"
        const val ACTION_START_BREAK = "start_break"
        const val ACTION_HOLD_PAUSE = "hold_pause"
        const val ACTION_REFRESH_NOTIFICATION = "refresh_notification"
        const val ACTION_STOP = "stop"
        private const val CHANNEL_ID = "pomodoro_session_v2"
        private const val NOTIFICATION_ID = 1

        fun startFocus(context: Context) = context.startForegroundService(
            Intent(context, PomodoroService::class.java).setAction(ACTION_START_FOCUS)
        )

        fun startBreak(context: Context) = context.startForegroundService(
            Intent(context, PomodoroService::class.java).setAction(ACTION_START_BREAK)
        )

        fun holdPause(context: Context) = context.startService(
            Intent(context, PomodoroService::class.java).setAction(ACTION_HOLD_PAUSE)
        )

        fun stop(context: Context) = context.startService(
            Intent(context, PomodoroService::class.java).setAction(ACTION_STOP)
        )

        fun refreshNotification(context: Context) {
            if (!TimerStore.isSessionActive(context)) return
            context.startService(
                Intent(context, PomodoroService::class.java).setAction(ACTION_REFRESH_NOTIFICATION)
            )
        }

        /** Ends the session completely. */
        fun pause(context: Context) = stop(context)
    }
}
