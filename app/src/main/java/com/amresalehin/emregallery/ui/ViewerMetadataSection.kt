package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem
import kotlin.math.abs

@Composable
internal fun OverviewMetadataSection(
    metadataRows: List<Pair<String, String>>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Metadata",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle metadata",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }

        if (expanded) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    metadataRows.forEachIndexed { index, (label, value) ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun buildMetadataRows(item: MediaItem, metadata: Map<String, String>): List<Pair<String, String>> {
    val videoWidth = metadata["video_width"]?.toIntOrNull() ?: 0
    val videoHeight = metadata["video_height"]?.toIntOrNull() ?: 0
    val width = item.width.takeIf { it > 0 } ?: videoWidth
    val height = item.height.takeIf { it > 0 } ?: videoHeight
    val dimensions = if (width > 0 && height > 0) "$width × $height px" else null

    val videoDurationMillis = item.durationMillis.takeIf { it > 0 }
        ?: metadata["video_duration_ms"]?.toLongOrNull()?.takeIf { it > 0 }
    val videoDuration = if (item.isVideo) {
        videoDurationMillis?.let { formatDuration(it) }
    } else null

    val bitrate = metadata["video_bitrate_bps"]?.toLongOrNull()?.takeIf { it > 0L }?.let {
        when {
            it >= 1_000_000L -> "%.2f Mbps".format(it / 1_000_000.0)
            it >= 1_000L -> "%.0f kbps".format(it / 1_000.0)
            else -> "$it bps"
        }
    }
    val captureFrameRate = metadata["video_capture_framerate"]?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }?.let { "%.2f fps".format(it) }
    val rotation = metadata["video_rotation_degrees"]?.toIntOrNull()?.let { "$it°" }
    val audioTrack = metadata["video_has_audio"]?.let {
        when {
            it.equals("yes", ignoreCase = true) -> "Present"
            it.equals("no", ignoreCase = true) -> "Not present"
            else -> it
        }
    }

    val formatString = run {
        val raw = item.mimeType?.takeIf { it.isNotBlank() } ?: metadata["mime_type"].orEmpty()
        when {
            raw.contains("jpeg", ignoreCase = true) || raw.contains("jpg", ignoreCase = true) -> "JPEG"
            raw.contains("png", ignoreCase = true) -> "PNG"
            raw.contains("webp", ignoreCase = true) -> "WEBP"
            raw.contains("gif", ignoreCase = true) -> "GIF"
            raw.contains("heic", ignoreCase = true) || raw.contains("heif", ignoreCase = true) -> "HEIF"
            raw.contains("mp4", ignoreCase = true) -> "MP4"
            raw.contains("webm", ignoreCase = true) -> "WEBM"
            raw.contains("3gp", ignoreCase = true) -> "3GP"
            raw.isNotBlank() -> raw.substringAfter('/').uppercase()
            else -> null
        }
    }

    val gpsCoordinates = if (metadata.containsKey("latitude") && metadata.containsKey("longitude")) {
        val lat = metadata["latitude"]?.toDoubleOrNull()
        val lon = metadata["longitude"]?.toDoubleOrNull()
        if (lat != null && lon != null) {
            "%.4f° %s, %.4f° %s".format(
                abs(lat), if (lat >= 0) "N" else "S",
                abs(lon), if (lon >= 0) "E" else "W"
            )
        } else null
    } else null

    val rawRows = listOf(
        "Media type" to if (item.isVideo) "Video" else "Image",
        "File format" to formatString,
        "Dimensions" to dimensions,
        "Camera manufacturer" to metadata["make"],
        "Camera model" to metadata["model"],
        "Aperture" to metadata["aperture"],
        "Shutter speed" to metadata["shutter"],
        "ISO" to metadata["iso"],
        "Focal length" to metadata["focal"],
        "Flash" to metadata["flash"],
        "Color space" to metadata["color"],
        "GPS coordinates" to gpsCoordinates,
        "Duration" to videoDuration,
        "Video bitrate" to (if (item.isVideo) bitrate else null),
        "Capture frame rate" to (if (item.isVideo) captureFrameRate else null),
        "Rotation" to (if (item.isVideo) rotation else null),
        "Audio track" to (if (item.isVideo) audioTrack else null),
        "Track count" to (if (item.isVideo) metadata["video_track_count"] else null),
        "Creator" to metadata["creator"],
        "Description" to metadata["description"],
        "Copyright" to metadata["copyright"],
        "Subject" to metadata["subject"],
        "Keywords" to metadata["keywords"],
        "Rating" to metadata["rating"]
    )

    return rawRows.mapNotNull { (label, value) ->
        value?.trim()?.takeIf { it.isNotEmpty() && it != "Not available" }?.let { label to it }
    }
}
