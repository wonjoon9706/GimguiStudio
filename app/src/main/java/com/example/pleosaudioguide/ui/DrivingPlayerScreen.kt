package com.example.pleosaudioguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 주행/정차 확인 불가 시 단일 콘텐츠와 기본 재생 명령만 제공한다. 상세 화면으로 나가는 경로 없음. */
@Composable
fun DrivingPlayerScreen(
    title: String,
    isPlaying: Boolean,
    hasCurrentAudio: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    preparing: Boolean,
    usingPleosVoice: Boolean,
    positionMs: Long,
    durationMs: Long,
    canResume: Boolean,
    controlsReady: Boolean,
    maxTitleLength: Int,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 주차 전 이전 목록/퀴즈 화면으로 돌아가지 않는다.
    BackHandler {}
    Column(modifier.testTag("drivingPlayer").padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("POTQUEST · 오디오", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val limit = maxTitleLength.coerceIn(0, 80)
        if (limit > 0) Text(title.ifBlank { "선택된 콘텐츠 없음" }.take(limit),
            modifier = Modifier.testTag("drivingTitle"), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (!usingPleosVoice && hasCurrentAudio && durationMs > 0) {
            LinearProgressIndicator(progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth())
            Text("${time(positionMs)} / ${time(durationMs)}",
                modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPrevious, enabled = hasCurrentAudio && hasPrevious && !preparing && controlsReady,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text("이전", fontSize = 18.sp) }
            Button(onClick = if (hasCurrentAudio) onToggle else onResume,
                enabled = controlsReady && !preparing && (hasCurrentAudio || canResume),
                modifier = Modifier.weight(1.2f).heightIn(min = 64.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                Text(if (preparing) "준비 중" else if (!hasCurrentAudio && canResume) "이어 듣기"
                    else if (isPlaying) { if (usingPleosVoice) "중지" else "일시정지" } else "재생",
                    maxLines = 1, fontSize = 16.sp)
            }
            Button(onClick = onNext, enabled = hasCurrentAudio && hasNext && !preparing && controlsReady,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text("다음", fontSize = 18.sp) }
        }
        Text(if (usingPleosVoice && hasCurrentAudio) "시연 음성은 중지 후 처음부터 재생해요"
            else "주차 후 탐색과 퀴즈를 이용할 수 있어요", style = MaterialTheme.typography.bodySmall)
    }
}

private fun time(ms: Long): String = (ms.coerceAtLeast(0) / 1000).let { "%d:%02d".format(it / 60, it % 60) }
