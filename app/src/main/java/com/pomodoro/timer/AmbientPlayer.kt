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
        if (phase != Phase.FOCUS) {
            stop()
            return
        }
        when (TimerStore.ambientMode(context)) {
            AmbientMode.OFF -> stop()
            AmbientMode.FILE -> playUri(Uri.parse(TimerStore.ambientUri(context)))
            AmbientMode.FOLDER -> playFromFolder(Uri.parse(TimerStore.ambientUri(context)))
        }
    }

    fun release() {
        stop()
    }

    private fun stop() {
        stopMonitoring()
        abandonFocus()
        player?.release()
        player = null
        wasPrivateOutput = false
        TimerStore.setNowPlayingUri(appContext, null)
        AmbientArt.clear()
    }

    private fun playUri(uri: Uri?) {
        if (uri == null) return
        stop()
        try {
            requestFocus()
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(appContext, uri)
                isLooping = true
                setVolume(1f, 1f)
                prepare()
                start()
            }
            onPlaybackStarted(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Ambient play failed", e)
            stop()
        }
    }

    private fun playFromFolder(treeUri: Uri?) {
        if (treeUri == null) return
        val files = audioFilesIn(treeUri)
        if (files.isEmpty()) return
        playNextFromFolder(files)
    }

    private fun playNextFromFolder(files: List<Uri>) {
        stop()
        val uri = files[Random.nextInt(files.size)]
        try {
            requestFocus()
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(appContext, uri)
                isLooping = false
                setVolume(1f, 1f)
                setOnCompletionListener { playNextFromFolder(files) }
                prepare()
                start()
            }
            onPlaybackStarted(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Ambient play failed", e)
            stop()
        }
    }

    private fun onPlaybackStarted(uri: Uri) {
        TimerStore.setNowPlayingUri(appContext, uri.toString())
        AmbientArt.load(appContext, uri) { IslandOverlay.reloadArt(appContext) }
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
        stop()
    }

    private fun hasPrivateOutput(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.isSink && it.type in privateOutputTypes }
    }

    private fun requestFocus() {
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
    }
}
