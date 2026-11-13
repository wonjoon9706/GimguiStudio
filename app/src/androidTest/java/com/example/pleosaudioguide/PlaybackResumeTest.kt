package com.example.pleosaudioguide

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pleosaudioguide.ui.ResumePlaybackDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class PlaybackResumeTest {
    @get:Rule val compose = createComposeRule()
    private val audio = ContentCatalog.all.first().copy(audioUri = "https://example.org/a",
        imageUri = "https://example.org/image", credit = "출처")

    @Test fun modalShowsSavedPositionAndOnlyExplicitButtonsChoosePosition() {
        var chosen: Long? = null
        val saved = PlaybackResume(audio, 150_000, 600_000)
        compose.setContent {
            MaterialTheme {
                ResumePlaybackDialog(saved, { chosen = 0 }, { chosen = saved.positionMs }, { chosen = -1 })
            }
        }
        compose.onNodeWithText("마지막 재생 위치 2:30 / 10:00").assertIsDisplayed()
        compose.runOnIdle { assertNull(chosen) }
        compose.onNodeWithText("이어듣기").performClick()
        compose.runOnIdle { assertEquals(150_000L, chosen) }
        compose.onNodeWithText("처음부터").performClick()
        compose.runOnIdle { assertEquals(0L, chosen) }
    }

    @Test fun perContentPositionsSurviveDatabaseReopenWithoutChangingHeardRanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.cacheDir.resolve("resume-${System.nanoTime()}.db")
        fun open() = ListeningHistoryStore(
            Room.databaseBuilder(context, ListeningDatabase::class.java, file.absolutePath).build())
        val first = open()
        val secondAudio = audio.copy(id = "second", audioUri = "https://example.org/b")
        first.record(audio, HeardRange(0, 20_000), 600_000)
        first.savePlaybackResume(PlaybackResume(audio, 480_000, 600_000))
        first.savePlaybackResume(PlaybackResume(secondAudio, 90_000, 300_000))
        first.close().get(5, TimeUnit.SECONDS)
        val restored = open()
        try {
            val saved = restored.playbackResume(audio).get(5, TimeUnit.SECONDS)!!
            assertEquals(480_000L, saved.positionMs)
            assertEquals(audio.title, saved.content.title)
            assertEquals(audio.imageUri, saved.content.imageUri)
            assertTrue(saved.canResume)
            assertEquals(90_000L, restored.playbackResume(secondAudio).get(5, TimeUnit.SECONDS)!!.positionMs)
            assertEquals(secondAudio.id, restored.latestPlaybackResume().get(5, TimeUnit.SECONDS)!!.content.id)
            assertNull(restored.playbackResume(audio.copy(script = "변경")).get(5, TimeUnit.SECONDS))
            assertEquals(20_000L, restored.snapshot().get(5, TimeUnit.SECONDS).single().listenedMs)
            assertFalse(restored.snapshot().get(5, TimeUnit.SECONDS).single().canUseFullScriptForQuiz)
            restored.savePlaybackResume(PlaybackResume(audio, 600_000, 600_000))
            assertFalse(restored.playbackResume(audio).get(5, TimeUnit.SECONDS)!!.canResume)
            restored.savePlaybackResume(PlaybackResume(audio, 0, 600_000))
            assertFalse(restored.playbackResume(audio).get(5, TimeUnit.SECONDS)!!.canResume)
        } finally { restored.close().get(5, TimeUnit.SECONDS); context.deleteDatabase(file.absolutePath) }
    }
}
