package com.example.pleosaudioguide

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.example.pleosaudioguide.ui.ContentCollection
import com.example.pleosaudioguide.ui.ContentListScreen
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
                HomeScreen(null, "목적지 없음", emptyList(), emptyList(), false, {}, {})
            }
        }
        compose.onNodeWithText("이어서 듣기").assertDoesNotExist()
        compose.onNodeWithText("목적지 기반 팟캐스트 추천").assertIsDisplayed()
    }

    @Test fun homeNoLongerContainsResumeCard() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(null, "목적지 없음", emptyList(), emptyList(), false, {}, {})
            }
        }
        compose.onNodeWithText("이어서 듣기").assertDoesNotExist()
        compose.onNodeWithText("처음부터 다시 듣기").assertDoesNotExist()
    }

    @Test fun homeCardsOpenCollectionsWithoutRequiringPlaybackReadiness() {
        var selected: ContentCollection? = null
        compose.setContent {
            MaterialTheme {
                HomeScreen("경주", "연결됨", ContentCatalog.all.take(1), ContentCatalog.all.drop(1),
                    false, {}, { selected = it })
            }
        }
        compose.onNodeWithText(ContentCatalog.all.first().title).performScrollTo().performClick()
        assertEquals(ContentCollection.DESTINATION, selected)
        compose.onNodeWithText("출퇴근 퀘스트").performScrollTo().performClick()
        assertEquals(ContentCollection.COMMUTE, selected)
        compose.onNodeWithText("가족 여행 퀘스트").performScrollTo().performClick()
        assertEquals(ContentCollection.TRIP, selected)
        compose.onNodeWithText("추천 에피소드").performScrollTo().performClick()
        assertEquals(ContentCollection.RECOMMENDED, selected)
    }

    @Test fun emptyDestinationStillAllowsGeneralContentSelection() {
        var collection by mutableStateOf(ContentCollection.DESTINATION)
        compose.setContent {
            MaterialTheme {
                ContentListScreen(collection, null, "목적지 없음", emptyList(), ContentCatalog.all,
                    false, { collection = it }, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("관련 콘텐츠가 없어요. 일반 추천에서 다른 이야기를 골라보세요.")
            .assertIsDisplayed()
        compose.onNodeWithText("일반 추천").performClick()
        compose.onNodeWithText(ContentCatalog.all.first().title).assertIsDisplayed()
    }

    @Test fun listOnlyStartsPlaybackWhenAnEpisodeIsChosen() {
        var playedQueue = emptyList<AudioContent>()
        var selected = -1
        val episodes = ContentCatalog.all.take(2)
        compose.setContent {
            MaterialTheme {
                ContentListScreen(ContentCollection.RECOMMENDED, null, "목적지 없음",
                    emptyList(), episodes, true, {}, {}, { queue, index ->
                        playedQueue = queue
                        selected = index
                    })
            }
        }
        assertEquals(-1, selected)
        compose.onNodeWithText(episodes[1].title).performScrollTo().performClick()
        assertEquals(episodes, playedQueue)
        assertEquals(1, selected)
    }

    @Test fun commuteQuestUsesGeneralLearningContent() {
        var playedQueue = emptyList<AudioContent>()
        compose.setContent {
            MaterialTheme {
                ContentListScreen(ContentCollection.COMMUTE, null, "목적지 없음",
                    emptyList(), ContentCatalog.all, true, {}, {}, { queue, _ -> playedQueue = queue })
            }
        }
        compose.onNodeWithText("하늘은 왜 파랄까요?").performScrollTo().performClick()
        assertEquals(listOf("science", "english", "culture"), playedQueue.map { it.id })
    }

    @Test fun reorderingUpdatesRanksWhileRetainingTheCurrentEpisode() {
        val episodes = ContentCatalog.all.take(3)
        var queue by mutableStateOf(episodes)
        val currentId = episodes.first().id
        var selectedId: String? = null
        compose.setContent {
            MaterialTheme {
                ContentListScreen(ContentCollection.RECOMMENDED, null, "목적지 없음",
                    emptyList(), episodes, true, {}, {}, { items, index -> selectedId = items[index].id },
                    playbackQueue = queue, currentContentId = currentId, isPlaying = true,
                    canReorder = true, onMoveItem = { from, to -> queue = PlaybackQueue.move(queue, from, to) })
            }
        }
        compose.onNodeWithText("재생 중").assertIsDisplayed()
        compose.onNodeWithText("1번째").assertDoesNotExist()
        compose.onNodeWithText("순서 변경").assertDoesNotExist()
        compose.onNodeWithContentDescription("${episodes.first().title} 순서 이동")
            .performScrollTo().performClick()
        compose.onNodeWithText("아래로").performClick()
        assertEquals(listOf(episodes[1], episodes[0], episodes[2]), queue)
        compose.onNodeWithText("재생 중").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2번째").assertDoesNotExist()
        compose.onNodeWithText(episodes[1].title).performScrollTo().performClick()
        assertEquals(episodes[1].id, selectedId)
    }

    @Test fun draftHandleReordersWithoutPlaybackAndPlayUsesNewOrder() {
        val episodes = ContentCatalog.all.take(3)
        var played = emptyList<AudioContent>()
        var selected = -1
        compose.setContent {
            MaterialTheme {
                ContentListScreen(ContentCollection.RECOMMENDED, null, "목적지 없음",
                    emptyList(), episodes, true, {}, {}, { queue, index ->
                        played = queue
                        selected = index
                    })
            }
        }
        compose.onNodeWithContentDescription("${episodes.first().title} 순서 이동")
            .performScrollTo().performClick()
        compose.onNodeWithText("맨 아래로").performClick()
        assertEquals(-1, selected)
        compose.onNodeWithText("듣기").assertDoesNotExist()
        compose.onNodeWithText(episodes[1].title).performScrollTo().performClick()
        assertEquals(listOf(episodes[1], episodes[2], episodes[0]), played)
        assertEquals(0, selected)
    }

    @Test fun anotherCollectionsQueueDoesNotColorUnqueuedEpisodes() {
        compose.setContent {
            MaterialTheme {
                ContentListScreen(ContentCollection.COMMUTE, null, "목적지 없음",
                    emptyList(), ContentCatalog.all, true, {}, {}, { _, _ -> },
                    playbackQueue = ContentCatalog.all.take(2),
                    currentContentId = ContentCatalog.all.first().id, isPlaying = true)
            }
        }
        compose.onNodeWithText("재생 순서 · 밝은색부터 순서대로").assertDoesNotExist()
        compose.onNodeWithText("1번째 · 재생 중").assertDoesNotExist()
        compose.onNodeWithText("하늘은 왜 파랄까요?").assertIsDisplayed()
    }

    @Test fun explorePassesQueueAndSelectedIndexToSharedPlayer() {
        var selected = -1
        compose.setContent {
            MaterialTheme {
                ExploreScreen(ContentCatalog.all.take(1), true, { _, index -> selected = index })
            }
        }
        compose.onNodeWithText("역사").performClick()
        compose.onNodeWithText("듣기").performScrollTo().performClick()
        assertEquals(0, selected)
    }
}
