package com.example.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.model.VideoClip
import com.example.ui.theme.*
import com.example.viewmodel.CameraViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val clips by viewModel.clips.collectAsState()
    val selectedClip by viewModel.selectedClip.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val context = LocalContext.current

    BackHandler {
        if (selectedClip != null) {
            viewModel.clearSelectedClip()
        } else {
            viewModel.navigateTo("camera")
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("gallery_screen"),
        containerColor = CinemaBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedClip != null) "SLOW-MOTION PLAYER" else "RECORDED CINEMA CLIPS",
                            color = CinemaAmber,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (selectedClip != null) "${selectedClip?.fps} FPS High-Speed Video" else "${clips.size} clips saved",
                            color = CinemaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedClip != null) {
                                viewModel.clearSelectedClip()
                            } else {
                                viewModel.navigateTo("camera")
                            }
                        },
                        modifier = Modifier.testTag("gallery_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CinemaTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CinemaDarkSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedClip != null) {
                // Video Player Screen
                ClipPlayerView(
                    clip = selectedClip!!,
                    speed = playbackSpeed,
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onDelete = {
                        viewModel.deleteClip(selectedClip!!.id)
                    },
                    onShare = {
                        val targetClip = selectedClip!!
                        try {
                            val shareUri = if (targetClip.filePath.startsWith("content://")) {
                                Uri.parse(targetClip.filePath)
                            } else {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    File(targetClip.filePath)
                                )
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Video Clip"))
                        } catch (e: Exception) {
                            viewModel.showToast("Unable to share clip: ${e.message ?: "No compatible app"}")
                        }
                    }
                )
            } else {
                // Clips List
                if (clips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = CinemaTextMuted,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No recorded video clips yet",
                                color = CinemaTextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Record stabilized 30-240 FPS cinema video from the viewfinder. Clips save to Movies/CineFlowPro.",
                                color = CinemaTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(clips, key = { it.id }) { clip ->
                            ClipListItem(
                                clip = clip,
                                onClick = { viewModel.selectClipForPlayback(clip) },
                                onDelete = { viewModel.deleteClip(clip.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipListItem(
    clip: VideoClip,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("clip_item_${clip.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaDarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CinemaBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail icon with FPS badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinemaElevatedSurface)
                    .border(1.dp, CinemaBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (clip.isSlowMotion) CinemaAmber else CinemaCyan,
                    modifier = Modifier.size(28.dp)
                )

                // High speed badge in corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(topStart = 6.dp))
                        .background(if (clip.fps >= 240) CinemaRed else if (clip.fps >= 120) CinemaAmber else CinemaCyan)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${clip.fps}F",
                        color = CinemaBlack,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clip.title,
                    color = CinemaTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${clip.resolution} • ${clip.fps} FPS",
                        color = if (clip.isSlowMotion) CinemaAmber else CinemaCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• ${clip.durationSeconds}s",
                        color = CinemaTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "LUT: ${clip.filterName} | Stab: ${clip.stabilization} | ${clip.fileSizeFormatted}",
                    color = CinemaTextMuted,
                    fontSize = 10.sp
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = CinemaTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ClipPlayerView(
    clip: VideoClip,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var isPlaying by remember(clip.id) { mutableStateOf(true) }
    var hasPlaybackError by remember(clip.id) { mutableStateOf(false) }
    var mediaPlayerInstance by remember(clip.id) { mutableStateOf<MediaPlayer?>(null) }
    var videoViewRef by remember(clip.id) { mutableStateOf<VideoView?>(null) }
    val speedOptions = listOf(0.125f, 0.25f, 0.5f, 1.0f)

    DisposableEffect(clip.id) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {
            }
            mediaPlayerInstance = null
        }
    }

    LaunchedEffect(speed, mediaPlayerInstance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayerInstance?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video Viewport Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CinemaDarkSurface)
                .border(1.dp, CinemaBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!hasPlaybackError) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            videoViewRef = this
                            val videoUri = if (clip.filePath.startsWith("content://")) {
                                Uri.parse(clip.filePath)
                            } else {
                                val file = File(clip.filePath)
                                if (file.exists()) Uri.fromFile(file) else null
                            }
                            if (videoUri != null) {
                                setVideoURI(videoUri)
                            } else {
                                hasPlaybackError = true
                            }
                            setOnErrorListener { _, _, _ ->
                                hasPlaybackError = true
                                isPlaying = false
                                true
                            }
                            setOnPreparedListener { mp ->
                                mediaPlayerInstance = mp
                                mp.isLooping = true
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val params = PlaybackParams()
                                        params.speed = speed
                                        mp.playbackParams = params
                                    }
                                } catch (_: Exception) {
                                }
                                mp.start()
                                isPlaying = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Play / Pause Overlay Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            mediaPlayerInstance?.let { mp ->
                                try {
                                    if (mp.isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        mp.start()
                                        isPlaying = true
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = CinemaAmber,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = CinemaRed,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unable to play this video clip",
                        color = CinemaTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Current Speed Overlay Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CinemaRed)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${speed}x SPEED",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Slow-Motion Speed Selector
        Text(
            text = "SLOW-MOTION PLAYBACK RATE",
            color = CinemaAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            speedOptions.forEach { s ->
                val isSelected = speed == s
                val label = when (s) {
                    0.125f -> "0.125x (8x Slow)"
                    0.25f -> "0.25x (4x Slow)"
                    0.5f -> "0.5x (2x Slow)"
                    else -> "1.0x (Normal)"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CinemaAmber else CinemaElevatedSurface)
                        .clickable { onSpeedChange(s) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) CinemaBlack else CinemaTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clip Metadata Inspector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CinemaDarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CinemaBorder))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "RECORDING EXIF & CAMERA PARAMETERS",
                    color = CinemaCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                MetadataRow(label = "Resolution & Rate", value = "${clip.resolution} @ ${clip.fps} FPS")
                MetadataRow(label = "Shutter Speed & ISO", value = "${clip.shutterSpeedText} • ISO ${clip.iso}")
                MetadataRow(label = "Stabilization Used", value = clip.stabilization)
                MetadataRow(label = "Color Grading LUT", value = clip.filterName)
                MetadataRow(label = "Duration & Size", value = "${clip.durationSeconds} seconds • ${clip.fileSizeFormatted}")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Buttons (Share & Delete)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .testTag("share_clip_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = CinemaBlack, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Share Video", color = CinemaBlack, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .testTag("delete_clip_btn"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CinemaRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = CinemaRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Delete Clip", color = CinemaRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = CinemaTextMuted, fontSize = 11.sp)
        Text(text = value, color = CinemaTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
