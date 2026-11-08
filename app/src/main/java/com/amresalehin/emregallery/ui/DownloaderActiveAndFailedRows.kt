package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.DownloadActivityItem
import com.amresalehin.emregallery.data.DownloadActivityManager


@Composable
internal fun ActiveDownloadRow(
    item: DownloadActivityItem,
    onPauseResume: () -> Unit,
    modifier: Modifier = Modifier,
    currentTimeMillis: Long = System.currentTimeMillis()
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Thumbnail with format tag and play icon
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = item.thumbnailUrl ?: item.mediaUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic format badge top-left (no false 4K)
            val formatBadge = getDisplayFormatBadge(item)
            if (formatBadge.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .padding(4.dp)
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

            // Play overlay if video
            if (item.isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Title, byte progress & speed / retry status, and linear progress bar
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.isRetrying) {
                val remainingSec = if (item.nextRetryTimestampMillis != null) {
                    ((item.nextRetryTimestampMillis - currentTimeMillis + 999L) / 1000L).coerceAtLeast(0L)
                } else 0L
                val retryLabel = if (remainingSec > 0) "Retrying in ${remainingSec}s" else "Retrying now..."
                Text(
                    text = retryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB74D),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            } else {
                val speedText = DownloadActivityManager.formatSpeed(item.speedBytesPerSec)
                val bytesText = if (item.totalBytes > 0) {
                    "${DownloadActivityManager.formatBytes(item.bytesRead)} / ${DownloadActivityManager.formatBytes(item.totalBytes)}"
                } else {
                    "${DownloadActivityManager.formatBytes(item.bytesRead)} downloaded"
                }

                Text(
                    text = "$bytesText • $speedText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            LinearProgressIndicator(
                progress = {
                    val p = if (item.isRetrying) 0.05f else item.progress
                    if (p.isNaN() || p.isInfinite() || p < 0f) 0.05f else p.coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (item.isRetrying) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurface,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }

        // Pause/Resume button matching image_ae11436d.png
        Surface(
            onClick = onPauseResume,
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (item.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (item.isPaused) "Resume" else "Pause",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Row displaying a failed item matching image_ae11436d.png with Retry and Delete buttons
 */
