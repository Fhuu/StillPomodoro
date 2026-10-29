package com.pomodoro.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.pomodoro.timer.widget.PomodoroWidget

class PomodoroService : Service() {
    private lateinit var ambient: AmbientPlayer
    private var mediaSession: MediaSessionCompat? = null
    private var foregroundStarted = false
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        ambient = AmbientPlayer(this)
        mediaSession = MediaSessionCompat(this, "just_pomodoro_session").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPause() {
                    if (TimerStore.isSessionActive(this@PomodoroService) && !TimerStore.isSessionPaused(this@PomodoroService)) {
                        holdPause()
                    }
                }

                override fun onPlay() {
                    if (TimerStore.isSessionPaused(this@PomodoroService)) {
                        holdPause()
                    }
                }

                override fun onStop() {
                    if (TimerStore.isSessionActive(this@PomodoroService)) {
                        stopAll()
                    }
                }
            })
            isActive = true
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_HOLD_PAUSE || action == ACTION_STOP || action == ACTION_REFRESH_NOTIFICATION ||
            action == ACTION_SESSION_ALARM
        ) {
            if (TimerStore.isSessionActive(this) || action == ACTION_SESSION_ALARM) {
                postSessionNotification(buildNotification())
            }
        }
        when (action) {
            ACTION_START_FOCUS -> startPhase(Phase.FOCUS, TimerStore.focusDurationSeconds(this))
            ACTION_START_BREAK -> startPhase(Phase.BREAK, TimerStore.breakMinutes(this) * 60)
            ACTION_HOLD_PAUSE -> if (TimerStore.isSessionActive(this)) holdPause()
            ACTION_SESSION_ALARM -> onSessionAlarm()
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

    private fun startPhase(phase: Phase, durationSeconds: Int) {
        TimerStore.clearSessionPaused(this)
        TimerStore.setPhase(this, phase)
        TimerStore.setRunningPhaseTotalSeconds(this, durationSeconds)
        TimerStore.setEndAtMillis(this, System.currentTimeMillis() + durationSeconds * 1000L)
        mediaSession?.isActive = true
        ambient.sync(phase)
        if (phase == Phase.FOCUS) {
            AmbientArt.loadAmbientSelection(this) { updateNotification() }
        }
        val notification = buildNotification()
        syncMediaSession()
        postSessionNotification(notification)
        SessionNotificationBuilder.logPromotionState(this, notification)
        beginSessionScheduling()
        IslandOverlay.ensureVisible(this)
    }

    private fun holdPause() {
        if (TimerStore.isSessionPaused(this)) {
            resumeSession()
            return
        }
        SessionAlarms.cancel(this)
        TimerStore.setSessionPaused(
            this,
            paused = true,
            phase = TimerStore.phase(this),
            remainingSec = TimerStore.remainingSeconds(this)
        )
        ambient.pauseForSessionHold()
        syncMediaSession()
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
        ambient.resumeFromSessionHold(phase)
        beginSessionScheduling()
        syncMediaSession()
        updateNotification()
        IslandOverlay.refresh(this)
        PomodoroWidget.updateAll(this)
    }

    private fun stopAll(@Suppress("UNUSED_PARAMETER") immediateAudio: Boolean = false) {
        SessionAlarms.cancel(this)
        TimerStore.clearSessionPaused(this)
        TimerStore.setPhase(this, Phase.IDLE)
        TimerStore.setEndAtMillis(this, 0L)
        TimerStore.clearRunningPhaseTotalSeconds(this)
        ambient.stopImmediate()
        IslandOverlay.hide()
        mediaSession?.isActive = false
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        PomodoroWidget.updateAll(this)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopAll(immediateAudio = true)
        super.onTaskRemoved(rootIntent)
    }

    private fun pause() = stopAll()

    private fun beginSessionScheduling() {
        ProgressRefresh.reset()
        ProgressRefresh.planRefresh(this)
        refreshSessionUi()
        SessionAlarms.scheduleNext(this)
    }

    private fun onSessionAlarm() {
        if (TimerStore.isSessionPaused(this)) {
            SessionAlarms.cancel(this)
            return
        }
        if (TimerStore.remainingSeconds(this) <= 0) {
            SessionAlarms.cancel(this)
            if (TimerStore.phase(this) != Phase.IDLE) {
                onPhaseFinished()
            }
            return
        }
        if (!TimerStore.isSessionActive(this)) {
            SessionAlarms.cancel(this)
            return
        }
        val plan = ProgressRefresh.planRefresh(this)
        if (plan.notification) {
            updateNotification()
        }
        if (plan.islandOrWidget) {
            IslandOverlay.ensureVisible(this)
            IslandOverlay.refresh(this)
            PomodoroWidget.updateAll(this)
        }
        SessionAlarms.scheduleNext(this)
    }

    private fun refreshSessionUi() {
        updateNotification()
        IslandOverlay.ensureVisible(this)
        IslandOverlay.refresh(this)
        PomodoroWidget.updateAll(this)
    }

    private fun onPhaseFinished() {
        when (TimerStore.phase(this)) {
            Phase.FOCUS -> startPhase(Phase.BREAK, TimerStore.breakMinutes(this) * 60)
            Phase.BREAK, Phase.IDLE -> pause()
        }
    }

    private fun buildNotification(): Notification {
        val open = pendingActivity()
        val pause = pendingService(ACTION_HOLD_PAUSE, 1)
        val stop = pendingService(ACTION_STOP, 2)
        return SessionNotificationBuilder.build(
            this, CHANNEL_ID, open, pause, stop, mediaSession?.sessionToken,
        )
    }

    private fun pendingActivity(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun pendingService(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PomodoroService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun updateNotification() {
        if (!TimerStore.isSessionActive(this)) return
        postSessionNotification(buildNotification())
    }

    /** First post must use startForeground; later updates use notify (lighter on battery). */
    private fun postSessionNotification(notification: Notification) {
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun syncMediaSession() {
        val session = mediaSession ?: return
        val paused = TimerStore.isSessionPaused(this)
        val actions = PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_STOP
        val state = if (paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
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
        SessionAlarms.cancel(this)
        ambient.stopImmediate()
        IslandOverlay.hide()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_FOCUS = "start_focus"
        const val ACTION_START_BREAK = "start_break"
        const val ACTION_HOLD_PAUSE = "hold_pause"
        const val ACTION_REFRESH_NOTIFICATION = "refresh_notification"
        const val ACTION_SESSION_ALARM = "session_alarm"
        const val ACTION_STOP = "stop"
        private const val CHANNEL_ID = "pomodoro_session_v2"
        private const val NOTIFICATION_ID = 1

        fun startFocus(context: Context) = context.startForegroundService(
            Intent(context, PomodoroService::class.java).setAction(ACTION_START_FOCUS)
        )

        fun startBreak(context: Context) = context.startForegroundService(
            Intent(context, PomodoroService::class.java).setAction(ACTION_START_BREAK)
        )

        fun holdPause(context: Context) = context.startForegroundService(
            Intent(context, PomodoroService::class.java).setAction(ACTION_HOLD_PAUSE)
        )

        fun stop(context: Context) = context.startForegroundService(
            Intent(context, PomodoroService::class.java).setAction(ACTION_STOP)
        )

        fun refreshNotification(context: Context) {
            if (!TimerStore.isSessionActive(context)) return
            context.startForegroundService(
                Intent(context, PomodoroService::class.java).setAction(ACTION_REFRESH_NOTIFICATION)
            )
        }

        /** SessionAlarmReceiver */
        fun expireIfDue(context: Context) {
            val app = context.applicationContext
            if (TimerStore.phase(app) == Phase.IDLE) return
            if (TimerStore.isSessionPaused(app)) return
            if (TimerStore.remainingSeconds(app) > 0) return
            SessionAlarms.deliverPhaseEnd(app)
        }

        /** Ends the session completely. */
        fun pause(context: Context) = stop(context)
    }
}
