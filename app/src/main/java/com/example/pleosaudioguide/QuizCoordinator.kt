package com.example.pleosaudioguide

import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface QuizStorage {
    fun loadQuiz(tripId: String, count: Int): CompletableFuture<QuizAttempt?>
    fun saveQuiz(attempt: QuizAttempt): CompletableFuture<Unit>
}

suspend fun <T> CompletableFuture<T>.awaitQuiz(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { result, error ->
        if (continuation.isActive) {
            if (error == null) continuation.resume(result) else continuation.resumeWithException(error.cause ?: error)
        }
    }
}

enum class QuizPage { SUMMARY, PREPARING, SETUP, QUESTION, RESULT }
data class QuizUiState(
    val page: QuizPage = QuizPage.SUMMARY, val tripId: String? = null, val count: Int = 2,
    val pool: QuizAttempt? = null,
    val attempt: QuizAttempt? = null, val loading: Boolean = false, val saving: Boolean = false,
    val error: String? = null
)

/** UI/LLM/저장소 경계. 모든 버튼과 응답 적용에서 동일 운행의 안전한 주차를 재확인한다. */
class QuizCoordinator(
    private val scope: CoroutineScope,
    private val trips: StateFlow<TripHistoryState>,
    private val storage: QuizStorage,
    private val generator: QuizGenerator
) {
    private val mutableState = MutableStateFlow(QuizUiState())
    val state = mutableState.asStateFlow()
    private var generation: Job? = null
    private var serial = 0

    init {
        scope.launch {
            trips.collect { trip ->
                val bound = mutableState.value.tripId
                if (bound != null && trip.endedTrip?.id != bound) {
                    generation?.cancel(); serial++
                    mutableState.value = QuizUiState()
                } else if (!trip.showEndScreen && mutableState.value.loading) {
                    generation?.cancel(); serial++
                    mutableState.value = mutableState.value.copy(loading = false,
                        error = "주차 상태가 확인되면 다시 시작해 주세요.")
                }
            }
        }
    }

    private fun safe(): Boolean = trips.value.showEndScreen &&
        trips.value.endedTrip?.id == mutableState.value.tripId

    fun openSetup() {
        val trip = trips.value
        if (!trip.showEndScreen) return
        val ended = trip.endedTrip ?: return
        val old = mutableState.value
        if (old.loading) return
        mutableState.value = if (old.tripId == ended.id) old.copy(page = QuizPage.SETUP, error = null)
            else QuizUiState(QuizPage.PREPARING, ended.id)
        if (mutableState.value.pool == null) prepare()
    }

    private fun prepare() {
        if (!safe() || mutableState.value.loading) return
        val tripId = mutableState.value.tripId ?: return
        val sources = QuizRules.sources(trips.value.records)
        if (sources.isEmpty()) {
            mutableState.value = mutableState.value.copy(error = "누적 80% 이상 청취한 대본이 없어요.")
            return
        }
        val request = ++serial
        mutableState.value = mutableState.value.copy(page = QuizPage.PREPARING, loading = true, error = null)
        generation = scope.launch {
            try {
                var pool: QuizAttempt? = null
                for (n in 10 downTo 1) {
                    val cached = storage.loadQuiz(tripId, n).awaitQuiz() ?: continue
                    require(cached.tripId == tripId && cached.count == n)
                    QuizRules.validate(cached.questions, n, sources)
                    pool = cached
                    break
                }
                if (pool == null) {
                    val questions = generator.generate(tripId, 10, sources)
                    ensureActive()
                    QuizRules.validate(questions, questions.size, sources)
                    pool = QuizAttempt(tripId, questions.size, sources, questions)
                    if (!safe() || request != serial) return@launch
                    storage.saveQuiz(pool).awaitQuiz()
                }
                ensureActive()
                if (!safe() || request != serial) return@launch
                mutableState.value = mutableState.value.copy(pool = pool, count = minOf(2, pool.count),
                    page = QuizPage.SETUP, loading = false)
            } catch (_: CancellationException) {
                // 주차 해제/화면 이탈 뒤 응답은 적용하지 않는다.
            } catch (e: Exception) {
                if (request == serial) mutableState.value = mutableState.value.copy(loading = false,
                    error = if (e is java.io.IOException) "퀴즈 서버 연결을 확인해 주세요."
                    else e.message?.takeIf { it.any { c -> c in '가'..'힣' } }?.take(180)
                        ?: "문제를 준비하지 못했어요. 다시 시도해 주세요.")
            }
        }
    }
    fun selectCount(count: Int) {
        if (!safe() || mutableState.value.loading || mutableState.value.saving ||
            QuizRules.choices(mutableState.value.pool?.count ?: 0).none { it.enabled && it.count == count }) return
        mutableState.value = mutableState.value.copy(count = count, attempt = null, error = null)
    }

    fun start() {
        if (!safe() || mutableState.value.loading || mutableState.value.saving) return
        val pool = mutableState.value.pool ?: run { prepare(); return }
        val tripId = mutableState.value.tripId ?: return
        val count = mutableState.value.count
        val sources = QuizRules.sources(trips.value.records)
        if (sources.isEmpty()) {
            mutableState.value = mutableState.value.copy(error = "누적 80% 이상 청취한 대본이 없어요.")
            return
        }
        val request = ++serial
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        generation = scope.launch {
            try {
                val cached = storage.loadQuiz(tripId, count).awaitQuiz()
                val attempt = if (cached != null) {
                    require(cached.tripId == tripId && cached.count == count)
                    QuizRules.validate(cached.questions, count, sources)
                    cached
                } else {
                    val questions = pool.questions.take(count)
                    ensureActive()
                    QuizRules.validate(questions, count, sources)
                    QuizAttempt(tripId, count, sources, questions)
                }
                if (!safe() || request != serial) return@launch
                storage.saveQuiz(attempt).awaitQuiz()
                ensureActive()
                if (!safe() || request != serial) return@launch
                mutableState.value = mutableState.value.copy(attempt = attempt, loading = false,
                    page = if (attempt.finished) QuizPage.RESULT else QuizPage.QUESTION)
            } catch (_: CancellationException) {
                // 주행 전환/화면 이탈의 이전 응답은 표시하지 않는다.
            } catch (e: Exception) {
                if (request == serial) mutableState.value = mutableState.value.copy(loading = false,
                    error = if (e is java.io.IOException) "퀴즈 서버 연결을 확인해 주세요."
                    else e.message?.takeIf { it.any { c -> c in '가'..'힣' } }?.take(180)
                        ?: "퀴즈 응답을 확인하지 못했어요. 다시 시도해 주세요.")
            }
        }
    }

    fun selectAnswer(option: Int) {
        val current = editableAttempt() ?: return
        persist(current.select(option))
    }
    fun checkAnswer() {
        val current = editableAttempt() ?: return
        if (current.answers[current.index] == null) return
        persist(current.check())
    }
    fun next() {
        val current = editableAttempt() ?: return
        if (!current.checked[current.index]) return
        if (current.finished) mutableState.value = mutableState.value.copy(page = QuizPage.RESULT)
        else persist(current.next())
    }
    fun retrySave() {
        if (!safe() || mutableState.value.saving) return
        mutableState.value.attempt?.let(::persist)
    }
    private fun editableAttempt(): QuizAttempt? =
        mutableState.value.takeIf { safe() && !it.saving && !it.loading && it.page == QuizPage.QUESTION }?.attempt

    private fun persist(attempt: QuizAttempt) {
        mutableState.value = mutableState.value.copy(attempt = attempt, saving = true, error = null)
        scope.launch {
            try {
                storage.saveQuiz(attempt).awaitQuiz()
                if (mutableState.value.attempt == attempt)
                    mutableState.value = mutableState.value.copy(saving = false)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (mutableState.value.attempt == attempt) mutableState.value = mutableState.value.copy(
                    saving = false, error = "진행 상황을 저장하지 못했어요. 다시 시도해 주세요.")
            }
        }
    }

    fun backToSetup() {
        if (!safe()) return
        generation?.cancel(); serial++
        mutableState.value = mutableState.value.copy(page = QuizPage.SETUP, loading = false)
    }
    fun dismiss() {
        generation?.cancel(); serial++
        mutableState.value = QuizUiState()
    }
}
