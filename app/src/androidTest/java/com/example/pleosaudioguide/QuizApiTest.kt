package com.example.pleosaudioguide

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 기기 내부 임시 HTTP 서버로 앱 요청/응답을 검증한다. 실제 LLM/서버 키를 사용하지 않는다. */
@RunWith(AndroidJUnit4::class)
class QuizApiTest {
    private val source = QuizSource("test-source", "불국사", "불국사는 경주에 있는 사찰입니다.")
    private val questions = (1..3).map {
        QuizQuestion(source.id, "위치 검증 질문 $it", listOf("경주", "서울", "부산"), 0,
            "대본에서 경주라고 나옵니다.", source.script)
    }

    private fun fixture(status: Int, body: String, action: (QuizApi) -> Unit): String {
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        socket.soTimeout = 10000
        val worker = Executors.newSingleThreadExecutor()
        val request = worker.submit<String> {
            socket.accept().use { client ->
                client.soTimeout = 10000
                val input = BufferedInputStream(client.getInputStream())
                val header = ByteArrayOutputStream()
                while (true) {
                    val byte = input.read()
                    check(byte >= 0)
                    header.write(byte)
                    val bytes = header.toByteArray()
                    if (bytes.size >= 4 && bytes.takeLast(4) == listOf(13.toByte(), 10.toByte(), 13.toByte(), 10.toByte())) break
                    check(bytes.size < 16384)
                }
                val text = header.toString("US-ASCII")
                val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE).find(text)!!.groupValues[1].toInt()
                val bytes = ByteArray(length)
                DataInputStream(input).readFully(bytes)
                val response = body.toByteArray(Charsets.UTF_8)
                client.getOutputStream().use {
                    it.write(("HTTP/1.1 $status Response\r\nContent-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${response.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                    it.write(response)
                    it.flush()
                }
                String(bytes, Charsets.UTF_8)
            }
        }
        try {
            action(QuizApi("http://127.0.0.1:${socket.localPort}/quiz"))
            return request.get(5, TimeUnit.SECONDS)
        } finally { socket.close(); worker.shutdownNow() }
    }

    @Test fun deviceHttpRequestAndResponseKeepKoreanSourcesAndValidateEvidence() {
        val body = JSONObject().put("tripId", "test-trip").put("count", 3)
            .put("questions", JSONArray(questions.map(QuizJson::question))).toString()
        val received = fixture(200, body) { api ->
            val result = runBlocking { api.generate("test-trip", 10, listOf(source)) }
            assertEquals(questions, result)
        }
        val request = JSONObject(received)
        assertEquals("test-trip", request.getString("tripId"))
        assertEquals(10, request.getInt("count"))
        assertEquals(source.script, request.getJSONArray("sources").getJSONObject(0).getString("script"))
    }

    @Test fun unconfiguredServerShowsFailureInsteadOfFallbackQuestions() {
        fixture(503, """{"error":"SERVER_NOT_CONFIGURED"}""") { api ->
            val error = runBlocking { runCatching { api.generate("test-trip", 3, listOf(source)) }.exceptionOrNull() }
            assertNotNull(error)
            assertEquals("퀴즈 생성 서버 설정을 확인해 주세요.", error!!.message)
        }
    }
}
