package com.example.pleosaudioguide.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.pleosaudioguide.R
import kotlin.math.PI
import kotlin.math.sin

/** Decorative only: neither playback completion nor quiz generation depends on this loop. */
@Composable
internal fun PotionPreparation(completedCount: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "potion preparation")
    val progress by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "knowledge ingredients")
    // A bounded number of representative ingredients keeps a long trip visually calm.
    val count = completedCount.coerceIn(1, 6)
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Image(painterResource(R.drawable.pot), "학습 재료 항아리",
            Modifier.fillMaxWidth().fillMaxHeight(0.65f).graphicsLayer {
                rotationZ = if (progress in 0.22f..0.58f)
                    (sin(progress * 48 * PI) * 1.6).toFloat() else 0f
            }, contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val mouth = Offset(size.width * 0.5f, size.height * 0.43f)
            val radius = size.width * 0.065f
            repeat(count) { index ->
                val start = 0.03f + index * 0.065f
                val local = (progress - start) / 0.25f
                if (local in 0f..1f) {
                    val falling = ((local - 0.22f) / 0.78f).coerceIn(0f, 1f)
                    val eased = falling * falling
                    val xStart = size.width * if (index % 2 == 0) 0.18f else 0.82f
                    val center = Offset(xStart + (mouth.x - xStart) * eased,
                        size.height * 0.08f + (mouth.y - size.height * 0.08f) * eased)
                    val alpha = if (local < 0.15f) local / 0.15f
                        else (1f - (falling - 0.8f).coerceAtLeast(0f) / 0.2f)
                    val r = radius * (1f - falling * 0.65f)
                    drawCircle(Color(0xFFB78629).copy(alpha = alpha * 0.12f), r,
                        center + Offset(0f, 3.dp.toPx()))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD888), Color(0xFFFFBD50)),
                        center, r), r, center, alpha = alpha)
                    drawOval(Color(0xFFFFF3CC).copy(alpha = alpha),
                        center - Offset(r * 0.5f, r * 0.6f),
                        androidx.compose.ui.geometry.Size(r * 0.65f, r * 0.4f))
                }
            }
            repeat(5) { index ->
                val t = (progress - 0.30f - index * 0.025f) / 0.30f
                if (t in 0f..1f) {
                    val alpha = sin(t * PI).toFloat()
                    drawCircle(Color(0xFFFFF5CF).copy(alpha = alpha),
                        radius * (0.25f + alpha * 0.12f),
                        mouth + Offset((index - 2) * radius * 0.65f, -t * size.height * 0.09f))
                }
            }
            if (progress in 0.62f..0.9f) {
                val alpha = sin((progress - 0.62f) / 0.28f * PI).toFloat().coerceIn(0f, 1f)
                listOf(Offset(size.width * 0.12f, size.height * 0.55f),
                    Offset(size.width * 0.87f, size.height * 0.40f)).forEach { center ->
                    val length = radius * alpha
                    val color = Color(0xFFFFBF3E).copy(alpha = alpha)
                    drawLine(color, center - Offset(length, 0f), center + Offset(length, 0f), 2.dp.toPx())
                    drawLine(color, center - Offset(0f, length), center + Offset(0f, length), 2.dp.toPx())
                }
            }
        }
    }
}
