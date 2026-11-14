package com.example.pleosaudioguide

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.ui.LibraryScreen
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyShelfAlwaysHasTwelveSlotsWithoutFakePotions() {
        compose.setContent { MaterialTheme {
            LibraryScreen(LibraryState(), Modifier.requiredSize(320.dp, 600.dp))
        } }
        repeat(12) { compose.onNodeWithTag("shelfSlot$it").assertExists() }
        compose.onNodeWithText("저장된 콘텐츠 · 0개 묶음").assertIsDisplayed()
        compose.onNodeWithText("다음").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("누적 80% 이상 들으면 선반에 포션이 생겨요.").performScrollTo().assertIsDisplayed()
    }
    @Test fun potionOpensItsAudioAndQuizRecordsAndThirteenthUsesNextPage() {
        val record = ListeningRecord("id", "저장된 이야기", "역사", "대본", null, "audio",
            100_000, listOf(HeardRange(0, 80_000)), lastListenedAt = 1)
        val items = (1..13).map { LibraryPotion("$it", it.toLong(), false, "pot_placeholder",
            listOf(record.copy(category = "역사$it")), emptyList()) }
        compose.setContent { MaterialTheme { LibraryScreen(LibraryState(items)) } }
        compose.onNodeWithContentDescription("역사1 · 퀴즈 미완료").performScrollTo().performClick()
        compose.onNodeWithText("저장된 이야기").assertIsDisplayed()
        compose.onNodeWithText("아직 푼 퀴즈가 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("‹ 보관함").performScrollTo().performClick()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNodeWithContentDescription("역사13 · 퀴즈 미완료").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 / 2").performScrollTo().assertIsDisplayed()
    }
}
