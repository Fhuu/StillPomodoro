package com.pomodoro.timer

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAudioEffect

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BuiltInAudioOutputTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun boostsOnlyItsDedicatedSessionWithoutChangingSystemVolume() {
        val manager = context.getSystemService(AudioManager::class.java)
        val originalVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val output = BuiltInAudioOutput(context)
        try {
            output.activate()
            assertTrue(output.boostEnabled)
            assertTrue(output.sessionId > 0)
            val effect = ShadowAudioEffect.getAudioEffects().filterIsInstance<LoudnessEnhancer>().single()
            assertEquals(output.sessionId, shadowOf(effect).audioSession)
            assertEquals(1_300f, effect.targetGain, 0.001f)
            assertTrue(effect.enabled)
            assertEquals(originalVolume, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertEquals(0.31622776f, output.boatCompensation, 0.000001f)
        } finally {
            output.release()
        }
    }

    @Test
    fun configuresBuiltInPlayersToShareOneNonzeroSession() {
        val output = BuiltInAudioOutput(context)
        val first = ExoPlayer.Builder(context).build()
        val second = ExoPlayer.Builder(context).build()
        try {
            output.configure(first)
            output.configure(second)
            assertTrue(output.sessionId > 0)
            assertEquals(output.sessionId, first.audioSessionId)
            assertEquals(output.sessionId, second.audioSessionId)
        } finally {
            first.release()
            second.release()
            output.release()
        }
    }

    @Test
    fun lateSessionCallbackCannotRecreateReleasedEffect() {
        val output = BuiltInAudioOutput(context)
        output.activate()
        assertTrue(output.boostEnabled)
        output.release()
        output.onAudioSessionReady(output.sessionId)
        assertFalse(output.boostEnabled)
        assertEquals(1f, output.boatCompensation)
        assertTrue(ShadowAudioEffect.getAudioEffects().isEmpty())
    }
}
