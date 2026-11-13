package com.example.pleosaudioguide.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.PlaybackResume

@Composable
fun ResumePlaybackDialog(saved: PlaybackResume, onRestart: () -> Unit, onResume: () -> Unit,
    onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이어서 들을까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(saved.content.title)
                Text("마지막 재생 위치 " + resumeTime(saved.positionMs) +
                    if (saved.durationMs > 0) " / " + resumeTime(saved.durationMs) else "")
                Text("건너뛴 구간은 청취 완료로 인정되지 않아요.")
            }
        },
        dismissButton = {
            TextButton(onClick = onRestart, modifier = Modifier.heightIn(min = 56.dp)) { Text("처음부터") }
        },
        confirmButton = {
            TextButton(onClick = onResume, modifier = Modifier.heightIn(min = 56.dp)) { Text("이어듣기") }
        }
    )
}

private fun resumeTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
