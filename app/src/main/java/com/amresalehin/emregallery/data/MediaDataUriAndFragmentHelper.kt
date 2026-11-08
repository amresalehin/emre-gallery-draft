package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

internal fun MediaClassifier.isSubstantialDataUriImpl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("data:image/", ignoreCase = true)) return false
    // Exclude SVG/XML data URIs which are placeholders/spacers
    if (trimmed.contains("svg+xml", ignoreCase = true) || trimmed.contains("<svg", ignoreCase = true) || trimmed.contains("PHN2Zy", ignoreCase = true)) return false
    // Exclude tiny spacers, 1x1 pixel tracking GIFs (< 2048 characters base64)
    if (trimmed.length < 2048) return false
    // Known 1x1 transparent GIF signature
    if (trimmed.contains("R0lGODlhAQABA", ignoreCase = true)) return false
    return trimmed.contains(";base64,", ignoreCase = true)
}

internal fun MediaClassifier.parseDataUriInfoImpl(dataUri: String): DataUriInfo? {
    if (!isSubstantialDataUri(dataUri)) return null
    val header = dataUri.substringBefore(";base64,").removePrefix("data:").trim().lowercase(Locale.ROOT)
    val mimeType = if (header.isBlank()) "image/png" else header
    val format = when {
        mimeType.contains("png") -> "PNG"
        mimeType.contains("webp") -> "WEBP"
        mimeType.contains("gif") -> "GIF"
        mimeType.contains("svg") -> "SVG"
        else -> "JPEG"
    }
    val ext = format.lowercase(Locale.ROOT)
    val base64Payload = dataUri.substringAfter(";base64,", "")
    val estimatedBytes = (base64Payload.length * 3L) / 4L
    val cleanTitle = "embedded_artwork_${abs(dataUri.hashCode()) % 100000}.$ext"
    return DataUriInfo(
        mimeType = mimeType,
        format = format,
        estimatedSizeBytes = estimatedBytes,
        cleanTitle = cleanTitle
    )
}

/**
 * Estimates the size in bytes of a data URI without loading full bitmap into memory.
 */
internal fun MediaClassifier.estimateDataUriSizeBytesImpl(url: String): Long? {
    if (!url.startsWith("data:", ignoreCase = true)) return null
    val base64Payload = url.substringAfter(";base64,", "")
    if (base64Payload.isNotEmpty()) {
        return (base64Payload.length * 3L) / 4L
    }
    val commaIdx = url.indexOf(',')
    if (commaIdx != -1) {
        return (url.length - commaIdx - 1).toLong()
    }
    return null
}

/**
 * Cleans streaming slice and sequence query parameters from a media URL.
 * Prevents downloading only a 1MB partial fragment or single byte-range slice of a video.
 */
internal fun MediaClassifier.cleanMediaUrlImpl(url: String): String {
    return try {
        val noHash = url.substringBefore("#")
        val qIdx = noHash.indexOf('?')
        if (qIdx == -1) return noHash
        val base = noHash.substring(0, qIdx)
        val query = noHash.substring(qIdx + 1)
        val remainingParams = query.split("&").filter { param ->
            val key = param.substringBefore("=").lowercase(Locale.ROOT)
            !STREAMING_SLICE_PARAMS.contains(key)
        }
        if (remainingParams.isEmpty()) base else "$base?${remainingParams.joinToString("&")}"
    } catch (_: Exception) {
        url
    }
}

/**
 * Detects if a URL is an unmerged streaming chunk, segment, or initialization fragment.
 * Standalone downloading of individual video chunks causes broken, partial, and multiplied downloads.
 */
