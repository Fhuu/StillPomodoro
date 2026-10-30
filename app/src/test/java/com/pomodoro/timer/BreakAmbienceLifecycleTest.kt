package com.pomodoro.timer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BreakAmbienceLifecycleTest {
    private lateinit var context: Context
    private lateinit var ambient: AmbientPlayer

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        TimerStore.prefs(context).edit().clear().commit()
        TimerStore.setPhase(context, Phase.BREAK)
        TimerStore.setEndAtMillis(context, System.currentTimeMillis() + 300_000)
        ambient = AmbientPlayer(context)
    }

    @After
    fun tearDown() {
        ambient.stopImmediate()
    }

    @Test
    fun builtInAmbienceLeavesExistingMusicFocusAlone() {
        val manager = context.getSystemService(AudioManager::class.java)
        val musicFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }
            .build()
        manager.requestAudioFocus(musicFocus)
        val originalRequest = shadowOf(manager).lastAudioFocusRequest
        TimerStore.applyBuiltInSource(context)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(breakMix().isActive)
        assertSame(originalRequest, shadowOf(manager).lastAudioFocusRequest)
    }

    @Test
    fun boatOnlyFocusLeavesWavesOffAndSupportsPauseResume() {
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.BOAT_HUM))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(boatMix().isActive)
        assertFalse(breakMix().isActive)
        ambient.pauseForSessionHold()
        assertFalse(boatMix().isActive)
        ambient.resumeFromSessionHold(Phase.FOCUS)
        assertTrue(boatMix().isActive)
        assertFalse(breakMix().isActive)
    }

    @Test
    fun removingWavesKeepsBoatPlaybackAndRoutingUntilDisconnect() {
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.OCEAN_WAVES, BuiltInAmbientSound.BOAT_HUM))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(breakMix().isActive && boatMix().isActive)
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.BOAT_HUM))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertFalse(breakMix().isActive)
        assertTrue(boatMix().isActive)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(boatMix().isActive)
    }

    @Test
    fun combinedBuiltInsKeepPeakHeadroomAndRestoreSoloGainAfterDeselection() {
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.OCEAN_WAVES, BuiltInAmbientSound.BOAT_HUM))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        val waveGain = targetGain(breakMix())
        val boatGain = targetGain(boatMix())
        assertTrue(0.82f * waveGain + 0.92f * boatGain < 0.95f)
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.BOAT_HUM))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        val outputField = AmbientPlayer::class.java.getDeclaredField("builtInOutput").apply { isAccessible = true }
        val output = outputField.get(ambient) as BuiltInAudioOutput
        assertTrue(output.boostEnabled)
        assertEquals(output.boatCompensation, targetGain(boatMix()))
    }

    @Test
    fun removingBoatKeepsWavesAndBreaksStillUseOnlyWaves() {
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.OCEAN_WAVES, BuiltInAmbientSound.BOAT_HUM))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(breakMix().isActive && boatMix().isActive)
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.OCEAN_WAVES))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(breakMix().isActive)
        assertFalse(boatMix().isActive)
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.BOAT_HUM))
        ambient.sync(Phase.FOCUS)
        assertTrue(boatMix().isActive)
        TimerStore.setPhase(context, Phase.BREAK)
        ambient.sync(Phase.BREAK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(breakMix().isActive)
        assertFalse(boatMix().isActive)
    }

    @Test
    fun builtInFocusPlaysOceanWavesAndSupportsPauseResume() {
        TimerStore.applyBuiltInSource(context)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(breakMix().isActive)
        ambient.pauseForSessionHold()
        assertFalse(breakMix().isActive)
        ambient.resumeFromSessionHold(Phase.FOCUS)
        assertTrue(breakMix().isActive)
    }

    @Test
    fun builtInFocusStopsWhenDeselectedOrEarphonesDisconnect() {
        TimerStore.applyBuiltInSource(context)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        TimerStore.setBuiltInAmbientSounds(context, emptySet())
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertFalse(breakMix().isActive)
        TimerStore.applyBuiltInSource(context)
        ambient.sync(Phase.FOCUS)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(breakMix().isActive)
    }

    @Test
    fun breakToBuiltInFocusKeepsTheSameOceanPlayers() {
        ambient.sync(Phase.BREAK)
        val field = BreakAmbiencePlayer::class.java.getDeclaredField("layers")
        field.isAccessible = true
        val original = (field.get(breakMix()) as List<*>).toList()
        TimerStore.applyBuiltInSource(context)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertEquals(original, field.get(breakMix()))
    }

    @Test
    fun preparesAllFiveDistinctOfflineRecordings() {
        val mix = BreakAmbiencePlayer(context)
        try {
            mix.start()
            val field = BreakAmbiencePlayer::class.java.getDeclaredField("layers")
            field.isAccessible = true
            val layers = field.get(mix) as List<*>
            val actualUris = layers.map { layer ->
                val playerField = layer!!.javaClass.getDeclaredField("player")
                playerField.isAccessible = true
                val player = playerField.get(layer) as ExoPlayer
                player.currentMediaItem!!.localConfiguration!!.uri.toString()
            }
            val expectedUris = listOf(
                R.raw.break_ocean_calm,
                R.raw.break_ocean_gentle,
                R.raw.break_ocean_lapping,
                R.raw.break_ocean_shingle,
                R.raw.break_ocean_distant,
            ).map { "android.resource://${context.packageName}/$it" }.toSet()
            assertEquals(5, actualUris.size)
            assertEquals(expectedUris, actualUris.toSet())
        } finally {
            mix.stop(immediate = true)
        }
    }

    @Test
    fun stoppingBeforeTheSecondLayerStartsCancelsFuturePlayback() {
        val mix = BreakAmbiencePlayer(context)
        mix.start()
        assertTrue(mix.isActive)
        mix.stop(immediate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertFalse(mix.isActive)
    }

    @Test
    fun restartDuringFadeIsNotReleasedByTheOldFade() {
        val mix = BreakAmbiencePlayer(context)
        try {
            mix.start()
            mix.stop()
            mix.start()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertTrue(mix.isActive)
        } finally {
            mix.stop(immediate = true)
        }
    }

    @Test
    fun pausingFocusAlsoStopsTheOutgoingBreakMix() {
        ambient.sync(Phase.BREAK)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        ambient.pauseForSessionHold()
        assertFalse(breakMix().isActive)
    }

    @Test
    fun disconnectDuringBreakFadeStillStopsTheMixImmediately() {
        ambient.sync(Phase.BREAK)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(breakMix().isActive)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(breakMix().isActive)
    }

    @Test
    fun completedBreakFadePreservesAudioFocusForYoutube() {
        ambient.sync(Phase.BREAK)
        TimerStore.setPhase(context, Phase.FOCUS)
        TimerStore.prefs(context).edit().putString("ambient_mode", "YOUTUBE").commit()
        // Exercise completion of the outgoing mix without resolving a network video in this test.
        breakMix().stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        val audioManager = context.getSystemService(AudioManager::class.java)
        assertNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }

    private fun targetGain(mix: Any): Float {
        val field = mix.javaClass.getDeclaredField("targetMixGain")
        field.isAccessible = true
        return field.getFloat(mix)
    }

    private fun boatMix(): BoatHumPlayer {
        val field = AmbientPlayer::class.java.getDeclaredField("boatHum")
        field.isAccessible = true
        return field.get(ambient) as BoatHumPlayer
    }

    private fun breakMix(): BreakAmbiencePlayer {
        // Inspect the real mixer owned by AmbientPlayer without exposing a test-only production API.
        val field = AmbientPlayer::class.java.getDeclaredField("breakAmbience")
        field.isAccessible = true
        return field.get(ambient) as BreakAmbiencePlayer
    }
}
