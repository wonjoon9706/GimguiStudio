package com.example.pleosaudioguide

/** 이번 운행에 실제 참여한 콘텐츠만, 같은 대본/방식의 과거 구간과 중복 없이 합친다. */
object CumulativeListening {
    fun forTrip(current: List<ListeningRecord>, history: List<ListeningRecord>): List<ListeningRecord> =
        current.filter { it.listenedMs > 0 || it.mode == "tts" }.map { item ->
            val matching = history.filter {
                it.contentId == item.contentId && it.script == item.script && it.mode == item.mode
            }
            val duration = matching.filter { it.durationMs > 0 }.maxByOrNull { it.lastListenedAt }?.durationMs
                ?: item.durationMs
            val base = item.copy(durationMs = duration, ranges = emptyList(),
                narrationCompleted = matching.any { it.narrationCompleted } || item.narrationCompleted)
            (matching + item).flatMap { it.ranges }.fold(base) { acc, range -> acc.mergeRange(range) }
        }
}
