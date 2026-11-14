package com.example.pleosaudioguide

import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** null은 닫기: 저장 위치를 유지한 채 일시정지한다. */
data class AutomaticResumePrompt(val saved: PlaybackResume, val choose: (Boolean?) -> Unit)

/** 자동 전환의 0초 저장보다 먼저 기존 위치를 조회한다. Activity가 없어도 서비스에서 대기한다. */
class AutomaticPlaybackResume(private val player: Player, private val history: ListeningHistoryStore) : Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lookup: Job? = null
    private var pendingId: String? = null
    private var pendingSaved: PlaybackResume? = null
    private var generation = 0L
    private var closed = false
    fun canSavePosition(id: String) = id != pendingId
    private fun canChoose() = history.state.value.let {
        !it.showEndScreen && (it.browsingAllowed || it.vehicleDataValid)
    }

    init {
        player.addListener(this)
        scope.launch {
            history.state.map { Triple(it.browsingAllowed, it.vehicleDataValid, it.showEndScreen) }
                .distinctUntilChanged().collect {
                    if (!canChoose()) pendingSaved?.let { saved -> finish(saved.positionMs, false) }
                }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo, reason: Int) {
        if (pendingId != null && newPosition.mediaItem?.mediaId != pendingId) cancelPending()
        if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
            oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId) return
        val content = ContentCatalog.all.find { it.id == newPosition.mediaItem?.mediaId }
            ?: OdiiStore.find(newPosition.mediaItem?.mediaId) ?: return
        cancelPending()
        pendingId = content.id
        val request = generation
        player.pause()
        lookup = scope.launch {
            try {
                val saved = history.playbackResume(content).awaitQuiz()
                if (closed || request != generation || player.currentMediaItem?.mediaId != content.id) return@launch
                if (!canChoose()) {
                    finish(saved?.takeIf { it.canResume }?.positionMs ?: 0, false)
                    return@launch
                }
                if (saved?.canResume != true) { finish(0, true); return@launch }
                pendingSaved = saved
                history.automaticResumePrompt.value = AutomaticResumePrompt(saved) { fromBeginning ->
                    if (!closed && request == generation && player.currentMediaItem?.mediaId == content.id) {
                        if (canChoose()) finish(if (fromBeginning == true) 0 else saved.positionMs, fromBeginning != null)
                        else finish(saved.positionMs, false)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                // 실패 시 기존 기록을 덮어쓰거나 임의로 재생하지 않는다. 다음 사용자 이동으로 재시도한다.
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (pendingId != null && playWhenReady) player.pause()
    }

    private fun finish(position: Long, play: Boolean) {
        val id = pendingId ?: return
        if (player.currentMediaItem?.mediaId != id) { cancelPending(); return }
        // seek 이벤트가 기존 위치를 덮어쓰지 않도록 요청 해제 전에 수행한다.
        player.seekTo(position)
        pendingId = null
        pendingSaved = null
        generation++
        history.automaticResumePrompt.value = null
        if (play && !history.state.value.showEndScreen) player.play()
    }

    private fun cancelPending() {
        generation++
        lookup?.cancel()
        lookup = null
        pendingId = null
        pendingSaved = null
        history.automaticResumePrompt.value = null
    }

    fun close() {
        pendingSaved?.let { finish(it.positionMs, false) }
        closed = true
        cancelPending()
        scope.cancel()
        player.removeListener(this)
    }
}
