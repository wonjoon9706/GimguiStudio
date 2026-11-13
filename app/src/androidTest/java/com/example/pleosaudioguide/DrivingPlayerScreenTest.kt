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
                    { previous++ }, { playing = !playing }, { next++ }, {},
                    Modifier.requiredSize(320.dp, 320.dp))
            }
        }
        compose.onNodeWithText("긴 제목").assertIsDisplayed()
        compose.onNodeWithText("일시정지").assertIsDisplayed().performClick()
        compose.onNodeWithText("재생").assertIsDisplayed().performClick()
        compose.onNodeWithText("이전").performClick()
        compose.onNodeWithText("다음").performClick()
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
                    {}, {}, {}, { resumed = true }, Modifier.requiredSize(320.dp, 320.dp))
            }
        }
        compose.runOnIdle { assertFalse(resumed) }
        compose.onNodeWithText("이전").assertIsNotEnabled()
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNodeWithText("이어 듣기").performClick()
        compose.runOnIdle { assertTrue(resumed) }
    }

    @Test fun changingRestrictionHidesQuizAndRestoresParkedScreen() {
        var parked by mutableStateOf(true)
        val state = TripHistoryState(endedTrip = TripEntity("test", 1, 2), parked = true)
        compose.setContent {
            MaterialTheme {
                if (parked) TripFinishedScreen(state, {})
                else DrivingPlayerScreen("", false, false, false, false, false, false, 0, 0,
                    false, false, 40, {}, {}, {}, {})
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
