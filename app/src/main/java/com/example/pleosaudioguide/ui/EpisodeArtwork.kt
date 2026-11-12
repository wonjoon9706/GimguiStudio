package com.example.pleosaudioguide.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.pleosaudioguide.AudioContent

/** 기존 기본 커버를 바탕에 유지하므로 이미지 없음/실패/로딩 시에도 빈칸이 생기지 않는다. */
@Composable
fun EpisodeArtwork(content: AudioContent?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        CoverArt(Modifier.matchParentSize())
        if (!content?.imageUri.isNullOrBlank()) {
            AsyncImage(
                model = content?.imageUri,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
