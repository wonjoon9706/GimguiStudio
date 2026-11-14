package com.example.pleosaudioguide

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class LibraryStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val audio = ContentCatalog.all.first().copy(audioUri = "https://example.org/test")
    private fun <T> java.util.concurrent.CompletableFuture<T>.done() = get(5, TimeUnit.SECONDS)

    @Test fun firstEightyPercentOwnsFinalTripAndRandomArtworkSurvivesReplaysAndReopen() {
        val file = context.cacheDir.resolve("library-${System.nanoTime()}.db")
        var now = 1_700_000_000_000L
        fun open(names: List<String> = listOf("potion_test_a", "potion_test_b")) = ListeningHistoryStore(
            Room.databaseBuilder(context, ListeningDatabase::class.java, file.absolutePath).build(),
            potionNames = names, clock = { now })
        val store = open()
        store.record(audio, HeardRange(0, 40_000), 100_000)
        assertTrue(store.refreshLibrary().done().isEmpty())
        now += 86_400_000
        store.updateVehicle(VehicleReading(8, 5f), 0)
        store.record(audio, HeardRange(40_000, 80_000), 100_000)
        val first = store.refreshLibrary().done().single()
        assertTrue(first.drive)
        assertEquals(now, first.completedAt)
        assertFalse(first.inferred)
        assertFalse(first.quizCompleted)
        assertTrue(first.potionName in listOf("potion_test_a", "potion_test_b"))
        store.updateVehicle(VehicleReading(4, 0f, true), 1000)
        store.updateVehicle(VehicleReading(4, 0f, true), 6000)
        store.snapshot().done()
        assertEquals(first.id, store.state.value.endedTrip!!.id)
        assertEquals(1, store.state.value.quizContentCount)
        val sources = QuizRules.sources(first.records)
        val q = QuizQuestion(sources.single().id, "검증용 문제", listOf("불국사", "서울", "부산"),
            0, "대본 근거", audio.script.take(400))
        // 근거에 정답이 포함되도록 실제 테스트 대본의 앞부분을 사용한다.
        val attempt = QuizAttempt(first.id, 1, sources, listOf(q)).select(0).check()
        store.saveQuiz(attempt).done()
        assertTrue(store.refreshLibrary().done().single().quizCompleted)
        now += 86_400_000
        store.record(audio, HeardRange(80_000, 100_000), 100_000)
        val replay = store.refreshLibrary().done().single()
        assertEquals(first.id, replay.id)
        assertEquals(first.completedAt, replay.completedAt)
        assertEquals(first.potionName, replay.potionName)
        store.close().done()
        val restored = open(listOf("potion_new"))
        try {
            assertEquals(replay, restored.refreshLibrary().done().single())
        } finally { restored.close().done(); context.deleteDatabase(file.absolutePath) }
    }

    @Test fun freeListeningIsAssignedOnlyToTheCompletionDayAndOverlapDoesNotComplete() {
        val file = context.cacheDir.resolve("free-library-${System.nanoTime()}.db")
        var now = 1_700_000_000_000L
        val store = ListeningHistoryStore(Room.databaseBuilder(context, ListeningDatabase::class.java, file.absolutePath)
            .build(), clock = { now })
        try {
            repeat(3) { store.record(audio, HeardRange(0, 40_000), 100_000) }
            assertTrue(store.refreshLibrary().done().isEmpty())
            now += 86_400_000
            store.record(audio, HeardRange(40_000, 80_000), 100_000)
            val saved = store.refreshLibrary().done().single()
            assertEquals(LibraryRules.freeSession(now), saved.id)
            assertFalse(saved.drive)
            assertEquals(now, saved.completedAt)
            store.updateVehicle(VehicleReading(8, 5f), 0)
            store.record(audio, HeardRange(80_000, 100_000), 100_000)
            store.updateVehicle(VehicleReading(4, 0f, true), 1000)
            store.updateVehicle(VehicleReading(4, 0f, true), 6000)
            store.snapshot().done()
            assertEquals(0, store.state.value.quizContentCount)
            assertEquals(1, store.refreshLibrary().done().size)
        } finally { store.close().done(); context.deleteDatabase(file.absolutePath) }
    }
}
