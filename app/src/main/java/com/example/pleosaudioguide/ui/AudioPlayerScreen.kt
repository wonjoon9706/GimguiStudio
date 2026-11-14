package com.example.pleosaudioguide.ui

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.AudioContent
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 표시와 사용자 입력만 담당한다. 플레이어 생성/prepare/play는 화면 수명주기와 연결하지 않는다. */
@Composable
fun AudioPlayerScreen(
    queue: List<AudioContent>,
    currentContentId: String?,
    hasCurrentAudio: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    canSeek: Boolean,
    preparing: Boolean,
    usingPleosVoice: Boolean,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek30: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSelectContent: (String) -> Unit,
    modifier: Modifier = Modifier,
    onShowQueue: () -> Unit = {}
) {
    val current = queue.find { it.id == currentContentId }.takeIf { hasCurrentAudio }
    var scrubFraction by remember(currentContentId) { mutableStateOf<Float?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val artworkHeight = (maxHeight * 0.37f).coerceIn(148.dp, 300.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppHeader()
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start).heightIn(min = 48.dp)) { Text("‹ 홈으로") }
            Text("지금 듣는 이야기", Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (current == null) {
                Spacer(Modifier.height(24.dp))
                CoverArt(Modifier.size(148.dp))
                Text("재생 중인 콘텐츠가 없어요.", style = MaterialTheme.typography.titleLarge)
                Text("홈에서 듣고 싶은 콘텐츠를 선택해 주세요.")
            } else {
                ArtworkCarousel(queue, current.id, !preparing, onSelectContent,
                    Modifier.fillMaxWidth().height(artworkHeight))
                Text(current.title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(current.category, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge)
                current.credit?.let {
                    Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.fillMaxWidth()) {
                    Slider(
                        value = scrubFraction ?: if (durationMs > 0)
                            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { scrubFraction = it },
                        onValueChangeFinished = {
                            scrubFraction?.let { onSeekTo((it * durationMs).toLong()) }
                            scrubFraction = null
                        },
                        enabled = canSeek && !preparing,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "재생 위치" }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(playerTimeLabel(scrubFraction?.let { (it * durationMs).toLong() } ?: positionMs))
                        Text(if (durationMs > 0) playerTimeLabel(durationMs) else "--:--")
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Seek30Button(false, canSeek && !preparing) { onSeek30(false) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onTogglePlayback, enabled = !preparing,
                            modifier = Modifier.size(80.dp).semantics {
                                contentDescription = if (preparing) "재생 준비 중"
                                else if (isPlaying) { if (usingPleosVoice) "중지" else "일시정지" }
                                else if (usingPleosVoice) "처음부터 재생" else "재생"
                            },
                            shape = CircleShape
                        ) {
                            if (preparing) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = LocalContentColor.current)
                            else TransportGlyph(if (isPlaying) {
                                if (usingPleosVoice) "stop" else "pause"
                            } else "play", Modifier.size(36.dp))
                        }
                    }
                    Seek30Button(true, canSeek && !preparing) { onSeek30(true) }
                }
                if (usingPleosVoice) {
                    Text("이 콘텐츠는 위치 이동을 지원하지 않아요. 중지 후에는 처음부터 재생됩니다.",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                } else if (!canSeek) {
                    Text("재생 위치를 확인하는 중이거나 위치 이동을 지원하지 않는 콘텐츠예요.",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = onShowQueue,
                    modifier = Modifier.heightIn(min = 56.dp).fillMaxWidth()
                ) { Text("재생 목록 · ${queue.size}개") }
            }
        }
    }
}

/** 사용자 드래그만 선택 명령으로 전달한다. 화면 진입·자동 다음 곡·큐 재배열의 스크롤은 명령을 보내지 않는다. */
@Composable
internal fun ArtworkCarousel(
    queue: List<AudioContent>,
    currentId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier
) {
    if (queue.isEmpty()) {
        CoverArt(modifier)
        return
    }
    // 후보가 줄거나 ID 순서가 바뀌면 이전 Pager의 페이지/키 캐시를 재사용하지 않는다.
    val snapshot = queue.toList()
    key(snapshot.map { it.id }) {
        ArtworkCarouselPages(snapshot, currentId, enabled, onSelect, modifier)
    }
}

@Composable
private fun ArtworkCarouselPages(
    queue: List<AudioContent>, currentId: String, enabled: Boolean,
    onSelect: (String) -> Unit, modifier: Modifier
) {
    val currentIndex = queue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = currentIndex, pageCount = { queue.size })
    val latestQueue by rememberUpdatedState(queue)
    val latestId by rememberUpdatedState(currentId)
    val latestSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(currentId, queue.map { it.id }) {
        if (pager.currentPage != currentIndex) pager.scrollToPage(currentIndex)
    }
    LaunchedEffect(pager) {
        var userDragged = false
        launch {
            pager.interactionSource.interactions.collect {
                if (it is DragInteraction.Start) userDragged = true
            }
        }
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.collect { (scrolling, page) ->
            if (!scrolling && userDragged) {
                userDragged = false
                latestQueue.getOrNull(page)?.id?.takeIf { it != latestId }?.let(latestSelect)
            }
        }
    }
    BoxWithConstraints(modifier.testTag("playerArtworkCarousel")) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = enabled && queue.size > 1,
            contentPadding = PaddingValues(horizontal = if (queue.size > 1) maxWidth * 0.18f else maxWidth * 0.12f),
            pageSpacing = 12.dp,
            key = { queue[it].id }
        ) { page ->
            val distance = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1f - distance * 0.14f
                scaleY = 1f - distance * 0.14f
                alpha = 1f - distance * 0.55f
            }, contentAlignment = Alignment.Center) {
                EpisodeArtwork(queue[page], Modifier.fillMaxSize().semantics {
                    contentDescription = "${queue[page].title} 콘텐츠 이미지"
                })
            }
        }
    }
}

@Composable
internal fun Seek30Button(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedIconButton(
            onClick = onClick, enabled = enabled,
            modifier = Modifier.size(64.dp).semantics {
                contentDescription = if (forward) "30초 앞으로" else "30초 뒤로"
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                TransportGlyph(if (forward) "forward" else "backward", Modifier.size(42.dp))
                Text("30", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text(if (forward) "30초 앞으로" else "30초 뒤로", style = MaterialTheme.typography.labelMedium)
    }
}

internal fun playerTimeLabel(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
