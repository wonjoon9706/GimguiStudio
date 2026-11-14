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
import androidx.compose.ui.unit.sp
import com.example.pleosaudioguide.R
import com.example.pleosaudioguide.TripHistoryState

@androidx.compose.ui.tooling.preview.Preview(name = "운행 종료", widthDp = 420, heightDp = 720)
@Composable
private fun TripFinishedPreview() {
    MaterialTheme(typography = com.example.pleosaudioguide.ui.theme.Typography) {
        TripFinishedScreen(TripHistoryState(endedTrip = com.example.pleosaudioguide.TripEntity("preview", 0, 1), parked = true), {})
    }
}

@Composable
fun TripFinishedScreen(state: TripHistoryState, onLater: () -> Unit, modifier: Modifier = Modifier,
    onQuiz: () -> Unit = {}) {
    BackHandler(onBack = onLater)
    val eligible = state.quizContentCount
    BoxWithConstraints(modifier.fillMaxSize().padding(20.dp)) {
    val scale = (maxWidth / 420.dp).coerceIn(1f, 1.6f)
    val imageSize = minOf(maxWidth * 0.55f, maxHeight * 0.34f, 400.dp)
    val sectionGap = (maxHeight * 0.05f).coerceIn(24.dp, 64.dp)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
        horizontalAlignment = Alignment.CenterHorizontally) {
        AppHeader(status = if (state.parked) AppHeaderStatus.PARKED else AppHeaderStatus.UNKNOWN)
        Text("주행을 마쳤어요", Modifier.fillMaxWidth().padding(top = 24.dp),
            textAlign = TextAlign.Center, fontSize = (32 * scale).sp, lineHeight = (40 * scale).sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(sectionGap))
        if (state.completedContentCount > 0) {
            PotionPreparation(state.completedContentCount, Modifier.width(imageSize).height(imageSize * 1.22f))
        } else {
            Image(painterResource(R.drawable.pot), "학습 재료 항아리", Modifier.size(imageSize))
        }
        Text(if (eligible > 0) "청취한 ${state.completedContentCount}개 에피소드에서\n학습재료를 준비했어요"
            else if (state.completedContentCount > 0) "완료한 에피소드에 퀴즈용 대본이 없어요"
            else "아직 누적 80% 이상 청취 완료한 에피소드가 없어요",
            Modifier.padding(top = 20.dp), fontSize = (20 * scale).sp, lineHeight = (28 * scale).sp,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(sectionGap))
        if (eligible > 0) {
            Button(onClick = onQuiz, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text("퀴즈 물약 완성하기", fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedButton(onClick = onLater,
            border = if (eligible == 0) androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Black) else null,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color.Black),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (eligible > 0) "나중에 하기" else "홈으로", fontSize = (18 * scale).sp)
        }
    }
    }
}
