package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

enum class BrowserSearchEngine(val label: String, val shortLabel: String, val searchUrl: String) {
    DUCKDUCKGO("DuckDuckGo", "DDG", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "Google", "https://www.google.com/search?q="),
    BING("Bing", "Bing", "https://www.bing.com/search?q="),
    WIKIPEDIA("Wikipedia", "Wiki", "https://en.wikipedia.org/wiki/Special:Search?search=")
}

/**
 * Pure URL normalization, validation, sanitization, redirect unwrapping,
 * and search-query formatting utilities.
 */
object WebUrlUtils {

    private val STREAMING_SLICE_PARAMS = setOf(
        "range", "bytestart", "byteend", "bytes", "rn", "sq", "live", "cpn", "sver", "chunk", "seg", "part"
    )

    private val HTTP_URL_REGEX = "(https?://[^\\s<>\"'\\[\\]{}|\\\\^`]+)".toRegex(RegexOption.IGNORE_CASE)

    private val YOUTUBE_VIDEO_ID_REGEX =
        Regex("""(?:youtu\.be/|youtube\.com/(?:embed/|v/|shorts/|watch\?v=|watch\?.+&v=))([a-zA-Z0-9_-]{11})""")
    private val YOUTUBE_THUMB_ID_REGEX =
        Regex("""(?:ytimg\.com|youtube\.com)/(?:vi|vi_webp)/([a-zA-Z0-9_-]{11})""")

    private val TRACKING_PARAMS_TO_REMOVE = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_reader", "utm_name",
        "fbclid", "gclid", "gbraid", "wbraid", "msclkid", "twclid", "dclid",
        "igshid", "si", "feature", "ref", "ref_src", "ref_url", "source", "share_id",
        "_ga", "_gl", "_hsenc", "_hsmi", "mc_cid", "mc_eid",
        "is_from_webapp", "sender_device", "sender_web_id",
        "ved", "usg", "context"
    )

    /**
     * Cleans streaming slice and sequence query parameters from a media URL.
     * Prevents downloading only a 1MB partial fragment or single byte-range slice of a video.
     */
    fun cleanMediaUrl(url: String): String {
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
     * Resolves a potentially relative URL against the base URL and standardizes protocol.
     */
    fun resolveUrl(rawUrl: String, baseUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || trimmed.startsWith("javascript:") || trimmed.startsWith("mailto:")) {
            return null
        }
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            return if (DataUriParser.isSubstantialDataUri(trimmed)) trimmed else null
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
            lower.startsWith("mailto:") || lower.startsWith("file:") || lower.startsWith("content:")
        ) {
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
     * Extracts a clean display domain from a web URL.
     */
    fun cleanDomainFromUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val normalized = if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                "https://$url"
            } else {
                url
            }
            val uri = URI(normalized)
            uri.host?.removePrefix("www.") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Thoroughly sanitizes a web URL before fetching:
     * 1. Strips wrapping quotes, whitespace, and punctuation.
     * 2. Unwraps known redirect wrappers (Google / Facebook outbound links).
     * 3. Strips tracking, analytics, and social share parameters (utm_*, igshid, si, fbclid, etc.).
     * 4. Ensures a valid https:// scheme.
     */
    fun sanitizeUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var cleaned = raw.trim().trim('"', '\'', '<', '>', '(', ')', '[', ']', ';')
        if (cleaned.isBlank()) return ""

        if (!cleaned.startsWith("http://", ignoreCase = true) && !cleaned.startsWith("https://", ignoreCase = true)) {
            cleaned = "https://$cleaned"
        }

        return try {
            val uri = URI(cleaned)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return cleaned

            // 1. Unwrap Google / Facebook redirect links if present
            if ((host.contains("google.") && uri.path == "/url") ||
                (host.contains("facebook.com") && uri.path?.endsWith("/l.php") == true)
            ) {
                val queryParams = uri.rawQuery?.split("&")?.associate {
                    val key = it.substringBefore("=").lowercase(Locale.ROOT)
                    val value = it.substringAfter("=", "")
                    key to URLDecoder.decode(value, "UTF-8")
                } ?: emptyMap()

                val target = queryParams["q"] ?: queryParams["url"] ?: queryParams["u"]
                if (!target.isNullOrBlank() && isValidWebUrl(target)) {
                    return sanitizeUrl(target)
                }
            }

            // 2. Clean query parameters (strip tracking parameters, retain functional ones like v=, id=, p=, etc.)
            val rawQuery = uri.rawQuery
            val filteredQuery = if (!rawQuery.isNullOrBlank()) {
                val remainingParams = rawQuery.split("&").filter { param ->
                    val key = param.substringBefore("=").lowercase(Locale.ROOT)
                    !TRACKING_PARAMS_TO_REMOVE.contains(key) && !key.startsWith("utm_")
                }
                if (remainingParams.isNotEmpty()) remainingParams.joinToString("&") else null
            } else null

            // 3. Reconstruct clean URI without tracking parameters or tracking fragments
            val cleanUri = URI(
                uri.scheme ?: "https",
                uri.authority,
                uri.path,
                filteredQuery,
                null
            )
            cleanUri.toString()
        } catch (_: Exception) {
            cleaned
        }
    }

    /**
     * Extracts the first valid web URL from arbitrary shared text, captions, or messages,
     * and sanitizes it by removing tracking parameters.
     */
    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = HTTP_URL_REGEX.find(text)
        if (match != null) {
            val candidate = match.value.trimEnd('.', ',', ')', ']', ';', '>', '"', '\'', ':', '!')
            val sanitized = sanitizeUrl(candidate)
            if (isValidWebUrl(sanitized)) {
                return sanitized
            }
        }
        val trimmed = text.trim()
        val sanitizedTrimmed = sanitizeUrl(trimmed)
        if (isValidWebUrl(sanitizedTrimmed)) {
            return sanitizedTrimmed
        }
        return null
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

    /**
     * Extracts an 11-character YouTube video ID from a watch/shorts/embed URL or thumbnail CDN URL.
     */
    fun extractYouTubeVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val m = YOUTUBE_VIDEO_ID_REGEX.find(url)
        if (m != null) return m.groupValues[1]
        return YOUTUBE_THUMB_ID_REGEX.find(url)?.groupValues?.get(1)
    }
}
