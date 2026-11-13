package com.example.pleosaudioguide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.ui.AudioPlayerScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AudioPlayerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enteringAndLeavingDoesNotRequestPlaybackOrChangeTheQueue() {
        var requests = 0
        var backs = 0
        compose.setContent {
            MaterialTheme {
                AudioPlayerScreen(ContentCatalog.all.take(2), ContentCatalog.all.first().id,
                    true, true, 35_000, 120_000, true, false, false,
                    { backs++ }, { requests++ }, { requests++ }, { requests++ }, { requests++ })
            }
        }
        compose.onNodeWithText("0:35").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("‹ 홈으로").performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(0, requests)
        }
    }

    @Test fun imageSwipesFollowQueueOrderAndStopAtBothEnds() {
        val queue = ContentCatalog.all.take(3)
        var currentId by mutableStateOf(queue.first().id)
        var selections = 0
        compose.setContent {
            MaterialTheme {
                AudioPlayerScreen(queue, currentId, true, false, 0, 120_000, true, false, false,
                    {}, {}, {}, {}, { currentId = it; selections++ })
            }
        }
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipe(Offset(width * .25f, centerY), Offset(width * .75f, centerY), 600) }
        compose.runOnIdle { assertEquals(0, selections) }
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipe(Offset(width * .75f, centerY), Offset(width * .25f, centerY), 600) }
        compose.runOnIdle { assertEquals(queue[1].id, currentId) }
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipe(Offset(width * .75f, centerY), Offset(width * .25f, centerY), 600) }
        compose.runOnIdle { assertEquals(queue[2].id, currentId) }
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipe(Offset(width * .75f, centerY), Offset(width * .25f, centerY), 600) }
        compose.runOnIdle {
            assertEquals(queue[2].id, currentId)
            assertEquals(2, selections)
        }
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipe(Offset(width * .25f, centerY), Offset(width * .75f, centerY), 600) }
        compose.runOnIdle { assertEquals(queue[1].id, currentId) }
    }

    @Test fun transportControlsDoNotSelectAnotherEpisodeAndReflectSharedState() {
        var playing by mutableStateOf(true)
        var selected = 0
        val directions = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                AudioPlayerScreen(ContentCatalog.all.take(2), ContentCatalog.all.first().id,
                    true, playing, 35_000, 120_000, true, false, false,
                    {}, { playing = !playing }, { directions.add(it) }, {}, { selected++ })
            }
        }
        compose.onNodeWithContentDescription("30초 뒤로").performScrollTo().performClick()
        compose.onNodeWithContentDescription("30초 앞으로").performClick()
        compose.onNodeWithContentDescription("일시정지").performClick()
        compose.onNodeWithContentDescription("재생").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(false, true), directions)
            assertEquals(0, selected)
        }
    }

    @Test fun queueButtonRequestsNavigationWithoutSelectingOrPlayingContent() {
        val queue = ContentCatalog.all.take(2)
        var opens = 0
        var selections = 0
        compose.setContent {
            MaterialTheme {
                AudioPlayerScreen(queue, queue.first().id, true, false, 0, 120_000, true, false, false,
                    {}, {}, {}, {}, { selections++ }, onShowQueue = { opens++ })
            }
        }
        compose.onNodeWithText("재생 목록 · 2개").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, opens)
            assertEquals(0, selections)
        }
    }

    @Test fun singleEpisodeCannotSwipeAndUnsupportedSeekingIsDisabled() {
        var requests = 0
        compose.setContent {
            MaterialTheme {
                AudioPlayerScreen(ContentCatalog.all.take(1), ContentCatalog.all.first().id,
                    true, false, 0, 0, false, false, true,
                    {}, {}, { requests++ }, {}, { requests++ })
            }
        }
        compose.onNodeWithTag("playerArtworkCarousel").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("30초 뒤로").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("30초 앞으로").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun noCurrentContentNeverStartsAnArbitraryEpisode() {
        var requests = 0
        compose.setContent {
            MaterialTheme {
                AudioPlayerScreen(ContentCatalog.all, null, false, false, 0, 0, false, false, false,
                    {}, { requests++ }, { requests++ }, { requests++ }, { requests++ })
            }
        }
        compose.onNodeWithText("재생 중인 콘텐츠가 없어요.").assertIsDisplayed()
        compose.onNodeWithContentDescription("재생").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun smallWindowCanReachAllMainControls() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(320.dp, 480.dp)) {
                    AudioPlayerScreen(ContentCatalog.all.take(1), ContentCatalog.all.first().id,
                        true, false, 0, 120_000, true, false, false, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("30초 뒤로").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("재생").assertIsDisplayed()
        compose.onNodeWithContentDescription("30초 앞으로").assertIsDisplayed()
        compose.onNodeWithText("재생 목록 · 1개").performScrollTo().assertIsDisplayed()
    }
}
