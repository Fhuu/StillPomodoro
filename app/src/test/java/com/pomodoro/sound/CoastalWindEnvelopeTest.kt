package com.pomodoro.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoastalWindEnvelopeTest {
    @Test
    fun fadesInSmoothlyAndPreservesAudibleBoundedNaturalGusts() {
        for (seed in 0..20) {
            val mix = CoastalWindEnvelope(seed)
            assertEquals(listOf(0f), mix.volumes(0.0))
            assertEquals(mix.volumes(0.0), mix.volumes(-1.0))
            var previous = 0f
            for (step in 1..18000) {
                val gains = mix.volumes(step / 10.0)
                assertEquals(1, gains.size)
                assertTrue(gains.single() in 0f..0.65f)
                if (step >= 50) assertTrue(gains.single() > 0.4f)
                assertTrue(abs(gains.single() - previous) < 0.025f)
                previous = gains.single()
            }
        }
        val mix = CoastalWindEnvelope(42)
        val samples = (30..1800 step 5).map { mix.volumes(it.toDouble()).single() }
        assertTrue(samples.max() - samples.min() > 0.03f)
        assertEquals(mix.volumes(123.4), CoastalWindEnvelope(42).volumes(123.4))
        assertTrue(mix.volumes(123.4) != CoastalWindEnvelope(43).volumes(123.4))
    }
}
