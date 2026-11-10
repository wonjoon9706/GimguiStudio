package com.example.pleosaudioguide

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GuideViewModel(application: Application) : AndroidViewModel(application) {
    private val bookmarks = application.getSharedPreferences("playback_bookmark", 0)
    var resumeContent by mutableStateOf(ContentCatalog.all.find { it.id == bookmarks.getString("id", null) })
        private set
    var hasCurrentAudio by mutableStateOf(false)
        private set
    private var pendingNativeIndex: Int? = null
    private var lastBookmarkSecond = -1L
    private val navigation = NavigationSource(application)
    val navigationState = navigation.state
    var audioStatus by mutableStateOf("시연 음성 엔진 준비 중")
        private set
    var voiceReady by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    var playerReady by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var hasPrevious by mutableStateOf(false)
        private set
    var hasNext by mutableStateOf(false)
        private set
    var usingPleosVoice by mutableStateOf(true)
        private set
    private var nativeQueue = emptyList<AudioContent>()
    private var nativeIndex = 0
    private var nativeActive = false
    private val pleosVoice = PleosNarration(application,
        onState = { audioStatus = it },
        onReady = { voiceReady = true },
        onStarted = {
            isPlaying = true; preparing = false; hasCurrentAudio = true
            nativeQueue.getOrNull(nativeIndex)?.let { saveBookmark(it, 0) }
            audioStatus = "Pleos 기기 내 시연 음성 재생 중"
        },
        onFinished = { completed ->
            nativeActive = false
            isPlaying = false
            preparing = false
            val nextIndex = pendingNativeIndex
            pendingNativeIndex = null
            if (nextIndex != null) {
                nativeIndex = nextIndex
                startNative()
            } else if (completed && nativeIndex < nativeQueue.lastIndex) {
                nativeIndex++
                startNative()
            } else if (completed) {
                clearBookmark()
                hasCurrentAudio = false
                audioStatus = "시연 음성 재생 완료"
            }
        })
    private var closed = false
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        application, SessionToken(application, ComponentName(application, GuidePlaybackService::class.java))
    ).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { updatePlayer() }
        override fun onPlayerError(error: PlaybackException) {
            audioStatus = "재생 실패: ${error.errorCodeName}"
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!usingPleosVoice && playbackState == Player.STATE_ENDED) {
                clearBookmark(); hasCurrentAudio = false; audioStatus = "재생 완료"
            }
        }
    }

    init {
        controllerFuture.addListener({
            if (!closed) {
                try {
                    controller = controllerFuture.get().also { it.addListener(listener) }
                    playerReady = true
                    updatePlayer()
                } catch (error: Exception) {
                    audioStatus = "플레이어 연결 실패: ${error.message}"
                }
            }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch {
            while (true) {
                updatePlayer()
                delay(500)
            }
        }
    }

    fun refreshDestination() = navigation.refresh()

    fun play(contents: List<AudioContent>, index: Int = 0, startPositionMs: Long = 0) {
        if (contents.isEmpty() || index !in contents.indices || preparing || !playerReady || !voiceReady) return
        if (nativeActive) return
        usingPleosVoice = contents[index].audioUri == null
        if (usingPleosVoice) {
            controller?.pause()
            nativeQueue = contents
            nativeIndex = index
            startNative()
            return
        }
        val fileQueue = contents.filter { it.audioUri != null }
        controller?.run {
            setMediaItems(NarrationAudio.mediaItems(fileQueue), fileQueue.indexOf(contents[index]), startPositionMs)
            prepare()
            play()
        }
        hasCurrentAudio = true
        audioStatus = "등록 음원 재생"
    }

    private fun startNative() {
        val content = nativeQueue.getOrNull(nativeIndex) ?: return
        title = content.title
        positionMs = 0
        durationMs = 0
        hasPrevious = nativeIndex > 0
        hasNext = nativeIndex < nativeQueue.lastIndex
        nativeActive = true
        preparing = true
        audioStatus = "Pleos 기기 내 시연 음성 재생 요청"
        pleosVoice.speak(content)
    }

    fun togglePlayback() {
        if (usingPleosVoice) {
            if (nativeActive) { audioStatus = "시연 음성 중지"; pleosVoice.stop() }
            else startNative()
            return
        }
        controller?.run { if (isPlaying) pause() else if (mediaItemCount > 0) {
            if (playbackState == Player.STATE_ENDED) seekToDefaultPosition(0)
            play()
        } }
    }

    fun previous() {
        if (usingPleosVoice) {
            moveNative(-1)
        } else controller?.seekToPreviousMediaItem()
    }
    fun next() {
        if (usingPleosVoice) {
            moveNative(1)
        } else controller?.seekToNextMediaItem()
    }

    private fun moveNative(offset: Int) {
        val target = nativeIndex + offset
        if (preparing || target !in nativeQueue.indices) return
        if (nativeActive) {
            pendingNativeIndex = target
            preparing = true
            pleosVoice.stop()
        } else {
            nativeIndex = target
            startNative()
        }
    }

    fun resumeLast() {
        val content = resumeContent ?: return
        play(listOf(content), startPositionMs = bookmarks.getLong("position", 0))
    }

    private fun saveBookmark(content: AudioContent, position: Long) {
        resumeContent = content
        bookmarks.edit().putString("id", content.id).putLong("position", position).apply()
    }

    private fun clearBookmark() {
        resumeContent = null
        bookmarks.edit().clear().apply()
    }

    private fun updatePlayer() {
        if (usingPleosVoice) return
        controller?.let {
            title = it.mediaMetadata.title?.toString().orEmpty()
            isPlaying = it.isPlaying
            positionMs = it.currentPosition.coerceAtLeast(0)
            durationMs = it.duration.coerceAtLeast(0)
            hasPrevious = it.hasPreviousMediaItem()
            hasNext = it.hasNextMediaItem()
            val second = positionMs / 5000
            if (it.playbackState != Player.STATE_ENDED && it.mediaItemCount > 0 && second != lastBookmarkSecond) {
                lastBookmarkSecond = second
                ContentCatalog.all.find { content -> content.id == it.currentMediaItem?.mediaId }
                    ?.let { content -> saveBookmark(content, positionMs) }
            }
        }
    }

    override fun onCleared() {
        closed = true
        navigation.close()
        pleosVoice.close()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
