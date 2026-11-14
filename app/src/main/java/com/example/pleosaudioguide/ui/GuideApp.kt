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
import com.example.pleosaudioguide.OdiiStore

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
    val finishToHome = {
        closePlayer()
        model.dismissTripFinished()
    }
    val savedScreens = rememberSaveableStateHolder()
    val savedHomeScreens = rememberSaveableStateHolder()
    val navigation by model.navigationState.collectAsState()
    val tripState by model.tripHistoryState.collectAsState()
    val quizState by model.quiz.state.collectAsState()
    val libraryState by model.libraryState.collectAsState()
    val automaticResume by model.automaticResumePrompt.collectAsState()
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
    val episodePool = (OdiiStore.all + model.odiiState.contents)
        .filter { it.audioUri?.startsWith("https://") == true }.distinctBy { it.id }.sortedBy { it.id }
    val episodes = remember(episodePool) { episodePool.shuffled().take(5) }
    CompositionLocalProvider(LocalAppHeaderStatus provides when {
        tripState.browsingAllowed -> AppHeaderStatus.PARKED
        tripState.vehicleDataValid -> AppHeaderStatus.DRIVING
        else -> AppHeaderStatus.UNKNOWN
    }) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF30302E), onPrimary = Color.White,
        secondaryContainer = Color(0xFFE3E3DF), onSecondaryContainer = Color(0xFF222220),
        background = Color(0xFFFAFAF8), surface = Color(0xFFFAFAF8),
        surfaceVariant = Color(0xFFF0F0EE), onSurface = Color(0xFF262624)
    )) {
        if (!tripState.showEndScreen && (tripState.browsingAllowed || tripState.vehicleDataValid)) automaticResume?.let { request ->
            ResumePlaybackDialog(request.saved, { request.choose(true) },
                { request.choose(false) }, { request.choose(null) },
                driving = drivingMode, maxTitleLength = tripState.maxTitleLength)
        }
        if (automaticResume == null && !tripState.showEndScreen &&
            (tripState.browsingAllowed || tripState.vehicleDataValid)) model.resumePrompt?.let { saved ->
            ResumePlaybackDialog(saved, { model.confirmResume(true) },
                { model.confirmResume(false) }, model::dismissResumePrompt,
                driving = drivingMode, maxTitleLength = tripState.maxTitleLength)
        }
        Scaffold(
            bottomBar = {
                if (!drivingMode && !tripState.showEndScreen) Column {
                    if ((model.hasCurrentAudio || model.resumeContent != null) && !playerVisible) MiniPlayer(model) {
                        if (model.preparePlayerForViewing()) playerVisible = true
                    }
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
                    onToggle = model::togglePlayback, onResume = model::resumeLast,
                    content = model.currentContent ?: model.resumeContent,
                    queue = model.playbackQueue, canSeek = model.canSeek,
                    onSeek30 = model::seekDrivingBy30Seconds,
                    onSelectContent = model::selectDrivingContent,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            } else if (tripState.showEndScreen) {
                if (quizState.page == com.example.pleosaudioguide.QuizPage.SUMMARY) {
                    TripFinishedScreen(tripState, finishToHome,
                        Modifier.fillMaxSize().padding(padding), model.quiz::openSetup)
                } else QuizScreen(tripState, quizState, model.quiz::selectCount, model.quiz::start,
                    model.quiz::selectAnswer, model.quiz::checkAnswer, model.quiz::next, model.quiz::retrySave,
                    model.quiz::backToSetup, finishToHome, Modifier.fillMaxSize().padding(padding))
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
                            episodes = if (collectionName == ContentCollection.RECOMMENDED.name) episodes else ContentCatalog.all,
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
                            episodes = episodes,
                            canRefresh = navigation.connected,
                            onRefresh = model::refreshDestination,
                            onOpenCollection = {
                                collectionName = it.name
                                showingPlaybackQueue = false
                            },
                            modifier = screenModifier
                        )
                    }
                    AppTab.EXPLORE -> ExploreScreen(
                        contents = (model.odiiState.contents + ContentCatalog.all).distinctBy { it.id }, canPlay = canPlay,
                        onPlay = { queue, index ->
                            playbackCollectionName = ContentCollection.RECOMMENDED.name
                            model.play(queue, index)
                        },
                        modifier = screenModifier
                    )
                    AppTab.LIBRARY -> LibraryScreen(libraryState, screenModifier)
                    AppTab.MY_POT -> Column(screenModifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppHeader()
                        Text("MY POT", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
}

@Composable
private fun MiniPlayer(model: GuideViewModel, onOpenPlayer: () -> Unit) {
    val active = model.hasCurrentAudio
    val content = if (active) model.currentContent else model.resumeContent
    val saved = model.savedPlaybackResume?.takeIf { it.content.id == content?.id }
    val position = if (active) model.positionMs else saved?.positionMs ?: 0L
    val duration = if (active) model.durationMs else saved?.durationMs ?: 0L
    val tts = if (active) model.usingPleosVoice else content?.audioUri == null
    val playbackLabel = if (model.preparing) "준비 중" else if (model.isPlaying) {
        if (model.usingPleosVoice) "중지" else "일시정지"
    } else if (!active) { if (tts) "처음부터 듣기" else "이어서 듣기" } else "재생"
    Surface(color = Color(0xFFEEEEEB), tonalElevation = 2.dp) {
        Column {
            if (!tts && duration > 0) {
                LinearProgressIndicator(
                    progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EpisodeArtwork(content, Modifier.size(48.dp)
                    .clickable(enabled = model.playerReady && !model.preparing, onClick = onOpenPlayer)
                    .semantics { contentDescription = "전체 재생 화면 열기" })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (active) model.title else content?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(if (tts) "시연 음성 · 중지 후 처음부터 재생"
                        else "${timeLabel(position)} / ${timeLabel(duration)}",
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = model::previous,
                    enabled = active && model.hasPrevious && !model.preparing,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "이전" }) {
                    TransportGlyph("previous", Modifier.size(32.dp))
                }
                FilledIconButton(onClick = if (active) model::togglePlayback else model::resumeLast,
                    enabled = model.playerReady && !model.preparing && (!tts || model.voiceReady),
                    modifier = Modifier.size(56.dp).semantics { contentDescription = playbackLabel }) {
                    if (model.preparing) CircularProgressIndicator(Modifier.size(24.dp),
                        strokeWidth = 2.dp, color = LocalContentColor.current)
                    else TransportGlyph(if (model.isPlaying) {
                        if (tts) "stop" else "pause"
                    } else "play", Modifier.size(32.dp))
                }
                IconButton(onClick = model::next,
                    enabled = active && model.hasNext && !model.preparing,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "다음" }) {
                    TransportGlyph("next", Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ComingSoonScreen(title: String, description: String, modifier: Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppHeader()
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description)
        Text("준비 중", color = Color.Gray)
    }
}

private fun timeLabel(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
