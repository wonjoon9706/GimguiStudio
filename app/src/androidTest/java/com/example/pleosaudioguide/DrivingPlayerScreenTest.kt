package com.example.pleosaudioguide

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pleosaudioguide.ui.DrivingPlayerScreen
import com.example.pleosaudioguide.ui.TripFinishedScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DrivingPlayerScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun basicControlsWorkWithoutExposingBrowsingAndTitleIsLimited() {
        var playing by mutableStateOf(true)
        var previous = 0
        var next = 0
        compose.setContent {
            MaterialTheme {
                DrivingPlayerScreen("긴 제목 테스트 콘텐츠", playing, true, true, true,
                    false, false, 1000, 60_000, false, true, 4,
                    { playing = !playing }, {}, Modifier.requiredSize(320.dp, 320.dp),
                    canSeek = true, onSeek30 = { if (it) next++ else previous++ })
            }
        }
        compose.onNodeWithText("긴 제목").assertIsDisplayed()
        compose.onNodeWithContentDescription("일시정지").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("재생").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("30초 뒤로").performClick()
        compose.onNodeWithContentDescription("30초 앞으로").performClick()
        compose.onNodeWithText("콘텐츠를 골라주세요").assertDoesNotExist()
        compose.onNodeWithText("순서 변경").assertDoesNotExist()
        compose.runOnIdle { assertTrue(playing); assertEquals(1, previous); assertEquals(1, next) }
    }

    @Test fun noCurrentContentDoesNotAutoPlayAndPreviousNextStayDisabled() {
        var resumed = false
        compose.setContent {
            MaterialTheme {
                DrivingPlayerScreen("", false, false, false, false,
                    false, false, 0, 0, true, true, 40,
                    {}, { resumed = true }, Modifier.requiredSize(320.dp, 320.dp))
            }
        }
        compose.runOnIdle { assertFalse(resumed) }
        compose.onNodeWithContentDescription("30초 뒤로").assertIsNotEnabled()
        compose.onNodeWithContentDescription("30초 앞으로").assertIsNotEnabled()
        compose.onNodeWithContentDescription("이어 듣기").performClick()
        compose.runOnIdle { assertTrue(resumed) }
    }

    @Test fun swipeSelectsAdjacentContentWithoutAutoplayOrListNavigation() {
        val queue = ContentCatalog.all.take(3)
        var current by mutableStateOf(1)
        var commands = 0
        compose.setContent {
            MaterialTheme {
                DrivingPlayerScreen(queue[current].title, false, true, current > 0, current < 2,
                    false, false, 10_000, 60_000, false, true, 80, {}, {},
                    Modifier.requiredSize(420.dp, 720.dp), content = queue[current],
                    queue = queue, canSeek = true, onSelectContent = { id ->
                        current = queue.indexOfFirst { it.id == id }
                        commands++
                    })
            }
        }
        compose.runOnIdle { assertEquals(0, commands) }
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeLeft() }
        compose.waitUntil(5000) { current == 2 }
        compose.waitForIdle()
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, current); assertEquals(1, commands) }
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeRight() }
        compose.waitUntil(5000) { current == 1 }
        repeat(2) {
            compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeLeft() }
            compose.waitUntil(5000) { current == 2 }
            compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeRight() }
            compose.waitUntil(5000) { current == 1 }
            compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeRight() }
            compose.waitUntil(5000) { current == 0 }
            compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeRight() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(0, current) }
            compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeLeft() }
            compose.waitUntil(5000) { current == 1 }
        }
        compose.runOnIdle { assertEquals(10, commands) }
        compose.onNodeWithContentDescription("재생").assertExists()
        compose.onNodeWithText("재생 목록 · 3개").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress))
            .assertDoesNotExist()
    }

    @Test fun changingRestrictionHidesQuizAndRestoresParkedScreen() {
        var parked by mutableStateOf(true)
        val state = TripHistoryState(endedTrip = TripEntity("test", 1, 2), parked = true)
        compose.setContent {
            MaterialTheme {
                if (parked) TripFinishedScreen(state, {})
                else DrivingPlayerScreen("", false, false, false, false, false, false, 0, 0,
                    false, false, 40, {}, {})
            }
        }
        compose.onNodeWithText("주행을 마쳤어요").assertExists()
        compose.runOnIdle { parked = false }
        compose.onNodeWithTag("drivingPlayer").assertExists()
        compose.onNodeWithText("주행을 마쳤어요").assertDoesNotExist()
        compose.onNodeWithText("퀴즈로 음악 완성하기").assertDoesNotExist()
        compose.runOnIdle { parked = true }
        compose.onNodeWithText("주행을 마쳤어요").assertExists()
    }

    @Test fun onlyDrivingAwareActivityDeclaresDistractionOptimization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java), PackageManager.GET_META_DATA)
        assertTrue(info.metaData.getBoolean("distractionOptimized"))
    }
}
