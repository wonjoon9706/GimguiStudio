package com.example.pleosaudioguide.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.AudioContent

/**
 * 탐색 페이지 팀원 작업 진입점.
 * 앱 하단 메뉴/미니 플레이어는 GuideApp 소유이므로 여기서 Scaffold를 중복 생성하지 않는다.
 * 데이터 로딩/검색 UI는 이 화면 안에서 확장하고 재생은 onPlay 콜백을 사용한다.
 */
@Composable
fun ExploreScreen(
    contents: List<AudioContent>,
    canPlay: Boolean,
    onPlay: (List<AudioContent>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("탐색", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("어떤 이야기를 들어볼까요?", Modifier.padding(top = 8.dp))
        }
        items(contents, key = { it.id }) { content ->
            EpisodeRow(content, canPlay) { onPlay(contents, contents.indexOf(content)) }
        }
    }
}
