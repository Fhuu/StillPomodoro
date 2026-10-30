package com.pomodoro.timer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

internal class BoatHumEnvelope(private val seed: Int) {
    fun volumes(elapsedSeconds: Double, transitionProgress: Double? = null): Pair<Float, Float> {
        val seconds = elapsedSeconds.coerceAtLeast(0.0)
        val entrance = FractalNoise.smoothstep((seconds / 5.0).coerceIn(0.0, 1.0))
        // +6 dB is baked into the assets; +4 dB here completes the requested +10 dB boost.
        val level = (0.45 + 0.10 * FractalNoise.sample(seconds, seed)) * entrance * 1.5848932
        if (transitionProgress == null) return level.toFloat() to 0f
        val blend = FractalNoise.smoothstep(transitionProgress.coerceIn(0.0, 1.0))
        val angle = blend * PI / 2.0
        return (level * cos(angle)).toFloat() to (level * sin(angle)).toFloat()
    }

    fun transitionDurationMs(elapsedSeconds: Double): Long =
        (6_000.0 + 4_000.0 * FractalNoise.sample(elapsedSeconds.coerceAtLeast(0.0), seed xor 0x5bd1e995)).roundToLong()
}
