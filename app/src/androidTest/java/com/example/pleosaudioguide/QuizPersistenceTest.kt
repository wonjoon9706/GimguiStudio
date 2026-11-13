package com.example.pleosaudioguide

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class QuizPersistenceTest {
    @Test fun roomV1MigratesWithoutLosingHistoryAndQuizProgressSurvivesReopen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = context.cacheDir.resolve("quiz-migration-${System.nanoTime()}.db")
        val record = ListeningRecord("preserved-audio", "이전 오디오", "역사",
            "불국사는 경주에 있는 사찰입니다.", null, "tts", narrationCompleted = true, lastListenedAt = 1)
        // 실제 내보낸 v1 스키마로 구버전 DB 생성. 프로덕션 DB에는 테스트 데이터를 넣지 않는다.
        val schema = JSONObject(instrumentation.context.assets.open(
            "com.example.pleosaudioguide.ListeningDatabase/1.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                raw.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                entity.optJSONArray("indices")?.let { indices ->
                    repeat(indices.length()) {
                        raw.execSQL(indices.getJSONObject(it).getString("createSql")
                            .replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                    }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { raw.execSQL(setup.getString(it)) }
            raw.insertOrThrow("trips", null, ContentValues().apply {
                put("id", "preserved-trip"); put("startedAt", 0L); put("endedAt", 1000L)
                put("kind", "drive"); put("dismissed", 1)
            })
            raw.insertOrThrow("listening", null, ContentValues().apply {
                put("key", "old-record"); put("tripId", "preserved-trip"); put("payload", ListeningHistoryJson.encode(listOf(record)))
            })
            raw.insertOrThrow("history_meta", null, ContentValues().apply {
                put("key", "json_imported"); put("value", "1")
            })
            raw.version = 1
        }
        fun open() = ListeningHistoryStore(Room.databaseBuilder(context, ListeningDatabase::class.java, file.absolutePath).build())
        val first = open()
        try {
            assertEquals(record, first.snapshot("preserved-trip").get(5, TimeUnit.SECONDS).single())
            val sources = QuizRules.sources(listOf(record))
            val questions = (1..3).map {
                QuizQuestion(sources.single().id, "검증 전용 질문 $it", listOf("경주", "서울", "부산"),
                    0, "대본에서 확인합니다.", record.script)
            }
            val attempt = QuizAttempt("preserved-trip", 3, sources, questions).select(0).check().next()
            first.saveQuiz(attempt).get(5, TimeUnit.SECONDS)
            first.close().get(5, TimeUnit.SECONDS)
            val restored = open()
            try {
                assertEquals(record, restored.snapshot("preserved-trip").get(5, TimeUnit.SECONDS).single())
                assertEquals(attempt, restored.loadQuiz("preserved-trip", 3).get(5, TimeUnit.SECONDS))
                assertNull(restored.loadQuiz("preserved-trip", 7).get(5, TimeUnit.SECONDS))
            } finally { restored.close().get(5, TimeUnit.SECONDS) }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
