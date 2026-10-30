package com.pomodoro.timer

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Boost the completed built-in mix, rather than altering recordings or system volume. */
internal class BuiltInAudioOutput(
    context: Context,
    private val onStateChanged: () -> Unit = {},
) {
    val sessionId = context.getSystemService(AudioManager::class.java).generateAudioSessionId()
    private var enhancer: LoudnessEnhancer? = null
    private var active = false
    private var retriedWhenReady = false
    var boostEnabled = false
        private set

    // Boat hum already has +10 dB from v0.16; offset its existing boost while keeping the additional +3 dB.
    val boatCompensation: Float get() = if (boostEnabled) 0.31622776f else 1f

    fun activate() {
        if (active) return
        active = true
        retriedWhenReady = false
        enableBoost()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun configure(player: ExoPlayer) {
        if (sessionId <= 0) return // Session zero would affect the global output mix.
        player.setAudioSessionId(sessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                onAudioSessionReady(audioSessionId)
            }
        })
    }

    fun onAudioSessionReady(id: Int) {
        if (!active || id != sessionId || boostEnabled || retriedWhenReady) return
        retriedWhenReady = true
        // Some devices require the AudioTrack to exist before an effect can be attached.
        enableBoost()
    }

    private fun enableBoost() {
        if (sessionId <= 0 || boostEnabled) return
        var created: LoudnessEnhancer? = null
        try {
            created = LoudnessEnhancer(sessionId)
            created.setTargetGain(1_300) // 100 millibels per dB: +13 dB, with platform peak compression.
            check(created.setEnabled(true) == AudioEffect.SUCCESS)
            enhancer = created
            boostEnabled = true
        } catch (error: RuntimeException) {
            created?.release()
            Log.w("BuiltInAudioOutput", "Loudness boost unavailable; retaining original playback levels", error)
            return
        }
        onStateChanged()
    }

    fun release() {
        active = false
        enhancer?.release()
        enhancer = null
        val changed = boostEnabled
        boostEnabled = false
        retriedWhenReady = false
        if (changed) onStateChanged()
    }
}
