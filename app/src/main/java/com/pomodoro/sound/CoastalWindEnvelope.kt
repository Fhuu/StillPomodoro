package com.pomodoro.sound

/** Preserve the recording's gusts, adding only a gentle slow change in strength. */
internal class CoastalWindEnvelope(private val seed: Int) {
    fun volumes(elapsedSeconds: Double): List<Float> {
        val seconds = elapsedSeconds.coerceAtLeast(0.0)
        val fade = FractalNoise.smoothstep((seconds / 5.0).coerceIn(0.0, 1.0))
        val strength = 0.70 + 0.30 * FractalNoise.sample(seconds, seed)
        return listOf((0.65 * strength * fade).toFloat())
    }
}
