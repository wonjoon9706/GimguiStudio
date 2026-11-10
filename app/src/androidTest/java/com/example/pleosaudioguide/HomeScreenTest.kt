package com.example.pleosaudioguide

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pleosaudioguide.ui.HomeScreen
import com.example.pleosaudioguide.ui.ExploreScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noBookmarkHidesContinueSection() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(null, "목적지 없음", emptyList(), emptyList(), null,
                    false, false, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("이어서 듣기").assertDoesNotExist()
        compose.onNodeWithText("목적지 기반 팟캐스트 추천").assertIsDisplayed()
    }

    @Test fun bookmarkShowsRestartForDemoVoice() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(null, "목적지 없음", emptyList(), emptyList(), ContentCatalog.all.first(),
                    true, false, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("이어서 듣기").assertExists()
        compose.onNodeWithText("처음부터 다시 듣기").assertExists()
    }

    @Test fun explorePassesQueueAndSelectedIndexToSharedPlayer() {
        var selected = -1
        compose.setContent {
            MaterialTheme {
                ExploreScreen(ContentCatalog.all.take(1), true, { _, index -> selected = index })
            }
        }
        compose.onNodeWithText("듣기").performClick()
        assertEquals(0, selected)
    }
}
