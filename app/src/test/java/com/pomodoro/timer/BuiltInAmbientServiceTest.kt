package com.pomodoro.timer

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BuiltInAmbientServiceTest {
    private lateinit var context: Context
    private lateinit var controller: ServiceController<PomodoroService>
    private lateinit var service: PomodoroService

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        TimerStore.prefs(context).edit().clear().commit()
        TimerStore.setPhase(context, Phase.FOCUS)
        TimerStore.setEndAtMillis(context, System.currentTimeMillis() + 300_000)
        controller = Robolectric.buildService(PomodoroService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    @Test
    fun refreshingBuiltInStartsAudioWithoutResettingTimerAndRemovesSkipControls() {
        val endAt = TimerStore.endAtMillis(context)
        val sessionField = PomodoroService::class.java.getDeclaredField("mediaSession")
        sessionField.isAccessible = true
        val session = sessionField.get(service) as MediaSessionCompat
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_PLAY)
            .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f).build())
        TimerStore.applyBuiltInSource(context)
        refresh()
        assertTrue(mix().isActive)
        assertEquals(endAt, TimerStore.endAtMillis(context))
        assertEquals(0L, session.controller.playbackState!!.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
    }

    @Test
    fun changingSourceWhilePausedWaitsForResumeAndDoesNotResumeTheOldLocalTrack() {
        TimerStore.setSingleTrackFile(context, "content://music/song", "My song")
        val source = "file:///test-song.ogg"
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(source), ShadowMediaPlayer.MediaInfo())
        val local = MediaPlayer().apply {
            setDataSource(source)
            prepare()
            start()
        }
        val field = AmbientPlayer::class.java.getDeclaredField("player")
        field.isAccessible = true
        field.set(ambient(), local)
        ambient().pauseForSessionHold()
        TimerStore.setSessionPaused(context, true, Phase.FOCUS, 120)
        val endAt = TimerStore.endAtMillis(context)
        TimerStore.applyBuiltInSource(context)
        refresh()
        assertFalse(mix().isActive)
        assertTrue(TimerStore.isSessionPaused(context))
        assertEquals(endAt, TimerStore.endAtMillis(context))
        TimerStore.clearSessionPaused(context)
        ambient().resumeFromSessionHold(Phase.FOCUS)
        assertTrue(mix().isActive)
    }

    private fun refresh() {
        service.onStartCommand(Intent(context, PomodoroService::class.java)
            .setAction(PomodoroService.ACTION_REFRESH_AMBIENT), 0, 1)
    }

    private fun ambient(): AmbientPlayer {
        val field = PomodoroService::class.java.getDeclaredField("ambient")
        field.isAccessible = true
        return field.get(service) as AmbientPlayer
    }

    private fun mix(): BreakAmbiencePlayer {
        val field = AmbientPlayer::class.java.getDeclaredField("breakAmbience")
        field.isAccessible = true
        return field.get(ambient()) as BreakAmbiencePlayer
    }
}
