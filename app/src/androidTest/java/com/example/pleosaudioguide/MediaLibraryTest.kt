package com.example.pleosaudioguide

import android.content.ComponentName
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class MediaLibraryTest {
    @Test fun browserConnectsAndProvidesPodcastRootWithoutAutoplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var future: ListenableFuture<MediaBrowser>
        instrumentation.runOnMainSync {
            future = MediaBrowser.Builder(context,
                SessionToken(context, ComponentName(context, GuidePlaybackService::class.java))).buildAsync()
        }
        val browser = future.get(15, TimeUnit.SECONDS)
        try {
            lateinit var root: ListenableFuture<androidx.media3.session.LibraryResult<androidx.media3.common.MediaItem>>
            instrumentation.runOnMainSync { root = browser.getLibraryRoot(null) }
            assertEquals("root", root.get(10, TimeUnit.SECONDS).value?.mediaId)
            lateinit var children: ListenableFuture<androidx.media3.session.LibraryResult<com.google.common.collect.ImmutableList<androidx.media3.common.MediaItem>>>
            instrumentation.runOnMainSync { children = browser.getChildren("root", 0, 10, null) }
            assertEquals("podcasts", children.get(10, TimeUnit.SECONDS).value?.single()?.mediaId)
            instrumentation.runOnMainSync {
                assertFalse(browser.isPlaying)
                assertEquals(0, browser.mediaItemCount)
            }
        } finally {
            instrumentation.runOnMainSync { MediaBrowser.releaseFuture(future) }
        }
    }
}
