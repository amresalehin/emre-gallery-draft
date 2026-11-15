package com.amresalehin.emregallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.DownloadActivityItem

/**
 * Returns a display-friendly format badge text (e.g. JPG, PNG, MP4, WEBP).
 * Strictly prevents false "4K" labels from appearing on thumbnails.
 */
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
 * Custom heartbeat/pulse icon used in the Activity preview card.
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
 * Shared compact format badge overlay rendered on activity thumbnails.
 */
@Composable
internal fun ActivityFormatBadge(
    text: String,
    backgroundAlpha: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = backgroundAlpha),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * Modal bottom sheet presenting bulk controls for download activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityControlsBottomSheet(
    failedCount: Int,
    hasDownloading: Boolean,
    hasFinished: Boolean,
    onDismiss: () -> Unit,
    onRetryAllFailed: () -> Unit,
    onCancelAllActive: () -> Unit,
    onClearFinished: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Activity Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (failedCount > 0) {
                Button(
                    onClick = onRetryAllFailed,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry All Failed ($failedCount)")
                }
            }

            if (hasDownloading) {
                OutlinedButton(
                    onClick = onCancelAllActive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel All Active Downloads")
                }
            }

            if (hasFinished) {
                OutlinedButton(
                    onClick = onClearFinished,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Past Download History")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
