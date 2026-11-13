package com.example.pleosaudioguide

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.ui.QuizScreen
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 테스트 전용 생성기. 실제 LLM 호출 성공을 주장하는 테스트가 아니다. */
class QuizFlowTest {
    @get:Rule val compose = createComposeRule()
    private val record = ListeningRecord("test-audio", "불국사 검증 대본", "역사",
        "불국사는 경주에 있는 사찰입니다.", null, "tts", narrationCompleted = true, lastListenedAt = 1)
    private val trip = TripHistoryState(endedTrip = TripEntity("test-trip", 0, 1000),
        records = listOf(record), parked = true, browsingAllowed = true)
    private class Memory : QuizStorage {
        var value: QuizAttempt? = null
        override fun loadQuiz(tripId: String, count: Int) = CompletableFuture.completedFuture(value?.takeIf { it.count == count })
        override fun saveQuiz(attempt: QuizAttempt): CompletableFuture<Unit> {
            value = attempt
            return CompletableFuture.completedFuture(Unit)
        }
    }
    private fun setup(scope: CoroutineScope, storage: Memory = Memory()): QuizCoordinator {
        val c = QuizCoordinator(scope, MutableStateFlow(trip), storage,
            QuizGenerator { _, count, sources -> (1..count).map {
                QuizQuestion(sources.single().id, "검증 전용 위치 질문 $it", listOf("경주", "서울", "부산"),
                    0, "대본에서 경주라고 설명합니다.", record.script)
            } })
        compose.setContent {
            val state by c.state.collectAsState()
            MaterialTheme {
                QuizScreen(trip, state, c::selectCount, c::start, c::selectAnswer, c::checkAnswer,
                    c::next, c::retrySave, c::backToSetup, c::dismiss, Modifier.requiredSize(320.dp, 600.dp))
            }
        }
        compose.runOnIdle { c.openSetup() }
        return c
    }

    @Test fun numberSelectionUpdatesEstimatedTime() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            setup(scope)
            compose.onNodeWithText("충분히\n5문제").performScrollTo().performClick()
            compose.onNodeWithText("5문제 · 약 2분 30초 예상").assertExists()
            compose.onNodeWithText("깊게\n10문제").performScrollTo().performClick()
            compose.onNodeWithText("10문제 · 약 5분 예상").assertExists()
        } finally { scope.cancel() }
    }

    @Test fun selectedAnswersAreGradedOnlyAfterCheckAndResultIsSaved() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val storage = Memory()
        try {
            setup(scope, storage)
            compose.onNodeWithText("지금 퀴즈 풀기").performScrollTo().performClick()
            repeat(2) { index ->
                compose.onNodeWithText("${index + 1} / 2 문제").assertExists()
                compose.onNodeWithText("정답 확인").performScrollTo().assertIsNotEnabled()
                compose.onNodeWithText(if (index == 1) "B  서울" else "A  경주").performScrollTo().performClick()
                compose.onNodeWithText("대본 속 정답 근거").assertDoesNotExist()
                compose.onNodeWithText("정답 확인").performScrollTo().performClick()
                compose.onNodeWithText("대본 속 정답 근거").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("A  경주").assertIsNotEnabled()
                compose.onNodeWithText(if (index == 1) "결과 보기" else "다음 문제").performScrollTo().performClick()
            }
            compose.onNodeWithText("2문제 중 1문제 정답").assertExists()
            compose.runOnIdle { assertTrue(storage.value!!.finished); assertEquals(1, storage.value!!.score) }
        } finally { scope.cancel() }
    }
}
