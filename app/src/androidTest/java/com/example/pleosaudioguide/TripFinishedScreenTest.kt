package com.example.pleosaudioguide

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.pleosaudioguide.ui.TripFinishedScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TripFinishedScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun countShowsOnlyCompletedAudioAndEnablesQuizWithOneCompleteSource() {
        val a = ListeningRecord("a", "첫 콘텐츠", "역사", "원고", null, "audio",
            durationMs = 10_000, ranges = listOf(HeardRange(0, 10_000)), lastListenedAt = 1)
        val b = a.copy(contentId = "b", title = "두 번째", ranges = listOf(HeardRange(0, 3000)))
        val state = TripHistoryState(endedTrip = TripEntity("trip", 1, 2), parked = true,
            records = listOf(a, a.copy(script = "수정 원고"), b))
        compose.setContent { MaterialTheme { TripFinishedScreen(state, {}) } }
        compose.onNodeWithText("청취한 1개 에피소드에서\n학습재료를 준비했어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("청취한 2개 에피소드에서\n학습재료를 준비했어요").assertDoesNotExist()
        compose.onNodeWithText("퀴즈 물약 완성하기").performScrollTo().assertIsEnabled()
    }

    @Test fun jarAndLaterButtonAreAccessibleOnSmallScreen() {
        var later = false
        val state = TripHistoryState(endedTrip = TripEntity("test", 1, 2), parked = true)
        compose.setContent {
            MaterialTheme { TripFinishedScreen(state, { later = true }, Modifier.requiredSize(320.dp, 480.dp)) }
        }
        compose.onNodeWithText("주행을 마쳤어요").assertIsDisplayed()
        compose.onNodeWithContentDescription("학습 재료 항아리").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("퀴즈 물약 완성하기").assertDoesNotExist()
        compose.onNodeWithText("선택하면 대본을 서버로 보내 문제를 준비해요").assertDoesNotExist()
        compose.onNodeWithText("홈으로").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(later) }
    }
}
