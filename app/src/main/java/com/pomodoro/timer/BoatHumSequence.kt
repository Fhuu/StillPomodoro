package com.pomodoro.timer

/** Sequencing decisions use decoded playback position, so buffering cannot advance a fade. */
internal class BoatHumSequence(seed: Int) {
    data class Frame(
        val gains: Pair<Float, Float>,
        val startNext: Boolean = false,
        val promoteNext: Boolean = false,
    )

    private val envelope = BoatHumEnvelope(seed)
    private var crossfadeMs = 0L
    private var transitioning = false

    fun update(
        elapsedSeconds: Double,
        currentPositionMs: Long,
        currentDurationMs: Long,
        nextPositionMs: Long,
        nextDurationMs: Long,
        nextReady: Boolean,
    ): Frame {
        if (crossfadeMs == 0L && currentDurationMs > 0L && nextDurationMs > 0L) {
            crossfadeMs = minOf(
                envelope.transitionDurationMs(elapsedSeconds),
                currentDurationMs / 3,
                nextDurationMs / 3,
            ).coerceAtLeast(1L)
        }
        val startNext = !transitioning && nextReady && crossfadeMs > 0L &&
            currentPositionMs >= currentDurationMs - crossfadeMs
        if (startNext) transitioning = true
        val progress = if (transitioning) (nextPositionMs.toDouble() / crossfadeMs).coerceIn(0.0, 1.0) else null
        return Frame(
            envelope.volumes(elapsedSeconds, progress),
            startNext = startNext,
            promoteNext = progress != null && progress >= 1.0,
        )
    }

    fun nextSample() {
        transitioning = false
        crossfadeMs = 0L
    }
}
