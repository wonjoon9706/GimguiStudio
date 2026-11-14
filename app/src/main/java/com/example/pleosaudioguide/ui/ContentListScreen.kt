package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.pleosaudioguide.AudioContent
import com.example.pleosaudioguide.PlaybackQueue
import com.example.pleosaudioguide.R

enum class ContentCollection { DESTINATION, COMMUTE, TRIP, RECOMMENDED }

/** 목록 순서 편집은 재생을 시작하지 않으며, 재생 중 이동은 기존 큐에 위임한다. */
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
    onMoveItem: (Int, Int) -> Unit = { _, _ -> },
    showPlaybackQueue: Boolean = false
) {
    BackHandler(onBack = onBack)
    val scrollStates = rememberSaveableStateHolder()
    val quest = collection == ContentCollection.COMMUTE || collection == ContentCollection.TRIP
    val contents = if (showPlaybackQueue) playbackQueue else when (collection) {
        ContentCollection.DESTINATION -> recommendations
        ContentCollection.COMMUTE -> episodes.filter { it.category in listOf("일반 상식", "어학 학습", "기타 교양") }
        ContentCollection.TRIP -> (recommendations + episodes.filter {
            it.category in listOf("관광·역사", "어학 학습")
        }).distinctBy { it.id }
        ContentCollection.RECOMMENDED -> episodes
    }
    var draftIds by rememberSaveable(collection.name) { mutableStateOf(emptyList<String>()) }
    val ranks = playbackQueue.mapIndexed { index, content -> content.id to index }.toMap()
    val queued = playbackQueue.filter { item -> contents.any { it.id == item.id } }
    val unqueued = contents.filter { it.id !in ranks }.let { items ->
        val byId = items.associateBy { it.id }
        draftIds.mapNotNull(byId::get) + items.filter { it.id !in draftIds }
    }
    val displayed = queued + unqueued
    fun move(content: AudioContent, target: Int) {
        val group = if (content.id in ranks) queued else unqueued
        val from = group.indexOfFirst { it.id == content.id }
        if (from < 0 || target !in group.indices) return
        if (content.id in ranks) {
            if (canReorder) onMoveItem(ranks.getValue(content.id), ranks.getValue(group[target].id))
        } else if (canPlay) draftIds = PlaybackQueue.move(group, from, target).map { it.id }
    }
    Column(modifier.fillMaxSize().background(Color(0xFFF6F7F9)).padding(horizontal = 24.dp)) {
        AppHeader()
        Text("콘텐츠를 골라주세요", Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (showPlaybackQueue) "현재 재생 목록 · ${playbackQueue.size}개" else when (collection) {
                ContentCollection.DESTINATION -> destinationName?.let { "$it 관련 콘텐츠" }
                    ?: "목적지를 설정하면 관련 이야기를 추천해요"
                ContentCollection.COMMUTE -> "출퇴근길에 듣는 상식 · 교양 · 어학"
                ContentCollection.TRIP -> "가족 여행에 어울리는 관광 · 역사 · 어학"
                ContentCollection.RECOMMENDED -> "목적지와 관계없이 자유롭게 골라보세요"
            }, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall
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
        if (quest) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = collection == ContentCollection.COMMUTE,
                onClick = { onSelectCollection(ContentCollection.COMMUTE) }, label = { Text("출퇴근 퀘스트") })
            FilterChip(selected = collection == ContentCollection.TRIP,
                onClick = { onSelectCollection(ContentCollection.TRIP) }, label = { Text("가족 여행 퀘스트") })
        }
        if (collection == ContentCollection.DESTINATION) Text(onlineStatus ?: destinationStatus,
            Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        scrollStates.SaveableStateProvider(collection.name) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (contents.isEmpty()) item {
                    Text(if (showPlaybackQueue) "재생 목록이 비어 있어요."
                        else if (collection == ContentCollection.DESTINATION)
                            "관련 콘텐츠가 없어요. 일반 추천에서 다른 이야기를 골라보세요."
                        else "아직 선택할 콘텐츠가 없어요.")
                }
                itemsIndexed(displayed, key = { _, content -> content.id }) { index, content ->
                    val group = if (content.id in ranks) queued else unqueued
                    val groupIndex = group.indexOfFirst { it.id == content.id }
                    val movable = if (content.id in ranks) canReorder else canPlay
                    QueueEpisodeRow(content, ranks[content.id], content.id == currentContentId, isPlaying,
                        canPlay, movable && groupIndex > 0, movable && groupIndex < group.lastIndex,
                        { onPlay(displayed, index) }, { move(content, groupIndex - 1) },
                        { move(content, groupIndex + 1) }, { move(content, 0) },
                        { move(content, group.lastIndex) })
                }
            }
        }
        Text("오른쪽 핸들을 끌거나 눌러 재생 순서를 바꿀 수 있어요.",
            Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun QueueEpisodeRow(
    content: AudioContent, rank: Int?, current: Boolean, isPlaying: Boolean, canPlay: Boolean,
    canMoveUp: Boolean, canMoveDown: Boolean, onPlay: () -> Unit,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit, onMoveFirst: () -> Unit, onMoveLast: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }
    val up by rememberUpdatedState(onMoveUp)
    val down by rememberUpdatedState(onMoveDown)
    val upAllowed by rememberUpdatedState(canMoveUp)
    val downAllowed by rememberUpdatedState(canMoveDown)
    val movable = canMoveUp || canMoveDown
    Row(Modifier.fillMaxWidth().onSizeChanged { height = it.height }
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer { translationY = dragOffset },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.weight(1f).semantics {
            stateDescription = if (rank == null) "재생 목록에 없음" else "재생 순서 ${rank + 1}"
        }, shape = RoundedCornerShape(12.dp), color = Color.White,
            border = BorderStroke(if (current) 2.dp else 1.dp,
                if (current) Color(0xFF786B51) else Color(0xFFE3E4E7))) {
            Row(Modifier.fillMaxWidth().clickable(enabled = canPlay, onClick = onPlay).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EpisodeArtwork(content, Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(content.title, fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text("${content.category} · 약 ${content.estimatedSeconds}초",
                        style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                    content.credit?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                    if (current) Text(if (isPlaying) "재생 중" else "현재 항목",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }, enabled = movable, modifier = Modifier.size(48.dp)
                .pointerInput(content.id, movable) {
                    if (movable) detectDragGestures(
                        onDragStart = { dragging = true; menu = false },
                        onDragEnd = { dragging = false; dragOffset = 0f },
                        onDragCancel = { dragging = false; dragOffset = 0f }
                    ) { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        if (dragOffset > height * 0.6f && downAllowed) {
                            down(); dragOffset -= height
                        } else if (dragOffset < -height * 0.6f && upAllowed) {
                            up(); dragOffset += height
                        }
                    }
                }.semantics { contentDescription = "${content.title} 순서 이동" }) {
                Image(painterResource(R.drawable.ic_dragle_handle), null, Modifier.size(40.dp))
            }
            DropdownMenu(expanded = menu && movable, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("맨 위로") }, enabled = canMoveUp,
                    onClick = { menu = false; onMoveFirst() })
                DropdownMenuItem(text = { Text("위로") }, enabled = canMoveUp,
                    onClick = { menu = false; onMoveUp() })
                DropdownMenuItem(text = { Text("아래로") }, enabled = canMoveDown,
                    onClick = { menu = false; onMoveDown() })
                DropdownMenuItem(text = { Text("맨 아래로") }, enabled = canMoveDown,
                    onClick = { menu = false; onMoveLast() })
            }
        }
    }
}

@Composable
private fun CollectionTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
    } else OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
    }
}
