package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pleosaudioguide.ContentCatalog
import com.example.pleosaudioguide.ContentRecommender
import com.example.pleosaudioguide.GuideViewModel

private enum class AppTab(val label: String, val glyph: String) {
    HOME("홈", "⌂"), EXPLORE("탐색", "⌕"), LIBRARY("보관함", "☷"), MY_POT("MY POT", "◇")
}

/** 탭 전환과 관계없이 동일 ViewModel/재생 세션을 유지하는 앱 공통 프레임. */
@Composable
fun GuideApp(model: GuideViewModel = viewModel()) {
    var selectedName by rememberSaveable { mutableStateOf(AppTab.HOME.name) }
    val selected = AppTab.valueOf(selectedName)
    val savedScreens = rememberSaveableStateHolder()
    val navigation by model.navigationState.collectAsState()
    val canPlay = model.voiceReady && model.playerReady && !model.preparing &&
        !(model.usingPleosVoice && model.isPlaying)
    BackHandler(selected != AppTab.HOME) { selectedName = AppTab.HOME.name }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF30302E), onPrimary = Color.White,
        secondaryContainer = Color(0xFFE3E3DF), onSecondaryContainer = Color(0xFF222220),
        background = Color(0xFFFAFAF8), surface = Color(0xFFFAFAF8),
        surfaceVariant = Color(0xFFF0F0EE), onSurface = Color(0xFF262624)
    )) {
        Scaffold(
            bottomBar = {
                Column {
                    if (model.hasCurrentAudio) MiniPlayer(model)
                    NavigationBar(containerColor = Color.White) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selected == tab,
                                onClick = { selectedName = tab.name },
                                icon = { Text(tab.glyph, fontSize = 26.sp) },
                                label = { Text(tab.label) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        ) { padding ->
            savedScreens.SaveableStateProvider(selectedName) {
                val screenModifier = Modifier.fillMaxSize().padding(padding)
                when (selected) {
                    AppTab.HOME -> HomeScreen(
                        destinationName = navigation.destination?.name,
                        destinationStatus = navigation.status,
                        recommendations = (model.odiiState.contents +
                            ContentRecommender.recommend(navigation.destination)).distinctBy { it.id },
                        onlineStatus = model.odiiState.status,
                        episodes = ContentCatalog.all,
                        resumeContent = model.resumeContent.takeIf { !model.isPlaying && !model.preparing },
                        canPlay = canPlay,
                        canRefresh = navigation.connected,
                        onRefresh = model::refreshDestination,
                        onResume = model::resumeLast,
                        onPlay = { queue, index -> model.play(queue, index) },
                        modifier = screenModifier
                    )
                    AppTab.EXPLORE -> ExploreScreen(
                        contents = ContentCatalog.all, canPlay = canPlay,
                        onPlay = { queue, index -> model.play(queue, index) },
                        modifier = screenModifier
                    )
                    AppTab.LIBRARY -> ComingSoonScreen("보관함", "저장한 에피소드를 모아볼 공간이에요.", screenModifier)
                    AppTab.MY_POT -> ComingSoonScreen("MY POT", "나의 학습과 취향을 담을 공간이에요.", screenModifier)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(model: GuideViewModel) {
    Surface(color = Color(0xFFEEEEEB), tonalElevation = 2.dp) {
        Column {
            if (!model.usingPleosVoice && model.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (model.positionMs.toFloat() / model.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoverArt(Modifier.size(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(model.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(if (model.usingPleosVoice) "시연 음성 · 중지 후 처음부터 재생"
                        else "${timeLabel(model.positionMs)} / ${timeLabel(model.durationMs)}",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = model::previous, enabled = model.hasPrevious && !model.preparing) { Text("이전") }
                Button(onClick = model::togglePlayback, enabled = !model.preparing) {
                    Text(if (model.preparing) "준비 중" else if (model.isPlaying) {
                        if (model.usingPleosVoice) "중지" else "일시정지"
                    } else "재생")
                }
                TextButton(onClick = model::next, enabled = model.hasNext && !model.preparing) { Text("다음") }
            }
        }
    }
}

@Composable
private fun ComingSoonScreen(title: String, description: String, modifier: Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(description)
        Text("준비 중", color = Color.Gray)
    }
}

private fun timeLabel(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
