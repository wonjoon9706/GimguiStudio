package com.example.pleosaudioguide

import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

fun interface QuizGenerator {
    suspend fun generate(tripId: String, count: Int, sources: List<QuizSource>): List<QuizQuestion>
}

/** 앱에는 LLM 키를 두지 않는다. 명시적인 사용자 요청을 중계 서버로 보내며 취소 시 연결을 닫는다. */
class QuizApi(private val endpoint: String = BuildConfig.QUIZ_API_URL) : QuizGenerator {
    override suspend fun generate(tripId: String, count: Int, sources: List<QuizSource>): List<QuizQuestion> {
        require(count in QuizRules.counts && sources.isNotEmpty())
        require(sources.size <= 50 && sources.sumOf { it.script.length } <= 100_000) { "이번 운행의 대본이 한 번에 처리할 수 있는 양을 넘었어요." }
        val url = URL(endpoint)
        require(url.protocol == "https" || (BuildConfig.DEBUG && url.protocol == "http" &&
            url.host in listOf("10.0.2.2", "127.0.0.1", "localhost"))) { "퀴즈 서버 주소를 확인해 주세요." }
        val connection = url.openConnection() as HttpURLConnection
        return suspendCancellableCoroutine { continuation ->
            val worker = Thread({
                try {
                    connection.requestMethod = "POST"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 110_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (BuildConfig.QUIZ_ACCESS_TOKEN.isNotBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.QUIZ_ACCESS_TOKEN)
                    }
                    connection.outputStream.use { it.write(QuizJson.request(tripId, count, sources).toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        val message = when (status) {
                            422 -> "대본에서 근거가 확인되는 문제를 준비하지 못했어요. 다시 시도해 주세요."
                            429 -> "현재 생성 요청이 많아요. 잠시 후 다시 시도해 주세요."
                            503 -> "퀴즈 생성 서버 설정을 확인해 주세요."
                            else -> "퀴즈 생성에 실패했어요. 잠시 후 다시 시도해 주세요."
                        }
                        throw IllegalStateException(message)
                    }
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val chars = CharArray(512_001)
                        var size = 0
                        while (size < chars.size) {
                            val n = reader.read(chars, size, chars.size - size)
                            if (n < 0) break
                            size += n
                        }
                        require(size < chars.size) { "퀴즈 응답이 너무 커요." }
                        String(chars, 0, size)
                    }
                    val response = JSONObject(body)
                    val actual = response.getInt("count")
                    require(response.getString("tripId") == tripId && actual in 1..count)
                    val questions = QuizJson.parseQuestions(response.getJSONArray("questions"))
                    QuizRules.validate(questions, actual, sources)
                    continuation.resume(questions)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                } finally { connection.disconnect() }
            }, "quiz-api").apply { isDaemon = true }
            continuation.invokeOnCancellation { connection.disconnect(); worker.interrupt() }
            worker.start()
        }
    }
}
