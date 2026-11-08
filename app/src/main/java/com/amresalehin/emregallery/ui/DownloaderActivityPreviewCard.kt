package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.DownloadActivityItem


fun getDisplayFormatBadge(item: DownloadActivityItem): String {
    val raw = item.format.trim().uppercase()
    // Strictly prevent false 4K on non-video items
    if (!item.isVideo && (raw == "4K" || raw.contains("4K"))) {
        // Fall through to determine genuine format from URL/title
    } else if (raw.isNotEmpty() && !raw.contains("4K") && raw != "UNKNOWN") {
        return if (raw == "JPEG") "JPG" else raw.take(4)
    } else if (item.isVideo && (raw.contains("4K") || item.title.contains("4K", ignoreCase = true))) {
        return "4K"
    }

    val lowerUrl = item.mediaUrl.lowercase()
    val lowerTitle = item.title.lowercase()
    return when {
        lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") || lowerTitle.endsWith(".jpg") || lowerTitle.endsWith(".jpeg") -> "JPG"
        lowerUrl.contains(".png") || lowerTitle.endsWith(".png") -> "PNG"
        lowerUrl.contains(".webp") || lowerTitle.endsWith(".webp") -> "WEBP"
        lowerUrl.contains(".gif") || lowerTitle.endsWith(".gif") -> "GIF"
        lowerUrl.contains(".svg") || lowerTitle.endsWith(".svg") -> "SVG"
        lowerUrl.contains(".mp4") || lowerTitle.endsWith(".mp4") -> "MP4"
        lowerUrl.contains(".webm") || lowerTitle.endsWith(".webm") -> "WEBM"
        lowerUrl.contains(".m3u8") -> "HLS"
        lowerUrl.contains(".mp3") || lowerTitle.endsWith(".mp3") -> "MP3"
        item.isVideo -> "MP4"
        item.isAudio -> "MP3"
        else -> "JPG"
    }
}

/**
 * Custom heartbeat/pulse icon matching image_4fd87912.png
 */
@Composable
fun ActivityPulseIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.5f)
            lineTo(w * 0.22f, h * 0.5f)
            lineTo(w * 0.35f, h * 0.15f)
            lineTo(w * 0.55f, h * 0.88f)
            lineTo(w * 0.72f, h * 0.35f)
            lineTo(w * 0.82f, h * 0.5f)
            lineTo(w, h * 0.5f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Prominent Activity Card container on the download inbox main page,
 * exactly matching the layout of image_4fd87912.png:
 * - Rounded card container with heartbeat icon, "Activity" bold title, and status pill ("1 failed") + chevron
 * - Horizontal thumbnails: downloading items (with progress bar), failed items (with ! badge), divider, finished items (with ✓ badge)
 * - Tapping opens the Activity Page
 */
@Composable
fun ActivityPreviewCard(
    activities: List<DownloadActivityItem>,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val containerShape = RoundedCornerShape(26.dp)

    val downloading = remember(activities) { activities.filter { it.isDownloading || it.isPaused || it.isRetrying } }
    val failed = remember(activities) { activities.filter { it.isFailed } }
    val finished = remember(activities) { activities.filter { it.isFinished } }

    val failedCount = activities.count { it.isFailed }
    val retryingCount = activities.count { it.isRetrying }
    val downloadingCount = activities.count { it.isDownloading || it.isPaused || it.isRetrying }

    Surface(
        onClick = onOpenActivity,
        shape = containerShape,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.65f else 0.75f)
        ),
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = containerShape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.12f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.18f)
            )
            .testTag("downloader_activity_preview_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: [Pulse Icon] Activity         [X failed pill] [>]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActivityPulseIcon(
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (failedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF3B1212) else Color(0xFFFDE8E8),
                            modifier = Modifier.testTag("activity_failed_badge")
                        ) {
                            Text(
                                text = "$failedCount failed",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFF87171) else Color(0xFF9B1C1C),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else if (downloadingCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFE1EFFE),
                            modifier = Modifier.testTag("activity_downloading_badge")
                        ) {
                            Text(
                                text = "$downloadingCount downloading",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFF60A5FA) else Color(0xFF1E429F),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Activity",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Thumbnail Row matching image_4fd87912.png
            if (activities.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val activeOrFailedList = (downloading.take(2) + failed.take(2)).take(2)
                    val finishedList = finished.take(3)

                    // Active or Failed Thumbnails
                    for (item in activeOrFailedList) {
                        ActivityThumbnailBox(item = item)
                    }

                    // Vertical Divider if both active/failed and finished items exist
                    if (activeOrFailedList.isNotEmpty() && finishedList.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(54.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }

                    // Finished Thumbnails (with green checkmark badge)
                    for (item in finishedList) {
                        ActivityThumbnailBox(item = item)
                    }
                }
            } else {
                // Empty state when no downloads have occurred yet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No active downloads • Activity appears here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thumbnail item for the Activity Preview Card
 */
@Composable
