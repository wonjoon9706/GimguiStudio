package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.R
import com.example.pleosaudioguide.TripHistoryState

@androidx.compose.ui.tooling.preview.Preview(name = "운행 종료", widthDp = 420, heightDp = 720)
@Composable
private fun TripFinishedPreview() {
    MaterialTheme {
        TripFinishedScreen(TripHistoryState(endedTrip = com.example.pleosaudioguide.TripEntity("preview", 0, 1), parked = true), {})
    }
}

@Composable
fun TripFinishedScreen(state: TripHistoryState, onLater: () -> Unit, modifier: Modifier = Modifier,
    onQuiz: () -> Unit = {}) {
    BackHandler(onBack = onLater)
    val eligible = state.quizContentCount
    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("POTQUEST", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("주행을 마쳤어요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) {
            Text("P · 주차 상태 확인 완료", Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(28.dp))
        Image(painterResource(R.drawable.pot_placeholder), "나의 항아리",
            Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).size(200.dp))
        Text("나의 항아리", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Text("이번 운행에서 ${state.completedContentCount}개의 콘텐츠를 청취 완료했어요",
            Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
        Text(if (eligible > 0) "퀴즈에 사용할 대본 ${eligible}개가 준비됐어요"
            else "누적 80% 이상 들은 대본이 아직 없어요",
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onQuiz, enabled = eligible > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("퀴즈로 음악 완성하기")
        }
        Text("선택하면 대본을 서버로 보내 문제를 준비해요", Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("나중에 하기")
        }
        Text("주차 상태가 해제되면 이 화면은 자동으로 닫혀요",
            Modifier.padding(top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center)
    }
}
