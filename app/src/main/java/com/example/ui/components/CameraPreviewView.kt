package com.example.ui.components

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.ColorFilterType
import com.example.model.GridType
import com.example.ui.theme.*
import com.example.viewmodel.CameraViewModel
import kotlinx.coroutines.delay

@Composable
fun CameraPreviewView(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorFilter by viewModel.colorFilter.collectAsState()
    val gridType by viewModel.gridType.collectAsState()
    val isLevelEnabled by viewModel.isLevelEnabled.collectAsState()
    val isHistogramEnabled by viewModel.isHistogramEnabled.collectAsState()
    val isFocusPeakingEnabled by viewModel.isFocusPeakingEnabled.collectAsState()
    val isZebraEnabled by viewModel.isZebraEnabled.collectAsState()
    val rollDegrees by viewModel.rollDegrees.collectAsState()
    val pitchDegrees by viewModel.pitchDegrees.collectAsState()
    val audioDb by viewModel.audioDb.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val stabilizerMode by viewModel.stabilizationMode.collectAsState()
    val isHwStabEnabled by viewModel.isHardwareStabilizationEnabled.collectAsState()
    val isHwStabSupported by viewModel.isHardwareStabilizationSupported.collectAsState()

    var tapFocusPoint by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(tapFocusPoint) {
        if (tapFocusPoint != null) {
            delay(1800)
            tapFocusPoint = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    tapFocusPoint = offset
                }
            }
            .testTag("camera_preview_container")
    ) {
        // CameraX PreviewView hosted inside AndroidView
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    viewModel.engine.startCamera(
                        lifecycleOwner = lifecycleOwner,
                        previewView = this
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Live Real-Time Cinematic Color Filter Overlay
        if (colorFilter != ColorFilterType.NORMAL) {
            LiveFilterCanvasOverlay(
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Zebra Stripes / Overexposure warning overlay
        if (isZebraEnabled) {
            ZebraStripesOverlay(modifier = Modifier.fillMaxSize())
        }

        // Focus Peaking Simulation Overlay
        if (isFocusPeakingEnabled) {
            FocusPeakingOverlay(modifier = Modifier.fillMaxSize())
        }

        // Framing Grids (Rule of Thirds, Golden Ratio, Crosshair, CinemaScope)
        if (gridType != GridType.NONE) {
            FramingGridOverlay(gridType = gridType, modifier = Modifier.fillMaxSize())
        }

        // Horizon Level & Gyro Roll/Pitch Overlay
        if (isLevelEnabled) {
            HorizonLevelOverlay(
                roll = rollDegrees,
                pitch = pitchDegrees,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tap to focus reticle
        tapFocusPoint?.let { pos ->
            FocusReticle(offset = pos)
        }

        // Audio VU Meter (left edge)
        if (isRecording) {
            AudioVuMeter(
                audioDb = audioDb,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        // Live Pro Histogram (Top Right)
        if (isHistogramEnabled) {
            HistogramOverlay(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 12.dp)
            )
        }

        // Interactive CameraX VideoCapture Hardware Stabilization Toggle Pill
        StabilizerActiveBadge(
            modeLabel = stabilizerMode.label,
            isHwStabEnabled = isHwStabEnabled,
            isHwStabSupported = isHwStabSupported,
            isRecording = isRecording,
            onToggle = { viewModel.toggleHardwareStabilization() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 80.dp, start = 12.dp)
        )
    }
}

@Composable
fun LiveFilterCanvasOverlay(colorFilter: ColorFilterType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (colorFilter) {
            ColorFilterType.TEAL_AND_ORANGE -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF00B4D8).copy(alpha = 0.22f), Color(0xFFFF9E00).copy(alpha = 0.22f)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    blendMode = BlendMode.Overlay
                )
            }
            ColorFilterType.NOIR -> {
                drawRect(
                    color = Color.Black.copy(alpha = 0.88f),
                    blendMode = BlendMode.Color
                )
                // Add contrast bump
                drawRect(
                    color = Color.Black.copy(alpha = 0.15f),
                    blendMode = BlendMode.Overlay
                )
            }
            ColorFilterType.CYBERPUNK -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF007F).copy(alpha = 0.25f), Color(0xFF00F5D4).copy(alpha = 0.25f)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    blendMode = BlendMode.Color
                )
            }
            ColorFilterType.VINTAGE_70S -> {
                drawRect(
                    color = Color(0xFFD97706).copy(alpha = 0.22f),
                    blendMode = BlendMode.Color
                )
                drawRect(
                    color = Color(0xFF78350F).copy(alpha = 0.12f),
                    blendMode = BlendMode.Overlay
                )
            }
            ColorFilterType.GOLDEN_HOUR -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.26f), Color(0xFFB45309).copy(alpha = 0.20f))
                    ),
                    blendMode = BlendMode.Overlay
                )
            }
            ColorFilterType.EMERALD_INDIE -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF065F46).copy(alpha = 0.24f), Color(0xFF10B981).copy(alpha = 0.18f))
                    ),
                    blendMode = BlendMode.Color
                )
            }
            ColorFilterType.FLAT_LOG -> {
                drawRect(
                    color = Color(0xFF718096).copy(alpha = 0.22f),
                    blendMode = BlendMode.Screen
                )
            }
            ColorFilterType.NORMAL -> {}
        }
    }
}

