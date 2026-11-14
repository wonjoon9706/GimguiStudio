package com.example.pleosaudioguide

import java.time.Instant
import java.time.ZoneId

data class LibraryPotion(
    val id: String,
    val completedAt: Long,
    val drive: Boolean,
    val potionName: String,
    val records: List<ListeningRecord>,
    val quizzes: List<QuizAttempt>,
    val inferred: Boolean = false
) {
    val quizCompleted: Boolean get() = quizzes.any { it.finished }
    val label: String get() = records.map { it.category }.distinct().joinToString(" · ")
}
data class LibraryState(val potions: List<LibraryPotion> = emptyList(), val error: String? = null)

object LibraryRules {
    fun freeSession(time: Long): String = "free:" + Instant.ofEpochMilli(time)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    fun group(entries: List<LibraryCompletionEntity>, quizzes: List<QuizAttempt>): List<LibraryPotion> =
        entries.groupBy { it.sessionId }.map { (id, items) ->
            val records = items.flatMap { ListeningHistoryJson.decode(it.recordPayload) }
            val sourceIds = QuizRules.sources(records).map { it.id }.toSet()
            val related = quizzes.filter { q ->
                q.tripId == id && q.questions.any { it.sourceId in sourceIds }
            }
            // 생성용 전체 문제 캐시와 실제 풀이를 중복 목록으로 보여주지 않는다.
            val answered = related.filter { it.answers.any { answer -> answer != null } }
            LibraryPotion(id, items.minOf { it.completedAt }, items.first().tripId != null,
                items.first().potionName, records,
                if (answered.isNotEmpty()) answered else related.take(1), items.any { it.inferred })
        }.sortedByDescending { it.completedAt }
}
