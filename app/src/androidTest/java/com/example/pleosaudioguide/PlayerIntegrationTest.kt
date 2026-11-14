package com.example.pleosaudioguide

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pleosaudioguide.ui.GuideApp
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** 테스트 전용 로컬 무음 WAV로 네트워크와 관계없이 실제 서비스·컨트롤러·화면을 검증한다. */
class PlayerIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedPlayerSurvivesNavigationSeeksAndPausedSwipes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, "android.car.permission.CAR_SPEED")
        val store = ViewModelStore()
        val wav = app.cacheDir.resolve("player-integration-test.wav")
        val pcmSize = 90 * 8_000 * 2
        val bytes = ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcmSize); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1.toShort()); putShort(1.toShort()); putInt(8_000); putInt(16_000)
            putShort(2.toShort()); putShort(16.toShort()); put("data".toByteArray()); putInt(pcmSize)
        }.array()
        wav.writeBytes(bytes)
        val runId = System.nanoTime()
        val queue = (1..2).map {
            AudioContent("player-test:$runId:$it", "플레이어 통합 검증 $it", "테스트 전용",
                emptyList(), "", 90, audioUri = wav.toURI().toString())
        }
        OdiiStore.remember(queue)
        lateinit var model: GuideViewModel
        lateinit var future: ListenableFuture<MediaController>
        instrumentation.runOnMainSync {
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))
                .get(GuideViewModel::class.java)
            future = MediaController.Builder(app,
                SessionToken(app, ComponentName(app, GuidePlaybackService::class.java))).buildAsync()
        }
        val controller = future.get(15, TimeUnit.SECONDS)
        try {
            compose.setContent { GuideApp(model) }
            compose.waitUntil(20_000) {
                model.playerReady && model.voiceReady && model.tripHistoryState.value.browsingAllowed
            }
            compose.runOnIdle {
                if (model.tripHistoryState.value.showEndScreen) model.dismissTripFinished()
            }
            compose.waitUntil(5_000) { !model.tripHistoryState.value.showEndScreen }
            compose.runOnIdle { model.play(queue) }
            compose.waitUntil(15_000) { model.durationMs > 0 && model.isPlaying }
            compose.runOnIdle {
                assertTrue(model.canSeek)
                model.togglePlayback()
                model.seekToPosition(35_000)
            }
            compose.waitUntil(5_000) { !model.isPlaying && abs(model.positionMs - 35_000) < 500 }

            compose.onNodeWithContentDescription("전체 재생 화면 열기").performClick()
            compose.onNodeWithTag("playerArtworkCarousel").assertExists()
            compose.runOnIdle {
                assertEquals(queue.first().id, model.currentContentId)
                assertFalse(model.isPlaying)
                assertTrue(abs(controller.currentPosition - 35_000) < 500)
            }
            compose.onNodeWithContentDescription("30초 앞으로").performScrollTo().performClick()
            compose.waitUntil(5_000) { abs(model.positionMs - 65_000) < 500 }
            compose.onNodeWithContentDescription("30초 뒤로").performClick()
            compose.waitUntil(5_000) { abs(model.positionMs - 35_000) < 500 }
            compose.runOnIdle { assertFalse(controller.playWhenReady) }

            compose.onNodeWithText("재생 목록 · 2개").performScrollTo().performClick()
            compose.onNodeWithText("콘텐츠를 골라주세요").assertIsDisplayed()
            compose.onNodeWithText("현재 재생 목록 · 2개").assertIsDisplayed()
            compose.onNodeWithText("현재 항목").assertExists()
            compose.onNodeWithText(queue[1].title).assertExists()
            compose.runOnIdle {
                assertFalse(controller.playWhenReady)
                assertTrue(abs(controller.currentPosition - 35_000) < 500)
                assertEquals(queue.map { it.id }, model.playbackQueue.map { it.id })
            }
            compose.onNodeWithContentDescription("전체 재생 화면 열기").performClick()

            compose.onNodeWithTag("playerArtworkCarousel").performScrollTo()
                .performTouchInput { swipeLeft() }
            compose.waitUntil(5_000) { model.currentContentId == queue[1].id }
            compose.runOnIdle {
                assertEquals(queue[1].id, controller.currentMediaItem?.mediaId)
                assertFalse(controller.playWhenReady)
            }
            compose.onNodeWithContentDescription("재생").performScrollTo().performClick()
            compose.waitUntil(5_000) { model.isPlaying }
            compose.onNodeWithText("재생 목록 · 2개").performScrollTo().performClick()
            compose.onNodeWithText("콘텐츠를 골라주세요").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(model.isPlaying)
                assertEquals(queue[1].id, controller.currentMediaItem?.mediaId)
            }
            compose.onNodeWithContentDescription("전체 재생 화면 열기").performClick()
            compose.onNodeWithText("‹ 홈으로").performScrollTo().performClick()
            compose.onNodeWithContentDescription("전체 재생 화면 열기").assertExists()
            compose.runOnIdle {
                assertEquals(queue[1].title, model.title)
                assertTrue(model.isPlaying)
                assertEquals(queue.map { it.id }, model.playbackQueue.map { it.id })
            }
            compose.onNodeWithContentDescription("전체 재생 화면 열기").performClick()
            compose.onNodeWithContentDescription("일시정지").performScrollTo().performClick()
            compose.waitUntil(5_000) { !model.isPlaying }
            compose.onNodeWithText("‹ 홈으로").performScrollTo().performClick()
            compose.runOnIdle { assertFalse(model.isPlaying) }

            // A의 35초 위치를 B 전환 후에도 보존. 취소는 현재 B를 유지한다.
            compose.runOnIdle { model.selectPlaybackContent(queue.first().id) }
            compose.waitUntil(5_000) { model.resumePrompt != null }
            compose.onNodeWithText("마지막 재생 위치 0:35 / 1:30").assertIsDisplayed()
            compose.runOnIdle {
                model.dismissResumePrompt()
                assertEquals(queue[1].id, model.currentContentId)
                assertFalse(controller.playWhenReady)
                model.selectPlaybackContent(queue.first().id)
            }
            compose.waitUntil(5_000) { model.resumePrompt != null }
            compose.onNodeWithText("이어듣기").performClick()
            compose.waitUntil(5_000) {
                model.currentContentId == queue.first().id && abs(model.positionMs - 35_000) < 500
            }
            compose.runOnIdle {
                assertFalse(controller.playWhenReady)
                model.play(queue, 0)
            }
            compose.waitUntil(5_000) { model.resumePrompt != null }
            compose.onNodeWithText("처음부터").performClick()
            compose.waitUntil(5_000) { model.isPlaying && model.positionMs < 5_000 }
        } finally {
            instrumentation.runOnMainSync {
                controller.stop()
                controller.clearMediaItems()
                store.clear()
                MediaController.releaseFuture(future)
                val bookmark = app.getSharedPreferences("playback_bookmark", 0)
                if (bookmark.getString("id", "").orEmpty().startsWith("player-test:"))
                    bookmark.edit().clear().apply()
            }
            wav.delete()
        }
    }
}
