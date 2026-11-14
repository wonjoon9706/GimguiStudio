package com.example.pleosaudioguide

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.ui.ExploreScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExploreScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchFiltersActualContentsAndOnlyClickStartsSharedPlayback() {
        var played = emptyList<AudioContent>()
        var index = -1
        compose.setContent {
            MaterialTheme {
                ExploreScreen(ContentCatalog.all, true, { q, i -> played = q; index = i })
            }
        }
        compose.onNodeWithText("오늘의 인기 오디오 집계 준비 중").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(-1, index) }
        compose.onNodeWithText("⌕  프로그램, 에피소드, 주제를 검색").performScrollTo().performClick()
        compose.onNodeWithText("프로그램, 에피소드, 주제를 검색").performTextInput("불국사")
        compose.onNodeWithText("검색 결과 · 1개").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("듣기").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("bulguksa"), played.map { it.id })
            assertEquals(0, index)
        }
    }

    @Test fun categoryEmptyAndNoSearchResultsAreHonestAndUsableOnSmallScreen() {
        compose.setContent {
            MaterialTheme {
                ExploreScreen(ContentCatalog.all, false, { _, _ -> error("Unexpected playback") },
                    Modifier.requiredSize(320.dp, 600.dp))
            }
        }
        compose.onNodeWithText("경제 · 시사").performScrollTo().performClick()
        compose.onNodeWithText("조건에 맞는 콘텐츠가 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("전체 보기").performClick()
        compose.onNodeWithText("프로그램, 에피소드, 주제를 검색").performScrollTo().performTextInput("없는검색어")
        compose.onNodeWithText("검색 결과 · 0개").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("닫기").performScrollTo().performClick()
        compose.onNodeWithText("지금 주목할 프로그램").performScrollTo().assertIsDisplayed()
    }
}
