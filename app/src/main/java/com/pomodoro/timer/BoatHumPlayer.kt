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

/** One current boat recording, plus a silent preload that overlaps only during a crossfade. */
internal class BoatHumPlayer(
    private val context: Context,
    private val output: BuiltInAudioOutput? = null,
    private val onStopped: () -> Unit = {},
) {
    private class Deck(val resource: Int, val player: ExoPlayer) {
        var prepared = false
        var fadeOutVolume = 0f
    }

    private val handler = Handler(Looper.getMainLooper())
    private var current: Deck? = null
    private var incoming: Deck? = null
    private var sequence = BoatHumSequence(0)
    private var startedAt = 0L
    private var stoppingAt: Long? = null
    private var mixGain = 1f
    private var targetMixGain = 1f

    val isActive: Boolean get() = current != null

    private val tick = object : Runnable {
        override fun run() {
            val active = current ?: return
            val next = incoming ?: return
            val now = SystemClock.elapsedRealtime()
            val stopping = stoppingAt
            if (stopping != null) {
                val t = ((now - stopping).toDouble() / FADE_OUT_MS).coerceIn(0.0, 1.0)
                if (t >= 1.0) {
                    releasePlayers()
                    return
                }
                val fade = (1.0 - FractalNoise.smoothstep(t)).toFloat()
                active.player.volume = active.fadeOutVolume * fade
                next.player.volume = next.fadeOutVolume * fade
            } else if (active.prepared) {
                mixGain += (targetMixGain - mixGain).coerceIn(-0.02f, 0.02f)
                val frame = sequence.update(
                    (now - startedAt) / 1000.0,
                    active.player.currentPosition,
                    active.player.duration,
                    next.player.currentPosition,
                    next.player.duration,
                    next.prepared,
                )
                active.player.volume = frame.gains.first * mixGain
                next.player.volume = frame.gains.second * mixGain
                if (frame.startNext) next.player.playWhenReady = true
                if (frame.promoteNext) {
                    current = next
                    incoming = null
                    active.player.release()
                    sequence.nextSample()
                    incoming = prepareDeck(nextResource(next.resource))
                }
            }
            handler.postDelayed(this, 100L)
        }
    }

    fun start() {
        if (isActive && stoppingAt == null) return
        releasePlayers(notifyStopped = false)
        sequence = BoatHumSequence(Random.nextInt())
        mixGain = targetMixGain
        startedAt = SystemClock.elapsedRealtime()
        val first = RESOURCES.random()
        current = prepareDeck(first)
        incoming = prepareDeck(nextResource(first))
        current?.player?.playWhenReady = true
        handler.post(tick)
    }

    fun setMixGain(gain: Float, immediate: Boolean = false) {
        targetMixGain = gain.coerceIn(0f, 1f)
        if (immediate) {
            val ratio = if (mixGain > 0f) targetMixGain / mixGain else 0f
            mixGain = targetMixGain
            listOfNotNull(current, incoming).forEach {
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
            current?.let { it.fadeOutVolume = it.player.volume }
            incoming?.let { it.fadeOutVolume = it.player.volume }
        }
    }

    private fun prepareDeck(resource: Int): Deck {
        val exo = ExoPlayer.Builder(context).build()
        output?.configure(exo)
        val deck = Deck(resource, exo)
        exo.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
        exo.setWakeMode(C.WAKE_MODE_LOCAL)
        // Prepared loop boundaries keep the current sound continuous if the next deck is late.
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.volume = 0f
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (deck !== current && deck !== incoming) return
                if (playbackState == Player.STATE_READY) deck.prepared = true
            }

            override fun onPlayerError(error: PlaybackException) {
                if (deck !== current && deck !== incoming) return
                Log.e("BoatHum", "Boat recording failed", error)
                stop(immediate = true)
            }
        })
        exo.setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/$resource"))
        exo.prepare()
        return deck
    }

    private fun nextResource(previous: Int): Int = RESOURCES.filter { it != previous }.random()

    private fun releasePlayers(notifyStopped: Boolean = true) {
        handler.removeCallbacksAndMessages(null)
        val outgoing = listOfNotNull(current, incoming)
        current = null
        incoming = null
        stoppingAt = null
        outgoing.forEach { it.player.release() }
        if (notifyStopped && outgoing.isNotEmpty()) onStopped()
    }

    private companion object {
        const val FADE_OUT_MS = 2_500L
        val RESOURCES = listOf(R.raw.boat_hum_diesel, R.raw.boat_hum_ship, R.raw.boat_hum_cabin)
    }
}
