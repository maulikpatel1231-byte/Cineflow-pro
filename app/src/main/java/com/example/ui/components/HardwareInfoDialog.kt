package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
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
import com.example.ui.theme.*
import com.example.viewmodel.CameraViewModel

@Composable
fun HardwareInfoDialog(
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    val hwInfo by viewModel.hardwareInfo.collectAsState()
    val isCameraXStabSupported by viewModel.isHardwareStabilizationSupported.collectAsState()
    val isPreviewStabSupported by viewModel.isPreviewStabilizationSupported.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("hardware_info_dialog"),
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
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = CinemaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SENSOR HARDWARE CAPABILITY",
                        color = CinemaCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = CinemaTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SpecItem(label = "Primary Sensor", value = "${hwInfo.facing} [ID: ${hwInfo.cameraId}]")
            SpecItem(label = "Hardware Support Level", value = hwInfo.hardwareLevel)
            SpecItem(
                label = "120 FPS High-Speed Video",
                value = if (hwInfo.supportsHighSpeed120) "Supported (Constrained High-Speed / Native 120 FPS)" else "Interpolated High-Speed Mode",
                highlight = hwInfo.supportsHighSpeed120
            )
            SpecItem(
                label = "240 FPS Ultra High-Speed",
                value = if (hwInfo.supportsHighSpeed240) "Supported (Native 240 FPS Slow-Mo)" else "Supported via Enhanced Cinema Pipeline",
                highlight = true
            )
            SpecItem(
                label = "Optical Stabilization (OIS)",
                value = if (hwInfo.supportsOis) "Available (Hardware Gyro Lens Shift)" else "Software Emulated Gyro Damping",
                highlight = hwInfo.supportsOis
            )
            SpecItem(
                label = "Electronic Stabilization (EIS)",
                value = if (hwInfo.supportsEis) "Available (Sensor Video Stabilization)" else "Active via Stream Damping",
                highlight = true
            )
            SpecItem(
                label = "CameraX VideoCapture HW Stabilization",
                value = if (isCameraXStabSupported) "Supported (VideoCapabilities.isStabilizationSupported)" else "Not reported on current lens",
                highlight = isCameraXStabSupported
            )
            SpecItem(
                label = "Super Extreme Preview / Action Stabilization",
                value = if (isPreviewStabSupported || hwInfo.supportsPreviewStabilization) {
                    "Supported (PreviewCapabilities + API 33+ Action Mode)"
                } else {
                    "Enhanced via Gyro Sensor Fusion + Horizon Lock"
                },
                highlight = true
            )
            SpecItem(
                label = "3-Axis OpenGL Recorded Video Gyro-EIS",
                value = "Active on VideoCapture + Preview (CameraEffect GPU Pipeline)",
                highlight = true
            )
            SpecItem(label = "ISO Sensitivity Range", value = "${hwInfo.minIso} - ${hwInfo.maxIso}")
            val minExpStr = if (hwInfo.minExposureTimeNs > 0L) {
                "1/${(1_000_000_000L / hwInfo.minExposureTimeNs).coerceAtLeast(1L)}s"
            } else "N/A"
            val maxExpStr = if (hwInfo.maxExposureTimeNs >= 1_000_000_000L) {
                "${String.format(java.util.Locale.US, "%.1f", hwInfo.maxExposureTimeNs / 1_000_000_000.0)}s"
            } else {
                "1/${(1_000_000_000L / hwInfo.maxExposureTimeNs.coerceAtLeast(1L)).coerceAtLeast(1L)}s"
            }
            SpecItem(label = "Exposure Time Range", value = "$minExpStr - $maxExpStr")
            SpecItem(label = "EV Exposure Steps", value = "${hwInfo.evStep} EV (Range: ${hwInfo.minEv} to ${hwInfo.maxEv})")
            SpecItem(
                label = "Manual Focus Range",
                value = if (hwInfo.maxFocusDistance > 0f) {
                    "0.0 - ${String.format(java.util.Locale.US, "%.1f", hwInfo.maxFocusDistance)} diopters (MF Ready)"
                } else {
                    "Fixed-Focus Lens"
                },
                highlight = hwInfo.maxFocusDistance > 0f
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Close Hardware Specs", color = CinemaBlack, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SpecItem(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CinemaElevatedSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = CinemaTextMuted, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = if (highlight) CinemaGreen else CinemaTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
