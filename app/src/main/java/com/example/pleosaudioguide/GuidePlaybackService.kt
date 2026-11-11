package com.example.pleosaudioguide

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** 차량의 표준 브라우저에 팟캐스트 > 학습 카테고리 > 등록 음원을 제공한다. */
class GuidePlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    // 직접 등록 음원 + 이번 프로세스에서 앱이 조회한 오디 음원만 노출한다.
    private val playableContents get() = ContentCatalog.all.filter { it.audioUri != null } + OdiiStore.all
    private val categories get() = (ContentCatalog.all + OdiiStore.all).map { it.category }.distinct()
    private val artwork get() = Uri.parse("android.resource://$packageName/${R.mipmap.ic_launcher}")

    private fun folder(id: String, title: String) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true)
            .setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setArtworkUri(artwork).build()).build()

    private fun episode(content: AudioContent) = NarrationAudio.mediaItems(listOf(content)).single()
        .buildUpon().setMediaMetadata(MediaMetadata.Builder().setTitle(content.title)
            .setArtist(content.credit ?: "POTQUEST").setGenre(content.category).setIsBrowsable(false)
            .setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setArtworkUri(content.imageUri?.let(Uri::parse) ?: artwork).build()).build()

    private fun page(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        if (page < 0 || pageSize <= 0) return emptyList()
        val start = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
        return items.drop(start).take(pageSize)
    }

    private val callback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder("root", "POTQUEST"), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = when {
                parentId == "root" -> listOf(folder("podcasts", "팟캐스트"))
                parentId == "podcasts" -> categories.map { folder("category:$it", it) }
                parentId.startsWith("category:") -> playableContents
                    .filter { it.category == parentId.removePrefix("category:") }.map(::episode)
                else -> return Futures.immediateFuture(LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE))
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(page(children, page, pageSize), params))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                "root" -> folder("root", "POTQUEST")
                "podcasts" -> folder("podcasts", "팟캐스트")
                else -> playableContents.find { it.id == mediaId }?.let(::episode)
                    ?: categories.find { "category:$it" == mediaId }?.let { folder(mediaId, it) }
            }
            return Futures.immediateFuture(if (item == null)
                LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE) else LibraryResult.ofItem(item, null))
        }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, search(query).size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(page(search(query), page, pageSize), params))

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
            // 외부에서 전달한 임의 URI 대신 등록된 콘텐츠 ID만 재생한다.
            val resolved = mediaItems.map { request ->
                playableContents.find { it.id == request.mediaId }?.let(::episode)
                    ?: return Futures.immediateFailedFuture(IllegalArgumentException("Unknown or unavailable episode"))
            }
            return Futures.immediateFuture(resolved)
        }
    }

    private fun search(query: String): List<MediaItem> = if (query.isBlank()) emptyList() else
        playableContents.filter { it.title.contains(query, true) || it.category.contains(query, true) }.map(::episode)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        session = MediaLibrarySession.Builder(this, player, callback).build()
        // 연결/라이브러리 조회만으로 play()를 호출하지 않는다.
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
