package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

internal fun MediaClassifier.isSvgImpl(url: String, mimeHint: String? = null, format: String? = null): Boolean {
    if (format?.equals("SVG", ignoreCase = true) == true) return true
    if (mimeHint?.contains("svg", ignoreCase = true) == true) return true
    val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return clean.endsWith(".svg")
}

/**
 * Classifies a media URL into category (IMAGE, VIDEO, AUDIO, STREAM) and specific format string.
 */
internal fun MediaClassifier.classifyImpl(url: String, mimeHint: String? = null): ClassificationResult {
    val lower = url.lowercase(Locale.ROOT)
    val mimeLower = mimeHint?.lowercase(Locale.ROOT) ?: ""

    // 1. Streaming media (HLS & DASH)
    val cleanPath = lower.substringBefore("?").substringBefore("#")
    val isHlsManifest = cleanPath.endsWith(".m3u8") || lower.contains(".m3u8") ||
            mimeLower.contains("x-mpegurl") || mimeLower.contains("vnd.apple.mpegurl") || mimeLower.contains("application/mpegurl")
    if (isHlsManifest) {
        return ClassificationResult(
            category = MediaCategory.STREAMS,
            format = "HLS (m3u8)",
            mimeType = "application/x-mpegURL",
            isVideo = true,
            isAudio = false,
            isStream = true
        )
    }
    val isDashManifest = cleanPath.endsWith(".mpd") || lower.contains(".mpd") ||
            mimeLower.contains("dash+xml") || mimeLower.contains("video/vnd.mpeg.dash.mpd")
    if (isDashManifest) {
        return ClassificationResult(
            category = MediaCategory.STREAMS,
            format = "DASH (mpd)",
            mimeType = "application/dash+xml",
            isVideo = true,
            isAudio = false,
            isStream = true
        )
    }

    // 2. Video formats
    if (lower.contains("/videoplayback") || lower.contains("googlevideo.com")) {
        val isAudioStream = lower.contains("mime=audio") || mimeLower.contains("audio/")
        return if (isAudioStream) {
            ClassificationResult(
                category = MediaCategory.AUDIO,
                format = "M4A",
                mimeType = "audio/mp4",
                isVideo = false,
                isAudio = true,
                isStream = false
            )
        } else {
            ClassificationResult(
                category = MediaCategory.VIDEOS,
                format = "MP4",
                mimeType = "video/mp4",
                isVideo = true,
                isAudio = false,
                isStream = false
            )
        }
    }

    if (lower.contains(".mp4") || mimeLower.contains("video/mp4") || lower.contains("video/mp4")) {
        return ClassificationResult(
            category = MediaCategory.VIDEOS,
            format = "MP4",
            mimeType = "video/mp4",
            isVideo = true,
            isAudio = false,
            isStream = false
        )
    }
    if (lower.contains(".webm") || mimeLower.contains("video/webm")) {
        return ClassificationResult(
            category = MediaCategory.VIDEOS,
            format = "WEBM",
            mimeType = "video/webm",
            isVideo = true,
            isAudio = false,
            isStream = false
        )
    }
    if (lower.contains(".mov") || mimeLower.contains("video/quicktime")) {
        return ClassificationResult(
            category = MediaCategory.VIDEOS,
            format = "MOV",
            mimeType = "video/quicktime",
            isVideo = true,
            isAudio = false,
            isStream = false
        )
    }
    if (lower.contains(".mkv") || mimeLower.contains("video/x-matroska")) {
        return ClassificationResult(
            category = MediaCategory.VIDEOS,
            format = "MKV",
            mimeType = "video/x-matroska",
            isVideo = true,
            isAudio = false,
            isStream = false
        )
    }
    if (mimeLower.startsWith("video/")) {
        val sub = mimeLower.substringAfter("video/").uppercase(Locale.ROOT)
        return ClassificationResult(
            category = MediaCategory.VIDEOS,
            format = sub,
            mimeType = mimeLower,
            isVideo = true,
            isAudio = false,
            isStream = false
        )
    }

    // 3. Audio formats
    if (lower.contains(".mp3") || mimeLower.contains("audio/mpeg") || mimeLower.contains("audio/mp3")) {
        return ClassificationResult(
            category = MediaCategory.AUDIO,
            format = "MP3",
            mimeType = "audio/mpeg",
            isVideo = false,
            isAudio = true,
            isStream = false
        )
    }
    if (lower.contains(".m4a") || lower.contains(".aac") || mimeLower.contains("audio/aac") || mimeLower.contains("audio/mp4")) {
        return ClassificationResult(
            category = MediaCategory.AUDIO,
            format = if (lower.contains(".aac")) "AAC" else "M4A",
            mimeType = "audio/mp4",
            isVideo = false,
            isAudio = true,
            isStream = false
        )
    }
    if (lower.contains(".wav") || mimeLower.contains("audio/wav") || mimeLower.contains("audio/x-wav")) {
        return ClassificationResult(
            category = MediaCategory.AUDIO,
            format = "WAV",
            mimeType = "audio/wav",
            isVideo = false,
            isAudio = true,
            isStream = false
        )
    }
    if (lower.contains(".ogg") || lower.contains(".opus") || mimeLower.contains("audio/ogg") || mimeLower.contains("audio/opus")) {
        return ClassificationResult(
            category = MediaCategory.AUDIO,
            format = if (lower.contains(".opus")) "OPUS" else "OGG",
            mimeType = "audio/ogg",
            isVideo = false,
            isAudio = true,
            isStream = false
        )
    }
    if (lower.contains(".flac") || mimeLower.contains("audio/flac")) {
        return ClassificationResult(
            category = MediaCategory.AUDIO,
            format = "FLAC",
            mimeType = "audio/flac",
            isVideo = false,
            isAudio = true,
            isStream = false
        )
    }
    if (mimeLower.startsWith("audio/")) {
        val sub = mimeLower.substringAfter("audio/").uppercase(Locale.ROOT)
        return ClassificationResult(
            category = MediaCategory.AUDIO,
            format = sub,
            mimeType = mimeLower,
            isVideo = false,
            isAudio = true,
            isStream = false
        )
    }

    // 4. Image formats
    val format = when {
        lower.contains(".webp") || mimeLower.contains("webp") -> "WEBP"
        lower.contains(".png") || mimeLower.contains("png") -> "PNG"
        lower.contains(".gif") || mimeLower.contains("gif") -> "GIF"
        lower.contains(".svg") || mimeLower.contains("svg") -> "SVG"
        lower.contains(".avif") || mimeLower.contains("avif") -> "AVIF"
        lower.contains(".bmp") || mimeLower.contains("bmp") -> "BMP"
        else -> "JPEG"
    }
    val imgMime = when (format) {
        "WEBP" -> "image/webp"
        "PNG" -> "image/png"
        "GIF" -> "image/gif"
        "SVG" -> "image/svg+xml"
        "AVIF" -> "image/avif"
        "BMP" -> "image/bmp"
        else -> "image/jpeg"
    }

    return ClassificationResult(
        category = MediaCategory.IMAGES,
        format = format,
        mimeType = imgMime,
        isVideo = false,
        isAudio = false,
        isStream = false
    )
}

