package com.example.pleosaudioguide

import android.app.Application
import android.content.ComponentName
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** 실제 서비스/Room/VM의 주행 전환 경로를 로컬 무음 WAV로 검증. 차량 신호는 변경하지 않는다. */
class DrivingResumeIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun adjacentDrivingSwitchOffersResumeOrRestartAndKeepsPlayState() = scenario(false)
    @Test fun savedThumbnailPreparesPausedPlayerWithoutModal() = scenario(true)

    private fun scenario(viewOnly: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val history = ListeningHistoryStore.get(app)
        // 시연 기기의 마지막 이어듣기를 테스트 후 되돌린다.
        val original = history.latestPlaybackResume().get(20, TimeUnit.SECONDS)
        assumeNotNull(original)
        val prefs = app.getSharedPreferences("playback_bookmark", 0)
        val originalId = prefs.getString("id", null)
        val hadPosition = prefs.contains("position")
        val originalPosition = prefs.getLong("position", 0)
        val wav = app.cacheDir.resolve("driving-resume-${System.nanoTime()}.wav")
        val pcmSize = 90 * 8000 * 2
        wav.writeBytes(ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcmSize); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1.toShort()); putShort(1.toShort()); putInt(8000); putInt(16000)
            putShort(2.toShort()); putShort(16.toShort()); put("data".toByteArray()); putInt(pcmSize)
        }.array())
        val queue = (1..2).map {
            AudioContent("driving-resume-test:${wav.name}:$it", "주행 이어듣기 검증 $it", "테스트 전용",
                emptyList(), "", 90, audioUri = wav.toURI().toString())
        }
        val store = ViewModelStore()
        var controller: MediaController? = null
        try {
            OdiiStore.remember(queue)
            history.savePlaybackResume(PlaybackResume(queue[0], 1000, 90_000))
            history.latestPlaybackResume().get(20, TimeUnit.SECONDS)
            prefs.edit().putString("id", queue[0].id).putLong("position", 1000).commit()
            lateinit var model: GuideViewModel
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))
                    .get(GuideViewModel::class.java)
            }
            compose.setContent { Text("실제 플레이어 주행 이어듣기 검증 · 테스트 데이터") }
            compose.waitUntil(15_000) {
                model.playerReady && model.voiceReady && model.resumeContent?.id == queue[0].id &&
                    (!viewOnly || model.tripHistoryState.value.vehicleDataValid)
            }
            if (viewOnly) assumeTrue("썸네일 진입은 정차 및 UX 제한 해제 상태에서 검증",
                model.tripHistoryState.value.browsingAllowed)
            compose.runOnIdle {
                assertFalse("종료 안내 중에는 전환 검증을 시작하지 않음", model.tripHistoryState.value.showEndScreen)
                if (viewOnly) assertTrue(model.preparePlayerForViewing()) else model.resumeLast()
            }
            if (viewOnly) {
                compose.waitUntil(10_000) { model.hasCurrentAudio && !model.usingPleosVoice && model.durationMs > 0 }
                compose.runOnIdle {
                    assertNull(model.resumePrompt)
                    assertFalse(model.isPlaying)
                    assertEquals(queue[0].id, model.currentContentId)
                    assertTrue(abs(model.positionMs - 1000) < 500)
                    assertTrue(model.preparePlayerForViewing())
                    assertFalse(model.isPlaying)
                    assertTrue(abs(model.positionMs - 1000) < 500)
                }
                return
            }
            compose.waitUntil(10_000) { model.hasCurrentAudio || model.resumePrompt != null }
            compose.runOnIdle { if (model.resumePrompt != null) model.confirmResume(false) }
            compose.waitUntil(10_000) { model.hasCurrentAudio && !model.usingPleosVoice }
            val future = MediaController.Builder(app, SessionToken(app,
                ComponentName(app, GuidePlaybackService::class.java))).buildAsync()
            val player = future.get(10, TimeUnit.SECONDS)
            controller = player
            compose.runOnIdle {
                player.pause()
                player.setMediaItems(queue.map { MediaItem.Builder().setMediaId(it.id).build() })
                player.prepare()
            }
            compose.waitUntil(15_000) { compose.runOnIdle {
                model.playbackQueue.size == 2 && player.playbackState == Player.STATE_READY && model.canSeek
            } }
            compose.runOnIdle { player.seekTo(0, 35_000) }
            compose.waitUntil(15_000) { abs(model.positionMs - 35_000) < 500 }
            compose.runOnIdle { model.selectDrivingContent(queue[1].id) }
            compose.waitUntil(15_000) { compose.runOnIdle { model.currentContentId == queue[1].id && model.canSeek } }
            compose.runOnIdle {
                assertFalse(player.playWhenReady)
                assertNull(model.resumePrompt)
                model.seekDrivingBy30Seconds(true)
            }
            compose.waitUntil(15_000) { abs(model.positionMs - 30_000) < 500 }
            compose.runOnIdle { model.selectDrivingContent(queue[0].id) }
            compose.waitUntil(15_000) { model.resumePrompt?.content?.id == queue[0].id }
            compose.runOnIdle {
                assertEquals(queue[1].id, model.currentContentId)
                assertFalse(player.playWhenReady)
                assertTrue(abs(model.resumePrompt!!.positionMs - 35_000) < 500)
                model.confirmResume(false)
            }
            compose.waitUntil(15_000) { model.currentContentId == queue[0].id && abs(model.positionMs - 35_000) < 500 }
            compose.runOnIdle {
                assertFalse(player.playWhenReady)
                model.selectDrivingContent(queue[1].id)
            }
            compose.waitUntil(15_000) { model.resumePrompt?.content?.id == queue[1].id }
            compose.runOnIdle {
                assertTrue(abs(model.resumePrompt!!.positionMs - 30_000) < 500)
                model.confirmResume(true)
            }
            compose.waitUntil(15_000) { compose.runOnIdle {
                model.currentContentId == queue[1].id && model.canSeek && model.positionMs < 500
            } }
            compose.runOnIdle {
                assertFalse(player.playWhenReady)
                model.togglePlayback()
            }
            compose.waitUntil(15_000) { model.isPlaying }
            compose.runOnIdle { model.selectDrivingContent(queue[0].id) }
            compose.waitUntil(15_000) { model.resumePrompt?.content?.id == queue[0].id }
            compose.runOnIdle {
                assertEquals(queue[1].id, model.currentContentId)
                assertTrue(player.playWhenReady)
                model.confirmResume(false)
            }
            compose.waitUntil(15_000) { model.currentContentId == queue[0].id && model.isPlaying }
            compose.runOnIdle {
                assertTrue(player.playWhenReady)
                assertTrue(abs(player.currentPosition - 35_000) < 2000)
                assertNull(model.resumePrompt)
            }
        } finally {
            instrumentation.runOnMainSync {
                controller?.stop()
                controller?.clearMediaItems()
                store.clear()
                controller?.release()
                prefs.edit().apply {
                    if (originalId == null) remove("id") else putString("id", originalId)
                    if (hadPosition) putLong("position", originalPosition) else remove("position")
                }.commit()
            }
            if (original != null) {
                history.savePlaybackResume(original)
                history.latestPlaybackResume().get(20, TimeUnit.SECONDS)
            }
            wav.delete()
        }
    }
}
