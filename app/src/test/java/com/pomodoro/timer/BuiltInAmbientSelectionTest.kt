package com.pomodoro.timer

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BuiltInAmbientSelectionTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        TimerStore.prefs(context).edit().clear().commit()
    }

    @Test
    fun activatingBuiltInDefaultsToOceanWavesAndPublishesItsLabel() {
        TimerStore.applyBuiltInSource(context)
        assertEquals(AmbientMode.BUILT_IN, TimerStore.ambientMode(context))
        assertEquals(setOf(BuiltInAmbientSound.OCEAN_WAVES), TimerStore.builtInAmbientSounds(context))
        assertEquals("Ocean waves", TimerStore.ambientLabel(context))
    }

    @Test
    fun deselectingTheLastSoundDisablesFocusAudio() {
        TimerStore.applyBuiltInSource(context)
        TimerStore.setBuiltInAmbientSounds(context, emptySet())
        assertEquals(AmbientMode.OFF, TimerStore.ambientMode(context))
        assertTrue(TimerStore.builtInAmbientSounds(context).isEmpty())
    }

    @Test
    fun switchingSourcesPreservesSavedBuiltInSelectionWithoutKeepingItActive() {
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.OCEAN_WAVES))
        TimerStore.setSingleTrackFile(context, "content://music/song", "My song")
        assertEquals(AmbientMode.LIBRARY, TimerStore.ambientMode(context))
        assertEquals("My song", TimerStore.ambientLabel(context))
        assertEquals(setOf(BuiltInAmbientSound.OCEAN_WAVES), TimerStore.builtInAmbientSounds(context))
        TimerStore.applyBuiltInSource(context)
        assertEquals(AmbientMode.BUILT_IN, TimerStore.ambientMode(context))
        assertEquals("content://music/song", TimerStore.singleTrackUri(context))
    }

    @Test
    fun unknownSavedSoundIdsAreIgnored() {
        TimerStore.prefs(context).edit().putStringSet(
            "built_in_ambient_sounds", setOf("OCEAN_WAVES", "UNKNOWN_SOUND"),
        ).commit()
        assertEquals(setOf(BuiltInAmbientSound.OCEAN_WAVES), TimerStore.builtInAmbientSounds(context))
    }

    @Test
    fun savesBothBuiltInSoundsAndAllowsEitherToBeRemovedIndependently() {
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.OCEAN_WAVES, BuiltInAmbientSound.BOAT_HUM))
        assertEquals(2, TimerStore.builtInAmbientSounds(context).size)
        assertEquals("Ocean waves, Boat hum", TimerStore.ambientLabel(context))
        TimerStore.setBuiltInAmbientSounds(context, setOf(BuiltInAmbientSound.BOAT_HUM))
        assertEquals(AmbientMode.BUILT_IN, TimerStore.ambientMode(context))
        assertEquals(setOf(BuiltInAmbientSound.BOAT_HUM), TimerStore.builtInAmbientSounds(context))
        assertEquals("Boat hum", TimerStore.ambientLabel(context))
    }

    @Test
    fun turningOffAudioClearsTheBuiltInSelection() {
        TimerStore.applyBuiltInSource(context)
        TimerStore.clearAmbient(context)
        assertEquals(AmbientMode.OFF, TimerStore.ambientMode(context))
        assertTrue(TimerStore.builtInAmbientSounds(context).isEmpty())
    }
}
