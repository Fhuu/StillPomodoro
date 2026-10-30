package com.pomodoro.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoatHumSequenceTest {
    @Test
    fun playsOnlyCurrentSampleUntilTheTransitionWindowAndNextReadiness() {
        val sequence = BoatHumSequence(42)
        val middle = sequence.update(30.0, 30_000, 60_000, 0, 45_000, true)
        assertFalse(middle.startNext)
        assertEquals(0f, middle.gains.second)
        val waiting = sequence.update(59.0, 59_000, 60_000, 0, 45_000, false)
        assertFalse(waiting.startNext)
        assertFalse(waiting.promoteNext)
        assertEquals(0f, waiting.gains.second)
    }

    @Test
    fun advancesCrossfadeByIncomingPlaybackRatherThanWallTime() {
        val sequence = BoatHumSequence(42)
        val begin = sequence.update(59.0, 59_000, 60_000, 0, 45_000, true)
        assertTrue(begin.startNext)
        assertEquals(0f, begin.gains.second)
        val stalled = sequence.update(159.0, 59_000, 60_000, 0, 45_000, true)
        assertFalse(stalled.startNext)
        assertFalse(stalled.promoteNext)
        assertEquals(0f, stalled.gains.second)
        val middle = sequence.update(160.0, 59_000, 60_000, 4_000, 45_000, true)
        assertTrue(middle.gains.first > 0f && middle.gains.second > 0f)
        val stalledMiddle = sequence.update(260.0, 59_000, 60_000, 4_000, 45_000, true)
        assertFalse(stalledMiddle.promoteNext)
        assertEquals(middle.gains.second / middle.gains.first,
            stalledMiddle.gains.second / stalledMiddle.gains.first, 0.00001f)
        val end = sequence.update(166.0, 59_000, 60_000, 10_000, 45_000, true)
        assertTrue(end.promoteNext)
        assertEquals(0f, end.gains.first, 0.000001f)
    }

    @Test
    fun promotionResetsTheTransitionForTheNextSample() {
        val sequence = BoatHumSequence(42)
        sequence.update(59.0, 59_000, 60_000, 0, 45_000, true)
        assertTrue(sequence.update(69.0, 59_000, 60_000, 10_000, 45_000, true).promoteNext)
        sequence.nextSample()
        val currentOnly = sequence.update(70.0, 11_000, 45_000, 0, 60_000, true)
        assertFalse(currentOnly.startNext)
        assertFalse(currentOnly.promoteNext)
        assertEquals(0f, currentOnly.gains.second)
    }
}
