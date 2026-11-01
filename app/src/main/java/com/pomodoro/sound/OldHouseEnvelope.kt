package com.pomodoro.sound

/** Quiet, independently evolving frame and window layers; sparse events live in the recordings. */
internal class OldHouseEnvelope(private val seed: Int) {
    fun volumes(elapsedSeconds: Double): List<Float> {
        val seconds = elapsedSeconds.coerceAtLeast(0.0)
        val fade = FractalNoise.smoothstep((seconds / 5.0).coerceIn(0.0, 1.0))
        return listOf(0.24, 0.18).mapIndexed { layer, maximum ->
            val strength = 0.55 + 0.45 * FractalNoise.sample(seconds, seed xor (layer * 0x5bd1e995))
            (maximum * strength * fade).toFloat()
        }
    }
}