internal fun MediaClassifier.isMediaFragmentImpl(url: String): Boolean {
    val lower = url.lowercase(Locale.ROOT)
    if (lower.startsWith("blob:")) return true
    if (lower.startsWith("data:")) {
        return !isSubstantialDataUri(url)
    }
    // YouTube video playback endpoints are the master playable stream, not fragment discards
    if (lower.contains("googlevideo.com/videoplayback") || lower.contains("/videoplayback")) {
        return false
    }

    val clean = lower.substringBefore("?").substringBefore("#")

    // 1. Fragment-only streaming container extensions
    if (clean.endsWith(".m4s") || clean.endsWith(".m4f") || clean.endsWith(".f4f") || clean.endsWith(".f4m") ||
        clean.endsWith(".dash") || clean.endsWith(".cmfv") || clean.endsWith(".cmfa")
    ) {
        return true
    }

    // 2. Transport stream chunks (.ts)
    // Standalone videos on websites are MP4/WebM/MKV/MOV/M3U8. A .ts with numeric/segment numbering is an HLS chunk
    if (clean.endsWith(".ts")) {
        val fileName = clean.substringAfterLast('/')
        val isSegmentTs = fileName.matches(Regex("""(?i)(?:seg|segment|chunk|frag|fragment|track|stream|hls)?[_\-.]?\d+\.ts""")) ||
                fileName.matches(Regex("""(?i)\d+\.ts""")) ||
                fileName.contains("slice") || fileName.contains("stream") ||
                clean.contains("/hls/") || clean.contains("/dash/") || clean.contains("/segments/") || clean.contains("/chunks/") ||
                clean.contains("/live/")
        if (isSegmentTs) return true
    }

    // 3. Initialization segments (contain metadata only, no playable frames)
    if (clean.contains("/init.") || clean.contains("/init-") || clean.contains("_init.") ||
        clean.endsWith("init.mp4") || clean.endsWith("init.m4s") || clean.contains("init-stream") ||
        clean.contains("/header.mp4") || clean.contains("/init_") || clean.contains("init_video") ||
        clean.contains("init_audio")
    ) {
        return true
    }

    // 4. Fragment / segment naming in URL path
    val segmentPattern = Regex("""[/_.-](?:seg|segment|frag|fragment|chunk|part)[-_.]?\d+""", RegexOption.IGNORE_CASE)
    if (segmentPattern.containsMatchIn(clean)) {
        return true
    }
    val numberedFragment = Regex("""\d+-(?:frag|seg|segment|chunk)\.(?:mp4|ts|m4s|m4v)""", RegexOption.IGNORE_CASE)
    if (numberedFragment.containsMatchIn(clean)) {
        return true
    }
    val chunkStreamPattern = Regex("""chunk-stream\d+-\d+\.m4s""", RegexOption.IGNORE_CASE)
    if (chunkStreamPattern.containsMatchIn(clean)) {
        return true
    }

    // 5. Query parameters indicating chunk indexing
    val query = lower.substringAfter("?", "")
    if (query.isNotBlank()) {
        val params = query.split("&")
        for (p in params) {
            val key = p.substringBefore("=").lowercase(Locale.ROOT)
            val value = p.substringAfter("=", "")
            if ((key == "sq" || key == "seg" || key == "segment" || key == "frag" || key == "chunk" || key == "part") &&
                value.toIntOrNull() != null
            ) {
                return true
            }
            if (key == "bytestart" || key == "byteend") {
                return true
            }
        }
    }

    return false
}

/**
 * Resolves a potentially relative URL against the base URL and standardizes protocol.
 */
internal fun MediaClassifier.resolveUrlImpl(rawUrl: String, baseUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank() || trimmed.startsWith("javascript:") || trimmed.startsWith("mailto:")) {
        return null
    }
    if (trimmed.startsWith("data:", ignoreCase = true)) {
        return if (isSubstantialDataUri(trimmed)) trimmed else null
    }

    return try {
        val base = URI(if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) "https://$baseUrl" else baseUrl)
        val resolved = if (trimmed.startsWith("//")) {
            (base.scheme ?: "https") + ":" + trimmed
        } else {
            base.resolve(trimmed).toString()
        }
        if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
            resolved
        } else {
            null
        }
    } catch (_: Exception) {
        // Fallback with java.net.URL
        try {
            val base = URL(if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) "https://$baseUrl" else baseUrl)
            val resolved = URL(base, trimmed).toString()
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved else null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Strictly validates whether an input string is a valid web URL (e.g. https://example.com, example.com/page).
 * Rejects arbitrary text, invalid hosts, spaces, local file/javascript URIs, or non-domain inputs.
 */
