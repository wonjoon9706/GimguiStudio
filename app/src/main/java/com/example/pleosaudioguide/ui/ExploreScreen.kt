package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import com.example.pleosaudioguide.AudioContent

private data class ExploreCategory(val title: String, val glyph: String, val keywords: List<String>)
private val exploreCategories = listOf(
    ExploreCategory("경제 · 시사", "◉", listOf("경제", "시사", "사회")),
    ExploreCategory("어학", "Aa", listOf("어학", "영어", "외국어")),
    ExploreCategory("과학", "✦", listOf("과학", "하늘", "산란")),
    ExploreCategory("역사", "▦", listOf("역사", "문화유산")),
    ExploreCategory("문화 · 예술", "♫", listOf("문화", "예술", "교양")),
    ExploreCategory("기후 · 여행", "⌖", listOf("관광", "여행", "기후", "날씨"))
)

/** 공통 하단 메뉴/미니 플레이어는 GuideApp 소유. 검색은 전달된 실제 콘텐츠 안에서만 수행한다. */
@Composable
fun ExploreScreen(
    contents: List<AudioContent>,
    canPlay: Boolean,
    onPlay: (List<AudioContent>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val category = exploreCategories.find { it.title == selected }
    val filtered = contents.distinctBy { it.id }.filter { content ->
        val text = listOf(content.title, content.category).plus(content.placeTags).joinToString(" ")
        val categoryMatch = category == null || category.keywords.any { text.contains(it, ignoreCase = true) }
        categoryMatch && (query.isBlank() || text.contains(query.trim(), ignoreCase = true))
    }
    BackHandler(searching || selected != null) {
        searching = false; query = ""; selected = null; focus.clearFocus()
    }
    LazyColumn(modifier.fillMaxSize().background(Color(0xFFF6F7F9)),
        contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("POT-QUEST", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline)
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFB52E)) {
                    Text("♫", Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color.White, fontSize = 26.sp)
                }
            }
            HorizontalDivider(Modifier.padding(top = 18.dp))
        }
        item {
            Text("탐색", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (searching) {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    label = { Text("프로그램, 에피소드, 주제를 검색") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    trailingIcon = { TextButton(onClick = { query = ""; searching = false; focus.clearFocus() }) { Text("닫기") } })
            } else OutlinedButton(onClick = { searching = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFE3E3E6)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFEEEEF0))) {
                Text("⌕  프로그램, 에피소드, 주제를 검색", Modifier.fillMaxWidth(), color = Color.DarkGray)
            }
        }
        item {
            Text("카테고리 탐색", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("원하는 분야의 팟캐스트를 찾아보세요", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exploreCategories.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { entry ->
                            OutlinedCard(onClick = { selected = if (selected == entry.title) null else entry.title },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                                border = BorderStroke(if (selected == entry.title) 2.dp else 1.dp,
                                    if (selected == entry.title) Color.DarkGray else Color(0xFFE1E2E5))) {
                                Column(Modifier.padding(12.dp).heightIn(min = 64.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(entry.glyph, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Text(entry.title, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (searching || selected != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (searching) "검색 결과 · ${filtered.size}개" else selected.orEmpty(),
                        Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { selected = null; query = ""; searching = true }) { Text("전체 보기") }
                }
                if (filtered.isEmpty()) Text("조건에 맞는 콘텐츠가 없어요.", Modifier.padding(vertical = 12.dp))
            }
            items(filtered, key = { it.id }) { content ->
                EpisodeRow(content, canPlay) { focus.clearFocus(); onPlay(filtered, filtered.indexOf(content)) }
            }
        } else {
            item {
                Text("지금 주목할 프로그램", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("오늘의 인기 오디오 집계 준비 중", fontWeight = FontWeight.Medium)
                        Text("검색이나 카테고리에서 콘텐츠를 골라보세요.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}
