package com.example.pleosaudioguide.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.AudioContent

enum class ContentCollection {
    DESTINATION, COMMUTE, TRIP, RECOMMENDED
}

/** 홈의 진입점만 바꾸며, 목록 이동은 재생 큐를 변경하지 않는다. */
@Composable
fun ContentListScreen(
    collection: ContentCollection,
    destinationName: String?,
    destinationStatus: String,
    recommendations: List<AudioContent>,
    episodes: List<AudioContent>,
    canPlay: Boolean,
    onSelectCollection: (ContentCollection) -> Unit,
    onBack: () -> Unit,
    onPlay: (List<AudioContent>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onlineStatus: String? = null,
    playbackQueue: List<AudioContent> = emptyList(),
    currentContentId: String? = null,
    isPlaying: Boolean = false,
    canReorder: Boolean = false,
    onMoveItem: (Int, Int) -> Unit = { _, _ -> }
) {
    val scrollStates = rememberSaveableStateHolder()
    val quest = collection == ContentCollection.COMMUTE || collection == ContentCollection.TRIP
    val contents = when (collection) {
        ContentCollection.DESTINATION -> recommendations
        ContentCollection.COMMUTE -> episodes.filter { it.category in listOf("일반 상식", "어학 학습", "기타 교양") }
        ContentCollection.TRIP -> (recommendations + episodes.filter {
            it.category in listOf("관광·역사", "어학 학습")
        }).distinctBy { it.id }
        ContentCollection.RECOMMENDED -> episodes
    }
    val ranks = playbackQueue.mapIndexed { index, content -> content.id to index }.toMap()
    val visibleIds = contents.map { it.id }.toSet()
    val queued = playbackQueue.filter { it.id in visibleIds }
    val displayed = queued + contents.filter { it.id !in ranks }
    var editing by rememberSaveable(collection.name) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("POTQUEST", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onBack) { Text("홈으로") }
        }
        HorizontalDivider()
        Text("콘텐츠를 골라주세요", Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            when (collection) {
                ContentCollection.DESTINATION -> destinationName?.let { "$it 관련 콘텐츠" }
                    ?: "목적지를 설정하면 관련 이야기를 추천해요"
                ContentCollection.COMMUTE -> "출퇴근길에 듣는 상식 · 교양 · 어학"
                ContentCollection.TRIP -> "가족 여행에 어울리는 관광 · 역사 · 어학"
                ContentCollection.RECOMMENDED -> "목적지와 관계없이 자유롭게 골라보세요"
            },
            Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectionTab("목적지 추천", collection == ContentCollection.DESTINATION,
                Modifier.weight(1f)) { onSelectCollection(ContentCollection.DESTINATION) }
            CollectionTab("오늘의 퀘스트", quest, Modifier.weight(1f)) {
                if (!quest) onSelectCollection(ContentCollection.COMMUTE)
            }
            CollectionTab("일반 추천", collection == ContentCollection.RECOMMENDED,
                Modifier.weight(1f)) { onSelectCollection(ContentCollection.RECOMMENDED) }
        }
        if (quest) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = collection == ContentCollection.COMMUTE,
                    onClick = { onSelectCollection(ContentCollection.COMMUTE) },
                    label = { Text("출퇴근 퀘스트") })
                FilterChip(selected = collection == ContentCollection.TRIP,
                    onClick = { onSelectCollection(ContentCollection.TRIP) },
                    label = { Text("가족 여행 퀘스트") })
            }
        }
        if (collection == ContentCollection.DESTINATION) {
            Text(onlineStatus ?: destinationStatus, Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        if (queued.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("재생 순서 · 밝은색부터 순서대로", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { editing = !editing }, enabled = canReorder) {
                    Text(if (editing) "순서 변경 완료" else "순서 변경")
                }
            }
        }
        // 각 목록은 별도 스크롤 상태를 갖는다. 목록 변경으로 재생은 시작하지 않는다.
        scrollStates.SaveableStateProvider(collection.name) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (contents.isEmpty()) {
                    item {
                        Text(if (collection == ContentCollection.DESTINATION)
                            "관련 콘텐츠가 없어요. 일반 추천에서 다른 이야기를 골라보세요."
                        else "아직 선택할 콘텐츠가 없어요.")
                    }
                }
                itemsIndexed(displayed, key = { _, content -> content.id }) { index, content ->
                    val rank = ranks[content.id]
                    val visibleIndex = queued.indexOfFirst { it.id == content.id }
                    QueueEpisodeRow(
                        content = content,
                        rank = rank,
                        queueSize = playbackQueue.size,
                        current = content.id == currentContentId,
                        isPlaying = isPlaying,
                        canPlay = canPlay,
                        editing = editing && rank != null,
                        canMoveUp = canReorder && visibleIndex > 0,
                        canMoveDown = canReorder && visibleIndex >= 0 && visibleIndex < queued.lastIndex,
                        onPlay = { onPlay(displayed, index) },
                        onMoveUp = {
                            if (rank != null && visibleIndex > 0)
                                onMoveItem(rank, ranks.getValue(queued[visibleIndex - 1].id))
                        },
                        onMoveDown = {
                            if (rank != null && visibleIndex >= 0 && visibleIndex < queued.lastIndex)
                                onMoveItem(rank, ranks.getValue(queued[visibleIndex + 1].id))
                        }
                    )
                }
            }
        }
    }
}

/** 순위 색상은 실제 큐 위치로 계산하며 색을 구분하지 못해도 번호로 확인할 수 있다. */
@Composable
private fun QueueEpisodeRow(
    content: AudioContent,
    rank: Int?,
    queueSize: Int,
    current: Boolean,
    isPlaying: Boolean,
    canPlay: Boolean,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val color = if (rank == null) Color(0xFFF0F0EE) else lerp(
        Color(0xFFFFF4E1), Color(0xFFAAA08F),
        if (queueSize <= 1) 0f else rank.toFloat() / (queueSize - 1)
    )
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = if (rank == null) "재생 목록에 없음" else "재생 순서 ${rank + 1}"
        },
        shape = RoundedCornerShape(16.dp),
        color = color,
        contentColor = Color(0xFF262624),
        border = if (current) BorderStroke(2.dp, Color(0xFF786B51)) else null
    ) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(enabled = canPlay, onClick = onPlay).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverArt(Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (rank != null) {
                        Text("${rank + 1}번째${if (current) {
                            if (isPlaying) " · 재생 중" else " · 현재 항목"
                        } else ""}", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    Text(content.title, fontWeight = FontWeight.SemiBold)
                    Text("${content.category} · 약 ${content.estimatedSeconds}초",
                        style = MaterialTheme.typography.bodySmall)
                    content.credit?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
                TextButton(onClick = onPlay, enabled = canPlay) { Text("듣기") }
            }
            if (editing) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onMoveUp, enabled = canMoveUp,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "${content.title} 위로 이동"
                        }) { Text("↑ 위로") }
                    TextButton(onClick = onMoveDown, enabled = canMoveDown,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "${content.title} 아래로 이동"
                        }) { Text("↓ 아래로") }
                }
            }
        }
    }
}

@Composable
private fun CollectionTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}
