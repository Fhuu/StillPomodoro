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
    private var refreshFocusOnResume = false
    private val builtInOutput = BuiltInAudioOutput(appContext) { updateBuiltInMixGains(immediate = true) }
    private val boatHum = BoatHumPlayer(appContext, output = builtInOutput) { onBuiltInStopped() }
    private val breakAmbience = BreakAmbiencePlayer(appContext, output = builtInOutput) {
        onBuiltInStopped()
    }

    private fun onBuiltInStopped() {
        if (!breakAmbience.isActive && !boatHum.isActive) builtInOutput.release()
        updateBuiltInMixGains()
        val youtubeFocus = TimerStore.phase(appContext) == Phase.FOCUS &&
            TimerStore.ambientMode(appContext) == AmbientMode.YOUTUBE &&
            TimerStore.isSessionActive(appContext)
        if (player == null && crossfadeOut == null && !breakAmbience.isActive && !boatHum.isActive && !youtubeFocus) stopSilent()
    }

    private fun updateBuiltInMixGains(immediate: Boolean = false) {
        val gain = if (breakAmbience.isActive && boatHum.isActive) 0.50f else 1f
        breakAmbience.setMixGain(gain, immediate)
        boatHum.setMixGain(gain * builtInOutput.boatCompensation, immediate)
    }

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
        refreshFocusOnResume = false
        val builtInFocus = phase == Phase.FOCUS && TimerStore.ambientMode(context) == AmbientMode.BUILT_IN
        if (phase != Phase.BREAK && !builtInFocus) {
            breakAmbience.stop()
            boatHum.stop()
        }
        when (phase) {
            Phase.IDLE -> {
                YoutubeAudioPlayer.stop()
                fadeOutStop()
            }
            Phase.BREAK -> {
                startBuiltInAmbience(setOf(BuiltInAmbientSound.OCEAN_WAVES))
            }
            Phase.FOCUS -> syncFocusAmbient()
        }
    }

    private fun startBuiltInAmbience(sounds: Set<BuiltInAmbientSound>) {
        YoutubeAudioPlayer.stop()
        fadeOutStop(stopBuiltIn = false)
        if (!TimerStore.isSessionActive(appContext)) return
        // Built-in ambience is background audio and must coexist with the user's music app.
        abandonFocus()
        builtInOutput.activate()
        TimerStore.setNowPlayingUri(appContext, null)
        AmbientArt.clear()
        wasPrivateOutput = hasPrivateOutput()
        ignoreRouteChangesUntil = System.currentTimeMillis() + 1200L
        startMonitoring()
        if (BuiltInAmbientSound.OCEAN_WAVES in sounds) breakAmbience.start() else breakAmbience.stop()
        if (BuiltInAmbientSound.BOAT_HUM in sounds) boatHum.start() else boatHum.stop()
        updateBuiltInMixGains()
    }

    fun release() {
        boatHum.stop(immediate = true)
        breakAmbience.stop(immediate = true)
        builtInOutput.release()
        fadeOutStop()
        YoutubeAudioPlayer.release()
    }

    /** Fade out ambient (session stop, phase change). */
    fun fadeOutAndStop() = fadeOutStop()

    /** Hard stop — task removed / must not keep playing after stop. */
    fun stopImmediate() {
        refreshFocusOnResume = false
        boatHum.stop(immediate = true)
        breakAmbience.stop(immediate = true)
        builtInOutput.release()
        cancelCrossfade()
        YoutubeAudioPlayer.stop()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { crossfadeOut?.release() }
        crossfadeOut = null
        stopSilent()
    }

    /** Stops ambient while the timer is on hold (break waves fade out; focus track keeps position). */
    fun pauseForSessionHold() {
        boatHum.stop(immediate = true)
        breakAmbience.stop(immediate = true)
        cancelCrossfade()
        runCatching { crossfadeOut?.stop() }
        runCatching { crossfadeOut?.release() }
        crossfadeOut = null
        if (TimerStore.phase(appContext) == Phase.BREAK) {
            stopImmediate()
            return
        }
        player?.pause()
        YoutubeAudioPlayer.pause()
    }

    fun refreshFocusAmbient() {
        if (TimerStore.isSessionPaused(appContext)) {
            refreshFocusOnResume = true
        } else {
            sync(Phase.FOCUS)
        }
    }

    fun resumeFromSessionHold(phase: Phase) {
        if (phase == Phase.BREAK) {
            sync(Phase.BREAK)
            return
        }
        if (refreshFocusOnResume) {
            sync(phase)
            return
        }
        if (TimerStore.ambientMode(context) == AmbientMode.YOUTUBE) {
            YoutubeAudioPlayer.resume()
            return
        }
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

    /** Skip to another random track from the music folder (focus + library only). */
    fun skipLibraryTrack() {
        if (TimerStore.phase(appContext) != Phase.FOCUS) return
        if (TimerStore.ambientMode(context) != AmbientMode.LIBRARY) return
        val files = folderFiles ?: libraryTrackUris()?.also { folderFiles = it } ?: return
        if (files.isEmpty()) return
        val current = TimerStore.nowPlayingUri(appContext)?.let { Uri.parse(it) }
        playNextFromFolder(files, avoid = current)
    }

    fun skipYoutubeTrack() {
        if (TimerStore.phase(appContext) != Phase.FOCUS) return
        if (TimerStore.ambientMode(context) != AmbientMode.YOUTUBE) return
        val current = TimerStore.youtubeVideoId(context) ?: return
        val next = YoutubePlaylist.nextEntry(context, current) ?: return
        TimerStore.setYoutubeSource(context, next.videoId, next.title)
        AmbientArt.notifyMetadataChanged()
        requestFocus()
        startMonitoring()
        YoutubeAudioPlayer.play(context, next.videoId)
        AmbientArt.loadYoutubeThumbnail(context, next.videoId) {
            IslandOverlay.reloadArt(appContext)
            PomodoroService.refreshNotification(appContext)
        }
    }

    private fun syncFocusAmbient() {
        when (TimerStore.ambientMode(context)) {
            AmbientMode.BUILT_IN -> {
                val sounds = TimerStore.builtInAmbientSounds(context)
                if (sounds.isNotEmpty()) {
                    startBuiltInAmbience(sounds)
                } else {
                    YoutubeAudioPlayer.stop()
                    fadeOutStop()
                }
            }
            AmbientMode.OFF -> {
                YoutubeAudioPlayer.stop()
                fadeOutStop()
            }
            AmbientMode.LIBRARY -> {
                YoutubeAudioPlayer.stop()
                syncLibraryAmbient()
            }
            AmbientMode.YOUTUBE -> {
                stopLocalPlayerOnly()
                val id = YoutubePlaylist.ensureFocusSelection(context)
                if (id == null) {
                    TimerStore.setYoutubePlaybackError(
                        context,
                        "Add a video on the YouTube tab and tap a row to select it",
                    )
                    return
                }
                requestFocus()
                startMonitoring()
                wasPrivateOutput = hasPrivateOutput()
                ignoreRouteChangesUntil = System.currentTimeMillis() + 1200L
                YoutubeAudioPlayer.play(context, id)
            }
        }
    }

    private fun syncLibraryAmbient() {
        val tracks = libraryTrackUris() ?: return fadeOutStop()
        folderFiles = tracks
        playNextFromFolder(tracks, avoid = null)
    }

    private fun libraryTrackUris(): List<Uri>? {
        TimerStore.singleTrackUri(context)?.let { return listOf(Uri.parse(it)) }
        val tree = TimerStore.libraryUri(context)?.let { Uri.parse(it) } ?: return null
        return AmbientLibrary.listTracks(context, tree).map { it.uri }.takeIf { it.isNotEmpty() }
    }

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
        if (loadArt) publishTrackUi(uri)
        val incoming = try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                bindDataSource(uri)
                isLooping = loop
                if (folderPlaylist != null) {
                    setOnCompletionListener { playNextFromFolder(folderPlaylist, avoid = uri) }
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
        if (outgoing == null || (!outgoing.isPlaying && crossfadeOut == null)) {
            outgoing?.release()
            if (!TimerStore.isSessionActive(appContext)) {
                incoming.release()
                return
            }
            incoming.setVolume(1f, 1f)
            incoming.start()
            player = incoming
            onPlaybackStarted(uri, loadArt)
            return
        }

        incoming.setVolume(0f, 0f)
        if (!TimerStore.isSessionActive(appContext)) {
            incoming.release()
            return
        }
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

    private fun fadeOutStop(stopBuiltIn: Boolean = true) {
        if (stopBuiltIn) {
            breakAmbience.stop()
            boatHum.stop()
        }
        cancelCrossfade()
        val outgoing = player ?: return stopSilent()
        player = null
        crossfadeOut = outgoing
        var step = 0
        val stepMs = CROSSFADE_MS / FADE_STEPS
        val tick = object : Runnable {
            override fun run() {
                step++
                val t = 1f - (step.toFloat() / FADE_STEPS).coerceIn(0f, 1f)
                if (outgoing.isPlaying) outgoing.setVolume(t, t)
                if (step >= FADE_STEPS) {
                    outgoing.release()
                    crossfadeOut = null
                    stopSilent()
                } else {
                    mainHandler.postDelayed(this, stepMs)
                }
            }
        }
        mainHandler.post(tick)
    }

    private fun stopSilent() {
        if (!breakAmbience.isActive && !boatHum.isActive) {
            stopMonitoring()
            abandonFocus()
            wasPrivateOutput = false
        }
        crossfadeOut?.release()
        crossfadeOut = null
        player?.release()
        player = null
        folderFiles = null
        TimerStore.setNowPlayingUri(appContext, null)
        AmbientArt.clear()
    }

    private fun cancelCrossfade() {
        mainHandler.removeCallbacksAndMessages(null)
        crossfadeOut?.release()
        crossfadeOut = null
    }

    private fun stopLocalPlayerOnly() {
        cancelCrossfade()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { crossfadeOut?.release() }
        crossfadeOut = null
    }

    private fun playNextFromFolder(files: List<Uri>, avoid: Uri?) {
        val pool = if (avoid != null && files.size > 1) {
            files.filter { it.toString() != avoid.toString() }
        } else {
            files
        }
        if (pool.isEmpty()) return
        val next = pool[Random.nextInt(pool.size)]
        val playlist = if (files.size > 1) files else null
        crossfadeTo(next, loop = files.size <= 1, loadArt = true, folderPlaylist = playlist)
    }

    private fun MediaPlayer.bindDataSource(uri: Uri) {
        if (uri.scheme == "content") {
            val fd = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open $uri")
            setDataSource(fd.fileDescriptor)
            fd.close()
        } else {
            setDataSource(appContext, uri)
        }
    }

    private fun publishTrackUi(uri: Uri) {
        val title = uri.lastPathSegment?.substringAfterLast('/') ?: "Track"
        TimerStore.setSelectedTrack(appContext, uri.toString(), title)
        TimerStore.setNowPlayingUri(appContext, uri.toString())
        AmbientArt.prepareTrackChange(uri)
        AmbientArt.load(appContext, uri) {
            IslandOverlay.reloadArt(appContext)
            PomodoroService.refreshNotification(appContext)
        }
    }

    private fun onPlaybackStarted(uri: Uri, loadArt: Boolean) {
        if (TimerStore.phase(appContext) == Phase.IDLE) {
            stopImmediate()
            return
        }
        if (!loadArt) {
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
            if (player?.isPlaying != true && !breakAmbience.isActive && !boatHum.isActive) return@post
            val privateOut = hasPrivateOutput()
            if (wasPrivateOutput && !privateOut) {
                silenceForPublicOutput()
                return@post
            }
            wasPrivateOutput = privateOut
        }
    }

    private fun silenceForPublicOutput() {
        YoutubeAudioPlayer.stop()
        if (breakAmbience.isActive || boatHum.isActive) {
            breakAmbience.stop(immediate = true)
            boatHum.stop(immediate = true)
            stopSilent()
            return
        }
        if (player?.isPlaying == true) fadeOutStop()
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

    private companion object {
        private const val TAG = "AmbientPlayer"
        private const val CROSSFADE_MS = 2_500L
        private const val FADE_STEPS = 25
    }
}
