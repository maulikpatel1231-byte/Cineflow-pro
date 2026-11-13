package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AudioVuMeter(
    audioDb: Float,
    modifier: Modifier = Modifier
) {
    // Normalizing -60 dB to 0 dB to 0.0 - 1.0
    val normalizedL = ((audioDb + 60f) / 60f).coerceIn(0.02f, 1.0f)
    val normalizedR = (((audioDb + 60f) / 60f) * 0.94f).coerceIn(0.02f, 1.0f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CinemaDarkSurface.copy(alpha = 0.85f))
            .border(1.dp, CinemaBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MIC VU",
            color = CinemaAmber,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VerticalMeterBar(level = normalizedL, label = "L")
            VerticalMeterBar(level = normalizedR, label = "R")
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${audioDb.toInt()} dB",
            color = CinemaTextSecondary,
            fontSize = 8.sp,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun VerticalMeterBar(level: Float, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .background(CinemaElevatedSurface)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val totalH = size.height
                val activeH = totalH * level
                val startY = totalH - activeH

                // Draw gradient or segmented zones
                val greenZoneY = totalH * 0.4f
                val yellowZoneY = totalH * 0.15f

                // Level fill
                val barColor = when {
                    level > 0.85f -> CinemaRed
                    level > 0.65f -> CinemaAmber
                    else -> CinemaGreen
                }

                drawRect(
                    color = barColor,
                    topLeft = Offset(0f, startY),
                    size = Size(size.width, activeH)
                )

                // Peak clip line at top
                if (level >= 0.95f) {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, 2.dp.toPx())
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = CinemaTextMuted, fontSize = 8.sp)
    }
}

@Composable
fun HistogramOverlay(
    lumaBins: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 92.dp, height = 50.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CinemaDarkSurface.copy(alpha = 0.82f))
            .border(1.dp, CinemaBorder, RoundedCornerShape(6.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val bins = if (lumaBins.isNotEmpty()) lumaBins else List(16) { 0.08f }
            val barCount = bins.size
            val barW = w / barCount.toFloat()

            for (i in 0 until barCount) {
                val mag = bins[i].coerceIn(0.03f, 1.0f)
                val barH = mag * (h * 0.82f)
                val barColor = when {
                    i >= barCount - 2 -> CinemaRed.copy(alpha = 0.85f)
                    i <= 1 -> CinemaAmber.copy(alpha = 0.7f)
                    else -> CinemaCyan.copy(alpha = 0.65f)
                }
                drawRect(
                    color = barColor,
                    topLeft = Offset(i * barW + 0.5f, h - barH),
                    size = Size((barW - 1f).coerceAtLeast(1f), barH)
                )
            }

            // Overexposure clipping threshold marker
            drawLine(
                color = CinemaRed.copy(alpha = 0.75f),
                start = Offset(w - 2f, 0f),
                end = Offset(w - 2f, h),
                strokeWidth = 1.dp.toPx()
            )
        }
        Text(
            text = "LUMA",
            color = CinemaTextMuted,
            fontSize = 7.sp,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
