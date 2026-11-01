package com.pomodoro.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RoofStormEnvelopeTest {
    @Test
    fun fadesInRainFirstAndIntroducesAllSevenLayers() {
        for (seed in 0..20) {
            val mix = RoofStormEnvelope(seed)
            assertEquals(List(7) { 0f }, mix.volumes(0.0))
            assertEquals(mix.volumes(0.0), mix.volumes(-5.0))
            assertTrue(mix.volumes(1.0)[0] > 0f)
            assertEquals(1, mix.volumes(1.0).count { it > 0f })
            assertTrue(mix.volumes(60.0).all { it > 0f })
        }
    }

    @Test
    fun keepsRainWindAndThunderAudibleWithoutExceedingHeadroom() {
        for (seed in 0..20) {
            val mix = RoofStormEnvelope(seed)
            for (second in 60..3600) {
                val gains = mix.volumes(second.toDouble())
                assertEquals(7, gains.size)
                assertTrue(gains.all { it in 0f..1f })
                // This bound holds even if every decoded recording peaks at full scale.
                assertTrue(gains.sum() <= 0.95f)
                assertTrue(gains.all { it > 0f })
                assertTrue(gains[4] + gains[5] > 0.06f)
                assertTrue(gains[6] < 0.1f)
            }
        }
    }

    @Test
    fun changesSmoothlyAndIndependentlyWithReproducibleSeeds() {
        val mix = RoofStormEnvelope(42)
        val same = RoofStormEnvelope(42)
        val other = RoofStormEnvelope(43)
        var previous = mix.volumes(0.0)
        for (step in 1..36000) {
            val current = mix.volumes(step / 10.0)
            current.zip(previous).forEach { (a, b) -> assertTrue(abs(a - b) < 0.02f) }
            previous = current
        }
        val samples = (60..1800 step 5).map { mix.volumes(it.toDouble()) }
        for (layer in 0..6) {
            assertTrue(samples.maxOf { it[layer] } - samples.minOf { it[layer] } > 0.01f)
        }
        assertTrue(samples.zipWithNext().any { (a, b) -> (b[4] - a[4]) * (b[5] - a[5]) < 0f })
        assertEquals(mix.volumes(123.4), same.volumes(123.4))
        assertTrue(mix.volumes(123.4) != other.volumes(123.4))
    }
}
