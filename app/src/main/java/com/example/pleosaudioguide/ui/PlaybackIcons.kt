package com.example.pleosaudioguide.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.R

/** 모든 재생 화면의 공통 아이콘. 접근성 이름과 클릭은 외부 버튼이 소유한다. */
@Composable
internal fun TransportGlyph(kind: String, modifier: Modifier) {
    val resource = when (kind) {
        "play" -> R.drawable.ic_transport_play
        "pause" -> R.drawable.ic_transport_pause
        "stop" -> R.drawable.ic_transport_stop
        "previous" -> R.drawable.ic_transport_previous
        "next" -> R.drawable.ic_transport_next
        else -> null
    }
    if (resource != null) {
        Icon(painterResource(resource), contentDescription = null, modifier = modifier)
        return
    }
    // 고정 30초 이동은 트랙 이전/다음과 다른 모양을 유지한다.
    val color = LocalContentColor.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val forward = kind == "forward"
        drawArc(color, -65f, 300f, false, Offset(w * .12f, h * .12f),
            Size(w * .76f, h * .76f), style = Stroke(width = 2.dp.toPx()))
        drawPath(Path().apply {
            if (forward) {
                moveTo(w * .65f, 0f); lineTo(w * .85f, h * .2f); lineTo(w * .59f, h * .24f)
            } else {
                moveTo(w * .35f, 0f); lineTo(w * .15f, h * .2f); lineTo(w * .41f, h * .24f)
            }
            close()
        }, color)
    }
}
