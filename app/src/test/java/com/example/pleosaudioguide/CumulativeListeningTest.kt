package com.example.pleosaudioguide

import org.junit.Assert.*
import org.junit.Test

class CumulativeListeningTest {
    private fun record(id: String = "a", start: Long = 0, end: Long = 0, script: String = "대본") =
        ListeningRecord(id, id, "역사", script, null, "audio", 100_000,
            if (end > start) listOf(HeardRange(start, end)) else emptyList(), lastListenedAt = 1)

    @Test fun eightyPercentBoundaryAndRepeatedRangesDoNotInflateCompletion() {
        assertTrue(record(end = 80_000).completionEligible)
        assertFalse(record(end = 79_999).completionEligible)
        assertFalse(record(start = 80_000, end = 100_000).completionEligible)
        val duplicate = record(end = 40_000).copy(ranges = List(3) { HeardRange(0, 40_000) })
        assertFalse(duplicate.completionEligible)
        assertFalse(record(end = 80_000).copy(script = "").canUseFullScriptForQuiz)
    }
    @Test fun preDriveAndEarlierTripsMergeButUnrelatedVersionsStayOut() {
        val current = record(start = 5_000, end = 80_000)
        val merged = CumulativeListening.forTrip(listOf(current), listOf(
            record(end = 5_000), current, record(id = "other", end = 100_000),
            record(start = 80_000, end = 100_000, script = "다른 버전")))
        assertEquals(1, merged.size)
        assertEquals(80_000L, merged.single().listenedMs)
        assertTrue(merged.single().completionEligible)
        val repeated = CumulativeListening.forTrip(listOf(record(end = 40_000)),
            listOf(record(end = 40_000)))
        assertFalse(repeated.single().completionEligible)
    }
}
