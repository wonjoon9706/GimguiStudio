package com.example.pleosaudioguide

import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** 운행 종료/학습 진입 상태는 서비스에서도 강제한다. 위치/큐는 보존하고 자동 재개하지 않는다. */
class TripPlaybackGuard(private val player: Player, private val trips: StateFlow<TripHistoryState>) : Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        player.addListener(this)
        scope.launch {
            trips.map { it.showEndScreen }.distinctUntilChanged().collect { enforcePause() }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { enforcePause() }

    private fun enforcePause() {
        if (trips.value.showEndScreen && player.playWhenReady) player.pause()
    }

    fun close() {
        scope.cancel()
        player.removeListener(this)
    }
}
