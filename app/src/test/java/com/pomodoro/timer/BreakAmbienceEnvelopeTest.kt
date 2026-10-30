package com.pomodoro.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BreakAmbienceEnvelopeTest {
    @Test
    fun startsSilentlyAndStaggersAllFiveLayers() {
        for (seed in 0..30) {
            val envelope = BreakAmbienceEnvelope(seed)
            assertEquals(List(5) { 0f }, envelope.volumes(0.0).toList())
            val early = envelope.volumes(1.0).toList()
            assertEquals(1, early.count { it > 0f })
            assertTrue(envelope.volumes(90.0).toList().all { it > 0.025f })
        }
    }

    @Test
    fun keepsAllFiveLayersAudibleWithoutExceedingCombinedHeadroom() {
        for (seed in 0..20) {
            val envelope = BreakAmbienceEnvelope(seed)
            for (second in 90..3600) {
                val volumes = envelope.volumes(second.toDouble()).toList()
                assertEquals(5, volumes.size)
                assertTrue(volumes.all { it > 0.025f })
                assertTrue(volumes.sum() > 0.74f)
                // Conservative decoded peak bounds: calm, gentle, lapping, shingle, distant.
                val peaks = listOf(0.55f, 0.72f, 0.80f, 0.48f, 0.34f)
                val worstAlignedPeak = volumes.zip(peaks).sumOf { (gain, peak) -> (gain * peak).toDouble() }
                assertTrue(worstAlignedPeak < 0.95)
            }
        }
    }

    @Test
    fun changesGentlyAcrossNoiseBoundaries() {
        val envelope = BreakAmbienceEnvelope(42)
        var previous = envelope.volumes(0.0).toList()
        for (step in 1..36000) {
            val current = envelope.volumes(step / 10.0).toList()
            current.zip(previous).forEach { (a, b) ->
                assertTrue(abs(a - b) < 0.025f)
            }
            previous = current
        }
    }

    @Test
    fun variesAllFiveLayersIndependentlyOverTime() {
        val envelope = BreakAmbienceEnvelope(42)
        val samples = (90..900 step 5).map { envelope.volumes(it.toDouble()).toList() }
        assertEquals(5, samples.first().size)
        for (layer in 0..4) {
            assertTrue(samples.maxOf { it[layer] } - samples.minOf { it[layer] } > 0.01f)
        }
        val changes = samples.zipWithNext()
        assertTrue(changes.any { (a, b) -> (b[0] - a[0]) * (b[1] - a[1]) < 0f })
    }

    @Test
    fun seedReproducesTheMixWhileDifferentSeedsVaryIt() {
        val first = BreakAmbienceEnvelope(42)
        val same = BreakAmbienceEnvelope(42)
        val other = BreakAmbienceEnvelope(43)
        for (second in listOf(1.0, 30.0, 91.5, 500.0)) {
            assertEquals(first.volumes(second), same.volumes(second))
        }
        assertTrue(first.volumes(100.0) != other.volumes(100.0))
    }
}