/**
 * Derives a clean filename from URL and format.
 */
internal fun MediaClassifier.deriveFileNameImpl(url: String, format: String, fallbackIndex: Int): String {
    return runCatching {
        val cleanUrl = url.substringBefore("?").substringBefore("#")
        val lastSlash = cleanUrl.lastIndexOf('/')
        val segment = if (lastSlash != -1 && lastSlash < cleanUrl.length - 1) cleanUrl.substring(lastSlash + 1) else ""
        val extInSegment = segment.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (segment.length > 2 && extInSegment in KNOWN_MEDIA_EXTENSIONS) {
            segment
        } else {
            val ext = when (format.uppercase(Locale.ROOT)) {
                "HLS (M3U8)", "HLS" -> "m3u8"
                "DASH (MPD)", "DASH" -> "mpd"
                "MP4" -> "mp4"
                "WEBM" -> "webm"
                "MOV" -> "mov"
                "MP3" -> "mp3"
                "M4A" -> "m4a"
                "AAC" -> "aac"
                "WAV" -> "wav"
                "FLAC" -> "flac"
                "WEBP" -> "webp"
                "PNG" -> "png"
                "GIF" -> "gif"
                "SVG" -> "svg"
                "AVIF" -> "avif"
                else -> "jpg"
            }
            "media_${fallbackIndex + 1}.$ext"
        }
    }.getOrDefault("media_${fallbackIndex + 1}.bin")
}

/**
 * Parses srcset attribute (e.g., "img1.jpg 300w, img2.jpg 600w, img3.jpg 1200w").
 * Returns a list of Pair(url, resolutionDescriptor).
 */
internal fun MediaClassifier.parseSrcsetImpl(srcset: String, baseUrl: String): List<Pair<String, String>> {
    val results = mutableListOf<Pair<String, String>>()
    val entries = srcset.split(",")
    for (entry in entries) {
        val parts = entry.trim().split(Regex("\\s+"))
        if (parts.isNotEmpty() && parts[0].isNotBlank()) {
            val resolved = resolveUrl(parts[0], baseUrl)
            if (resolved != null && isValidMedia(resolved)) {
                val descriptor = if (parts.size > 1) parts[1] else ""
                results.add(Pair(resolved, descriptor))
            }
        }
    }
    return results
}

