package com.example.pleosaudioguide.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosaudioguide.AudioContent

/** 화면은 SDK/ViewModel을 참조하지 않고 데이터와 사용자 동작만 전달받는다. */
@Composable
fun HomeScreen(
    destinationName: String?,
    destinationStatus: String,
    recommendations: List<AudioContent>,
    episodes: List<AudioContent>,
    resumeContent: AudioContent?,
    canPlay: Boolean,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
    onResume: () -> Unit,
    onPlay: (List<AudioContent>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("POTQUEST", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                HorizontalDivider(Modifier.padding(top = 16.dp))
            }
        }
        item {
            SectionHeading("목적지 기반 팟캐스트 추천",
                destinationName?.let { "$it 가는 길, 함께 들을 이야기" } ?: "목적지를 설정하면 관련 이야기를 추천해요")
            if (recommendations.isEmpty()) {
                Surface(Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), color = Color(0xFFF0F0EE)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (destinationName == null) destinationStatus else "아직 관련 콘텐츠가 없어요. 추천 에피소드를 둘러보세요.")
                        OutlinedButton(onClick = onRefresh, enabled = canRefresh) { Text("목적지 새로고침") }
                    }
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(recommendations, key = { it.id }) { content ->
                        EpisodeTile(content, canPlay, Modifier.width(248.dp)) {
                            onPlay(recommendations, recommendations.indexOf(content))
                        }
                    }
                }
            }
        }
        if (resumeContent != null) {
            item {
                SectionHeading("이어서 듣기", "지난번에 듣던 이야기를 다시 만나보세요")
                Surface(Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), color = Color(0xFFF0F0EE)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CoverArt(Modifier.size(80.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(resumeContent.title, fontWeight = FontWeight.Bold)
                            Text(resumeContent.category, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = onResume, enabled = canPlay) {
                                Text(if (resumeContent.audioUri == null) "처음부터 다시 듣기" else "이어서 듣기")
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeading("오늘의 퀘스트", "이동 시간을 배움의 시간으로")
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuestCard("DAILY", "출퇴근 퀘스트", "시사 · 교양 · 어학", Modifier.weight(1f))
                QuestCard("TRIP", "가족 여행 퀘스트", "목적지 이야기 듣기", Modifier.weight(1f))
            }
        }
        item { SectionHeading("추천 에피소드", "오늘 함께 들을 짧은 이야기 · 시연 콘텐츠") }
        items(episodes, key = { it.id }) { content ->
            EpisodeRow(content, canPlay, Modifier.padding(horizontal = 24.dp)) {
                onPlay(episodes, episodes.indexOf(content))
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
    }
}

@Composable
fun CoverArt(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE1E1DF)),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            drawCircle(Color(0xFF747471), radius = size.minDimension / 2)
            drawCircle(Color.White, radius = size.minDimension / 2 - 4.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            val centerX = size.width / 2
            listOf(-8f, 0f, 8f).forEachIndexed { index, offset ->
                val halfHeight = if (index == 1) 10.dp.toPx() else 6.dp.toPx()
                val x = centerX + offset.dp.toPx()
                drawLine(Color.White, Offset(x, size.height / 2 - halfHeight),
                    Offset(x, size.height / 2 + halfHeight), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun EpisodeTile(content: AudioContent, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color(0xFFF0F0EE)) {
        Column(Modifier.clickable(enabled = enabled, onClick = onClick).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverArt(Modifier.fillMaxWidth().height(120.dp))
            Text(content.title, fontWeight = FontWeight.Bold, minLines = 2)
            Text("${content.category} · 약 ${content.estimatedSeconds}초", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClick, enabled = enabled) { Text("듣기  ›") }
        }
    }
}

@Composable
fun EpisodeRow(content: AudioContent, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFFF0F0EE)) {
        Row(Modifier.clickable(enabled = enabled, onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverArt(Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(content.title, fontWeight = FontWeight.SemiBold)
                Text("${content.category} · 약 ${content.estimatedSeconds}초", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClick, enabled = enabled) { Text("듣기") }
        }
    }
}

@Composable
private fun QuestCard(tag: String, title: String, subtitle: String, modifier: Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tag, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            Text("준비 중", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        }
    }
}
