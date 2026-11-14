package com.example.pleosaudioguide

import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

/** LLM/음성으로 생성한 내용이 아닌, 검증 전용 대본과 문제. */
class QuizTest {
    private val record = ListeningRecord("a", "불국사", "역사",
        "불국사는 경주에 있는 사찰입니다. 석가탑과 다보탑이 있습니다.", null, "tts",
        narrationCompleted = true, lastListenedAt = 1)
    private val sources = QuizRules.sources(listOf(record))
    private fun questions(count: Int) = (1..count).map {
        QuizQuestion(sources.single().id, "위치에 관한 검증 질문 $it", listOf("경주", "서울", "부산"),
            0, "대본에 경주라고 나옵니다.", record.script)
    }
    private fun trip() = TripHistoryState(endedTrip = TripEntity("trip", 0, 1000),
        records = listOf(record), parked = true, browsingAllowed = true)
    private class Memory : QuizStorage {
        val data = mutableMapOf<String, QuizAttempt>()
        override fun loadQuiz(tripId: String, count: Int) = CompletableFuture.completedFuture(data["$tripId:$count"])
        override fun saveQuiz(attempt: QuizAttempt): CompletableFuture<Unit> {
            data["${attempt.tripId}:${attempt.count}"] = attempt
            return CompletableFuture.completedFuture(Unit)
        }
    }

    @Test fun codecPreservesAnswersAndPreventsChangingCheckedAnswer() {
        val a = QuizAttempt("trip", 3, sources, questions(3)).select(0).check()
        assertEquals(a, a.select(2))
        assertEquals(a, QuizJson.decode(QuizJson.encode(a)))
        assertEquals(1, a.score)
        assertEquals(1, a.next().index)
    }
    @Test fun evidenceSourceCountAndDuplicateQuestionsAreValidated() {
        val valid = questions(3)
        QuizRules.validate(valid, 3, sources)
        assertTrue(runCatching { QuizRules.validate(valid.take(2), 3, sources) }.isFailure)
        assertTrue(runCatching { QuizRules.validate(List(3) { valid.first() }, 3, sources) }.isFailure)
        assertTrue(runCatching { QuizRules.validate(valid.map { it.copy(sourceId = "other-trip") }, 3, sources) }.isFailure)
        assertTrue(runCatching { QuizRules.validate(valid.map { it.copy(evidence = "대본에 존재하지 않는 거짓 근거입니다.") }, 3, sources) }.isFailure)
        assertTrue(runCatching { QuizRules.validate(valid.map { it.copy(correctIndex = 3) }, 3, sources) }.isFailure)
    }
    @Test fun lightEvidenceAllowsParaphrasedOptionsShortQuotesAndFormattingDifferences() {
        val q = questions(1).single().copy(options = listOf("경주시", "서울", "부산"),
            evidence = "경주")
        QuizRules.validate(listOf(q), 1, sources)
        QuizRules.validate(listOf(q.copy(evidence = "불국사는  경주에 있는 사찰입니다!")), 1, sources)
        assertTrue(runCatching { QuizRules.validate(listOf(q.copy(evidence = "!!!")), 1, sources) }.isFailure)
        assertTrue(runCatching { QuizRules.validate(listOf(q.copy(evidence = "제주")), 1, sources) }.isFailure)
        val numeric = listOf(sources.single().copy(script = "거리는 1.5km이고 기온은 -5도입니다."))
        assertTrue(runCatching { QuizRules.validate(listOf(q.copy(evidence = "15km")), 1, numeric) }.isFailure)

    }

