package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FpsMode
import com.example.model.ResolutionMode
import com.example.ui.theme.*
import com.example.viewmodel.CameraViewModel

@Composable
fun FpsResolutionDialog(
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    val activeFps by viewModel.fpsMode.collectAsState()
    val activeRes by viewModel.resolutionMode.collectAsState()
    val hwInfo by viewModel.hardwareInfo.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("fps_resolution_dialog"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaDarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CinemaBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = CinemaAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "FPS & RESOLUTION CONFIG",
                            color = CinemaAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "High-speed 120-240 FPS & HD Video",
                            color = CinemaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = CinemaTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // FPS Mode Selection
            Text(
                text = "RECORDING FRAME RATE",
                color = CinemaTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FpsMode.values().forEach { mode ->
                    val isSelected = activeFps == mode
                    val isSupported = when (mode) {
                        FpsMode.FPS_120 -> hwInfo.supportsHighSpeed120
                        FpsMode.FPS_240 -> hwInfo.supportsHighSpeed240
                        else -> true
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CinemaAmber.copy(alpha = 0.15f) else CinemaElevatedSurface)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) CinemaAmber else CinemaBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                viewModel.setFpsMode(mode)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) CinemaAmber else CinemaTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (mode.isHighSpeed) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (mode == FpsMode.FPS_240) CinemaRed else CinemaCyan)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (mode == FpsMode.FPS_240) "ULTRA HIGH-SPEED" else "HIGH-SPEED",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Playback: ${mode.slowMoMultiplier}",
                                color = CinemaTextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setFpsMode(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CinemaAmber,
                                unselectedColor = CinemaTextMuted
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Resolution Section
            Text(
                text = "VIDEO RESOLUTION",
                color = CinemaTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolutionMode.values().forEach { res ->
                    val isSelected = activeRes == res
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CinemaCyan else CinemaElevatedSurface)
                            .clickable { viewModel.setResolutionMode(res) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = res.shortName,
                                color = if (isSelected) CinemaBlack else CinemaTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${res.width}x${res.height}",
                                color = if (isSelected) CinemaBlack.copy(alpha = 0.7f) else CinemaTextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hardware info note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaElevatedSurface)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Tip: 120 & 240 FPS high-speed capture uses HD 720p/1080p sensor stream. Stabilization (OIS/EIS) remains active across all frame rates.",
                    color = CinemaTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
