package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosaudioguide.*
import com.example.pleosaudioguide.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LibraryScreen(state: LibraryState, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val potion = state.potions.find { it.id == selected }
    val pages = maxOf(1, (state.potions.size + 11) / 12)
    val currentPage = page.coerceIn(0, pages - 1)
    BackHandler(potion != null) { selected = null }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val shelfHeight = ((maxHeight.value - 280f) / 3f).coerceIn(80f, 140f).dp
    Column(Modifier.fillMaxSize().background(Color(0xFFF6F7F9))
        .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("POT-QUEST", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("● 정차 중", style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider()
        if (potion != null) {
            TextButton(onClick = { selected = null }) { Text("‹ 보관함") }
            PotionDetail(potion)
        } else {
            Text("보관함", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("저장된 콘텐츠 · ${state.potions.size}개 묶음",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            repeat(3) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { column ->
                        val slot = row * 4 + column
                        val item = state.potions.getOrNull(currentPage * 12 + slot)
                        Column(Modifier.weight(1f).height(shelfHeight).testTag("shelfSlot$slot"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom) {
                            if (item != null) {
                                TextButton(onClick = { selected = item.id }, modifier = Modifier
                                    .fillMaxWidth().weight(1f),
                                    contentPadding = PaddingValues(0.dp)) {
                                    PotionImage(item)
                                }
                            } else Spacer(Modifier.weight(1f))
                            Box(Modifier.fillMaxWidth().height(26.dp)
                                .background(Color(0xFFFFB24B), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center) {
                                if (item != null) Text(item.records.firstOrNull()?.category.orEmpty(),
                                    Modifier.padding(horizontal = 4.dp), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, fontSize = 10.sp, color = Color(0xFF332618))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { page = currentPage - 1 }, enabled = currentPage > 0) { Text("이전") }
                Text("${currentPage + 1} / $pages", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { page = currentPage + 1 }, enabled = currentPage < pages - 1) { Text("다음") }
            }
            Text(if (state.potions.isEmpty()) "누적 80% 이상 들으면 선반에 포션이 생겨요."
                else "회색 포션은 퀴즈 미완료, 컬러 포션은 퀴즈 완료예요.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
    }
}

@Composable
private fun PotionImage(potion: LibraryPotion) {
    val context = LocalContext.current
    val artwork = remember(potion.potionName) {
        @Suppress("DiscouragedApi")
        val id = context.resources.getIdentifier(potion.potionName, "drawable", context.packageName)
        id.takeIf { it != 0 } ?: R.drawable.pot_placeholder
    }
    Image(painterResource(artwork),
        "${potion.label} · ${if (potion.quizCompleted) "퀴즈 완료" else "퀴즈 미완료"}",
        Modifier.fillMaxSize().padding(4.dp),
        colorFilter = if (potion.quizCompleted) null else
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
}

@Composable
private fun PotionDetail(potion: LibraryPotion) {
    val date = Instant.ofEpochMilli(potion.completedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
    Text(if (potion.drive) "운행 청취" else "자유 청취", fontWeight = FontWeight.Bold)
    Text(if (potion.inferred) "이전 기록 · 완료 시점 미확인" else "$date · 80% 청취 완료",
        style = MaterialTheme.typography.bodySmall)
    Text("오디오 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    potion.records.forEach { record ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.title, fontWeight = FontWeight.Bold)
                Text(record.category, style = MaterialTheme.typography.bodySmall)
                Text(if (record.mode == "tts") "시연 음성 청취 완료"
                    else "누적 청취 ${record.listenedMs / 1000}초 / ${record.durationMs / 1000}초",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("퀴즈 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (potion.quizzes.isEmpty()) Text("아직 푼 퀴즈가 없어요.", style = MaterialTheme.typography.bodySmall)
    potion.quizzes.forEach { quiz ->
        var expanded by rememberSaveable(potion.id, quiz.count) { mutableStateOf(false) }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${quiz.count}문제 · " + when {
                    quiz.finished -> "${quiz.score}개 정답"
                    quiz.answers.any { it != null } -> "풀이 중"
                    else -> "문제 준비됨 · 미풀이"
                })
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "접기" else "문제와 답안 보기") }
                if (expanded) quiz.questions.forEachIndexed { index, q ->
                    Text("${index + 1}. ${q.prompt}", fontWeight = FontWeight.Bold)
                    Text("내 답: " + (quiz.answers[index]?.let { q.options[it] } ?: "미응답"))
                    if (quiz.checked[index]) {
                        Text("정답: ${q.options[q.correctIndex]}")
                        Text(q.explanation)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
