package com.pomodoro.timer

import kotlin.math.floor

/** Smooth value-noise octaves at 23, 61 and 173 seconds, bounded to [0, 1]. */
internal object FractalNoise {
    fun sample(seconds: Double, seed: Int): Double =
        0.60 * octave(seconds / 23.0, seed) +
            0.27 * octave(seconds / 61.0, seed xor 0x27d4eb2d) +
            0.13 * octave(seconds / 173.0, seed xor 0x165667b1)

    private fun octave(position: Double, salt: Int): Double {
        val cell = floor(position).toInt()
        val blend = smoothstep(position - cell)
        return lattice(cell, salt) * (1.0 - blend) + lattice(cell + 1, salt) * blend
    }

    private fun lattice(cell: Int, salt: Int): Double {
        var hash = cell * 0x27d4eb2d xor salt
        hash = (hash xor (hash ushr 16)) * 0x45d9f3b
        hash = (hash xor (hash ushr 16)) * 0x45d9f3b
        hash = hash xor (hash ushr 16)
        return (hash ushr 8).toDouble() / 0xffffff
    }

    fun smoothstep(value: Double): Double = value * value * (3.0 - 2.0 * value)
}
