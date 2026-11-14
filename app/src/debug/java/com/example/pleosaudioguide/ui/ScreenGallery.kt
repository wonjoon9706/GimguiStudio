package com.example.pleosaudioguide.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.*

/** 개발용 샘플만 사용. ViewModel, Room, 차량 SDK, 플레이어, LLM을 생성하지 않는다. */
enum class GalleryScreen(val label: String) {
    HOME("홈"), HOME_EMPTY("홈 · 추천 없음"), EXPLORE("탐색"),
    LIST("콘텐츠 목록"), PLAYER("전체 재생"), PLAYER_EMPTY("전체 재생 · 비어 있음"),
    DRIVING("주행 플레이어"), TRIP_END("운행 종료"), TRIP_EMPTY("운행 종료 · 완료 0개"),
    QUIZ_LOADING("퀴즈 생성 중"), QUIZ_ERROR("퀴즈 생성 실패"),
    QUIZ_SETUP("퀴즈 분량 선택"), QUIZ_SHORT("퀴즈 · 1문제만 준비"),
    QUESTION("퀴즈 문제"), ANSWER("퀴즈 정답·해설"), RESULT("퀴즈 결과"),
    LIBRARY_EMPTY("보관함 · 빈 선반"), LIBRARY("보관함 · 저장된 포션"), RESUME("이어듣기 선택창");
    override fun toString() = label
}

class GalleryScreenProvider : PreviewParameterProvider<GalleryScreen> {
    override val values = GalleryScreen.entries.asSequence()
}

private object GalleryData {
    val contents = ContentCatalog.all.take(3).mapIndexed { index, content ->
        content.copy(id = "preview-$index", audioUri = "preview://audio/$index", imageUri = null)
    }
    val records = contents.map {
        ListeningRecord(it.id, it.title, it.category, it.script, it.credit, "audio",
            durationMs = 120_000, ranges = listOf(HeardRange(0, 100_000)), lastListenedAt = 1_794_614_400_000)
    }
    val trip = TripHistoryState(endedTrip = TripEntity("preview-trip", 1_794_610_800_000, 1_794_614_400_000),
        records = records, parked = true, browsingAllowed = true)
    val sources = QuizRules.sources(records)
    val questions = List(6) { i ->
        val source = sources[i % sources.size]
        QuizQuestion(source.id, "미리보기 문제 ${i + 1}: 이 이야기의 주요 주제는 무엇일까요?",
            listOf("이야기에 등장하는 역사와 문화", "우주 탐사 기술", "운동 경기의 규칙"), 0,
            "미리보기용 해설입니다. 실제 LLM이 생성한 문제가 아닙니다.", source.script.take(60))
    }
    val pool = QuizAttempt("preview-trip", 6, sources, questions)
    val answered = pool.copy(answers = List(6) { 0 }, checked = List(6) { true })
    val library = LibraryState(listOf(
        LibraryPotion("preview-trip", 1_794_614_400_000, true, "potion_01", records, listOf(answered)),
        LibraryPotion("preview-free", 1_794_528_000_000, false, "potion_02", records.take(1), emptyList())
    ))
}

@Preview(name = "POTQUEST 화면 모음", group = "개발용 화면", widthDp = 840, heightDp = 820, showBackground = true)
@Composable
fun ScreenGalleryPreview(@PreviewParameter(GalleryScreenProvider::class) screen: GalleryScreen) {
    ScreenGallery(screen)
}

