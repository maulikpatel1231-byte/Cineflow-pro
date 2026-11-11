package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GridType
import com.example.ui.theme.*
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.CameraViewModel

@Composable
fun ProSettingsDialog(
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    val currentGrid by viewModel.gridType.collectAsState()
    val isLevelEnabled by viewModel.isLevelEnabled.collectAsState()
    val isHistogramEnabled by viewModel.isHistogramEnabled.collectAsState()
    val isFocusPeakingEnabled by viewModel.isFocusPeakingEnabled.collectAsState()
    val isZebraEnabled by viewModel.isZebraEnabled.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("pro_settings_dialog"),
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
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = CinemaAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VIEWFINDER & CINEMA TOOLS",
                        color = CinemaAmber,
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

            // Framing Grid selector
            Text(
                text = "FRAMING OVERLAY GRID",
                color = CinemaTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GridType.values().forEach { grid ->
                    val isSelected = currentGrid == grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CinemaAmber.copy(alpha = 0.15f) else CinemaElevatedSurface)
                            .clickable { viewModel.setGridType(grid) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = grid.label,
                            color = if (isSelected) CinemaAmber else CinemaTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setGridType(grid) },
                            colors = RadioButtonDefaults.colors(selectedColor = CinemaAmber)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tool Toggles
            Text(
                text = "CINEMATOGRAPHY ASSISTS",
                color = CinemaTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            ToolToggleRow(
                title = "Horizon Gyro Level",
                subtitle = "Real-time tilt and roll leveling line",
                isChecked = isLevelEnabled,
                onToggle = { viewModel.toggleLevelOverlay() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ToolToggleRow(
                title = "RGB / Luma Live Histogram",
                subtitle = "Waveform and clipping exposure monitoring",
                isChecked = isHistogramEnabled,
                onToggle = { viewModel.toggleHistogram() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ToolToggleRow(
                title = "Focus Peaking Assist",
                subtitle = "Highlight high-contrast in-focus edges",
                isChecked = isFocusPeakingEnabled,
                onToggle = { viewModel.toggleFocusPeaking() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ToolToggleRow(
                title = "Zebra Exposure Stripes",
                subtitle = "Overexposure highlights indicator",
                isChecked = isZebraEnabled,
                onToggle = { viewModel.toggleZebraStripes() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ToolToggleRow(
                title = "Cinema Video Torch",
                subtitle = "Continuous LED illumination fill light",
                isChecked = isTorchOn,
                onToggle = { viewModel.toggleTorch() }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Hardware info button
            Button(
                onClick = { viewModel.openModal(ActiveModal.HardwareCapabilities) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_hardware_specs_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CinemaElevatedSurface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = CinemaCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "View Camera Hardware Capabilities", color = CinemaCyan, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ToolToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CinemaElevatedSurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = CinemaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = CinemaTextMuted, fontSize = 10.sp)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = CinemaAmber,
                checkedTrackColor = CinemaAmber.copy(alpha = 0.4f)
            )
        )
    }
}
