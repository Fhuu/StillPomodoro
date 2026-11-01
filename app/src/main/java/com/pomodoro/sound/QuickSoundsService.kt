package com.pomodoro.sound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pomodoro.timer.R

class QuickSoundsService : Service() {
    private var player: QuickSoundsPlayer? = null
    private var registered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stopPlayback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ambient sounds", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_REFRESH -> if (isPlaying) play() else stopPlayback()
            else -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun play() {
        val sounds = QuickSoundsStore.selection(this)
        if (sounds.isEmpty()) {
            stopPlayback()
            return
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, SoundPanelActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0,
            Intent(this, QuickSoundsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_ambient_tile)
            .setContentTitle("Ambient sounds")
            .setContentText(sounds.joinToString { it.title })
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        startForeground(NOTIFICATION_ID, notification)
        if (!registered) {
            ContextCompat.registerReceiver(this, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        isPlaying = true
        try {
            val mix = player ?: QuickSoundsPlayer(this) { stopPlayback() }.also { player = it }
            mix.play(sounds)
        } catch (error: RuntimeException) {
            android.util.Log.e("QuickSounds", "Could not start ambient sounds", error)
            stopPlayback()
        }
        AmbientTileService.refresh()
    }

    private fun stopPlayback() {
        isPlaying = false
        player?.release()
        player = null
        if (registered) {
            unregisterReceiver(noisyReceiver)
            registered = false
        }
        AmbientTileService.refresh()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PLAY = "quick_sounds_play"
        const val ACTION_STOP = "quick_sounds_stop"
        const val ACTION_REFRESH = "quick_sounds_refresh"
        private const val CHANNEL = "quick_ambient_sounds"
        private const val NOTIFICATION_ID = 42
        var isPlaying by mutableStateOf(false)
            private set

        fun toggle(context: Context) {
            if (isPlaying) stop(context) else context.startForegroundService(
                Intent(context, QuickSoundsService::class.java).setAction(ACTION_PLAY))
        }

        fun selectSounds(context: Context, sounds: Set<BuiltInAmbientSound>) {
            QuickSoundsStore.setSelection(context, sounds)
            if (sounds.isEmpty()) stop(context)
            else context.startForegroundService(
                Intent(context, QuickSoundsService::class.java).setAction(ACTION_PLAY))
            AmbientTileService.refresh()
        }

        fun stop(context: Context) {
            if (isPlaying) context.startService(Intent(context, QuickSoundsService::class.java).setAction(ACTION_STOP))
        }

        fun refresh(context: Context) {
            if (isPlaying) context.startService(Intent(context, QuickSoundsService::class.java).setAction(ACTION_REFRESH))
            AmbientTileService.refresh()
        }
    }
}
