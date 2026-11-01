package com.pomodoro.sound

import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.service.quicksettings.Tile
import android.media.AudioManager
import android.app.NotificationManager
import com.pomodoro.timer.Phase
import com.pomodoro.timer.TimerStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [QuickSoundsTest.ShadowQuickTile::class])
@LooperMode(LooperMode.Mode.PAUSED)
class QuickSoundsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        context.getSharedPreferences("quick_sounds", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun selectionDefaultsToOceanAndCanRememberAnEmptyMix() {
        assertEquals(setOf(BuiltInAmbientSound.OCEAN_WAVES), QuickSoundsStore.selection(context))
        QuickSoundsStore.setSelection(context, emptySet())
        assertTrue(QuickSoundsStore.selection(context).isEmpty())
    }

    @Test fun panelAppearanceOptionsPersistIndependentlyOfTheMix() {
        assertTrue(QuickSoundsStore.cyberpunkPanel(context))
        assertFalse(QuickSoundsStore.blurredPanel(context))
        QuickSoundsStore.setCyberpunkPanel(context, false)
        QuickSoundsStore.setBlurredPanel(context, true)
        assertFalse(QuickSoundsStore.cyberpunkPanel(context))
        assertTrue(QuickSoundsStore.blurredPanel(context))
        assertEquals(setOf(BuiltInAmbientSound.OCEAN_WAVES), QuickSoundsStore.selection(context))
        QuickSoundsStore.setCyberpunkPanel(context, true)
        assertTrue(QuickSoundsStore.blurredPanel(context))
    }

    @Test fun selectingSoundsStartsStandalonePlaybackWithoutStartingTimer() {
        TimerStore.setPhase(context, Phase.IDLE)
        QuickSoundsService.selectSounds(context, setOf(BuiltInAmbientSound.OLD_HOUSE))
        assertEquals(setOf(BuiltInAmbientSound.OLD_HOUSE), QuickSoundsStore.selection(context))
        assertEquals(QuickSoundsService.ACTION_PLAY,
            shadowOf(RuntimeEnvironment.getApplication()).nextStartedService.action)
        assertEquals(Phase.IDLE, TimerStore.phase(context))
    }

    @Test fun deselectingLastSoundRequestsPlaybackStop() {
        val controller = Robolectric.buildService(QuickSoundsService::class.java).create()
        try {
            controller.get().onStartCommand(Intent().setAction(QuickSoundsService.ACTION_PLAY), 0, 1)
            assertTrue(QuickSoundsService.isPlaying)
            QuickSoundsService.selectSounds(context, emptySet())
            assertTrue(QuickSoundsStore.selection(context).isEmpty())
            assertEquals(QuickSoundsService.ACTION_STOP,
                shadowOf(RuntimeEnvironment.getApplication()).nextStartedService.action)
        } finally {
            controller.destroy()
        }
    }

    @Test fun selectionDoesNotChangeSessionOrItsMix() {
        TimerStore.setPhase(context, Phase.FOCUS)
        TimerStore.setEndAtMillis(context, 123456L)
        val original = TimerStore.builtInAmbientSounds(context)
        val mix = setOf(BuiltInAmbientSound.ROOF_STORM, BuiltInAmbientSound.OLD_HOUSE)
        QuickSoundsStore.setSelection(context, mix)
        assertEquals(mix, QuickSoundsStore.selection(context))
        assertEquals(original, TimerStore.builtInAmbientSounds(context))
        assertEquals(Phase.FOCUS, TimerStore.phase(context))
        assertEquals(123456L, TimerStore.endAtMillis(context))
    }

    @Test fun emptySelectionCannotStartPlaybackOrATimer() {
        TimerStore.setPhase(context, Phase.IDLE)
        QuickSoundsStore.setSelection(context, emptySet())
        val controller = Robolectric.buildService(QuickSoundsService::class.java).create()
        controller.get().onStartCommand(Intent().setAction(QuickSoundsService.ACTION_PLAY), 0, 1)
        assertFalse(QuickSoundsService.isPlaying)
        assertEquals(Phase.IDLE, TimerStore.phase(context))
        controller.destroy()
    }

    @Test fun stoppingStandaloneServiceLeavesTimerIntact() {
        TimerStore.setPhase(context, Phase.FOCUS)
        TimerStore.setEndAtMillis(context, 123456L)
        val controller = Robolectric.buildService(QuickSoundsService::class.java).create()
        controller.get().onStartCommand(Intent().setAction(QuickSoundsService.ACTION_STOP), 0, 1)
        controller.destroy()
        assertFalse(QuickSoundsService.isPlaying)
        assertEquals(Phase.FOCUS, TimerStore.phase(context))
        assertEquals(123456L, TimerStore.endAtMillis(context))
    }

    @Test fun playbackRunsWhileTimerIsIdleAndDisconnectStopsIt() {
        TimerStore.setPhase(context, Phase.IDLE)
        val controller = Robolectric.buildService(QuickSoundsService::class.java).create()
        try {
            controller.get().onStartCommand(Intent().setAction(QuickSoundsService.ACTION_PLAY), 0, 1)
            assertTrue(QuickSoundsService.isPlaying)
            assertEquals(Phase.IDLE, TimerStore.phase(context))
            val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
            assertEquals(1, notifications.allNotifications.size)
            context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse(QuickSoundsService.isPlaying)
            assertEquals(0, notifications.allNotifications.size)
            assertEquals(Phase.IDLE, TimerStore.phase(context))
        } finally {
            controller.destroy()
        }
    }

    @Test fun clearingRunningMixStopsPlaybackAndDestructionResetsState() {
        val controller = Robolectric.buildService(QuickSoundsService::class.java).create()
        try {
            val service = controller.get()
            service.onStartCommand(Intent().setAction(QuickSoundsService.ACTION_PLAY), 0, 1)
            assertTrue(QuickSoundsService.isPlaying)
            QuickSoundsStore.setSelection(context, emptySet())
            service.onStartCommand(Intent().setAction(QuickSoundsService.ACTION_REFRESH), 0, 2)
            assertFalse(QuickSoundsService.isPlaying)
            QuickSoundsStore.setSelection(context, setOf(BuiltInAmbientSound.BOAT_HUM))
            service.onStartCommand(Intent().setAction(QuickSoundsService.ACTION_PLAY), 0, 3)
            assertTrue(QuickSoundsService.isPlaying)
        } finally {
            controller.destroy()
        }
        assertFalse(QuickSoundsService.isPlaying)
        assertEquals(setOf(BuiltInAmbientSound.BOAT_HUM), QuickSoundsStore.selection(context))
    }

    @Test fun longPressResolvesToSoundPanel() {
        val intent = Intent(TileService.ACTION_QS_TILE_PREFERENCES).setPackage(context.packageName)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertEquals(SoundPanelActivity::class.java.name, resolved?.activityInfo?.name)
    }

    @Test fun visibleTileUpdatesImmediatelyWhenPlaybackStartsAndStops() {
        val tileController = Robolectric.buildService(AmbientTileService::class.java).create()
        val serviceController = Robolectric.buildService(QuickSoundsService::class.java).create()
        val tile = tileController.get().qsTile
        try {
            tileController.get().onStartListening()
            assertEquals(Tile.STATE_INACTIVE, tile.state)
            serviceController.get().onStartCommand(Intent().setAction(QuickSoundsService.ACTION_PLAY), 0, 1)
            assertEquals(Tile.STATE_ACTIVE, tile.state)
            serviceController.get().onStartCommand(Intent().setAction(QuickSoundsService.ACTION_STOP), 0, 2)
            assertEquals(Tile.STATE_INACTIVE, tile.state)
        } finally {
            serviceController.destroy()
            tileController.get().onStopListening()
        }
    }

    @Implements(Tile::class)
    class ShadowQuickTile {
        @Implementation fun updateTile() {} // SystemUI binder is outside this test process.
    }
}
