package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.DownloadActivityItem


internal fun ActivityThumbnailBox(
    item: DownloadActivityItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 68.dp, height = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val previewModel = item.localUri ?: item.thumbnailUrl ?: item.mediaUrl
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(previewModel)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top-left format tag (e.g. JPG, PNG, MP4)
        val formatBadge = getDisplayFormatBadge(item)
        if (formatBadge.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .padding(top = 4.dp, start = 4.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = formatBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        when {
            item.isDownloading -> {
                // Center play overlay if video
                if (item.isVideo) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Bottom linear progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = item.progress.coerceIn(0.08f, 1f))
                            .background(Color.White)
                    )
                }
            }
            item.isRetrying -> {
                // Bottom-right circular amber retry badge
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFB74D),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .padding(bottom = 4.dp, end = 4.dp)
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retrying",
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            item.isFailed -> {
                // Bottom-right circular white exclamation mark badge
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .padding(bottom = 4.dp, end = 4.dp)
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "Failed",
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            item.isFinished -> {
                // Bottom-right circular green checkmark badge
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2E7D32),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .padding(bottom = 4.dp, end = 4.dp)
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Finished",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Activity Page matching image_ae11436d.png with various controls:
 * - Top bar with Back arrow and Tune/controls button
 * - Filter pills: All, Downloading (X), Failed (Y), Finished (Z)
 * - Section for active downloads (progress bar, speed, pause/resume)
 * - Section for failed downloads (error reason, retry, delete)
 * - Section for past downloads (finished items with green checkmark, size, timestamp)
 * - Floating action button '+' on bottom right
 * - Modal bottom sheet with global controls (retry all, cancel all, clear history)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable



