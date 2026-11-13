package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var collectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var playerVisible by rememberSaveable { mutableStateOf(false) }
    var showingPlaybackQueue by rememberSaveable { mutableStateOf(false) }
    var playbackCollectionName by rememberSaveable { mutableStateOf(ContentCollection.RECOMMENDED.name) }
    val closePlayer = {
        playerVisible = false
        selectedName = AppTab.HOME.name
        collectionName = null
        showingPlaybackQueue = false
    }
    val savedScreens = rememberSaveableStateHolder()
    val savedHomeScreens = rememberSaveableStateHolder()
    val navigation by model.navigationState.collectAsState()
    val tripState by model.tripHistoryState.collectAsState()
    val quizState by model.quiz.state.collectAsState()
    val drivingMode = !tripState.browsingAllowed
    val canPlay = model.voiceReady && model.playerReady && !model.preparing &&
        !(model.usingPleosVoice && model.isPlaying)
    BackHandler(!drivingMode && (playerVisible || selected != AppTab.HOME || collectionName != null)) {
        if (playerVisible) closePlayer()
        else if (selected == AppTab.HOME) {
            collectionName = null
            showingPlaybackQueue = false
        }
        else selectedName = AppTab.HOME.name
    }
    val recommendations = (model.odiiState.contents +
        ContentRecommender.recommend(navigation.destination)).distinctBy { it.id }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF30302E), onPrimary = Color.White,
        secondaryContainer = Color(0xFFE3E3DF), onSecondaryContainer = Color(0xFF222220),
        background = Color(0xFFFAFAF8), surface = Color(0xFFFAFAF8),
        surfaceVariant = Color(0xFFF0F0EE), onSurface = Color(0xFF262624)
    )) {
        if (!drivingMode && !tripState.showEndScreen) model.resumePrompt?.let { saved ->
            ResumePlaybackDialog(saved, { model.confirmResume(true) },
                { model.confirmResume(false) }, model::dismissResumePrompt)
        }
        Scaffold(
            bottomBar = {
                if (!drivingMode && !tripState.showEndScreen) Column {
                    if (model.hasCurrentAudio && !playerVisible) MiniPlayer(model) { playerVisible = true }
                    NavigationBar(containerColor = Color.White) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selected == tab,
                                onClick = {
                                    playerVisible = false
                                    selectedName = tab.name
                                    if (tab == AppTab.HOME) {
                                        collectionName = null
                                        showingPlaybackQueue = false
                                    }
                                },
                                icon = { Text(tab.glyph, fontSize = 26.sp) },
                                label = { Text(tab.label) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        ) { padding ->
            if (drivingMode) {
                DrivingPlayerScreen(
                    title = model.title.ifBlank { model.resumeContent?.title.orEmpty() },
                    isPlaying = model.isPlaying, hasCurrentAudio = model.hasCurrentAudio,
                    hasPrevious = model.hasPrevious, hasNext = model.hasNext,
                    preparing = model.preparing, usingPleosVoice = model.usingPleosVoice,
                    positionMs = model.positionMs, durationMs = model.durationMs,
                    canResume = model.resumeContent != null,
                    controlsReady = model.playerReady && (!model.usingPleosVoice || model.voiceReady),
                    maxTitleLength = tripState.maxTitleLength,
                    onPrevious = model::previous, onToggle = model::togglePlayback,
                    onNext = model::next, onResume = model::resumeLast,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            } else if (tripState.showEndScreen) {
                if (quizState.page == com.example.pleosaudioguide.QuizPage.SUMMARY) {
                    TripFinishedScreen(tripState, model::dismissTripFinished,
                        Modifier.fillMaxSize().padding(padding), model.quiz::openSetup)
                } else QuizScreen(tripState, quizState, model.quiz::selectCount, model.quiz::start,
                    model.quiz::selectAnswer, model.quiz::checkAnswer, model.quiz::next, model.quiz::retrySave,
                    model.quiz::backToSetup, model::dismissTripFinished, Modifier.fillMaxSize().padding(padding))
            } else if (playerVisible) {
                AudioPlayerScreen(
                    queue = model.playbackQueue,
                    currentContentId = model.currentContentId,
                    hasCurrentAudio = model.hasCurrentAudio,
                    isPlaying = model.isPlaying,
                    positionMs = model.positionMs,
                    durationMs = model.durationMs,
                    canSeek = model.canSeek,
                    preparing = model.preparing,
                    usingPleosVoice = model.usingPleosVoice,
                    onBack = closePlayer,
                    onTogglePlayback = model::togglePlayback,
                    onSeek30 = model::seekBy30Seconds,
                    onSeekTo = model::seekToPosition,
                    onSelectContent = model::selectPlaybackContent,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onShowQueue = {
                        playerVisible = false
                        selectedName = AppTab.HOME.name
                        collectionName = playbackCollectionName
                        showingPlaybackQueue = true
                    }
                )
            } else savedScreens.SaveableStateProvider(selectedName) {
                val screenModifier = Modifier.fillMaxSize().padding(padding)
                when (selected) {
                    AppTab.HOME -> savedHomeScreens.SaveableStateProvider(
                        if (collectionName == null) "home" else "content-list"
                    ) {
                        if (collectionName != null) ContentListScreen(
                            collection = ContentCollection.valueOf(collectionName!!),
                            destinationName = navigation.destination?.name,
                            destinationStatus = navigation.status,
                            recommendations = recommendations,
                            episodes = ContentCatalog.all,
                            canPlay = canPlay,
                            onSelectCollection = {
                                collectionName = it.name
                                showingPlaybackQueue = false
                            },
                            onBack = {
                                collectionName = null
                                showingPlaybackQueue = false
                            },
                            onPlay = { queue, index ->
                                playbackCollectionName = collectionName ?: ContentCollection.RECOMMENDED.name
                                model.play(queue, index)
                            },
                            modifier = screenModifier,
                            onlineStatus = model.odiiState.status,
                            playbackQueue = model.playbackQueue,
                            currentContentId = model.currentContentId,
                            isPlaying = model.isPlaying,
                            canReorder = model.playerReady && !model.preparing,
                            onMoveItem = model::movePlaybackItem,
                            showPlaybackQueue = showingPlaybackQueue
                        ) else HomeScreen(
                            destinationName = navigation.destination?.name,
                            destinationStatus = navigation.status,
                            recommendations = recommendations,
                            onlineStatus = model.odiiState.status,
                            episodes = ContentCatalog.all,
                            resumeContent = model.resumeContent.takeIf { !model.isPlaying && !model.preparing },
                            canPlay = canPlay,
                            canRefresh = navigation.connected,
                            onRefresh = model::refreshDestination,
                            onResume = model::resumeLast,
                            onOpenCollection = {
                                collectionName = it.name
                                showingPlaybackQueue = false
                            },
                            modifier = screenModifier
                        )
                    }
                    AppTab.EXPLORE -> ExploreScreen(
                        contents = ContentCatalog.all, canPlay = canPlay,
                        onPlay = { queue, index ->
                            playbackCollectionName = ContentCollection.RECOMMENDED.name
                            model.play(queue, index)
                        },
                        modifier = screenModifier
                    )
                    AppTab.LIBRARY -> ComingSoonScreen("보관함", "저장한 에피소드를 모아볼 공간이에요.", screenModifier)
                    AppTab.MY_POT -> Column(screenModifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("MY POT", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("최근 운행 퀴즈")
                        Text("이전에 선택한 학습 분량을 고르면 저장된 풀이를 이어갈 수 있어요.")
                        Button(onClick = { model.reopenLatestQuiz() }, enabled = tripState.endedTrip != null) {
                            Text("최근 운행 퀴즈 열기")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(model: GuideViewModel, onOpenPlayer: () -> Unit) {
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
                EpisodeArtwork(model.currentContent, Modifier.size(48.dp)
                    .clickable(onClick = onOpenPlayer).semantics { contentDescription = "전체 재생 화면 열기" })
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
