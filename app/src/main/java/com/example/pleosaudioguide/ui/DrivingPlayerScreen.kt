package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.AudioContent

/** 재생 화면의 이미지/타이틀/전송 아이콘을 공유하고 주행 중에는 기본 제어만 제공한다. */
@Composable
fun DrivingPlayerScreen(
    title: String,
    isPlaying: Boolean,
    hasCurrentAudio: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    preparing: Boolean,
    usingPleosVoice: Boolean,
    positionMs: Long,
    durationMs: Long,
    canResume: Boolean,
    controlsReady: Boolean,
    maxTitleLength: Int,
    onToggle: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    content: AudioContent? = null,
    queue: List<AudioContent> = emptyList(),
    canSeek: Boolean = false,
    onSeek30: (Boolean) -> Unit = {},
    onSelectContent: (String) -> Unit = {}
) {
    BackHandler {}
    BoxWithConstraints(modifier.fillMaxSize().testTag("drivingPlayer")) {
        val compact = maxHeight < 480.dp
        val artworkHeight = if (compact) 48.dp else (maxHeight * 0.37f).coerceIn(100.dp, 300.dp)
        val limit = maxTitleLength.coerceIn(0, 80)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
            AppHeader(compact = compact)
            Text("지금 듣는 이야기", Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            val currentIndex = queue.indexOfFirst { it.id == content?.id }
            if (hasCurrentAudio && currentIndex >= 0) {
                // 현재 항목과 인접 항목만 노출: 한 번에 여러 콘텐츠를 건너뛰지 않는다.
                val adjacent = queue.subList(
                    if (hasPrevious) (currentIndex - 1).coerceAtLeast(0) else currentIndex,
                    if (hasNext) (currentIndex + 2).coerceAtMost(queue.size) else currentIndex + 1)
                ArtworkCarousel(adjacent, content!!.id, controlsReady && !preparing,
                    { id ->
                        val target = queue.indexOfFirst { it.id == id }
                        if (target == currentIndex - 1 && hasPrevious || target == currentIndex + 1 && hasNext)
                            onSelectContent(id)
                    }, Modifier.fillMaxWidth().height(artworkHeight))
            } else Box(Modifier.fillMaxWidth().height(artworkHeight), contentAlignment = Alignment.Center) {
                EpisodeArtwork(content, Modifier.fillMaxWidth(0.8f).fillMaxHeight())
            }
            if (limit > 0) Text(title.ifBlank { "선택된 콘텐츠 없음" }.take(limit),
                Modifier.fillMaxWidth().testTag("drivingTitle"), textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            if (!compact) {
                content?.category?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                content?.credit?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (!usingPleosVoice && hasCurrentAudio && durationMs > 0) {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "재생 진행률" })
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(playerTimeLabel(positionMs))
                        Text(playerTimeLabel(durationMs))
                    }
                }
            }
            val label = if (preparing) "준비 중" else if (!hasCurrentAudio && canResume) "이어 듣기"
                else if (isPlaying) { if (usingPleosVoice) "중지" else "일시정지" } else "재생"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
                Seek30Button(false, canSeek && hasCurrentAudio && controlsReady && !preparing) { onSeek30(false) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(onClick = if (hasCurrentAudio) onToggle else onResume,
                        enabled = controlsReady && !preparing && (hasCurrentAudio || canResume),
                        modifier = Modifier.size(if (compact) 64.dp else 80.dp)
                            .semantics { contentDescription = label }, shape = CircleShape) {
                        if (preparing) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = LocalContentColor.current)
                        else TransportGlyph(if (isPlaying) {
                            if (usingPleosVoice) "stop" else "pause"
                        } else "play", Modifier.size(36.dp))
                    }
                }
                Seek30Button(true, canSeek && hasCurrentAudio && controlsReady && !preparing) { onSeek30(true) }
            }
            if (!compact) Text(if (usingPleosVoice && hasCurrentAudio) "시연 음성은 중지 후 처음부터 재생해요"
                else "썸네일을 좌우로 넘기면 이전·다음 오디오를 들을 수 있어요",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
