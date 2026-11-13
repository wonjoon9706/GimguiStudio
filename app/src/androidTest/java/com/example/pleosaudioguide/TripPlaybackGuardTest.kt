package com.example.pleosaudioguide

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** 실제 ExoPlayer로 종료 진입·외부 재생 명령·해제 후 자동 재개 금지를 검증한다. */
class TripPlaybackGuardTest {
    @Test fun enteringTripEndPausesWithoutLosingPositionOrQueueAndNeverAutoResumes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wav = context.cacheDir.resolve("trip-pause-${System.nanoTime()}.wav")
        val size = 60 * 8000 * 2
        wav.writeBytes(ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1.toShort()); putShort(1.toShort()); putInt(8000); putInt(16000)
            putShort(2.toShort()); putShort(16.toShort()); put("data".toByteArray()); putInt(size)
        }.array())
        val trips = MutableStateFlow(TripHistoryState())
        lateinit var player: ExoPlayer
        lateinit var guard: TripPlaybackGuard
        var savedPosition = 0L
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun awaitPlaying() {
            var playing = false
            val limit = System.currentTimeMillis() + 10_000
            while (!playing && System.currentTimeMillis() < limit) {
                onMain { playing = player.isPlaying }
                if (!playing) Thread.sleep(50)
            }
            assertTrue("실제 재생 시작 실패", playing)
        }
        try {
            onMain {
                player = ExoPlayer.Builder(context).build()
                guard = TripPlaybackGuard(player, trips)
                player.setMediaItems(listOf("first", "second").map {
                    MediaItem.Builder().setMediaId(it).setUri(wav.toURI().toString()).build()
                }, 0, 10_000)
                player.prepare()
                player.play()
            }
            awaitPlaying()
            onMain {
                savedPosition = player.currentPosition
                trips.value = TripHistoryState(endedTrip = TripEntity("test", 1, 2), parked = true)
            }
            instrumentation.waitForIdleSync()
            onMain {
                assertFalse(player.playWhenReady)
                assertFalse(player.isPlaying)
                assertEquals("first", player.currentMediaItem!!.mediaId)
                assertEquals(2, player.mediaItemCount)
                assertTrue(abs(player.currentPosition - savedPosition) < 1000)
                savedPosition = player.currentPosition
                player.play() // 시스템/외부 컨트롤러에 해당하는 재생 요청
            }
            instrumentation.waitForIdleSync()
            onMain { assertFalse(player.playWhenReady) }
            onMain { trips.value = trips.value.copy(endedTrip = trips.value.endedTrip!!.copy(dismissed = true)) }
            instrumentation.waitForIdleSync()
            onMain {
                assertFalse(player.playWhenReady)
                assertTrue(abs(player.currentPosition - savedPosition) < 1000)
                player.play() // 나중에 하기 이후 명시적인 사용자 재생만 허용
            }
            awaitPlaying()
        } finally {
            onMain { guard.close(); player.release() }
            wav.delete()
        }
    }
}
