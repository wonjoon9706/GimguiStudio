package com.example.pleosaudioguide

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** 녹음 음원 도입 시 콘텐츠에 로컬 audioUri를 등록해 공통 플레이어에 전달한다. */
object NarrationAudio {
    fun mediaItems(contents: List<AudioContent>): List<MediaItem> = contents.map { content ->
        MediaItem.Builder().setMediaId(content.id).setUri(requireNotNull(content.audioUri))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(content.title).build()).build()
    }
}
