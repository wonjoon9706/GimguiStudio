package com.example.pleosaudioguide

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.room.Room
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 저장 경계는 유지하고 구현을 Room으로 교체. DB 작업과 차량 이벤트를 단일 큐에서 순서대로 처리한다. */
class ListeningHistoryStore internal constructor(
    private val database: ListeningDatabase,
    private val legacyFile: File? = null
) : QuizStorage {
    private val worker = Executors.newSingleThreadExecutor()
    private val dao get() = database.history()
    private var detector = TripDetection()
    private var reading = VehicleReading()
    private var active: TripEntity? = null
    private var monitor: VehicleTripMonitor? = null
    private val checkpoints = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addCheckpoint(checkpoint: () -> Unit) { checkpoints.add(checkpoint) }
    fun removeCheckpoint(checkpoint: () -> Unit) { checkpoints.remove(checkpoint) }
    /** 호출자는 플레이어 main 스레드. DB 조회 앞에 현재 위치를 직렬 저장한다. */
    fun checkpointPlayback() { checkpoints.forEach { it() } }

    fun savePlaybackResume(value: PlaybackResume) = submit {
        if (value.content.audioUri == null) return@submit
        val payload = value.encode()
        dao.saveMeta(HistoryMeta("playback:" + value.content.id, payload))
        dao.saveMeta(HistoryMeta("playback:latest", payload))
    }
    fun playbackResume(content: AudioContent): CompletableFuture<PlaybackResume?> = query {
        dao.meta("playback:" + content.id)?.let(PlaybackResume::decode)?.takeIf {
            it.content.audioUri == content.audioUri && it.content.script == content.script
        }
    }
    fun latestPlaybackResume(): CompletableFuture<PlaybackResume?> = query {
        dao.meta("playback:latest")?.let(PlaybackResume::decode)
    }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val ttsStarts = mutableMapOf<String, String>()
    private val mutableState = MutableStateFlow(TripHistoryState())
    val state = mutableState.asStateFlow()
    @Volatile var error: String? = null
        private set

    init {
        submit {
            migrateJson()
            active = dao.activeTrip()
            detector = TripDetection(active != null)
            publish()
        }
    }

    private fun migrateJson() {
        if (dao.meta("json_imported") != null) return
        val imported = try {
            legacyFile?.let {
                try { ListeningHistoryJson.decode(AtomicFile(it).openRead().bufferedReader().use { r -> r.readText() }) }
                catch (_: java.io.FileNotFoundException) { emptyList() }
            }.orEmpty()
        } catch (e: Exception) {
            report(e) // 보존 후 다음 앱 실행에 재시도. 신규 Room 기록은 계속 저장한다.
            return
        }
        database.runInTransaction {
            dao.insertTrip(TripEntity(LEGACY, 0, 0, "legacy", true))
            imported.forEach { save(LEGACY, it) }
            dao.saveMeta(HistoryMeta("json_imported", "1"))
        }
        // 원본 JSON은 삭제하지 않는다. import marker로 중복 이관을 막는다.
    }

    fun updateVehicle(value: VehicleReading, elapsedMs: Long) = submit {
        reading = value
        when (detector.update(value, elapsedMs)) {
            TripDetection.Action.START -> {
                active = TripEntity(UUID.randomUUID().toString(), System.currentTimeMillis())
                dao.insertTrip(active!!)
            }
            TripDetection.Action.END -> {
                active?.let {
                    val ended = it.copy(endedAt = System.currentTimeMillis())
                    database.runInTransaction {
                        dao.updateTrip(ended)
                        dao.saveMeta(HistoryMeta("trip-completion:v2:" + it.id,
                            ListeningHistoryJson.encode(CumulativeListening.forTrip(
                                decode(dao.records(it.id)), decode(dao.allRecords())))))
                    }
                }
                active = null
            }
            TripDetection.Action.NONE -> Unit
        }
        publish()
    }

    fun dismissEndScreen() = submit {
        dao.latestEndedTrip()?.let { dao.updateTrip(it.copy(dismissed = true)) }
        publish()
    }

    fun record(content: AudioContent, range: HeardRange? = null, durationMs: Long = 0,
        narrationCompleted: Boolean = false) = submit {
        val currentTripId = active?.id ?: UNASSIGNED
        val isTts = content.audioUri == null
        if (isTts && !narrationCompleted) ttsStarts[content.id] = currentTripId
        val tripId = if (isTts && narrationCompleted) ttsStarts.remove(content.id) ?: currentTripId else currentTripId
        val completedWithinTrip = narrationCompleted && tripId == currentTripId
        dao.insertTrip(TripEntity(UNASSIGNED, 0, 0, "unassigned", true))
        val mode = if (content.audioUri == null) "tts" else "audio"
        val key = recordKey(tripId, content.id, content.script, mode)
        val old = dao.record(key)?.let { ListeningHistoryJson.decode(it.payload).single() }
        var next = (old ?: ListeningRecord(content.id, content.title, content.category,
            content.script, content.credit, mode, lastListenedAt = 0)).copy(
            title = content.title, credit = content.credit,
            durationMs = durationMs.takeIf { it > 0 } ?: old?.durationMs ?: 0,
            narrationCompleted = completedWithinTrip || old?.narrationCompleted == true,
            lastListenedAt = System.currentTimeMillis())
        if (range != null) next = next.mergeRange(range)
        save(tripId, next)
        publish()
    }

    private fun save(tripId: String, record: ListeningRecord) {
        dao.save(ListeningEntity(recordKey(tripId, record.contentId, record.script, record.mode),
            tripId, ListeningHistoryJson.encode(listOf(record))))
    }

    /** 운행 ID 지정 시 해당 운행만 반환. 생략 시 이전 API와 같이 전체 기록을 반환한다. */
    fun snapshot(tripId: String? = null): CompletableFuture<List<ListeningRecord>> = query {
        decode(if (tripId == null) dao.allRecords() else dao.records(tripId))
    }
    internal fun trips(): CompletableFuture<List<TripEntity>> = query { dao.trips() }

    override fun loadQuiz(tripId: String, count: Int): CompletableFuture<QuizAttempt?> = query {
        dao.quiz("$tripId:$count")?.let {
            QuizJson.decode(it.payload).also { attempt -> require(attempt.tripId == tripId && attempt.count == count) }
        }
    }
    override fun saveQuiz(attempt: QuizAttempt): CompletableFuture<Unit> = query {
        val trip = dao.trip(attempt.tripId)
        require(trip?.kind == "drive" && trip.endedAt != null)
        QuizRules.validate(attempt.questions, attempt.count, attempt.sources)
        dao.saveQuiz(QuizAttemptEntity("${attempt.tripId}:${attempt.count}", attempt.tripId,
            attempt.count, QuizJson.encode(attempt), System.currentTimeMillis()))
    }
    fun reopenLatestQuiz(): CompletableFuture<Boolean> = query {
        val trip = dao.latestEndedTrip()
        if (reading.parked && active == null && trip != null) {
            dao.updateTrip(trip.copy(dismissed = false))
            publish()
            true
        } else false
    }

    private fun decode(rows: List<ListeningEntity>) = rows.flatMap { ListeningHistoryJson.decode(it.payload) }
    private fun publish() {
        val ended = dao.latestEndedTrip()
        val completionRecords = ended?.let { trip ->
            val key = "trip-completion:v2:" + trip.id
            dao.meta(key)?.let(ListeningHistoryJson::decode) ?: run {
                // 기존 운행은 확인 가능한 종료 시점 이전 기록으로 한 번만 계산해 고정한다.
                val records = CumulativeListening.forTrip(decode(dao.records(trip.id)),
                    decode(dao.allRecords()).filter { it.lastListenedAt <= (trip.endedAt ?: 0) })
                dao.saveMeta(HistoryMeta(key, ListeningHistoryJson.encode(records)))
                records
            }
        }.orEmpty()
        mutableState.value = TripHistoryState(active?.id, ended,
            completionRecords, reading.parked, reading.error ?: error,
            browsingAllowed = reading.parked, maxTitleLength = reading.maxStringLength.coerceIn(0, 80),
            permissionPromptAllowed = reading.uxAllowed)
    }

    private fun submit(action: () -> Unit) {
        worker.execute {
            try { action() } catch (e: Exception) {
                report(e)
                mutableState.value = mutableState.value.copy(parked = false, browsingAllowed = false, permissionPromptAllowed = false, error = error)
            }
        }
    }
    private fun <T> query(action: () -> T) = CompletableFuture<T>().also { result ->
        worker.execute {
            try { result.complete(action()) } catch (e: Exception) { report(e); result.completeExceptionally(e) }
        }
    }
    private fun report(e: Exception) {
        error = "청취 기록 저장/복원 실패: ${e.javaClass.simpleName}"
        Log.e("ListeningHistory", error, e)
    }
    internal fun close(): CompletableFuture<Unit> {
        monitor?.close()
        val completion = query { database.close() }
        worker.shutdown()
        return completion
    }

    companion object {
        const val LEGACY = "legacy-json"
        const val UNASSIGNED = "outside-trip"
        @Volatile private var instance: ListeningHistoryStore? = null
        fun get(context: Context): ListeningHistoryStore = instance ?: synchronized(this) {
            instance ?: ListeningHistoryStore(
                Room.databaseBuilder(context.applicationContext, ListeningDatabase::class.java, "listening.db").build(),
                File(context.applicationContext.filesDir, "listening_history.json")
            ).also {
                instance = it
                if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE))
                    it.monitor = VehicleTripMonitor(context.applicationContext) { value, elapsed ->
                    it.main.post {
                        // 세션 경계 전까지 재생한 구간은 이전 세션에 먼저 저장한다.
                        it.checkpoints.forEach { checkpoint -> checkpoint() }
                        it.updateVehicle(value, elapsed)
                    }
                }
            }
        }
        private fun recordKey(tripId: String, id: String, script: String, mode: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(org.json.JSONArray(listOf(tripId, id, script, mode)).toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
