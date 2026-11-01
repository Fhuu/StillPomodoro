package com.pomodoro.sound

import kotlin.random.Random

/** Fractal volume envelopes for roof rain, texture, rumble, downpour, two winds, and thunder. */
internal class RoofStormEnvelope(private val seed: Int) {
    // Sum <= 0.94 even with full-scale sources, before shared output peak compression.
    private val maximumGains = listOf(0.28, 0.07, 0.15, 0.12, 0.14, 0.10, 0.08)
    private val startSeconds = DoubleArray(7).also { starts ->
        val random = Random(seed)
        var delay = 0.0
        (1..6).shuffled(random).forEach { layer ->
            delay += random.nextDouble(2.0, 6.0)
            starts[layer] = delay
        }
    }

    fun volumes(elapsedSeconds: Double): List<Float> {
        val seconds = elapsedSeconds.coerceAtLeast(0.0)
        return maximumGains.mapIndexed { layer, gain ->
            val wind = layer == 4 || layer == 5
            val noise = FractalNoise.sample(seconds * if (wind) 2.0 else 1.0, seed xor (layer * 0x5bd1e995))
            val strength = if (wind) 0.35 + 0.65 * noise else 0.65 + 0.35 * noise
            val fade = FractalNoise.smoothstep(((seconds - startSeconds[layer]) / 5.0).coerceIn(0.0, 1.0))
            (gain * strength * fade).toFloat()
        }
    }
}
