package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.amresalehin.emregallery.data.MediaItem
import kotlin.math.abs



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
            raw.isNotBlank() -> raw.substringAfter('/') .uppercase()
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

/**
 * FFmpeg Multimedia Tools section for videos in Viewer details actions tab.
 */
