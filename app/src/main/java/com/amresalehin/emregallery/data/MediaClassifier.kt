package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

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
    internal val TRACKING_PATTERNS = listOf(
        "google-analytics", "analytics", "doubleclick", "pixel", "1x1", "tracking",
        "beacon", "telemetry", "hotjar", "clarity.ms", "stats.wp.com", "facebook.com/tr"
    )

    // Query parameters that should be stripped to produce canonical deduplication keys
    internal val TRACKING_QUERY_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "msclkid", "ref", "source", "_ga", "_gl", "_t", "cachebust",
        "range", "bytestart", "byteend", "bytes", "rn", "sq", "sver", "live", "cpn",
        "chunk", "seg", "segment", "frag", "part", "subpart"
    )

    internal val STREAMING_SLICE_PARAMS = setOf(
        "range", "bytestart", "byteend", "bytes", "rn", "sq", "live", "cpn", "sver", "chunk", "seg", "part"
    )

    /**
     * Checks if a string is a substantial, legitimate Base64 inline data URI (e.g. artwork, canvas exports),
     * while rejecting tiny spacer GIFs, 1x1 transparent tracking pixels, and empty vectors.
     */

    data class DataUriInfo(
        val mimeType: String,
        val isBase64: Boolean,
        val payload: String,
        val estimatedBytes: Long
    )

    internal val HTTP_URL_REGEX = "(https?://[^\\s<>\"'\\[\\]{}|\\\\^`]+)".toRegex(RegexOption.IGNORE_CASE)
    internal val TRACKING_PARAMS_TO_REMOVE = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_reader", "utm_name",
        "fbclid", "gclid", "gbraid", "wbraid", "msclkid", "twclid", "dclid",
        "igshid", "si", "feature", "ref", "ref_src", "ref_url", "source", "share_id",
        "_ga", "_gl", "_hsenc", "_hsmi", "mc_cid", "mc_eid",
        "is_from_webapp", "sender_device", "sender_web_id",
        "ved", "usg", "context"
    )

    /**
     * Extracts a clean display domain from a web URL.
     */


    val KNOWN_MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "svg", "avif", "bmp", "heic", "heif", "ico",
        "mp4", "webm", "mov", "mkv", "avi", "flv", "3gp", "m4v", "ts", "m3u8", "mpd",
        "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "wma"
    )

    /**
     * Checks whether the URL is valid media and not tracking/pixel/non-media page.
     */


    data class ResolvedMediaInfo(
        val mediaUrl: String,
        val thumbnailUrl: String?,
        val cleanTitle: String? = null
    )

    internal val WIKIMEDIA_THUMB_REGEX = Regex(
        """^https?://(?:upload|thumb)\.wikimedia\.org/wikipedia/([^/]+)/thumb/([0-9a-fA-F]/[0-9a-fA-F]{2}/([^/]+))/.+"""
    )
    internal val MEDIAWIKI_GENERIC_THUMB_REGEX = Regex(
        """^(https?://[^/]+(?:/[^/]+)*?)/thumb/([0-9a-fA-F]/[0-9a-fA-F]{2}/([^/]+))/.+"""
    )
    internal val WIKIMEDIA_TRANSCODE_REGEX = Regex(
        """^https?://(?:upload|thumb)\.wikimedia\.org/wikipedia/([^/]+)/transcoded/([0-9a-fA-F]/[0-9a-fA-F]{2}/([^/]+))/.+"""
    )
    internal val MEDIAWIKI_GENERIC_TRANSCODE_REGEX = Regex(
        """^(https?://[^/]+(?:/[^/]+)*?)/transcoded/([0-9a-fA-F]/[0-9a-fA-F]{2}/([^/]+))/.+"""
    )

    /**
     * Resolves a media URL to its original full-resolution master file URL across major CDNs
     * (Wikimedia, Reddit, Imgur, Twitter/X, WordPress/Jetpack, Base64 Data URIs)
     * while preserving the sharp thumbnail URL for immediate preview.

    fun isSubstantialDataUri(url: String): Boolean = isSubstantialDataUriImpl(url)
    fun parseDataUriInfo(dataUri: String): DataUriInfo? = parseDataUriInfoImpl(dataUri)
    fun estimateDataUriSizeBytes(url: String): Long? = estimateDataUriSizeBytesImpl(url)
    fun cleanMediaUrl(url: String): String = cleanMediaUrlImpl(url)
    fun isMediaFragment(url: String): Boolean = isMediaFragmentImpl(url)
    fun resolveUrl(rawUrl: String, baseUrl: String): String? = resolveUrlImpl(rawUrl, baseUrl)
    fun isValidWebUrl(raw: String): Boolean = isValidWebUrlImpl(raw)
    fun cleanDomainFromUrl(url: String?): String = cleanDomainFromUrlImpl(url)
    fun sanitizeUrl(raw: String?): String = sanitizeUrlImpl(raw)
    fun extractFirstUrl(text: String?): String? = extractFirstUrlImpl(text)
    fun isValidMedia(url: String): Boolean = isValidMediaImpl(url)
    fun resolveOriginalMedia(rawUrl: String): ResolvedMediaInfo = resolveOriginalMediaImpl(rawUrl)
    fun canonicalKey(url: String): String = deduplicationKey(url)
    fun deduplicationKey(url: String): String = deduplicationKeyImpl(url)
    fun parsePixelArea(item: ScrapedMediaItem): Long? = parsePixelAreaImpl(item)
    fun parsePixelArea(resolutionHint: String?, url: String?): Long? = parsePixelAreaImpl(resolutionHint, url)
    fun compareMediaQuality(a: ScrapedMediaItem, b: ScrapedMediaItem): Int = compareMediaQualityImpl(a, b)
    fun mergeMediaItems(existing: ScrapedMediaItem, incoming: ScrapedMediaItem): ScrapedMediaItem =
        mergeMediaItemsImpl(existing, incoming)
    fun deduplicateScrapedItems(items: List<ScrapedMediaItem>): List<ScrapedMediaItem> =
        deduplicateScrapedItemsImpl(items)
    fun extractYouTubeVideoId(url: String?): String? = extractYouTubeVideoIdImpl(url)
    fun isSvg(url: String, mimeHint: String? = null, format: String? = null): Boolean =
        isSvgImpl(url, mimeHint, format)
    fun classify(url: String, mimeHint: String? = null): ClassificationResult = classifyImpl(url, mimeHint)
    fun deriveFileName(url: String, format: String, fallbackIndex: Int): String =
        deriveFileNameImpl(url, format, fallbackIndex)
    fun parseSrcset(srcset: String, baseUrl: String): List<Pair<String, String>> =
        parseSrcsetImpl(srcset, baseUrl)
    fun formatQueryOrUrl(
        input: String,
        searchEngine: BrowserSearchEngine = BrowserSearchEngine.DUCKDUCKGO
    ): String = formatQueryOrUrlImpl(input, searchEngine)
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
