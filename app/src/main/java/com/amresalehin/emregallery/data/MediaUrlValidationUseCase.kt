package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

internal fun MediaClassifier.isValidWebUrlImpl(raw: String): Boolean {
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

internal fun MediaClassifier.cleanDomainFromUrlImpl(url: String?): String {
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
 * 2. Unwraps known redirect wrappers (Google / Facebook / Reddit outbound links).
 * 3. Strips tracking, analytics, and social share parameters (utm_*, igshid, si, fbclid, etc.).
 * 4. Ensures a valid https:// scheme.
 */
internal fun MediaClassifier.sanitizeUrlImpl(raw: String?): String {
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
                key to java.net.URLDecoder.decode(value, "UTF-8")
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
internal fun MediaClassifier.extractFirstUrlImpl(text: String?): String? {
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
internal fun MediaClassifier.formatQueryOrUrlImpl(input: String, searchEngine: BrowserSearchEngine = BrowserSearchEngine.DUCKDUCKGO): String {
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