@Composable
fun StabilizerActiveBadge(
    modeLabel: String,
    isHwStabEnabled: Boolean,
    isHwStabSupported: Boolean,
    isRecording: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = when {
        isRecording && isHwStabEnabled -> CinemaRed
        isHwStabEnabled && isHwStabSupported -> CinemaCyan
        isHwStabEnabled -> CinemaAmber
        else -> CinemaTextMuted
    }
    val statusText = when {
        !isHwStabEnabled -> "HW STAB: OFF"
        isHwStabSupported -> "HW STAB: $modeLabel"
        else -> "STAB: $modeLabel"
    }

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onToggle)
            .testTag("hw_stabilization_toggle")
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isHwStabEnabled) CinemaDarkSurface.copy(alpha = 0.88f)
                    else CinemaDarkSurface.copy(alpha = 0.7f)
                )
                .border(
                    width = 1.dp,
                    color = if (isHwStabEnabled) activeColor.copy(alpha = 0.65f) else CinemaBorder,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isHwStabEnabled) Icons.Default.MotionPhotosOn else Icons.Default.MotionPhotosOff,
                contentDescription = if (isHwStabEnabled) "Hardware stabilization enabled" else "Hardware stabilization disabled",
                tint = activeColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                color = if (isHwStabEnabled) CinemaTextPrimary else CinemaTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun FocusReticle(offset: Offset) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val radius = 38.dp.toPx()
        // Bounding ring
        drawCircle(
            color = CinemaAmber,
            radius = radius,
            center = offset,
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        )
        // Center pip
        drawCircle(
            color = CinemaAmber,
            radius = 3.dp.toPx(),
            center = offset
        )
    }
}

@Composable
fun FramingGridOverlay(gridType: GridType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val gridColor = Color.White.copy(alpha = 0.35f)

        when (gridType) {
            GridType.RULE_OF_THIRDS -> {
                // Vertical lines
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), strokeWidth = 1.dp.toPx())
                // Horizontal lines
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), strokeWidth = 1.dp.toPx())
            }
            GridType.GOLDEN_RATIO -> {
                val phi = 0.618f
                val v1 = w * (1f - phi)
                val v2 = w * phi
                val h1 = h * (1f - phi)
                val h2 = h * phi
                drawLine(gridColor, Offset(v1, 0f), Offset(v1, h), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(v2, 0f), Offset(v2, h), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(0f, h1), Offset(w, h1), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(0f, h2), Offset(w, h2), strokeWidth = 1.dp.toPx())
            }
            GridType.CROSSHAIR -> {
                val cx = w / 2f
                val cy = h / 2f
                val len = 24.dp.toPx()
                drawLine(CinemaAmber.copy(alpha = 0.7f), Offset(cx - len, cy), Offset(cx + len, cy), strokeWidth = 1.5.dp.toPx())
                drawLine(CinemaAmber.copy(alpha = 0.7f), Offset(cx, cy - len), Offset(cx, cy + len), strokeWidth = 1.5.dp.toPx())
                drawCircle(CinemaAmber.copy(alpha = 0.7f), radius = 18.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
            }
            GridType.CINEMASCOPE -> {
                // 2.39:1 crop letterbox bars
                val targetHeight = w / 2.39f
                val barHeight = (h - targetHeight) / 2f
                if (barHeight > 0) {
                    drawRect(Color.Black.copy(alpha = 0.75f), Offset(0f, 0f), Size(w, barHeight))
                    drawRect(Color.Black.copy(alpha = 0.75f), Offset(0f, h - barHeight), Size(w, barHeight))
                    drawLine(CinemaAmber.copy(alpha = 0.6f), Offset(0f, barHeight), Offset(w, barHeight), strokeWidth = 1.dp.toPx())
                    drawLine(CinemaAmber.copy(alpha = 0.6f), Offset(0f, h - barHeight), Offset(w, h - barHeight), strokeWidth = 1.dp.toPx())
                }
            }
            GridType.NONE -> {}
        }
    }
}

@Composable
fun HorizonLevelOverlay(roll: Float, pitch: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val isLeveled = Math.abs(roll) < 1.0f

        val levelColor = if (isLeveled) CinemaGreen else CinemaAmber.copy(alpha = 0.75f)
        val strokeW = if (isLeveled) 2.5.dp.toPx() else 1.5.dp.toPx()

        val rad = Math.toRadians(roll.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val lineLen = 90.dp.toPx()
        val gap = 20.dp.toPx()

        // Left wing
        val lx1 = cx + (-gap - lineLen) * cos
        val ly1 = cy + (-gap - lineLen) * sin
        val lx2 = cx + (-gap) * cos
        val ly2 = cy + (-gap) * sin
        drawLine(levelColor, Offset(lx1, ly1), Offset(lx2, ly2), strokeWidth = strokeW, cap = StrokeCap.Round)

        // Right wing
        val rx1 = cx + gap * cos
        val ry1 = cy + gap * sin
        val rx2 = cx + (gap + lineLen) * cos
        val ry2 = cy + (gap + lineLen) * sin
        drawLine(levelColor, Offset(rx1, ry1), Offset(rx2, ry2), strokeWidth = strokeW, cap = StrokeCap.Round)

        // Center level pip
        drawCircle(
            color = levelColor,
            radius = 4.dp.toPx(),
            center = Offset(cx, cy)
        )
    }
}

@Composable
fun ZebraStripesOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stripeColor = Color.White.copy(alpha = 0.18f)

        var x = 0f
        while (x < w + h) {
            drawLine(
                color = stripeColor,
                start = Offset(x, 0f),
                end = Offset(x - h, h),
                strokeWidth = 2.dp.toPx()
            )
            x += 16.dp.toPx()
        }
    }
}

@Composable
fun FocusPeakingOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(
            color = CinemaGreen.copy(alpha = 0.35f),
            radius = 120.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        )
    }
}