    @Test fun partialOrEmptyScriptIsNotSentAndVersionIsStable() {
        assertTrue(QuizRules.sources(listOf(record.copy(narrationCompleted = false))).isEmpty())
        assertTrue(QuizRules.sources(listOf(record.copy(script = " <p> </p> "))).isEmpty())
        assertEquals(1, QuizRules.sources(listOf(record, record)).size)
        assertEquals(sources, QuizRules.sources(listOf(record)))
    }
    @Test fun recommendationsAndTimeUseRealTripDuration() {
        assertEquals(3, QuizRules.recommended(TripEntity("t", 0, 1000)))
        assertEquals(7, QuizRules.recommended(TripEntity("t", 0, 15 * 60_000)))
        assertEquals(10, QuizRules.recommended(TripEntity("t", 0, 30 * 60_000)))
        assertEquals(90, QuizRules.estimatedSeconds(3))
        assertEquals(210, QuizRules.estimatedSeconds(7))
        assertEquals(300, QuizRules.estimatedSeconds(10))
    }
    @Test fun preparationAcceptsOneOrTwoQuestionsAndDoesNotRegenerateForSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val storage = Memory()
        var calls = 0
        val response = CompletableDeferred<List<QuizQuestion>>()
        try {
            val coordinator = QuizCoordinator(scope, MutableStateFlow(trip()), storage,
                QuizGenerator { _, max, _ -> assertEquals(10, max); calls++; response.await() })
            coordinator.openSetup()
            assertEquals(QuizPage.PREPARING, coordinator.state.value.page)
            assertTrue(coordinator.state.value.loading)
            coordinator.openSetup()
            assertEquals(1, calls)
            response.complete(questions(1))
            yield()
            assertEquals(QuizPage.SETUP, coordinator.state.value.page)
            assertEquals(1, coordinator.state.value.pool!!.count)
            assertEquals(listOf(true, false, false), QuizRules.choices(1).map { it.enabled })
            coordinator.selectCount(5)
            assertEquals(1, coordinator.state.value.count)
            coordinator.start()
            assertEquals(QuizPage.QUESTION, coordinator.state.value.page)
            assertEquals(1, coordinator.state.value.attempt!!.count)
            assertEquals(1, calls)
            assertEquals(listOf(2, 5, 7), QuizRules.choices(7).map { it.count })
            assertEquals(listOf(true, true, false), QuizRules.choices(3).map { it.enabled })
        } finally { scope.cancel() }
    }

    @Test fun restoredProgressReusesSavedQuizWithoutAnotherGeneration() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val trips = MutableStateFlow(trip())
        val storage = Memory()
        var calls = 0
        val generator = QuizGenerator { _, n, _ -> calls++; questions(n) }
        try {
            val first = QuizCoordinator(scope, trips, storage, generator)
            first.openSetup(); first.start()
            first.selectAnswer(0); first.checkAnswer(); first.next()
            assertEquals(1, first.state.value.attempt!!.index)
            val restored = QuizCoordinator(scope, trips, storage, generator)
            restored.openSetup(); restored.start()
            assertEquals(1, calls)
            assertEquals(1, restored.state.value.attempt!!.index)
            assertTrue(restored.state.value.attempt!!.checked[0])
        } finally { scope.cancel() }
    }
    @Test fun drivingCancelsPendingGenerationAndLocksAnswerChanges() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val trips = MutableStateFlow(trip())
        val storage = Memory()
        val response = CompletableDeferred<List<QuizQuestion>>()
        try {
            val coordinator = QuizCoordinator(scope, trips, storage, QuizGenerator { _, _, _ -> response.await() })
            coordinator.openSetup(); coordinator.start()
            assertTrue(coordinator.state.value.loading)
            trips.value = trips.value.copy(parked = false, browsingAllowed = false)
            response.complete(questions(3))
            yield()
            assertFalse(coordinator.state.value.loading)
            assertNull(coordinator.state.value.attempt)
            assertTrue(storage.data.isEmpty())
            coordinator.selectCount(10)
            assertEquals(2, coordinator.state.value.count)
        } finally { scope.cancel() }
    }
    @Test fun missingSourceNeverCallsLlmAndWrongTripCacheIsRejected() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val trips = MutableStateFlow(trip().copy(records = emptyList()))
        val storage = Memory()
        var calls = 0
        try {
            val coordinator = QuizCoordinator(scope, trips, storage, QuizGenerator { _, n, _ -> calls++; questions(n) })
            coordinator.openSetup(); coordinator.start()
            assertEquals(0, calls)
            assertNotNull(coordinator.state.value.error)
            trips.value = trip()
            storage.data["trip:3"] = QuizAttempt("wrong-trip", 3, sources, questions(3))
            coordinator.openSetup()
            assertNull(coordinator.state.value.attempt)
            assertNotNull(coordinator.state.value.error)
        } finally { scope.cancel() }
    }
}
