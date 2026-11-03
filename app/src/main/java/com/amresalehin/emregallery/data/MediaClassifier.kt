package com.amresalehin.emregallery.data

import android.net.Uri
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * High-level category for discovered media.
 */
enum class MediaCategory(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    STREAMS("Streams")
}

/**
 * Extraction source indicator for transparency.
 */
enum class ExtractionSource(val label: String) {
    GALLERY_DL("gallery-dl"),
    STATIC_HTML("HTML"),
    DOM_SCRAPE("DOM"),
    CSS_BACKGROUND("CSS"),
    SRCSET("Srcset"),
    NETWORK_INTERCEPT("Network"),
    IFRAME("iFrame")
}

/**
 * Normalizes, classifies, and deduplicates media URLs discovered via static HTML scraping
 * or live browser network interception.
 */
object MediaClassifier {

    // Common tracking and analytics domains or path segments to exclude
    private val TRACKING_PATTERNS = listOf(
        "google-analytics", "analytics", "doubleclick", "pixel", "1x1", "tracking",
        "beacon", "telemetry", "hotjar", "clarity.ms", "stats.wp.com", "facebook.com/tr"
    )

    // Query parameters that should be stripped to produce canonical deduplication keys
    private val TRACKING_QUERY_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "msclkid", "ref", "source", "_ga", "_gl", "_t", "cachebust"
    )

    /**
     * Resolves a potentially relative URL against the base URL and standardizes protocol.
     */
    fun resolveUrl(rawUrl: String, baseUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || trimmed.startsWith("javascript:") || trimmed.startsWith("data:") || trimmed.startsWith("mailto:")) {
            return null
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
    fun isValidWebUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.contains(" ") || trimmed.length < 4) {
            return false
        }
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("javascript:") || lower.startsWith("data:") ||
            lower.startsWith("mailto:") || lower.startsWith("file:") || lower.startsWith("content:")) {
            return false
        }
        val candidate = if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return try {
            val uri = URI(candidate)
            val host = uri.host ?: return false
            if (host.equals("localhost", ignoreCase = true)) return true
            if (!host.contains(".") || host.startsWith(".") || host.endsWith(".")) return false
            val parts = host.split(".")
            if (parts.any { it.isBlank() }) return false
            val tld = parts.last()
            val isAlphabeticTld = tld.length >= 2 && tld.all { it.isLetter() }
            val isIp = parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
            isAlphabeticTld || isIp
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether the URL is valid media and not tracking/pixel.
     */
    fun isValidMedia(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        for (pattern in TRACKING_PATTERNS) {
            if (lower.contains(pattern)) return false
        }
        if (lower.contains("favicon.ico") || lower.contains("/avatar_default") || lower.contains("/blank.gif")) {
            return false
        }
        return true
    }

    /**
     * Creates a canonical deduplication key by removing non-functional tracking query parameters.
     */
    fun canonicalKey(url: String): String {
        return try {
            val noHash = url.substringBefore("#")
            val qIdx = noHash.indexOf('?')
            if (qIdx == -1) return noHash
            val base = noHash.substring(0, qIdx)
            val query = noHash.substring(qIdx + 1)
            val cleanParams = query.split("&").filter { param ->
                val key = param.substringBefore("=").lowercase(Locale.ROOT)
                !TRACKING_QUERY_PARAMS.contains(key)
            }.sorted()
            if (cleanParams.isEmpty()) base else "$base?${cleanParams.joinToString("&")}"
        } catch (_: Exception) {
            url.substringBefore("#")
        }
    }

    /**
     * Classifies a media URL into category (IMAGE, VIDEO, AUDIO, STREAM) and specific format string.
     */
    fun classify(url: String, mimeHint: String? = null): ClassificationResult {
        val lower = url.lowercase(Locale.ROOT)
        val mimeLower = mimeHint?.lowercase(Locale.ROOT) ?: ""

        // 1. Streaming media (HLS & DASH)
        if (lower.contains(".m3u8") || lower.contains("/hls/") || mimeLower.contains("x-mpegurl") || mimeLower.contains("vnd.apple.mpegurl")) {
            return ClassificationResult(
                category = MediaCategory.STREAMS,
                format = "HLS (m3u8)",
                mimeType = "application/x-mpegURL",
                isVideo = true,
                isAudio = false,
                isStream = true
            )
        }
        if (lower.contains(".mpd") || lower.contains("/dash/") || mimeLower.contains("dash+xml")) {
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
    fun deriveFileName(url: String, format: String, fallbackIndex: Int): String {
        return runCatching {
            val cleanUrl = url.substringBefore("?").substringBefore("#")
            val lastSlash = cleanUrl.lastIndexOf('/')
            val segment = if (lastSlash != -1 && lastSlash < cleanUrl.length - 1) cleanUrl.substring(lastSlash + 1) else ""
            if (segment.length > 2 && segment.contains(".")) {
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
    fun parseSrcset(srcset: String, baseUrl: String): List<Pair<String, String>> {
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

    fun formatQueryOrUrl(input: String, searchEngine: BrowserSearchEngine = BrowserSearchEngine.DUCKDUCKGO): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length > 3) {
            "https://$trimmed"
        } else {
            "${searchEngine.searchUrl}${URLEncoder.encode(trimmed, "UTF-8")}"
        }
    }
}

enum class BrowserSearchEngine(val label: String, val shortLabel: String, val searchUrl: String) {
    DUCKDUCKGO("DuckDuckGo", "DDG", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "Google", "https://www.google.com/search?q="),
    BING("Bing", "Bing", "https://www.bing.com/search?q="),
    WIKIPEDIA("Wikipedia", "Wiki", "https://en.wikipedia.org/wiki/Special:Search?search=")
}

data class ClassificationResult(
    val category: MediaCategory,
    val format: String,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isStream: Boolean
)
