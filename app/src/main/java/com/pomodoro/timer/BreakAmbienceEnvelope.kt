package com.pomodoro.timer

import kotlin.random.Random

/** Slow, independent value-noise octaves modulate volume, never the audio itself. */
internal class BreakAmbienceEnvelope(private val seed: Int) {
    private val random = Random(seed)
    // Another +3 dB (about +6 dB total); headroom is checked against the bundled recording peaks.
    private val maximumGains = listOf(0.16, 0.16, 0.14, 0.14, 0.08).map { it * 1.99733 }
    private val startSeconds = DoubleArray(maximumGains.size).also { starts ->
        var delay = 0.0
        maximumGains.indices.shuffled(random).forEach { layer ->
            starts[layer] = delay
            delay += random.nextDouble(5.0, 15.0)
        }
    }

    fun volumes(elapsedSeconds: Double): List<Float> {
        val seconds = elapsedSeconds.coerceAtLeast(0.0)
        return maximumGains.mapIndexed { layer, gain ->
            val strength = 0.55 + 0.45 * FractalNoise.sample(seconds, seed xor (layer * 0x5bd1e995))
            (gain * strength * fadeIn(seconds - startSeconds[layer])).toFloat()
        }
    }

    private fun fadeIn(seconds: Double): Double = FractalNoise.smoothstep((seconds / 5.0).coerceIn(0.0, 1.0))

}
