package com.pomodoro.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OldHouseEnvelopeTest {
    @Test
    fun fadesInQuietlyAndKeepsBothLayersBoundedAndIndependent() {
        for (seed in 0..20) {
            val mix = OldHouseEnvelope(seed)
            assertEquals(listOf(0f, 0f), mix.volumes(0.0))
            assertEquals(mix.volumes(0.0), mix.volumes(-1.0))
            var previous = mix.volumes(0.0)
            for (step in 1..12000) {
                val gains = mix.volumes(step / 10.0)
                assertEquals(2, gains.size)
                assertTrue(gains[0] in 0f..0.24f)
                assertTrue(gains[1] in 0f..0.18f)
                assertTrue(gains.sum() <= 0.42001f)
                gains.zip(previous).forEach { (a, b) -> assertTrue(abs(a - b) < 0.02f) }
                previous = gains
            }
        }
        val mix = OldHouseEnvelope(42)
        val samples = (30..1800 step 5).map { mix.volumes(it.toDouble()) }
        assertTrue(samples.all { it.all { gain -> gain > 0f } })
        assertTrue(samples.zipWithNext().any { (a, b) -> (b[0] - a[0]) * (b[1] - a[1]) < 0f })
        assertEquals(mix.volumes(123.4), OldHouseEnvelope(42).volumes(123.4))
        assertTrue(mix.volumes(123.4) != OldHouseEnvelope(43).volumes(123.4))
    }
}
