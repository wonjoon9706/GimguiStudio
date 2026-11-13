package com.example.pleosaudioguide

import org.junit.Assert.*
import org.junit.Test

class ListeningRecordTest {
    private val content = ContentCatalog.all.first().copy(audioUri = "https://example.org/audio")
    private fun record(script: String = content.script) = ListeningRecord(content.id, content.title,
        content.category, script, null, "audio", 60_000, lastListenedAt = 123)

    @Test fun overlapDoesNotDoubleCountAndGapsArePreserved() {
        val r = record().mergeRange(HeardRange(0, 10_000))
            .mergeRange(HeardRange(5_000, 20_000)).mergeRange(HeardRange(40_000, 60_000))
        assertEquals(40_000L, r.listenedMs)
        assertEquals(2, r.ranges.size)
        assertFalse(r.canUseFullScriptForQuiz)
        assertTrue(r.mergeRange(HeardRange(20_000, 40_000)).canUseFullScriptForQuiz)
    }

    @Test fun jsonRoundTripKeepsScriptRangesAndTtsCompletion() {
        val audio = record().mergeRange(HeardRange(1000, 5000))
        val tts = record().copy(mode = "tts", durationMs = 0, narrationCompleted = true)
        assertEquals(listOf(audio, tts), ListeningHistoryJson.decode(ListeningHistoryJson.encode(listOf(audio, tts))))
        assertFalse(tts.copy(script = "").canUseFullScriptForQuiz)
        assertTrue(tts.canUseFullScriptForQuiz)
    }

    @Test fun unknownVersionAndBrokenJsonAreRejected() {
        assertTrue(runCatching { ListeningHistoryJson.decode("{") }.isFailure)
        assertTrue(runCatching { ListeningHistoryJson.decode("""{"version":99,"records":[]}""") }.isFailure)
    }

    @Test fun trackerExcludesSeekPausedTimeAndUnplayedItems() {
        val heard = mutableListOf<HeardRange>()
        val tracker = ListeningTracker { _, range, _ -> heard.add(range) }
        tracker.sample(content, 0, 60_000, true, 0)
        tracker.sample(content, 5000, 60_000, true, 5000)
        // 시크 직전 위치 기록 후 새 위치를 기준점으로 사용.
        tracker.sample(content, 5100, 60_000, false, 5100)
        tracker.reset()
        tracker.sample(content, 35_100, 60_000, true, 5100)
        tracker.sample(content, 40_100, 60_000, false, 10_100)
        tracker.sample(content, 40_100, 60_000, false, 20_100)
        tracker.sample(content.copy(id = "unplayed"), 0, 60_000, false, 20_100)
        assertEquals(listOf(HeardRange(0, 5000), HeardRange(5000, 5100),
            HeardRange(35_100, 40_100)), heard)
    }

    @Test fun completionAllowsOnlySmallEventBoundaryErrorsAndCountsOnlyFinishedItems() {
        val complete = record().copy(ranges = listOf(HeardRange(30, 30_000), HeardRange(30_040, 59_950)))
        assertTrue(complete.fullScriptHeard)
        val partial = record().copy(contentId = "partial", ranges = listOf(HeardRange(0, 18_000)))
        val skipped = record().copy(ranges = listOf(HeardRange(0, 20_000), HeardRange(21_000, 60_000)))
        assertFalse(partial.fullScriptHeard)
        assertFalse(skipped.fullScriptHeard)
        assertFalse(record().copy(ranges = listOf(HeardRange(0, 59_000))).fullScriptHeard)
        val state = TripHistoryState(records = listOf(complete, complete, partial))
        assertEquals(2, state.listenedContentCount)
        assertEquals(1, state.completedContentCount)
        assertEquals(1, state.quizContentCount)
        assertFalse(complete.copy(script = "").canUseFullScriptForQuiz)
        assertFalse(record().copy(mode = "tts", narrationCompleted = false).fullScriptHeard)
    }

    @Test fun unexpectedPositionJumpIsNotHeard() {
        val heard = mutableListOf<HeardRange>()
        val tracker = ListeningTracker { _, range, _ -> heard.add(range) }
        tracker.sample(content, 0, 60_000, true, 0)
        tracker.sample(content, 30_000, 60_000, true, 100)
        assertTrue(heard.isEmpty())
    }
}
