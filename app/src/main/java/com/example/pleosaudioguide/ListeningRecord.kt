package com.example.pleosaudioguide

import org.json.JSONArray
import org.json.JSONObject

data class HeardRange(val startMs: Long, val endMs: Long)

data class ListeningRecord(
    val contentId: String,
    val title: String,
    val category: String,
    val script: String,
    val credit: String?,
    val mode: String,
    val durationMs: Long = 0,
    val ranges: List<HeardRange> = emptyList(),
    val narrationCompleted: Boolean = false,
    val lastListenedAt: Long
) {
    val listenedMs: Long get() = ranges.sumOf { it.endMs - it.startMs }
    // 대본에 타임코드가 없으므로 부분 청취에서 어떤 문장을 들었는지는 알 수 없다.
    val fullScriptHeard: Boolean get() {
        if (mode == "tts") return narrationCompleted
        if (durationMs <= 0 || ranges.isEmpty()) return false
        // 플레이어 이벤트 경계의 미세한 오차만 허용한다(전체 250ms/1% 이하).
        // 구간을 건너뛰거나 끝에 도달했다는 사실만으로 완료 처리하지 않는다.
        val normalized = ranges.map {
            HeardRange(it.startMs.coerceIn(0, durationMs), it.endMs.coerceIn(0, durationMs))
        }.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        if (normalized.isEmpty()) return false
        val gaps = mutableListOf(normalized.first().startMs)
        var end = normalized.first().endMs
        for (range in normalized.drop(1)) {
            gaps.add((range.startMs - end).coerceAtLeast(0))
            end = maxOf(end, range.endMs)
        }
        gaps.add((durationMs - end).coerceAtLeast(0))
        return gaps.all { it <= 100 } && gaps.sum() <= minOf(250L, durationMs / 100)
    }
    val completionEligible: Boolean get() {
        if (mode == "tts") return narrationCompleted
        if (durationMs <= 0) return false
        val merged = copy(ranges = emptyList())
        val heard = ranges.fold(merged) { acc, range ->
            val start = range.startMs.coerceIn(0, durationMs)
            val end = range.endMs.coerceIn(0, durationMs)
            if (end > start) acc.mergeRange(HeardRange(start, end)) else acc
        }.listenedMs
        return heard.toDouble() / durationMs >= 0.8
    }
    val canUseFullScriptForQuiz: Boolean get() = script.isNotBlank() && completionEligible

    fun mergeRange(range: HeardRange): ListeningRecord {
        val merged = mutableListOf<HeardRange>()
        (ranges + range).filter { it.startMs >= 0 && it.endMs > it.startMs }
            .sortedBy { it.startMs }.forEach {
                val last = merged.lastOrNull()
                if (last != null && it.startMs <= last.endMs)
                    merged[merged.lastIndex] = HeardRange(last.startMs, maxOf(last.endMs, it.endMs))
                else merged.add(it)
            }
        return copy(ranges = merged)
    }
}

/** 시크/항목 전환 경계에서는 이전 위치를 수집한 뒤 reset한다. 일시정지·버퍼링 시간은 제외한다. */
class ListeningTracker(private val record: (AudioContent, HeardRange, Long) -> Unit) {
    private var content: AudioContent? = null
    private var position = 0L
    private var clock = 0L
    private var playing = false

    fun reset() { content = null; playing = false }

    fun sample(item: AudioContent?, positionMs: Long, durationMs: Long, isPlaying: Boolean,
        elapsedMs: Long, speed: Float = 1f) {
        val previous = content
        val end = if (durationMs > 0) positionMs.coerceIn(0, durationMs) else positionMs.coerceAtLeast(0)
        val delta = end - position
        if (previous != null && previous.id == item?.id && previous.script == item.script &&
            playing && delta > 0 && delta <= (elapsedMs - clock).coerceAtLeast(0) * speed + 1000) {
            record(previous, HeardRange(position, end), durationMs)
        }
        content = item
        position = end
        clock = elapsedMs
        playing = isPlaying
    }
}

object ListeningHistoryJson {
    const val VERSION = 1

    fun encode(records: List<ListeningRecord>): String = JSONObject().put("version", VERSION)
        .put("records", JSONArray().apply {
            records.forEach { r -> put(JSONObject().put("contentId", r.contentId)
                .put("title", r.title).put("category", r.category).put("script", r.script)
                .put("credit", r.credit ?: JSONObject.NULL).put("mode", r.mode)
                .put("durationMs", r.durationMs).put("narrationCompleted", r.narrationCompleted)
                .put("lastListenedAt", r.lastListenedAt).put("ranges", JSONArray().apply {
                    r.ranges.forEach { put(JSONArray().put(it.startMs).put(it.endMs)) }
                })) }
        }).toString()

    fun decode(text: String): List<ListeningRecord> {
        val root = JSONObject(text)
        require(root.getInt("version") == VERSION) { "Unsupported listening history version" }
        val records = root.getJSONArray("records")
        return (0 until records.length()).map { index ->
            val r = records.getJSONObject(index)
            var record = ListeningRecord(r.getString("contentId"), r.getString("title"),
                r.getString("category"), r.getString("script"),
                if (r.isNull("credit")) null else r.getString("credit"), r.getString("mode"),
                r.getLong("durationMs"), narrationCompleted = r.getBoolean("narrationCompleted"),
                lastListenedAt = r.getLong("lastListenedAt"))
            val ranges = r.getJSONArray("ranges")
            repeat(ranges.length()) {
                val range = ranges.getJSONArray(it)
                val start = range.getLong(0)
                val end = range.getLong(1)
                require(start >= 0 && end > start)
                record = record.mergeRange(HeardRange(start, end))
            }
            record
        }
    }
}
