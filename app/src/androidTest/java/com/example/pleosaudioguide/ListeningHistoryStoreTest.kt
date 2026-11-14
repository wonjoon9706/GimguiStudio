package com.example.pleosaudioguide

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ListeningHistoryStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(file: java.io.File, legacy: java.io.File? = null) = ListeningHistoryStore(
        androidx.room.Room.databaseBuilder(context, ListeningDatabase::class.java, file.absolutePath).build(), legacy)
    private fun drainAndClose(store: ListeningHistoryStore) {
        store.snapshot().get(5, TimeUnit.SECONDS)
        store.close().get(5, TimeUnit.SECONDS)
    }
    private fun move(store: ListeningHistoryStore, time: Long) =
        store.updateVehicle(VehicleReading(8, 5f, false), time)
    private fun park(store: ListeningHistoryStore, time: Long) {
        store.updateVehicle(VehicleReading(4, 0f, true), time)
        store.updateVehicle(VehicleReading(4, 0f, true), time + 5000)
    }

    @Test fun vehicleValidityIsAvailableForPlaybackChoiceWithoutUnlockingBrowsing() {
        val file = context.cacheDir.resolve("resume-vehicle-${System.nanoTime()}.db")
        val store = open(file)
        try {
            move(store, 0)
            store.snapshot().get(5, TimeUnit.SECONDS)
            assertTrue(store.state.value.vehicleDataValid)
            assertFalse(store.state.value.browsingAllowed)
            store.updateVehicle(VehicleReading(error = "테스트 신호 없음"), 1000)
            store.snapshot().get(5, TimeUnit.SECONDS)
            assertFalse(store.state.value.vehicleDataValid)
            assertFalse(store.state.value.browsingAllowed)
        } finally { drainAndClose(store); context.deleteDatabase(file.absolutePath) }
    }

    @Test fun cumulativeEightyPercentIncludesPreDriveAndFreezesAtTripEnd() {
        val file = context.cacheDir.resolve("cumulative-${System.nanoTime()}.db")
        val content = ContentCatalog.all.first().copy(audioUri = "https://example.org/a")
        val store = open(file)
        store.record(content, HeardRange(0, 5000), 100_000)
        move(store, 0)
        store.record(content, HeardRange(5000, 80_000), 100_000)
        park(store, 1000)
        store.snapshot().get(5, TimeUnit.SECONDS)
        assertEquals(1, store.state.value.completedContentCount)
        assertEquals(1, store.state.value.quizContentCount)
        val trip = store.state.value.endedTrip!!.id
        assertEquals(75_000L, store.snapshot(trip).get(5, TimeUnit.SECONDS).single().listenedMs)
        store.record(content, HeardRange(80_000, 100_000), 100_000)
        store.snapshot().get(5, TimeUnit.SECONDS)
        assertEquals(80_000L, store.state.value.records.single().listenedMs)
        drainAndClose(store)
        val restored = open(file)
        try {
            restored.snapshot().get(5, TimeUnit.SECONDS)
            assertEquals(80_000L, restored.state.value.records.single().listenedMs)
            assertEquals(1, restored.state.value.quizContentCount)
        } finally { drainAndClose(restored); context.deleteDatabase(file.absolutePath) }
    }

    @Test fun separateTripsSurviveReopenAndDismissalDoesNotRepeat() {
        val file = context.cacheDir.resolve("trips-${System.nanoTime()}.db")
        val store = open(file)
        val content = ContentCatalog.all.first()
        move(store, 0)
        store.record(content, narrationCompleted = true)
        park(store, 1000)
        store.snapshot().get(5, TimeUnit.SECONDS)
        val firstId = store.state.value.endedTrip!!.id
        assertTrue(store.state.value.showEndScreen)
        assertEquals(1, store.snapshot(firstId).get(5, TimeUnit.SECONDS).size)
        store.dismissEndScreen()
        store.snapshot().get(5, TimeUnit.SECONDS)
        assertFalse(store.state.value.showEndScreen)
        move(store, 10_000)
        store.record(content.copy(id = "second"), narrationCompleted = true)
        park(store, 11_000)
        store.snapshot().get(5, TimeUnit.SECONDS)
        val secondId = store.state.value.endedTrip!!.id
        assertNotEquals(firstId, secondId)
        assertEquals("second", store.snapshot(secondId).get(5, TimeUnit.SECONDS).single().contentId)
        drainAndClose(store)
        val restored = open(file)
        try {
            restored.snapshot().get(5, TimeUnit.SECONDS)
            assertEquals(2, restored.trips().get(5, TimeUnit.SECONDS).count { it.kind == "drive" })
            assertEquals(content.id, restored.snapshot(firstId).get(5, TimeUnit.SECONDS).single().contentId)
            assertEquals("second", restored.snapshot(secondId).get(5, TimeUnit.SECONDS).single().contentId)
            assertFalse(restored.state.value.showEndScreen) // 현재 차량 상태 확인 전에는 표시 금지
        } finally { drainAndClose(restored); context.deleteDatabase(file.absolutePath) }
    }

    @Test fun tripCountUsesDistinctHeardContentsFromDbNotRowsOrOtherTrips() {
        val file = context.cacheDir.resolve("trip-count-${System.nanoTime()}.db")
        val store = open(file)
        val audio = ContentCatalog.all.first().copy(audioUri = "https://example.org/audio")
        try {
            store.record(audio.copy(id = "outside"), HeardRange(0, 1000))
            move(store, 0)
            store.record(audio, HeardRange(0, 1000))
            store.record(audio.copy(script = "대본 변경"), HeardRange(0, 1000))
            store.record(audio.copy(id = "second"), HeardRange(0, 1000))
            store.record(audio.copy(id = "not-played")) // 위치 변화 없음
            park(store, 1000)
            store.snapshot().get(5, TimeUnit.SECONDS)
            val state = store.state.value
            assertEquals(4, store.snapshot(state.endedTrip!!.id).get(5, TimeUnit.SECONDS).size)
            assertEquals(0, state.completedContentCount)
            // 부분 청취는 raw snapshot에 보존하되 퀴즈/보관함 완료 후보에서 제외한다.
            assertEquals(0, state.listenedContentCount)
            assertEquals(0, state.quizContentCount)
        } finally { drainAndClose(store); context.deleteDatabase(file.absolutePath) }
    }

    @Test fun legacyJsonImportedOnceAndOriginalPreserved() {
        val file = context.cacheDir.resolve("import-${System.nanoTime()}.db")
        val legacy = context.cacheDir.resolve("legacy-${System.nanoTime()}.json")
        val r = ListeningRecord("legacy", "이전 기록", "역사", "대본", null, "tts",
            narrationCompleted = true, lastListenedAt = 123)
        legacy.writeText(ListeningHistoryJson.encode(listOf(r)))
        val first = open(file, legacy)
        assertEquals(r, first.snapshot(ListeningHistoryStore.LEGACY).get(5, TimeUnit.SECONDS).single())
        drainAndClose(first)
        val restored = open(file, legacy)
        try {
            assertEquals(1, restored.snapshot().get(5, TimeUnit.SECONDS).size)
            assertTrue(legacy.exists())
        } finally { drainAndClose(restored); context.deleteDatabase(file.absolutePath); legacy.delete() }
    }

    @Test fun actualPlayerRecordsPlaybackButNotPausedSeek() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = context.cacheDir.resolve("collector-${System.nanoTime()}.json")
        val wav = context.cacheDir.resolve("collector-${System.nanoTime()}.wav")
        val pcm = 60 * 8000 * 2
        wav.writeBytes(java.nio.ByteBuffer.allocate(44 + pcm).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1.toShort()); putShort(1.toShort()); putInt(8000); putInt(16000)
            putShort(2.toShort()); putShort(16.toShort()); put("data".toByteArray()); putInt(pcm)
        }.array())
        val content = ContentCatalog.all.first().copy(id = "collector-test",
            audioUri = wav.toURI().toString())
        OdiiStore.remember(listOf(content))
        val history = open(file)
        lateinit var player: androidx.media3.exoplayer.ExoPlayer
        lateinit var collector: PlaybackHistoryCollector
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun waitPlaying() {
            val limit = System.currentTimeMillis() + 10_000
            var playing = false
            while (!playing && System.currentTimeMillis() < limit) {
                onMain { playing = player.isPlaying }
                if (!playing) Thread.sleep(50)
            }
            assertTrue("Player did not start", playing)
        }
        try {
            onMain {
                player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
                collector = PlaybackHistoryCollector(player, history)
                player.setMediaItem(androidx.media3.common.MediaItem.Builder()
                    .setMediaId(content.id).setUri(content.audioUri!!).build())
                player.prepare()
                player.play()
            }
            waitPlaying()
            Thread.sleep(1500)
            onMain { player.pause() }
            instrumentation.waitForIdleSync()
            val before = history.snapshot().get(5, TimeUnit.SECONDS).single()
            assertTrue(before.listenedMs in 500..5000)
            onMain { player.seekTo(30_000) }
            instrumentation.waitForIdleSync()
            Thread.sleep(300)
            assertEquals(before.listenedMs, history.snapshot().get(5, TimeUnit.SECONDS).single().listenedMs)
            onMain { player.play() }
            waitPlaying()
            Thread.sleep(1500)
            onMain { player.pause(); collector.close(); player.release() }
            val after = history.snapshot().get(5, TimeUnit.SECONDS).single()
            assertTrue(after.ranges.any { it.startMs >= 29_000 })
            assertTrue(after.listenedMs in 1000..7000)
            assertFalse(after.canUseFullScriptForQuiz)
            val bookmark = history.playbackResume(content).get(5, TimeUnit.SECONDS)!!
            assertTrue(bookmark.positionMs in 30_000..35_000)
            assertTrue(bookmark.canResume)
            val restored = open(file)
            try {
                assertEquals(after, restored.snapshot().get(5, TimeUnit.SECONDS).single())
            } finally { restored.close().get(5, TimeUnit.SECONDS) }
        } finally {
            onMain { collector.close(); player.release() }
            history.close().get(5, TimeUnit.SECONDS)
            file.delete()
            wav.delete()
        }
    }

    @Test fun jsonFileRestoresWithoutOdiiCacheAndKeepsScriptVersions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.cacheDir.resolve("history-test-${System.nanoTime()}.json")
        val content = ContentCatalog.all.first().copy(id = "history-test", audioUri = "https://example.org/a")
        val first = open(file)
        try {
            first.record(content, HeardRange(0, 5000), 60_000)
            first.record(content, HeardRange(3000, 8000), 60_000)
            first.record(content, HeardRange(38_000, 40_000), 60_000)
            first.record(content.copy(script = "변경된 원고"), HeardRange(0, 1000), 60_000)
            first.snapshot().get(5, TimeUnit.SECONDS)
        } finally { first.close().get(5, TimeUnit.SECONDS) }
        val restored = open(file)
        try {
            val records = restored.snapshot().get(5, TimeUnit.SECONDS)
            assertEquals(2, records.size)
            val old = records.first { it.script == content.script }
            assertEquals(content.title, old.title)
            assertEquals(10_000L, old.listenedMs)
            assertEquals(listOf(HeardRange(0, 8000), HeardRange(38_000, 40_000)), old.ranges)
            assertFalse(old.canUseFullScriptForQuiz)
            assertNull(restored.error)
        } finally { restored.close().get(5, TimeUnit.SECONDS); file.delete() }
    }

    @Test fun corruptFileIsReportedAndNeverOverwritten() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.cacheDir.resolve("history-corrupt-${System.nanoTime()}.json")
        file.writeText("{broken")
        val database = context.cacheDir.resolve("corrupt-import-${System.nanoTime()}.db")
        val store = open(database, file)
        try {
            store.record(ContentCatalog.all.first(), narrationCompleted = true)
            assertEquals(1, store.snapshot().get(5, TimeUnit.SECONDS).size)
            assertNotNull(store.error)
            assertEquals("{broken", file.readText())
        } finally { drainAndClose(store); context.deleteDatabase(database.absolutePath); file.delete() }
    }
}
