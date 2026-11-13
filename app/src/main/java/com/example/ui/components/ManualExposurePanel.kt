package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExposureMode
import com.example.model.FocusMode
import com.example.model.WhiteBalanceMode
import com.example.ui.theme.*
import com.example.viewmodel.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualExposurePanel(
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    val exposureMode by viewModel.exposureMode.collectAsState()
    val manualIso by viewModel.manualIso.collectAsState()
    val shutterIndex by viewModel.manualShutterIndex.collectAsState()
    val manualEv by viewModel.manualEv.collectAsState()
    val focusMode by viewModel.focusMode.collectAsState()
    val manualFocusDistance by viewModel.manualFocusDistance.collectAsState()
    val wbMode by viewModel.whiteBalanceMode.collectAsState()
    val isAeLocked by viewModel.isAeLocked.collectAsState()
    val fpsMode by viewModel.fpsMode.collectAsState()
    val hwInfo by viewModel.hardwareInfo.collectAsState()

    val baseIsoPresets = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
    val isoPresets = remember(hwInfo.minIso, hwInfo.maxIso) {
        val inRange = baseIsoPresets.filter { it in hwInfo.minIso..hwInfo.maxIso }
        (listOf(hwInfo.minIso) + inRange + listOf(hwInfo.maxIso)).distinct().sorted()
    }
    val shutterOptions = viewModel.engine.shutterSpeeds
    val minEvFloat = remember(hwInfo) { (hwInfo.minEv * hwInfo.evStep).coerceAtMost(-0.5f) }
    val maxEvFloat = remember(hwInfo) { (hwInfo.maxEv * hwInfo.evStep).coerceAtLeast(0.5f) }

    // Calculate ideal 180-degree cinema shutter speed for currently selected FPS
    val idealShutterSpeed = "1/${fpsMode.fps * 2}s"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("manual_exposure_panel"),
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
                Column {
                    Text(
                        text = "MANUAL EXPOSURE & SENSOR PRO",
                        color = CinemaAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Direct hardware ISO, Shutter & Focus controls",
                        color = CinemaTextSecondary,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("close_exposure_panel_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = CinemaTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Exposure Mode Selector (Auto vs Manual Pro)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinemaElevatedSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModeTabButton(
                    title = "AUTO EXPOSURE (AE)",
                    isSelected = exposureMode == ExposureMode.AUTO,
                    onClick = { viewModel.setExposureMode(ExposureMode.AUTO) }
                )
                ModeTabButton(
                    title = "MANUAL PRO (M)",
                    isSelected = exposureMode == ExposureMode.MANUAL,
                    onClick = { viewModel.setExposureMode(ExposureMode.MANUAL) }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (exposureMode == ExposureMode.MANUAL) {
                // ISO Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "SENSOR ISO", color = CinemaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "ISO $manualIso", color = CinemaAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(isoPresets) { iso ->
                        val isSelected = manualIso == iso
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CinemaAmber else CinemaElevatedSurface)
                                .clickable { viewModel.setManualIso(iso) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$iso",
                                color = if (isSelected) CinemaBlack else CinemaTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Shutter Speed Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "SHUTTER SPEED", color = CinemaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = shutterOptions.getOrNull(shutterIndex)?.first ?: "1/120s",
                            color = CinemaCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(180° = $idealShutterSpeed)",
                            color = CinemaTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(shutterOptions.indices.toList()) { idx ->
                        val option = shutterOptions[idx]
                        val supported = option.second in hwInfo.minExposureTimeNs..hwInfo.maxExposureTimeNs
                        val isSelected = shutterIndex == idx
                        val isIdeal = option.first == idealShutterSpeed
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isSelected -> CinemaCyan
                                        !supported -> CinemaElevatedSurface.copy(alpha = 0.45f)
                                        isIdeal -> CinemaElevatedSurface.copy(alpha = 0.9f)
                                        else -> CinemaElevatedSurface
                                    }
                                )
                                .border(
                                    width = if (isIdeal && !isSelected && supported) 1.dp else 0.dp,
                                    color = if (isIdeal && !isSelected && supported) CinemaCyan else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = supported) { viewModel.setShutterIndex(idx) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.first,
                                color = when {
                                    isSelected -> CinemaBlack
                                    !supported -> CinemaTextMuted
                                    else -> CinemaTextPrimary
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            } else {
                // Exposure Compensation (EV) in Auto Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "EXPOSURE COMPENSATION (EV)", color = CinemaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${if (manualEv > 0) "+" else ""}${String.format("%.1f", manualEv)} EV",
                        color = CinemaAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = manualEv.coerceIn(minEvFloat, maxEvFloat),
                    onValueChange = { viewModel.setManualEv(it) },
                    valueRange = minEvFloat..maxEvFloat,
                    colors = SliderDefaults.colors(
                        thumbColor = CinemaAmber,
                        activeTrackColor = CinemaAmber,
                        inactiveTrackColor = CinemaElevatedSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // AE Lock button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAeLocked) CinemaAmber.copy(alpha = 0.2f) else CinemaElevatedSurface)
                        .clickable { viewModel.toggleAeLock() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAeLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "AE Lock",
                            tint = if (isAeLocked) CinemaAmber else CinemaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAeLocked) "AUTO EXPOSURE LOCKED (AE-L)" else "LOCK AUTO EXPOSURE",
                            color = if (isAeLocked) CinemaAmber else CinemaTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Switch(
                        checked = isAeLocked,
                        onCheckedChange = { viewModel.toggleAeLock() },
                        colors = SwitchDefaults.colors(checkedThumbColor = CinemaAmber)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Focus Mode Section
            Text(text = "FOCUS CONTROL", color = CinemaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FocusMode.values().forEach { mode ->
                    val isSelected = focusMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CinemaCyan else CinemaElevatedSurface)
                            .clickable { viewModel.setFocusMode(mode) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.name.take(4),
                            color = if (isSelected) CinemaBlack else CinemaTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            if (focusMode == FocusMode.MANUAL) {
                Spacer(modifier = Modifier.height(8.dp))
                if (hwInfo.maxFocusDistance <= 0f) {
                    Text(
                        text = "Fixed-focus lens detected on this camera (manual diopter slider unavailable)",
                        color = CinemaTextMuted,
                        fontSize = 11.sp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Near (Macro)", color = CinemaTextMuted, fontSize = 10.sp)
                        Text(text = "MF Distance", color = CinemaCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Infinity (∞)", color = CinemaTextMuted, fontSize = 10.sp)
                    }
                    Slider(
                        value = manualFocusDistance,
                        onValueChange = { viewModel.setManualFocusDistance(it) },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = CinemaCyan,
                            activeTrackColor = CinemaCyan,
                            inactiveTrackColor = CinemaElevatedSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // White Balance Section
            Text(text = "WHITE BALANCE (AWB / KELVIN)", color = CinemaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(WhiteBalanceMode.values()) { wb ->
                    val isSelected = wbMode == wb
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CinemaAmber else CinemaElevatedSurface)
                            .clickable { viewModel.setWhiteBalance(wb) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = wb.label.substringBefore(" ("),
                            color = if (isSelected) CinemaBlack else CinemaTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ModeTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) CinemaAmber else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) CinemaBlack else CinemaTextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
