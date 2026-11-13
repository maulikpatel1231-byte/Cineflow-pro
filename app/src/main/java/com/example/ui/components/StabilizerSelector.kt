package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StabilizationMode
import com.example.ui.theme.*
import com.example.viewmodel.CameraViewModel

@Composable
fun StabilizerSelector(
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    val activeMode by viewModel.stabilizationMode.collectAsState()
    val isHwStabEnabled by viewModel.isHardwareStabilizationEnabled.collectAsState()
    val isHwStabSupported by viewModel.isHardwareStabilizationSupported.collectAsState()
    val isHwStabActive by viewModel.isHardwareStabilizationActive.collectAsState()
    val rollDegrees by viewModel.rollDegrees.collectAsState()
    val pitchDegrees by viewModel.pitchDegrees.collectAsState()
    val damping by viewModel.stabilizerDamping.collectAsState()
    val hwInfo by viewModel.hardwareInfo.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("stabilizer_selector_modal"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaDarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CinemaBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MotionPhotosOn,
                        contentDescription = null,
                        tint = CinemaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "IMAGE STABILIZATION ENGINE",
                            color = CinemaCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Hardware OIS & EIS Electronic Damping",
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

            // Live Gyroscope Damping Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinemaElevatedSurface)
                    .border(1.dp, CinemaBorder, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    // Fixed outer gimbal target
                    drawCircle(
                        color = CinemaTextMuted.copy(alpha = 0.4f),
                        radius = 36.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = CinemaTextMuted.copy(alpha = 0.3f),
                        radius = 18.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Moving gyro stabilizing floating reticle
                    val offsetX = (rollDegrees * 2.5f).coerceIn(-30f, 30f)
                    val offsetY = (pitchDegrees * 2.5f).coerceIn(-20f, 20f)
                    val gyroCenter = Offset(cx + offsetX, cy + offsetY)

                    // Stabilized counter-shift indicator (if active)
                    val stabOffset = if (activeMode != StabilizationMode.OFF) {
                        Offset(cx - (offsetX * 0.75f), cy - (offsetY * 0.75f))
                    } else {
                        gyroCenter
                    }

                    // Raw sensor drift dot (red/amber)
                    drawCircle(
                        color = CinemaAmber.copy(alpha = 0.6f),
                        radius = 4.dp.toPx(),
                        center = gyroCenter
                    )

                    // Stabilized vector center (Cyan)
                    if (activeMode != StabilizationMode.OFF) {
                        drawCircle(
                            color = CinemaCyan,
                            radius = 6.dp.toPx(),
                            center = stabOffset
                        )
                        drawLine(
                            color = CinemaCyan.copy(alpha = 0.5f),
                            start = gyroCenter,
                            end = stabOffset,
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "GYRO TELEMETRY",
                        color = CinemaTextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Roll: ${String.format("%.1f", rollDegrees)}°",
                        color = CinemaTextPrimary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Pitch: ${String.format("%.1f", pitchDegrees)}°",
                        color = CinemaTextPrimary,
                        fontSize = 10.sp
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "DAMPING EFFICIENCY",
                        color = CinemaTextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(damping * 100).toInt()}%",
                        color = CinemaGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CameraX VideoCapture Hardware-Accelerated Video Stabilization Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isHwStabEnabled) CinemaCyan.copy(alpha = 0.14f)
                        else CinemaElevatedSurface
                    )
                    .border(
                        width = 1.dp,
                        color = if (isHwStabEnabled) CinemaCyan else CinemaBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.toggleHardwareStabilization(!isHwStabEnabled) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Hardware-Accelerated Stabilization",
                        color = if (isHwStabEnabled) CinemaCyan else CinemaTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            !isHwStabSupported -> "CameraX VideoCapture HW stab unsupported on lens (OIS/EIS fallback)"
                            isHwStabActive -> "CameraX VideoCapture stabilization ACTIVE"
                            else -> "CameraX VideoCapture stabilization OFF (raw sensor)"
                        },
                        color = CinemaTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isHwStabEnabled,
                    onCheckedChange = { viewModel.toggleHardwareStabilization(it) },
                    modifier = Modifier.testTag("hw_stabilization_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CinemaBlack,
                        checkedTrackColor = CinemaCyan,
                        uncheckedThumbColor = CinemaTextSecondary,
                        uncheckedTrackColor = CinemaDarkSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Modes List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StabilizationMode.values().forEach { mode ->
                    val isSelected = activeMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CinemaCyan.copy(alpha = 0.15f) else CinemaElevatedSurface)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) CinemaCyan else CinemaBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.setStabilizationMode(mode) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) CinemaCyan else CinemaTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${mode.iconName})",
                                    color = CinemaTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = mode.description,
                                color = CinemaTextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setStabilizationMode(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CinemaCyan,
                                unselectedColor = CinemaTextMuted
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Hardware status: OIS ${if (hwInfo.supportsOis) "Ready (Lens Gyro Shift)" else "Emulated / Software"} • EIS ${if (hwInfo.supportsEis) "Ready (Sensor Flow Crop)" else "Active"}",
                color = CinemaTextMuted,
                fontSize = 10.sp
            )
        }
    }
}
