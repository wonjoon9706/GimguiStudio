package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.*

@Composable
fun QuizScreen(
    trip: TripHistoryState, state: QuizUiState, onCount: (Int) -> Unit, onStart: () -> Unit,
    onAnswer: (Int) -> Unit, onCheck: () -> Unit, onNext: () -> Unit, onRetrySave: () -> Unit,
    onBack: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier
) {
    BackHandler { if (state.page == QuizPage.SETUP) onLater() else onBack() }
    val sources = QuizRules.sources(trip.records)
    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("POTQUEST", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        HorizontalDivider()
        when (state.page) {
            QuizPage.PREPARING -> {
                Text("퀴즈를 준비하고 있어요", style = MaterialTheme.typography.headlineSmall)
                Text("청취 대본에서 근거가 확인되는 문제를 준비한 뒤 학습 분량을 선택할 수 있어요.")
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                QuizError(state.error)
                if (!state.loading) Button(onClick = onStart) { Text("문제 준비 다시 시도") }
                OutlinedButton(onClick = onLater) { Text("나중에") }
            }
            QuizPage.SETUP, QuizPage.SUMMARY -> {
                Text("퀴즈를 풀어볼까요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("이번 운행에서 들은 콘텐츠 중 누적 80% 이상 청취한 대본으로 출제해요.", style = MaterialTheme.typography.bodySmall)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("오늘의 청취 요약", fontWeight = FontWeight.Bold)
                        Text("콘텐츠 ${trip.listenedContentCount}편 · 출제 가능 ${sources.size}편")
                        Text("대본 전체에서 출제하므로 듣지 않은 부분이 포함될 수 있어요.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val total = state.pool?.count ?: 0
                if (total > 0) {
                    Text("${total}문제를 준비했어요", fontWeight = FontWeight.Bold)
                    Text("학습 분량 선택", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuizRules.choices(total).forEach { choice ->
                            val label = choice.label + if (choice.enabled) "\n${choice.count}문제" else "\n문제 부족"
                            val enabled = choice.enabled && !state.loading && !state.saving
                            if (enabled && choice.count == state.count) Button(
                                onClick = { onCount(choice.count) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                                contentPadding = PaddingValues(4.dp)) { Text(label) }
                            else OutlinedButton(onClick = { onCount(choice.count) }, enabled = enabled,
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                                contentPadding = PaddingValues(4.dp)) { Text(label) }
                        }
                    }
                    val seconds = QuizRules.estimatedSeconds(state.count)
                    Text("${state.count}문제 · 약 ${seconds / 60}분${if (seconds % 60 > 0) " ${seconds % 60}초" else ""} 예상",
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("출제 범위 / 이번 운행", fontWeight = FontWeight.Bold)
                        Text(sources.joinToString(" · ") { it.title }.ifEmpty { "출제 가능한 대본이 없어요." })
                    }
                }
                Text("청취 대본을 서버로 보내 준비한 문제예요. 저장된 문제는 다시 생성하지 않아요.", style = MaterialTheme.typography.bodySmall)
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("문제를 만들고 있어요. 잠시 기다려 주세요.")
                }
                QuizError(state.error)
                Button(onClick = onStart, enabled = sources.isNotEmpty() && !state.loading && !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (state.loading) "퀴즈 준비 중" else if (state.pool == null) "문제 준비 다시 시도" else "지금 퀴즈 풀기")
                }
                OutlinedButton(onClick = onLater, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("나중에") }
            }
            QuizPage.QUESTION -> {
                val attempt = state.attempt
                if (attempt != null) {
                    val q = attempt.questions[attempt.index]
                    val checked = attempt.checked[attempt.index]
                    val answer = attempt.answers[attempt.index]
                    Text("오늘의 퀴즈", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${attempt.index + 1} / ${attempt.count} 문제")
                    LinearProgressIndicator(progress = { (attempt.index + 1f) / attempt.count },
                        modifier = Modifier.fillMaxWidth())
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("출처: ${attempt.sources.first { it.id == q.sourceId }.title} · 전체 대본 기준",
                                style = MaterialTheme.typography.bodySmall)
                            Text(q.prompt, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    q.options.forEachIndexed { index, option ->
                        val highlighted = if (checked) index == q.correctIndex else index == answer
                        OutlinedButton(onClick = { onAnswer(index) }, enabled = !checked && !state.saving,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(if (highlighted) 2.dp else 1.dp,
                                if (highlighted) MaterialTheme.colorScheme.primary else Color.LightGray),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                disabledContainerColor = if (highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                            Text("${'A' + index}  $option", Modifier.fillMaxWidth())
                        }
                    }
                    if (checked) {
                        Text(if (answer == q.correctIndex) "정답이에요!" else "정답은 ${'A' + q.correctIndex}예요.",
                            fontWeight = FontWeight.Bold)
                        Text(q.explanation)
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("대본 속 정답 근거", fontWeight = FontWeight.Bold)
                                Text(q.evidence)
                            }
                        }
                    } else Text("제출 후 정답 해설과 대본 근거를 확인할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall)
                    QuizError(state.error)
                    if (state.error != null) OutlinedButton(onClick = onRetrySave, enabled = !state.saving) {
                        Text("진행 상황 다시 저장")
                    }
                    Button(onClick = if (checked) onNext else onCheck,
                        enabled = answer != null && !state.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(if (state.saving) "저장 중" else if (!checked) "정답 확인"
                            else if (attempt.finished) "결과 보기" else "다음 문제")
                    }
                    OutlinedButton(onClick = onLater, enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("나중에 이어하기") }
                }
            }
            QuizPage.RESULT -> {
                state.attempt?.let { attempt ->
                    Text("오늘의 퀴즈 완료", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${attempt.count}문제 중 ${attempt.score}문제 정답", style = MaterialTheme.typography.titleLarge)
                    Text("이번 운행에서 들은 이야기를 복습했어요. 결과는 기기에 저장됩니다.")
                    Button(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("마치기") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("문제 수 다시 선택")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizError(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
}