/** Preview의 Interactive 모드에서도 선택 메뉴와 샘플 퀴즈를 조작할 수 있다. */
@Composable
fun ScreenGallery(initialScreen: GalleryScreen = GalleryScreen.HOME) {
    var selected by remember(initialScreen) { mutableStateOf(initialScreen) }
    var menu by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF30302E),
        onPrimary = Color.White, background = Color(0xFFFAFAF8), surface = Color(0xFFFAFAF8),
        surfaceVariant = Color(0xFFF0F0EE))) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("개발용 미리보기 · 샘플 데이터", color = MaterialTheme.colorScheme.error)
                    Box {
                        TextButton(onClick = { menu = true }) { Text(selected.label + " ▾") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            GalleryScreen.entries.forEach { item ->
                                DropdownMenuItem(text = { Text(item.label) },
                                    onClick = { selected = item; menu = false })
                            }
                        }
                    }
                }
                HorizontalDivider()
                CompositionLocalProvider(LocalAppHeaderStatus provides
                    if (selected == GalleryScreen.DRIVING) AppHeaderStatus.DRIVING else AppHeaderStatus.PARKED) {
                    key(selected) {
                        GalleryContent(selected, { selected = it }, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}

@Preview(name = "주행 플레이어 · 작은 창", widthDp = 320, heightDp = 320, showBackground = true)
@Composable
private fun CompactDrivingPreview() {
    MaterialTheme {
        CompositionLocalProvider(LocalAppHeaderStatus provides AppHeaderStatus.DRIVING) {
            GalleryContent(GalleryScreen.DRIVING, {}, Modifier.fillMaxSize())
        }
    }
}

@Preview(name = "재생·이전·다음 아이콘", widthDp = 320, heightDp = 88, showBackground = true)
@Composable
private fun PlaybackIconsPreview() {
    MaterialTheme {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            listOf("previous", "play", "pause", "next", "stop").forEach { kind ->
                IconButton(onClick = {}) { TransportGlyph(kind, Modifier.size(32.dp)) }
            }
        }
    }
}

@Composable
private fun GalleryContent(screen: GalleryScreen, navigate: (GalleryScreen) -> Unit, modifier: Modifier) {
    val data = GalleryData
    val contents = data.contents
    when (screen) {
        GalleryScreen.HOME, GalleryScreen.HOME_EMPTY -> HomeScreen(
            if (screen == GalleryScreen.HOME) "경주 (샘플)" else null, "목적지 없음",
            if (screen == GalleryScreen.HOME) contents else emptyList(), contents, true, {},
            { navigate(GalleryScreen.LIST) }, modifier, "미리보기 · 실제 API 응답 아님")
        GalleryScreen.EXPLORE -> ExploreScreen(contents, true, { _, _ -> navigate(GalleryScreen.PLAYER) }, modifier)
        GalleryScreen.LIST -> ContentListScreen(ContentCollection.DESTINATION, "경주 (샘플)", "미리보기",
            contents, contents, true, {}, { navigate(GalleryScreen.HOME) },
            { _, _ -> navigate(GalleryScreen.PLAYER) }, modifier)
        GalleryScreen.PLAYER, GalleryScreen.PLAYER_EMPTY -> AudioPlayerScreen(
            contents, contents.first().id, screen == GalleryScreen.PLAYER, false, 45_000, 120_000,
            true, false, false, { navigate(GalleryScreen.HOME) }, {}, {}, {}, {}, modifier,
            { navigate(GalleryScreen.LIST) })
        GalleryScreen.DRIVING -> {
            var index by remember { mutableStateOf(0) }
            var playing by remember { mutableStateOf(true) }
            var position by remember { mutableStateOf(45_000L) }
            DrivingPlayerScreen(contents[index].title, playing, true, index > 0, index < contents.lastIndex,
                false, false, position, 120_000, false, true, 80,
                { playing = !playing }, {}, modifier, content = contents[index], queue = contents,
                canSeek = true, onSeek30 = { position = PlaybackPosition.skip30(position, 120_000, it) },
                onSelectContent = { id ->
                    contents.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { index = it; position = 0 }
                })
        }
        GalleryScreen.TRIP_END, GalleryScreen.TRIP_EMPTY -> TripFinishedScreen(
            if (screen == GalleryScreen.TRIP_END) data.trip else data.trip.copy(records = emptyList()),
            { navigate(GalleryScreen.HOME) }, modifier, { navigate(GalleryScreen.QUIZ_SETUP) })
        GalleryScreen.LIBRARY_EMPTY, GalleryScreen.LIBRARY -> LibraryScreen(
            if (screen == GalleryScreen.LIBRARY) data.library else LibraryState(), modifier)
        GalleryScreen.RESUME -> {
            HomeScreen(null, "미리보기", emptyList(), contents, true, {}, {}, modifier)
            ResumePlaybackDialog(PlaybackResume(contents.first(), 45_000, 120_000),
                { navigate(GalleryScreen.PLAYER) }, { navigate(GalleryScreen.PLAYER) },
                { navigate(GalleryScreen.HOME) })
        }
        else -> {
            val initial = when (screen) {
                GalleryScreen.QUIZ_LOADING -> QuizUiState(page = QuizPage.PREPARING, loading = true)
                GalleryScreen.QUIZ_ERROR -> QuizUiState(page = QuizPage.PREPARING,
                    error = "미리보기: 문제를 준비하지 못했어요. 다시 시도해 주세요.")
                GalleryScreen.QUIZ_SHORT -> QuizUiState(page = QuizPage.SETUP, count = 1,
                    pool = data.pool.copy(count = 1, questions = data.questions.take(1),
                        answers = listOf(null), checked = listOf(false)))
                GalleryScreen.QUESTION -> QuizUiState(page = QuizPage.QUESTION, attempt = data.pool.select(0))
                GalleryScreen.ANSWER -> QuizUiState(page = QuizPage.QUESTION, attempt = data.pool.select(0).check())
                GalleryScreen.RESULT -> QuizUiState(page = QuizPage.RESULT, attempt = data.answered)
                else -> QuizUiState(page = QuizPage.SETUP, pool = data.pool)
            }
            var state by remember { mutableStateOf(initial) }
            QuizScreen(data.trip, state,
                onCount = { state = state.copy(count = it) },
                onStart = {
                    val pool = state.pool ?: data.pool
                    val count = state.count.coerceAtMost(pool.count)
                    state = state.copy(page = QuizPage.QUESTION, pool = pool,
                        attempt = pool.copy(count = count, questions = pool.questions.take(count),
                            answers = List(count) { null }, checked = List(count) { false }))
                },
                onAnswer = { state = state.copy(attempt = state.attempt?.select(it)) },
                onCheck = { state = state.copy(attempt = state.attempt?.check()) },
                onNext = {
                    val attempt = state.attempt
                    state = if (attempt?.finished == true) state.copy(page = QuizPage.RESULT)
                        else state.copy(attempt = attempt?.next())
                },
                onRetrySave = {}, onBack = { navigate(GalleryScreen.QUIZ_SETUP) },
                onLater = { navigate(GalleryScreen.HOME) }, modifier = modifier)
        }
    }
}
