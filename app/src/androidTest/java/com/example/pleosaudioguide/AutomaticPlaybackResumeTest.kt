package com.example.pleosaudioguide

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pleosaudioguide.ui.ResumePlaybackDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** 기기 ExoPlayer의 실제 자동 경계 이벤트. 사용자 DB/차량 신호/앱 데이터는 변경하지 않는다. */
class AutomaticPlaybackResumeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun automaticNextWaitsForResumeChoice() = scenario(choice = false)
    @Test fun automaticNextCanStartFromBeginning() = scenario(choice = true)
    @Test fun dismissKeepsSavedPositionAndPauses() = scenario(dismiss = true)
    @Test fun drivingAutomaticNextWaitsForResumeChoice() = scenario(driving = true)
    @Test fun drivingAutomaticNextCanStartFromBeginning() = scenario(driving = true, choice = true)
    @Test fun drivingDismissKeepsSavedPositionAndPauses() = scenario(driving = true, dismiss = true)
    @Test fun signalLossCancelsAutomaticChoiceAndKeepsPosition() = scenario(driving = true, loseSignal = true)
    @Test fun unheardNextStartsWithoutDialog() = scenario(savedPosition = null)
    @Test fun finishedNextStartsWithoutDialog() = scenario(savedPosition = 6000)

    private fun scenario(choice: Boolean = false, dismiss: Boolean = false,
        driving: Boolean = false, savedPosition: Long? = 3000, loseSignal: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val history = ListeningHistoryStore(Room.inMemoryDatabaseBuilder(context, ListeningDatabase::class.java).build())
        val wav = context.cacheDir.resolve("automatic-resume-${System.nanoTime()}.wav")
        val size = 6 * 8000 * 2
        wav.writeBytes(ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1.toShort()); putShort(1.toShort()); putInt(8000); putInt(16000)
            putShort(2.toShort()); putShort(16.toShort()); put("data".toByteArray()); putInt(size)
        }.array())
        val queue = (1..2).map {
            AudioContent("automatic-resume-test:${wav.name}:$it", "자동 전환 검증 $it",
                "테스트", emptyList(), "", 6, audioUri = wav.toURI().toString())
        }
        OdiiStore.remember(queue)
        history.updateVehicle(if (driving) VehicleReading(8, 5f, false) else VehicleReading(4, 0f, true), 0)
        savedPosition?.let { history.savePlaybackResume(PlaybackResume(queue[1], it, 6000)) }
        history.snapshot().get(5, TimeUnit.SECONDS)
        lateinit var player: ExoPlayer
        lateinit var guard: AutomaticPlaybackResume
        lateinit var collector: PlaybackHistoryCollector
        var cleanup: () -> Unit = {}
        val resumedAt = java.util.concurrent.atomic.AtomicLong(-1)
        try {
            compose.setContent {
                val prompt by history.automaticResumePrompt.collectAsState()
                MaterialTheme {
                    androidx.compose.material3.Text("자동 전환 테스트 · 로컬 무음 음원")
                    prompt?.let { p ->
                        ResumePlaybackDialog(p.saved, { p.choose(true) }, { p.choose(false) }, { p.choose(null) }, driving = driving)
                    }
                }
            }
            compose.runOnIdle {
                player = ExoPlayer.Builder(context).build()
                cleanup = { player.release() }
                guard = AutomaticPlaybackResume(player, history)
                cleanup = { guard.close(); player.release() }
                collector = PlaybackHistoryCollector(player, history, guard::canSavePosition)
                cleanup = { collector.close(); guard.close(); player.release() }
                // 기기 테스트의 UI idle 대기 중 음원이 끝나지 않게 실제 재개 시점을 즉시 포착한다.
                player.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady && player.currentMediaItemIndex == 1 && guard.canSavePosition(queue[1].id)) {
                            resumedAt.set(player.currentPosition)
                            player.pause()
                        }
                    }
                })
                player.setMediaItems(queue.map {
                    MediaItem.Builder().setMediaId(it.id).setUri(it.audioUri).build()
                })
                player.prepare()
            }
            compose.waitUntil(10_000) { compose.runOnIdle { player.playbackState == Player.STATE_READY } }
            compose.runOnIdle { player.seekTo(0, 5750); player.play() }
            compose.waitUntil(10_000) { compose.runOnIdle { player.currentMediaItemIndex == 1 } }
            if (savedPosition == 3000L) {
                compose.waitUntil(5000) { history.automaticResumePrompt.value != null }
                compose.onNodeWithText("마지막 재생 위치 0:03 / 0:06").assertIsDisplayed()
                compose.runOnIdle { assertFalse(player.playWhenReady) }
                assertEquals(3000L, history.playbackResume(queue[1]).get(5, TimeUnit.SECONDS)!!.positionMs)
                if (loseSignal) {
                    val stale = history.automaticResumePrompt.value!!
                    history.updateVehicle(VehicleReading(error = "테스트 신호 없음"), 1000)
                    compose.waitUntil(5000) { history.automaticResumePrompt.value == null }
                    compose.runOnIdle { stale.choose(false) }
                } else if (dismiss) compose.runOnIdle { history.automaticResumePrompt.value!!.choose(null) }
                else compose.onNodeWithText(if (choice) "처음부터" else "이어듣기").performClick()
                compose.waitUntil(5000) { history.automaticResumePrompt.value == null }
                if (!dismiss && !loseSignal) compose.waitUntil(5000) { resumedAt.get() >= 0 }
                compose.runOnIdle {
                    assertFalse(player.playWhenReady)
                    assertEquals(!dismiss && !loseSignal, resumedAt.get() >= 0)
                    val expected = if (choice && !dismiss && !loseSignal) 0L else 3000L
                    val actual = if (dismiss || loseSignal) player.currentPosition else resumedAt.get()
                    assertTrue("선택 위치 복원: $actual", kotlin.math.abs(actual - expected) < 500)
                    player.pause()
                }
            } else {
                compose.waitUntil(5000) { resumedAt.get() >= 0 }
                compose.runOnIdle {
                    assertNull(history.automaticResumePrompt.value)
                    val expected = 0L
                    assertTrue("자동 위치 복원: ${resumedAt.get()}", kotlin.math.abs(resumedAt.get() - expected) < 500)
                    player.pause()
                }
            }
        } finally {
            compose.runOnIdle {
                cleanup()
            }
            history.close().get(5, TimeUnit.SECONDS)
            wav.delete()
        }
    }
}
