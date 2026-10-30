package com.pomodoro.timer

import android.content.Context
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
class BoatHumPlayerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun preparesDifferentCurrentAndNextSamplesWithoutStartingTheNext() {
        val hum = BoatHumPlayer(context)
        try {
            hum.start()
            assertTrue(hum.isActive)
            val current = deck(hum, "current")!!
            val incoming = deck(hum, "incoming")!!
            val known = listOf(R.raw.boat_hum_diesel, R.raw.boat_hum_ship, R.raw.boat_hum_cabin)
                .map { "android.resource://${context.packageName}/$it" }
            val currentUri = current.currentMediaItem!!.localConfiguration!!.uri.toString()
            val nextUri = incoming.currentMediaItem!!.localConfiguration!!.uri.toString()
            assertTrue(currentUri in known && nextUri in known)
            assertNotEquals(currentUri, nextUri)
            assertFalse(incoming.playWhenReady)
            assertEquals(0f, incoming.volume)
        } finally {
            hum.stop(immediate = true)
        }
    }

    @Test
    fun stopBeforeReadinessCancelsBothDecksAndFutureTransitions() {
        val hum = BoatHumPlayer(context)
        hum.start()
        assertTrue(hum.isActive)
        hum.stop(immediate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        assertFalse(hum.isActive)
    }

    @Test
    fun restartDuringFadeCannotBeReleasedByThePreviousFade() {
        val hum = BoatHumPlayer(context)
        try {
            hum.start()
            hum.stop()
            hum.start()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertTrue(hum.isActive)
        } finally {
            hum.stop(immediate = true)
        }
    }

    private fun deck(hum: BoatHumPlayer, name: String): ExoPlayer? {
        val field = BoatHumPlayer::class.java.getDeclaredField(name)
        field.isAccessible = true
        val deck = field.get(hum) ?: return null
        val playerField = deck.javaClass.getDeclaredField("player")
        playerField.isAccessible = true
        return playerField.get(deck) as ExoPlayer
    }
}
