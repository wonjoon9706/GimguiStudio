package com.example.pleosaudioguide.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosaudioguide.R

enum class AppHeaderStatus(val label: String) {
    PARKED("정차 중"), DRIVING("주행 모드"), UNKNOWN("확인 중")
}

/** 실제 차량 상태는 GuideApp이 제공한다. 독립 Preview에서는 상태를 임의로 확정하지 않는다. */
val LocalAppHeaderStatus = staticCompositionLocalOf { AppHeaderStatus.UNKNOWN }

@Composable
fun AppHeader(modifier: Modifier = Modifier, compact: Boolean = false,
    status: AppHeaderStatus = LocalAppHeaderStatus.current) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val small = compact || maxWidth < 360.dp
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 40.dp else 68.dp)
                .padding(vertical = if (compact) 0.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 10.dp)) {
                Text("POT-QUEST", Modifier.weight(1f),
                    fontSize = if (compact) 20.sp else if (small) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E3E5))) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).background(Color(0xFFFFC548), CircleShape))
                        Text(status.label, fontSize = if (compact) 10.sp else 11.sp,
                            color = Color(0xFF30302E), maxLines = 1)
                    }
                }
                Image(painterResource(R.drawable.logo), "POTQUEST 로고",
                    Modifier.size(if (compact) 36.dp else if (small) 48.dp else 56.dp))
            }
            HorizontalDivider(color = Color(0xFFDDDEE1))
        }
    }
}
