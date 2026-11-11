package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ColorFilterType
import com.example.ui.theme.*
import com.example.viewmodel.CameraViewModel

@Composable
fun FilterSelectorSheet(
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    val activeFilter by viewModel.colorFilter.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("filter_selector_sheet"),
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
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CinemaAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "CINEMATIC COLOR GRADING (LUTS)",
                            color = CinemaAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Real-time color science matrix preview",
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

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ColorFilterType.values()) { filter ->
                    val isSelected = activeFilter == filter
                    FilterThumbnailCard(
                        filter = filter,
                        isSelected = isSelected,
                        onClick = { viewModel.setColorFilter(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter description info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinemaElevatedSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeFilter.displayName,
                            color = CinemaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "LIVE ACTIVE",
                            color = CinemaGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeFilter.description,
                        color = CinemaTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterThumbnailCard(
    filter: ColorFilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val gradientBrush = when (filter) {
        ColorFilterType.NORMAL -> Brush.linearGradient(listOf(Color(0xFF4A5568), Color(0xFF718096)))
        ColorFilterType.TEAL_AND_ORANGE -> Brush.linearGradient(listOf(Color(0xFF00B4D8), Color(0xFFFF9E00)))
        ColorFilterType.NOIR -> Brush.linearGradient(listOf(Color(0xFF1A202C), Color(0xFFE2E8F0)))
        ColorFilterType.CYBERPUNK -> Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF00F5D4)))
        ColorFilterType.VINTAGE_70S -> Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFF78350F)))
        ColorFilterType.GOLDEN_HOUR -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFB45309)))
        ColorFilterType.EMERALD_INDIE -> Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF10B981)))
        ColorFilterType.FLAT_LOG -> Brush.linearGradient(listOf(Color(0xFF4B5563), Color(0xFF9CA3AF)))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(gradientBrush)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) CinemaAmber else CinemaBorder,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = filter.badge,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = filter.displayName.take(11),
            color = if (isSelected) CinemaAmber else CinemaTextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
