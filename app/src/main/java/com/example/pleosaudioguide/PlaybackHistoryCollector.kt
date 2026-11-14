package com.example.pleosaudioguide

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player

/** 서비스 소유: Activity가 없어도 실제 재생을 추적하고 시크 구간을 청취로 세지 않는다. */
class PlaybackHistoryCollector(private val player: Player, private val history: ListeningHistoryStore,
    private val canSavePosition: (String) -> Boolean = { true }) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val tracker = ListeningTracker { content, range, duration ->
        history.record(content, range, duration)
    }
    private fun content(id: String?) = ContentCatalog.all.find { it.id == id } ?: OdiiStore.find(id)
    private var savedPosition: PlaybackResume? = null
    private fun savePosition(item: AudioContent?, position: Long, duration: Long) {
        if (item?.audioUri == null || duration <= 0 || !canSavePosition(item.id)) return
        val value = PlaybackResume(item, position.coerceIn(0, duration), duration)
        if (value != savedPosition) {
            savedPosition = value
            history.savePlaybackResume(value)
        }
    }
    private fun sample() {
        val item = content(player.currentMediaItem?.mediaId)
        tracker.sample(item, player.currentPosition, player.duration, player.isPlaying,
            SystemClock.elapsedRealtime(), player.playbackParameters.speed)
        savePosition(item, player.currentPosition, player.duration)
    }
    private val tick = object : Runnable {
        override fun run() { sample(); main.postDelayed(this, 5000) }
    }

    private val checkpoint: () -> Unit = { sample() }
    init {
        player.addListener(this)
        history.addCheckpoint(checkpoint)
        sample()
        main.postDelayed(tick, 5000)
    }

    override fun onEvents(player: Player, events: Player.Events) { sample() }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo, reason: Int) {
        // 항목 전환 직전 위치를 이전 콘텐츠에 저장한다. seek이면 새 위치만 저장한다.
        if (oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) {
            savePosition(content(oldPosition.mediaItem?.mediaId), oldPosition.positionMs,
                savedPosition?.takeIf { it.content.id == oldPosition.mediaItem?.mediaId }?.durationMs ?: 0)
        }
        // oldPosition은 seek 전/자동 다음 곡 전의 끝 위치이다.
        val oldDuration = savedPosition?.takeIf {
            it.content.id == oldPosition.mediaItem?.mediaId
        }?.durationMs ?: 0
        tracker.sample(content(oldPosition.mediaItem?.mediaId), oldPosition.positionMs,
            oldDuration, false, SystemClock.elapsedRealtime(), player.playbackParameters.speed)
        tracker.reset()
        sample()
    }

    fun close() {
        history.removeCheckpoint(checkpoint)
        sample()
        main.removeCallbacksAndMessages(null)
        player.removeListener(this)
    }
}
