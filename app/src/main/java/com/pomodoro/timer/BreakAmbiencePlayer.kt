package com.pomodoro.timer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.random.Random

/** Five offline ocean layers with independent slow volume modulation. Main thread only. */
internal class BreakAmbiencePlayer(
    private val context: Context,
    private val output: BuiltInAudioOutput? = null,
    private val onStopped: () -> Unit = {},
) {
    private class Layer(val player: ExoPlayer) {
        var positioned = false
        var fadeOutVolume = 0f
    }

    private val handler = Handler(Looper.getMainLooper())
    private val layers = mutableListOf<Layer>()
    private var envelope = BreakAmbienceEnvelope(0)
    private var startedAt = 0L
    private var stoppingAt: Long? = null
    private var mixGain = 1f
    private var targetMixGain = 1f

    val isActive: Boolean get() = layers.isNotEmpty()

    private val tick = object : Runnable {
        override fun run() {
            if (!isActive) return
            val now = SystemClock.elapsedRealtime()
            val stopping = stoppingAt
            if (stopping != null && now - stopping >= FADE_OUT_MS) {
                releasePlayers()
                return
            }
            val volumes = envelope.volumes((now - startedAt) / 1000.0)
            if (stopping == null) mixGain += (targetMixGain - mixGain).coerceIn(-0.02f, 0.02f)
            val fadeOut = if (stopping == null) 1f else {
                val t = ((now - stopping).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
                1f - t * t * (3f - 2f * t)
            }
            layers.forEachIndexed { index, layer ->
                val volume = if (stopping == null) volumes[index] * mixGain else layer.fadeOutVolume * fadeOut
                layer.player.volume = volume
                layer.player.playWhenReady = layer.positioned && volume > 0f
            }
            handler.postDelayed(this, 100L)
        }
    }

    fun start() {
        if (isActive && stoppingAt == null) return
        releasePlayers(notifyStopped = false)
        envelope = BreakAmbienceEnvelope(Random.nextInt())
        mixGain = targetMixGain
        startedAt = SystemClock.elapsedRealtime()
        stoppingAt = null
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        listOf(
            R.raw.break_ocean_calm,
            R.raw.break_ocean_gentle,
            R.raw.break_ocean_lapping,
            R.raw.break_ocean_shingle,
            R.raw.break_ocean_distant,
        ).forEach { resource ->
            val exo = ExoPlayer.Builder(context).build()
            output?.configure(exo)
            val layer = Layer(exo)
            layers.add(layer)
            exo.setAudioAttributes(attributes, false)
            exo.setWakeMode(C.WAKE_MODE_LOCAL)
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.volume = 0f
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (layer !in layers || layer.positioned || playbackState != Player.STATE_READY) return
                    val duration = exo.duration
                    if (duration > 0L) {
                        layer.positioned = true
                        exo.seekTo(Random.nextLong(duration))
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("BreakAmbience", "Ocean layer failed", error)
                    stop(immediate = true)
                }
            })
            exo.setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/$resource"))
            exo.prepare()
        }
        handler.post(tick)
    }

    fun setMixGain(gain: Float, immediate: Boolean = false) {
        targetMixGain = gain.coerceIn(0f, 1f)
        if (immediate) {
            val ratio = if (mixGain > 0f) targetMixGain / mixGain else 0f
            mixGain = targetMixGain
            layers.forEach {
                it.player.volume = (it.player.volume * ratio).coerceIn(0f, 1f)
                it.fadeOutVolume *= ratio
            }
        }
    }

    fun stop(immediate: Boolean = false) {
        if (immediate) {
            releasePlayers()
        } else if (isActive && stoppingAt == null) {
            stoppingAt = SystemClock.elapsedRealtime()
            layers.forEach { it.fadeOutVolume = it.player.volume }
        }
    }

    private fun releasePlayers(notifyStopped: Boolean = true) {
        handler.removeCallbacksAndMessages(null)
        val outgoing = layers.toList()
        layers.clear()
        stoppingAt = null
        outgoing.forEach { it.player.release() }
        if (notifyStopped && outgoing.isNotEmpty()) onStopped()
    }

    private companion object {
        const val FADE_OUT_MS = 2_500L
    }
}
