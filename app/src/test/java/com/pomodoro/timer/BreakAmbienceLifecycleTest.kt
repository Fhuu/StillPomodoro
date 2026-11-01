package com.pomodoro.timer

import com.pomodoro.sound.AmbientPlayer
import com.pomodoro.sound.BoatHumPlayer
import com.pomodoro.sound.BreakAmbiencePlayer
import com.pomodoro.sound.BuiltInAmbientSound
import com.pomodoro.sound.BuiltInAudioOutput

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
    fun coastalWindIsIndependentlySelectableAndSupportsPauseResumeAndDisconnect() {
        val house = BuiltInAmbientSound.valueOf("COASTAL_WIND")
        TimerStore.setBuiltInAmbientSounds(context, setOf(house))
        assertEquals("Coastal wind", TimerStore.ambientLabel(context))
        assertEquals(setOf(house), TimerStore.builtInAmbientSounds(context))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(coastalMix().isActive)
        assertFalse(stormMix().isActive || breakMix().isActive || boatMix().isActive)
        val layersField = BreakAmbiencePlayer::class.java.getDeclaredField("layers").apply { isAccessible = true }
        val layers = layersField.get(coastalMix()) as List<*>
        val names = layers.map { layer ->
            val field = layer!!.javaClass.getDeclaredField("player").apply { isAccessible = true }
            val player = field.get(layer) as ExoPlayer
            context.resources.getResourceEntryName(player.currentMediaItem!!.localConfiguration!!.uri.lastPathSegment!!.toInt())
        }.toSet()
        assertEquals(setOf("coastal_wind"), names)
        ambient.pauseForSessionHold()
        assertFalse(coastalMix().isActive)
        ambient.resumeFromSessionHold(Phase.FOCUS)
        assertTrue(coastalMix().isActive)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(coastalMix().isActive)
    }

    @Test
    fun fiveGroupsKeepCoastalWindActiveAndRestoreItsSoloGain() {
        val wind = BuiltInAmbientSound.valueOf("COASTAL_WIND")
        TimerStore.setBuiltInAmbientSounds(context, setOf(wind, BuiltInAmbientSound.OCEAN_WAVES, BuiltInAmbientSound.BOAT_HUM, BuiltInAmbientSound.ROOF_STORM, BuiltInAmbientSound.OLD_HOUSE))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(coastalMix().isActive && houseMix().isActive && stormMix().isActive && breakMix().isActive && boatMix().isActive)
        assertEquals(0.2f, targetGain(coastalMix()), 0.0001f)
        TimerStore.setBuiltInAmbientSounds(context, setOf(wind))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(coastalMix().isActive)
        assertEquals(1f, targetGain(coastalMix()), 0.0001f)
        assertFalse(houseMix().isActive || stormMix().isActive || breakMix().isActive || boatMix().isActive)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(coastalMix().isActive)
    }

    @Test
    fun coastalRestartDuringFadeSurvivesAndBreaksReturnToOceanOnly() {
        val house = BuiltInAmbientSound.valueOf("COASTAL_WIND")
        TimerStore.setBuiltInAmbientSounds(context, setOf(house))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        TimerStore.setBuiltInAmbientSounds(context, emptySet())
        ambient.sync(Phase.FOCUS)
        TimerStore.setBuiltInAmbientSounds(context, setOf(house))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(coastalMix().isActive)
        TimerStore.setPhase(context, Phase.BREAK)
        ambient.sync(Phase.BREAK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(breakMix().isActive)
        assertFalse(coastalMix().isActive || stormMix().isActive || boatMix().isActive)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        ambient.stopImmediate()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertFalse(coastalMix().isActive)
    }

    @Test
    fun oldHouseIsIndependentlySelectableAndSupportsPauseResumeAndDisconnect() {
        val house = BuiltInAmbientSound.valueOf("OLD_HOUSE")
        TimerStore.setBuiltInAmbientSounds(context, setOf(house))
        assertEquals("Old house", TimerStore.ambientLabel(context))
        assertEquals(setOf(house), TimerStore.builtInAmbientSounds(context))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(houseMix().isActive)
        assertFalse(stormMix().isActive || breakMix().isActive || boatMix().isActive)
        val layersField = BreakAmbiencePlayer::class.java.getDeclaredField("layers").apply { isAccessible = true }
        val layers = layersField.get(houseMix()) as List<*>
        val names = layers.map { layer ->
            val field = layer!!.javaClass.getDeclaredField("player").apply { isAccessible = true }
            val player = field.get(layer) as ExoPlayer
            context.resources.getResourceEntryName(player.currentMediaItem!!.localConfiguration!!.uri.lastPathSegment!!.toInt())
        }.toSet()
        assertEquals(setOf("old_house_frames", "old_house_windows"), names)
        ambient.pauseForSessionHold()
        assertFalse(houseMix().isActive)
        ambient.resumeFromSessionHold(Phase.FOCUS)
        assertTrue(houseMix().isActive)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(houseMix().isActive)
    }

    @Test
    fun fourGroupsKeepHouseActiveAndRestoreSoloGainWhenOthersExit() {
        val house = BuiltInAmbientSound.valueOf("OLD_HOUSE")
        TimerStore.setBuiltInAmbientSounds(context, setOf(house, BuiltInAmbientSound.OCEAN_WAVES, BuiltInAmbientSound.BOAT_HUM, BuiltInAmbientSound.ROOF_STORM))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(houseMix().isActive && stormMix().isActive && breakMix().isActive && boatMix().isActive)
        assertEquals(0.25f, targetGain(houseMix()), 0.0001f)
        assertEquals(0.25f, targetGain(stormMix()), 0.0001f)
        assertTrue(0.82f * targetGain(breakMix()) + 0.92f * targetGain(boatMix()) + 0.94f * targetGain(stormMix()) + 0.42f * targetGain(houseMix()) < 0.95f)
        TimerStore.setBuiltInAmbientSounds(context, setOf(house))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(houseMix().isActive)
        assertEquals(1f, targetGain(houseMix()), 0.0001f)
        assertFalse(stormMix().isActive || breakMix().isActive || boatMix().isActive)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(houseMix().isActive)
    }

    @Test
    fun houseRestartDuringFadeSurvivesAndBreaksReturnToOceanOnly() {
        val house = BuiltInAmbientSound.valueOf("OLD_HOUSE")
        TimerStore.setBuiltInAmbientSounds(context, setOf(house))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        TimerStore.setBuiltInAmbientSounds(context, emptySet())
        ambient.sync(Phase.FOCUS)
        TimerStore.setBuiltInAmbientSounds(context, setOf(house))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(houseMix().isActive)
        TimerStore.setPhase(context, Phase.BREAK)
        ambient.sync(Phase.BREAK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(breakMix().isActive)
        assertFalse(houseMix().isActive || stormMix().isActive || boatMix().isActive)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        ambient.stopImmediate()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertFalse(houseMix().isActive)
    }

    @Test
    fun stormOnlyPreparesSevenOfflineLayersAndSupportsPauseResumeAndDisconnect() {
        val storm = BuiltInAmbientSound.valueOf("ROOF_STORM")
        TimerStore.setBuiltInAmbientSounds(context, setOf(storm))
        assertEquals("Roof storm", TimerStore.ambientLabel(context))
        assertEquals(setOf(storm), TimerStore.builtInAmbientSounds(context))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(stormMix().isActive)
        assertFalse(breakMix().isActive || boatMix().isActive)
        val field = BreakAmbiencePlayer::class.java.getDeclaredField("layers").apply { isAccessible = true }
        val layers = (field.get(stormMix()) as List<*>).toList()
        val names = layers.map { layer ->
            val playerField = layer!!.javaClass.getDeclaredField("player").apply { isAccessible = true }
            val player = playerField.get(layer) as ExoPlayer
            val id = player.currentMediaItem!!.localConfiguration!!.uri.lastPathSegment!!.toInt()
            context.resources.getResourceEntryName(id)
        }.toSet()
        assertEquals(setOf(
            "roof_storm_roof", "roof_storm_roof_texture", "roof_storm_roof_rumble", "roof_storm_downpour",
            "roof_storm_wind_gusts", "roof_storm_wind_window", "roof_storm_thunder",
        ), names)
        ambient.sync(Phase.FOCUS)
        assertEquals(layers, field.get(stormMix()))
        ambient.pauseForSessionHold()
        assertFalse(stormMix().isActive)
        ambient.resumeFromSessionHold(Phase.FOCUS)
        assertTrue(stormMix().isActive)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(stormMix().isActive)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertFalse(stormMix().isActive)
    }

    @Test
    fun stormRemainsActiveAfterOtherGroupsExitAndRestoresSoloGain() {
        val storm = BuiltInAmbientSound.valueOf("ROOF_STORM")
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.OCEAN_WAVES, BuiltInAmbientSound.BOAT_HUM, storm))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        assertTrue(breakMix().isActive && boatMix().isActive && stormMix().isActive)
        assertEquals(1f / 3f, targetGain(stormMix()), 0.0001f)
        assertTrue(0.82f * targetGain(breakMix()) + 0.92f * targetGain(boatMix()) + 0.94f * targetGain(stormMix()) < 0.95f)
        TimerStore.setBuiltInAmbientSounds(context, setOf(storm))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(stormMix().isActive)
        assertFalse(breakMix().isActive || boatMix().isActive)
        assertEquals(1f, targetGain(stormMix()), 0.0001f)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(stormMix().isActive)
    }

    @Test
    fun stormDeselectionRestartAndBreakTransitionCancelOutgoingFades() {
        val storm = BuiltInAmbientSound.valueOf("ROOF_STORM")
        TimerStore.setBuiltInAmbientSounds(context, setOf(storm))
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        TimerStore.setBuiltInAmbientSounds(context, emptySet())
        ambient.sync(Phase.FOCUS)
        TimerStore.setBuiltInAmbientSounds(context, setOf(storm))
        ambient.sync(Phase.FOCUS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(stormMix().isActive)
        TimerStore.setPhase(context, Phase.BREAK)
        ambient.sync(Phase.BREAK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertFalse(stormMix().isActive)
        assertTrue(breakMix().isActive)
        TimerStore.setPhase(context, Phase.FOCUS)
        ambient.sync(Phase.FOCUS)
        ambient.stopImmediate()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertFalse(stormMix().isActive || boatMix().isActive || breakMix().isActive)
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

    private fun stormMix(): BreakAmbiencePlayer {
        val field = AmbientPlayer::class.java.getDeclaredField("roofStorm").apply { isAccessible = true }
        return field.get(ambient) as BreakAmbiencePlayer
    }

    private fun coastalMix(): BreakAmbiencePlayer {
        val field = AmbientPlayer::class.java.getDeclaredField("coastalWind").apply { isAccessible = true }
        return field.get(ambient) as BreakAmbiencePlayer
    }

    private fun houseMix(): BreakAmbiencePlayer {
        val field = AmbientPlayer::class.java.getDeclaredField("oldHouse").apply { isAccessible = true }
        return field.get(ambient) as BreakAmbiencePlayer
    }

    private fun breakMix(): BreakAmbiencePlayer {
        // Inspect the real mixer owned by AmbientPlayer without exposing a test-only production API.
        val field = AmbientPlayer::class.java.getDeclaredField("breakAmbience")
        field.isAccessible = true
        return field.get(ambient) as BreakAmbiencePlayer
    }
}
