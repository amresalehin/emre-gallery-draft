package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.DownloadActivityItem
import com.amresalehin.emregallery.data.DownloadActivityManager

/**
 * Row displaying an active downloading item with progress bar, byte/speed telemetry, and pause/resume button.
 */
@Composable
internal fun ActiveDownloadRow(
    item: DownloadActivityItem,
    onPauseResume: () -> Unit,
    modifier: Modifier = Modifier,
    currentTimeMillis: Long = System.currentTimeMillis()
) {
    ActivityRowContainer(
        thumbnailModel = item.thumbnailUrl ?: item.mediaUrl,
        item = item,
        modifier = modifier,
        showVideoPlayOverlay = item.isVideo,
        subtitleContent = {
            if (item.isRetrying) {
                val retryLabel = formatRetryCountdown(item.nextRetryTimestampMillis, currentTimeMillis)
                Text(
                    text = retryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ActivityRetryAmber,
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
                progress = { if (item.isRetrying) 0.05f else item.progress.coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (item.isRetrying) ActivityRetryAmber else MaterialTheme.colorScheme.onSurface,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        },
        trailingContent = {
            Surface(
                onClick = onPauseResume,
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(36.dp)
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
    )
}
