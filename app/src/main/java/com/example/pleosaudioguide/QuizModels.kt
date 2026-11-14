package com.example.pleosaudioguide

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class QuizSource(val id: String, val title: String, val script: String)
data class QuizQuestion(
    val sourceId: String, val prompt: String, val options: List<String>,
    val correctIndex: Int, val explanation: String, val evidence: String
)
data class QuizAttempt(
    val tripId: String, val count: Int, val sources: List<QuizSource>, val questions: List<QuizQuestion>,
    val answers: List<Int?> = List(count) { null }, val checked: List<Boolean> = List(count) { false },
    val index: Int = 0
) {
    val finished: Boolean get() = checked.all { it }
    val score: Int get() = questions.indices.count { checked[it] && answers[it] == questions[it].correctIndex }
    fun select(option: Int): QuizAttempt = if (finished || checked[index] || option !in 0..2) this else
        copy(answers = answers.mapIndexed { i, old -> if (i == index) option else old })
    fun check(): QuizAttempt = if (answers[index] == null) this else
        copy(checked = checked.mapIndexed { i, old -> old || i == index })
    fun next(): QuizAttempt = if (!checked[index] || index >= count - 1) this else copy(index = index + 1)
}

object QuizRules {
    val counts = (1..10).toList()
    data class Choice(val label: String, val count: Int, val enabled: Boolean)
    fun choices(total: Int) = listOf(
        Choice("가볍게", minOf(2, total), total > 0),
        Choice("충분히", minOf(5, total), total > 2),
        Choice("깊게", minOf(10, total), total > 5))

    fun recommended(trip: TripEntity): Int = when {
        (trip.endedAt ?: trip.startedAt) - trip.startedAt >= 30 * 60_000 -> 10
        (trip.endedAt ?: trip.startedAt) - trip.startedAt >= 15 * 60_000 -> 7
        else -> 3
    }
    fun estimatedSeconds(count: Int) = count * 30
    fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()
    private fun evidenceText(text: String): String {
        val compact = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
            .replace(Regex("[\\p{Z}\\s]"), "")
        return Regex("\\p{P}").replace(compact) { match ->
            val index = match.range.first
            val mark = match.value
            val before = compact.getOrNull(index - 1)?.isDigit() == true
            val after = compact.getOrNull(index + 1)?.isDigit() == true
            if ((mark == "-" && after) || (mark in listOf(".", ",", ":") && before && after)) mark else ""
        }
    }
    fun sources(records: List<ListeningRecord>): List<QuizSource> = records
        .filter { it.canUseFullScriptForQuiz }.sortedByDescending { it.lastListenedAt }
        .distinctBy { it.contentId }.mapNotNull {
            val script = normalize(it.script.replace(Regex("<[^>]+>"), " "))
            if (script.isBlank()) null else QuizSource(it.contentId + ":" +
                MessageDigest.getInstance("SHA-256").digest(script.toByteArray(Charsets.UTF_8))
                    .take(8).joinToString("") { b -> "%02x".format(b) }, it.title, script)
        }

    fun validate(questions: List<QuizQuestion>, count: Int, sources: List<QuizSource>) {
        require(count in counts && questions.size == count) { "준비된 문제 수를 확인하지 못했어요. 다시 준비해 주세요." }
        require(questions.map { normalize(it.prompt) }.distinct().size == count) { "중복 문제가 포함됐어요." }
        questions.forEach { q ->
            val source = sources.singleOrNull { it.id == q.sourceId }
                ?: throw IllegalArgumentException("이번 운행 밖의 문제가 포함됐어요.")
            require(q.prompt.isNotBlank() && q.prompt.length <= 400)
            require(q.options.size == 3 && q.options.all { it.isNotBlank() && it.length <= 300 } &&
                q.options.map(::normalize).distinct().size == 3 && q.correctIndex in 0..2)
            require(q.explanation.isNotBlank() && q.explanation.length <= 1500)
            val evidence = normalize(q.evidence)
            require(evidence.length in 2..600 && evidenceText(evidence).length >= 2 &&
                evidenceText(source.script).contains(evidenceText(evidence))) {
                "대본에서 정답 근거를 확인하지 못했어요."
            }
        }
    }
}

object QuizJson {
    fun source(s: QuizSource) = JSONObject().put("id", s.id).put("title", s.title).put("script", s.script)
    fun question(q: QuizQuestion) = JSONObject().put("sourceId", q.sourceId).put("prompt", q.prompt)
        .put("options", JSONArray(q.options)).put("correctIndex", q.correctIndex)
        .put("explanation", q.explanation).put("evidence", q.evidence)
    fun parseQuestions(array: JSONArray): List<QuizQuestion> = (0 until array.length()).map {
        val q = array.getJSONObject(it)
        val options = q.getJSONArray("options")
        require(q.get("correctIndex") is Number && q.getDouble("correctIndex") % 1 == 0.0)
        QuizQuestion(q.getString("sourceId"), q.getString("prompt"),
            (0 until options.length()).map(options::getString), q.getInt("correctIndex"),
            q.getString("explanation"), q.getString("evidence"))
    }
    fun request(tripId: String, count: Int, sources: List<QuizSource>) = JSONObject()
        .put("tripId", tripId).put("count", count).put("sources", JSONArray(sources.map(::source))).toString()
    fun encode(a: QuizAttempt): String = JSONObject().put("version", 1).put("tripId", a.tripId)
        .put("count", a.count).put("sources", JSONArray(a.sources.map(::source)))
        .put("questions", JSONArray(a.questions.map(::question))).put("answers", JSONArray(a.answers.map { it ?: JSONObject.NULL }))
        .put("checked", JSONArray(a.checked)).put("index", a.index).toString()
    fun decode(text: String): QuizAttempt {
        val json = JSONObject(text)
        require(json.getInt("version") == 1)
        val sources = json.getJSONArray("sources").let { array ->
            (0 until array.length()).map { array.getJSONObject(it).let { s ->
                QuizSource(s.getString("id"), s.getString("title"), s.getString("script"))
            } }
        }
        val count = json.getInt("count")
        val questions = parseQuestions(json.getJSONArray("questions"))
        QuizRules.validate(questions, count, sources)
        val answers = json.getJSONArray("answers")
        val checked = json.getJSONArray("checked")
        require(answers.length() == count && checked.length() == count)
        val result = QuizAttempt(json.getString("tripId"), count, sources, questions,
            (0 until count).map { if (answers.isNull(it)) null else answers.getInt(it).also { v -> require(v in 0..2) } },
            (0 until count).map(checked::getBoolean), json.getInt("index"))
        require(result.index in 0 until count)
        require((0 until count).all { !result.checked[it] || result.answers[it] != null })
        return result
    }
}
