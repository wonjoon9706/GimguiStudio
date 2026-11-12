package com.example.pleosaudioguide

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionTest {
    @Test fun backwardStopsAtZero() {
        assertEquals(0L, PlaybackPosition.skip30(12_000, 120_000, false))
        assertEquals(35_000L, PlaybackPosition.skip30(65_000, 120_000, false))
    }

    @Test fun forwardStopsAtTheActualDuration() {
        assertEquals(120_000L, PlaybackPosition.skip30(110_000, 120_000, true))
        assertEquals(65_000L, PlaybackPosition.skip30(35_000, 120_000, true))
    }

    @Test fun scrubIsClampedToKnownDuration() {
        assertEquals(0L, PlaybackPosition.clamp(-1, 120_000))
        assertEquals(120_000L, PlaybackPosition.clamp(130_000, 120_000))
    }

    @Test fun unknownDurationAndOverflowDoNotProduceInvalidPositions() {
        assertEquals(0L, PlaybackPosition.skip30(100, -1, true))
        assertEquals(Long.MAX_VALUE, PlaybackPosition.skip30(Long.MAX_VALUE - 10, Long.MAX_VALUE, true))
    }
}
