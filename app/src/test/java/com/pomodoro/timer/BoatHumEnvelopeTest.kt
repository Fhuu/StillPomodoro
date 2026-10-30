package com.pomodoro.timer

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoatHumEnvelopeTest {
    @Test
    fun startsSilentlyAndKeepsTheNextSampleSilentOutsideTransitions() {
        val envelope = BoatHumEnvelope(42)
        assertEquals(0f to 0f, envelope.volumes(0.0))
        for (second in 5..1000) {
            val (current, next) = envelope.volumes(second.toDouble())
            assertTrue(current in 0.71f..0.88f)
            assertEquals(0f, next)
        }
    }

    @Test
    fun crossfadeTransfersPowerSmoothlyFromCurrentToNext() {
        val envelope = BoatHumEnvelope(42)
        val level = envelope.volumes(100.0).first
        assertEquals(level to 0f, envelope.volumes(100.0, 0.0))
        val end = envelope.volumes(100.0, 1.0)
        assertEquals(0f, end.first, 0.000001f)
        assertEquals(level, end.second, 0.000001f)
        var previous = envelope.volumes(100.0, 0.0)
        for (step in 1..1000) {
            val gains = envelope.volumes(100.0, step / 1000.0)
            assertEquals((level * level).toDouble(), (gains.first * gains.first + gains.second * gains.second).toDouble(), 0.000001)
            assertTrue(gains.first <= previous.first && gains.second >= previous.second)
            assertTrue(abs(gains.first - previous.first) < 0.003f)
            assertTrue(abs(gains.second - previous.second) < 0.003f)
            previous = gains
        }
    }

    @Test
    fun fractalVariationIsSmoothSeededAndControlsTransitionDuration() {
        val first = BoatHumEnvelope(42)
        val same = BoatHumEnvelope(42)
        val other = BoatHumEnvelope(43)
        val durations = mutableSetOf<Long>()
        var previous = first.volumes(0.0).first
        for (step in 1..10000) {
            val second = step / 10.0
            val current = first.volumes(second).first
            assertTrue(abs(current - previous) < 0.03f)
            assertEquals(first.volumes(second), same.volumes(second))
            val duration = first.transitionDurationMs(second)
            assertTrue(duration in 6_000L..10_000L)
            durations.add(duration)
            previous = current
        }
        assertTrue(durations.size > 10)
        assertTrue(first.volumes(100.0) != other.volumes(100.0))
    }

    @Test
    fun louderBoatCrossfadesRetainStandaloneHeadroomForDistinctRecordings() {
        val envelope = BoatHumEnvelope(42)
        // Conservative decoded peak bounds after the +6 dB asset boost: diesel, ship, cabin.
        val peaks = listOf(0.60, 0.85, 0.50)
        for (step in 0..1000) {
            val (a, b) = envelope.volumes(100.0, step / 1000.0)
            assertTrue(a <= 1f && b <= 1f)
            for (current in peaks.indices) for (next in peaks.indices) {
                if (current != next) assertTrue(a * peaks[current] + b * peaks[next] < 0.95)
            }
        }
    }

    @Test
    fun combinedBuiltInsRetainHeadroomEvenDuringBoatCrossfades() {
        val waves = BreakAmbienceEnvelope(42)
        val boat = BoatHumEnvelope(42)
        val wavePeaks = listOf(0.55f, 0.72f, 0.80f, 0.48f, 0.34f)
        for (second in 90..1000) {
            val wavePeak = waves.volumes(second.toDouble()).zip(wavePeaks).sumOf { (gain, peak) -> (gain * peak).toDouble() }
            val (a, b) = boat.volumes(second.toDouble(), 0.5)
            val totalPeak = 0.50 * (wavePeak + 0.85 * a + 0.60 * b)
            assertTrue(totalPeak < 0.95)
        }
    }
}
