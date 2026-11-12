package com.example.pleosaudioguide

/** 실제 음원 길이 안에서만 탐색한다. 알 수 없는 길이는 호출 측에서 탐색을 비활성화한다. */
object PlaybackPosition {
    fun clamp(positionMs: Long, durationMs: Long): Long =
        positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))

    fun skip30(positionMs: Long, durationMs: Long, forward: Boolean): Long {
        val current = clamp(positionMs, durationMs)
        return if (forward) current + minOf(30_000L, durationMs.coerceAtLeast(0L) - current)
        else (current - 30_000L).coerceAtLeast(0L)
    }
}
