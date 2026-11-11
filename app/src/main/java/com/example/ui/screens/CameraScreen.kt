package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExposureMode
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.CameraViewModel

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val fpsMode by viewModel.fpsMode.collectAsState()
    val resolutionMode by viewModel.resolutionMode.collectAsState()
    val stabilizationMode by viewModel.stabilizationMode.collectAsState()
    val exposureMode by viewModel.exposureMode.collectAsState()
    val manualIso by viewModel.manualIso.collectAsState()
    val shutterIndex by viewModel.manualShutterIndex.collectAsState()
    val manualEv by viewModel.manualEv.collectAsState()
    val colorFilter by viewModel.colorFilter.collectAsState()
    val activeModal by viewModel.activeModal.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val clips by viewModel.clips.collectAsState()

    val shutterText = viewModel.engine.shutterSpeeds.getOrNull(shutterIndex)?.first ?: "1/120s"

    // If modal is active, back press dismisses modal
    BackHandler(enabled = activeModal !is ActiveModal.None) {
        viewModel.closeModal()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
    ) {
        // Main Camera Preview Layer
        CameraPreviewView(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Top Pro Bar (Status, FPS/Res, Stabilizer, Flip, Assists)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(CinemaOverlayTranslucent)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recording Tally Indicator
                if (isRecording) {
                    val minutes = recordingDuration / 60
                    val seconds = recordingDuration % 60
                    val frames = ((recordingDuration * fpsMode.fps) % fpsMode.fps).toInt()
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CinemaRed)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format("REC %02d:%02d:%02d", minutes, seconds, frames),
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CinemaElevatedSurface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STBY",
                            color = CinemaGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Middle: FPS & Resolution Quick Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (fpsMode.isHighSpeed) CinemaAmber.copy(alpha = 0.2f) else CinemaElevatedSurface)
                        .border(
                            1.dp,
                            if (fpsMode.isHighSpeed) CinemaAmber else CinemaBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.openModal(ActiveModal.FpsResolutionSelector) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("fps_resolution_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (fpsMode.isHighSpeed) CinemaAmber else CinemaTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${fpsMode.label} • ${resolutionMode.shortName}",
                        color = if (fpsMode.isHighSpeed) CinemaAmber else CinemaTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Top Actions (Stab, Torch, Flip, Assists)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stabilizer Button
                    IconButton(
                        onClick = { viewModel.openModal(ActiveModal.StabilizerSelector) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (stabilizationMode.label != "Off") CinemaCyan.copy(alpha = 0.2f) else CinemaElevatedSurface)
                            .testTag("stabilizer_btn")
                    ) {
                        Icon(
                            Icons.Default.MotionPhotosOn,
                            contentDescription = "Stabilizer",
                            tint = if (stabilizationMode.label != "Off") CinemaCyan else CinemaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Torch Button
                    IconButton(
                        onClick = { viewModel.toggleTorch() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isTorchOn) CinemaAmber.copy(alpha = 0.25f) else CinemaElevatedSurface)
                            .testTag("torch_btn")
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Torch",
                            tint = if (isTorchOn) CinemaAmber else CinemaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Switch Camera
                    IconButton(
                        onClick = { viewModel.switchCamera() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CinemaElevatedSurface)
                            .testTag("switch_cam_btn")
                    ) {
                        Icon(
                            Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = CinemaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Viewfinder Assists & Grids
                    IconButton(
                        onClick = { viewModel.openModal(ActiveModal.ProSettingsDialog) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CinemaElevatedSurface)
                            .testTag("pro_assists_btn")
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Tools",
                            tint = CinemaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Floating Quick Parameter Readout Bar (Above Bottom Deck)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CinemaDarkSurface.copy(alpha = 0.88f))
                .border(1.dp, CinemaBorder, RoundedCornerShape(24.dp))
                .clickable { viewModel.openModal(ActiveModal.ManualExposurePanel) }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("quick_exposure_bar"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParamPill(
                label = "EXP",
                value = if (exposureMode == ExposureMode.MANUAL) "M" else "AUTO",
                isHighlighted = exposureMode == ExposureMode.MANUAL
            )
            ParamPill(
                label = "ISO",
                value = if (exposureMode == ExposureMode.MANUAL) "$manualIso" else "AUTO",
                isHighlighted = exposureMode == ExposureMode.MANUAL
            )
            ParamPill(
                label = "SHT",
                value = if (exposureMode == ExposureMode.MANUAL) shutterText else "AUTO",
                isHighlighted = exposureMode == ExposureMode.MANUAL
            )
            ParamPill(
                label = "EV",
                value = "${if (manualEv > 0) "+" else ""}${String.format("%.1f", manualEv)}",
                isHighlighted = manualEv != 0f
            )
            ParamPill(
                label = "LUT",
                value = colorFilter.badge,
                isHighlighted = colorFilter.badge != "Standard"
            )
        }

        // Bottom Cinema Control Deck
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CinemaOverlayTranslucent)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Gallery Thumbnail Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { viewModel.navigateTo("gallery") }
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CinemaCardSurface)
                            .border(1.5.dp, CinemaBorder, RoundedCornerShape(14.dp))
                            .testTag("gallery_thumbnail_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (clips.isNotEmpty()) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "Gallery",
                                tint = CinemaAmber,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = CinemaTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CLIPS (${clips.size})",
                        color = CinemaTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Center: Pro Cinema Shutter/Record Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(CinemaElevatedSurface)
                        .border(
                            width = 3.dp,
                            color = if (isRecording) CinemaRed else Color.White,
                            shape = CircleShape
                        )
                        .clickable { viewModel.toggleRecording() }
                        .padding(6.dp)
                        .testTag("shutter_record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        // Square stop icon
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CinemaRed)
                        )
                    } else {
                        // Red recording circle
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(CinemaRed)
                        )
                    }
                }

                // Right: Cinema LUT Filters & Pro Manual Exposure
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Filters button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.openModal(ActiveModal.FilterSelector) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (colorFilter.badge != "Standard") CinemaAmber.copy(alpha = 0.2f) else CinemaCardSurface)
                                .border(
                                    width = 1.5.dp,
                                    color = if (colorFilter.badge != "Standard") CinemaAmber else CinemaBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .testTag("filters_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Filters",
                                tint = if (colorFilter.badge != "Standard") CinemaAmber else CinemaTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "FILTERS",
                            color = if (colorFilter.badge != "Standard") CinemaAmber else CinemaTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Pro Manual Exposure Drawer Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.openModal(ActiveModal.ManualExposurePanel) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (exposureMode == ExposureMode.MANUAL) CinemaCyan.copy(alpha = 0.2f) else CinemaCardSurface)
                                .border(
                                    width = 1.5.dp,
                                    color = if (exposureMode == ExposureMode.MANUAL) CinemaCyan else CinemaBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .testTag("pro_exposure_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PRO M",
                                color = if (exposureMode == ExposureMode.MANUAL) CinemaCyan else CinemaTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "MANUAL",
                            color = if (exposureMode == ExposureMode.MANUAL) CinemaCyan else CinemaTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Modals & Drawers Overlay
        AnimatedVisibility(
            visible = activeModal !is ActiveModal.None,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            when (activeModal) {
                is ActiveModal.ManualExposurePanel -> {
                    ManualExposurePanel(
                        viewModel = viewModel,
                        onClose = { viewModel.closeModal() }
                    )
                }
                is ActiveModal.FilterSelector -> {
                    FilterSelectorSheet(
                        viewModel = viewModel,
                        onClose = { viewModel.closeModal() }
                    )
                }
                is ActiveModal.FpsResolutionSelector -> {
                    FpsResolutionDialog(
                        viewModel = viewModel,
                        onClose = { viewModel.closeModal() }
                    )
                }
                is ActiveModal.StabilizerSelector -> {
                    StabilizerSelector(
                        viewModel = viewModel,
                        onClose = { viewModel.closeModal() }
                    )
                }
                is ActiveModal.ProSettingsDialog -> {
                    ProSettingsDialog(
                        viewModel = viewModel,
                        onClose = { viewModel.closeModal() }
                    )
                }
                is ActiveModal.HardwareCapabilities -> {
                    HardwareInfoDialog(
                        viewModel = viewModel,
                        onClose = { viewModel.closeModal() }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ParamPill(
    label: String,
    value: String,
    isHighlighted: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = CinemaTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = if (isHighlighted) CinemaAmber else CinemaTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
