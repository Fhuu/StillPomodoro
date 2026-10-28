package com.pomodoro.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlin.random.Random

class AmbientPlayer(private val context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var crossfadeOut: MediaPlayer? = null
    private var folderFiles: List<Uri>? = null
    private var wasPrivateOutput = false
    private var monitoring = false
    private var ignoreRouteChangesUntil = 0L
    private var focusRequest: AudioFocusRequest? = null

    private val privateOutputTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
    )

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) = onRouteMaybeChanged()
        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) = onRouteMaybeChanged()
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                silenceForPublicOutput()
            }
        }
    }

    fun sync(phase: Phase) {
        when (phase) {
            Phase.IDLE -> fadeOutStop()
            Phase.BREAK -> crossfadeTo(breakOceanUri(), loop = true, loadArt = false)
            Phase.FOCUS -> syncFocusAmbient()
        }
    }

    fun release() = fadeOutStop()

    /** Keeps playback position when the user pauses the pomodoro session. */
    fun pauseForSessionHold() {
        cancelCrossfade()
        player?.pause()
    }

    fun resumeFromSessionHold(phase: Phase) {
        val p = player
        if (p != null) {
            runCatching {
                if (!p.isPlaying) p.start()
            }.onFailure {
                sync(phase)
            }
            return
        }
        sync(phase)
    }

    private fun syncFocusAmbient() {
        when (TimerStore.ambientMode(context)) {
            AmbientMode.OFF -> fadeOutStop()
            AmbientMode.FILE -> crossfadeTo(Uri.parse(TimerStore.ambientUri(context)), loop = true, loadArt = true)
            AmbientMode.FOLDER -> {
                val tree = Uri.parse(TimerStore.ambientUri(context))
                val files = audioFilesIn(tree)
                if (files.isEmpty()) fadeOutStop()
                else crossfadeTo(files[Random.nextInt(files.size)], loop = false, loadArt = true, folderPlaylist = files)
            }
        }
    }

    private fun breakOceanUri(): Uri =
        Uri.parse("android.resource://${appContext.packageName}/${R.raw.break_ocean_waves}")

    private fun crossfadeTo(
        uri: Uri?,
        loop: Boolean,
        loadArt: Boolean,
        folderPlaylist: List<Uri>? = null,
    ) {
        if (uri == null) {
            fadeOutStop()
            return
        }
        cancelCrossfade()
        requestFocus()
        val incoming = try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(appContext, uri)
                isLooping = loop
                if (folderPlaylist != null) {
                    setOnCompletionListener { playNextFromFolder(folderPlaylist) }
                }
                prepare()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ambient prepare failed", e)
            fadeOutStop()
            return
        }

        folderFiles = folderPlaylist
        val outgoing = player
        if (outgoing == null || !outgoing.isPlaying) {
            outgoing?.release()
            incoming.setVolume(1f, 1f)
            incoming.start()
            player = incoming
            onPlaybackStarted(uri, loadArt)
            return
        }

        incoming.setVolume(0f, 0f)
        incoming.start()
        player = incoming
        crossfadeOut = outgoing
        var step = 0
        val stepMs = CROSSFADE_MS / FADE_STEPS
        val tick = object : Runnable {
            override fun run() {
                step++
                val t = (step.toFloat() / FADE_STEPS).coerceIn(0f, 1f)
                outgoing.setVolume(1f - t, 1f - t)
                incoming.setVolume(t, t)
                if (step >= FADE_STEPS) {
                    outgoing.release()
                    crossfadeOut = null
                    onPlaybackStarted(uri, loadArt)
                } else {
                    mainHandler.postDelayed(this, stepMs)
                }
            }
        }
        mainHandler.post(tick)
    }

    private fun fadeOutStop() {
        cancelCrossfade()
        val outgoing = player ?: return stopSilent()
        player = null
        var step = 0
        val stepMs = CROSSFADE_MS / FADE_STEPS
        val tick = object : Runnable {
            override fun run() {
                step++
                val t = 1f - (step.toFloat() / FADE_STEPS).coerceIn(0f, 1f)
                if (outgoing.isPlaying) outgoing.setVolume(t, t)
                if (step >= FADE_STEPS) {
                    outgoing.release()
                    stopSilent()
                } else {
                    mainHandler.postDelayed(this, stepMs)
                }
            }
        }
        mainHandler.post(tick)
    }

    private fun stopSilent() {
        stopMonitoring()
        abandonFocus()
        crossfadeOut?.release()
        crossfadeOut = null
        player?.release()
        player = null
        folderFiles = null
        wasPrivateOutput = false
        TimerStore.setNowPlayingUri(appContext, null)
        AmbientArt.clear()
    }

    private fun cancelCrossfade() {
        mainHandler.removeCallbacksAndMessages(null)
        crossfadeOut?.release()
        crossfadeOut = null
    }

    private fun playNextFromFolder(files: List<Uri>) {
        crossfadeTo(files[Random.nextInt(files.size)], loop = false, loadArt = true, folderPlaylist = files)
    }

    private fun onPlaybackStarted(uri: Uri, loadArt: Boolean) {
        if (loadArt) {
            TimerStore.setNowPlayingUri(appContext, uri.toString())
            AmbientArt.load(appContext, uri) {
                IslandOverlay.reloadArt(appContext)
                PomodoroService.refreshNotification(appContext)
            }
        } else {
            TimerStore.setNowPlayingUri(appContext, null)
            AmbientArt.clear()
        }
        ignoreRouteChangesUntil = System.currentTimeMillis() + 1200L
        wasPrivateOutput = hasPrivateOutput()
        startMonitoring()
    }

    private fun onRouteMaybeChanged() {
        mainHandler.post {
            if (System.currentTimeMillis() < ignoreRouteChangesUntil) return@post
            if (player?.isPlaying != true) return@post
            val privateOut = hasPrivateOutput()
            if (wasPrivateOutput && !privateOut) {
                silenceForPublicOutput()
                return@post
            }
            wasPrivateOutput = privateOut
        }
    }

    private fun silenceForPublicOutput() {
        if (player?.isPlaying != true) return
        fadeOutStop()
    }

    private fun hasPrivateOutput(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.isSink && it.type in privateOutputTypes }
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        audioManager.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun startMonitoring() {
        if (monitoring) return
        monitoring = true
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun stopMonitoring() {
        if (!monitoring) return
        monitoring = false
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        try {
            appContext.unregisterReceiver(noisyReceiver)
        } catch (_: IllegalArgumentException) { }
    }

    private fun audioFilesIn(treeUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return emptyList()
        return root.listFiles()
            .filter { it.isFile && (it.type?.startsWith("audio/") == true || looksLikeAudio(it.name)) }
            .map { it.uri }
    }

    private fun looksLikeAudio(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".wav")
                || lower.endsWith(".flac") || lower.endsWith(".m4a")
    }

    companion object {
        private const val TAG = "AmbientPlayer"
        private const val CROSSFADE_MS = 2_500L
        private const val FADE_STEPS = 25
    }
}
