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
        "fbclid", "gclid", "msclkid", "ref", "source", "_ga", "_gl", "_t", "cachebust",
        "range", "bytestart", "byteend", "bytes", "rn", "sq", "sver", "live", "cpn",
        "chunk", "seg", "segment", "frag", "part"
    )

    private val STREAMING_SLICE_PARAMS = setOf(
        "range", "bytestart", "byteend", "bytes", "rn", "sq", "live", "cpn"
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
     * Detects if a URL is an unmerged streaming chunk, segment, or initialization fragment.
     * Standalone downloading of individual video chunks causes broken, partial, and multiplied downloads.
     */
    fun isMediaFragment(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return true

        val clean = lower.substringBefore("?").substringBefore("#")

        // 1. Fragment-only file extensions
        if (clean.endsWith(".m4s") || clean.endsWith(".m4f") || clean.endsWith(".f4f") || clean.endsWith(".f4m")) {
            return true
        }

        // 2. Transport stream chunks (.ts)
        // Standalone videos on websites are MP4/WebM/MKV/MOV/M3U8. A .ts with numeric/segment numbering is an HLS chunk
        if (clean.endsWith(".ts")) {
            val fileName = clean.substringAfterLast('/')
            val isSegmentTs = fileName.matches(Regex("""(?i)(?:seg|segment|chunk|frag|fragment)?[_\-.]?\d+\.ts""")) ||
                    fileName.contains("slice") || fileName.contains("stream") ||
                    clean.contains("/hls/") || clean.contains("/dash/") || clean.contains("/segments/") || clean.contains("/chunks/")
            if (isSegmentTs) return true
        }

        // 3. Initialization segments (contain metadata only, no playable frames)
        if (clean.contains("/init.") || clean.contains("/init-") || clean.contains("_init.") ||
            clean.endsWith("init.mp4") || clean.endsWith("init.m4s") || clean.contains("init-stream") ||
            clean.contains("/header.mp4") || clean.contains("/init_")
        ) {
            return true
        }

        // 4. Fragment / segment naming in URL path
        val segmentPattern = Regex("""[/_.-](?:seg|segment|frag|fragment|chunk)[-_.]?\d+""", RegexOption.IGNORE_CASE)
        if (segmentPattern.containsMatchIn(clean)) {
            return true
        }
        val numberedFragment = Regex("""\d+-(?:frag|seg|segment)\.(?:mp4|ts|m4s|m4v)""", RegexOption.IGNORE_CASE)
        if (numberedFragment.containsMatchIn(clean)) {
            return true
        }

        // 5. Query parameters indicating chunk indexing
        val query = lower.substringAfter("?", "")
        if (query.isNotBlank()) {
            val params = query.split("&")
            for (p in params) {
                val key = p.substringBefore("=")
                val value = p.substringAfter("=", "")
                if ((key == "sq" || key == "seg" || key == "segment" || key == "frag" || key == "chunk") &&
                    value.toIntOrNull() != null
                ) {
                    return true
                }
            }
        }

        return false
    }

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

    val KNOWN_MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "svg", "avif", "bmp", "heic", "heif", "ico",
        "mp4", "webm", "mov", "mkv", "avi", "flv", "3gp", "m4v", "ts", "m3u8", "mpd",
        "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "wma"
    )

    /**
     * Checks whether the URL is valid media and not tracking/pixel/non-media page.
     */
    fun isValidMedia(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        for (pattern in TRACKING_PATTERNS) {
            if (lower.contains(pattern)) return false
        }
        if (lower.contains("favicon.ico") || lower.contains("/avatar_default") ||
            lower.contains("/blank.gif") || lower.contains("transparent.gif") ||
            lower.contains("spacer.gif") || lower.contains("spinner.gif")
        ) {
            return false
        }
        val cleanPath = lower.substringBefore("?").substringBefore("#")
        // Reject web document and script endpoints
        if (cleanPath.endsWith(".html") || cleanPath.endsWith(".htm") || cleanPath.endsWith(".php") ||
            cleanPath.endsWith(".jsp") || cleanPath.endsWith(".asp") || cleanPath.endsWith(".aspx") ||
            cleanPath.endsWith(".js") || cleanPath.endsWith(".css") || cleanPath.endsWith(".json") ||
            cleanPath.endsWith(".xml") || cleanPath.endsWith(".txt") || cleanPath.endsWith(".do")
        ) {
            return false
        }
        val candidate = when {
            lower.startsWith("http://") || lower.startsWith("https://") -> url
            lower.startsWith("//") -> "https:$url"
            lower.startsWith("/") -> "https://placeholder.invalid$url"
            else -> "https://placeholder.invalid/$url"
        }
        val uri = runCatching { URI(candidate) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT)?.takeIf { it != "placeholder.invalid" } ?: ""
        val path = uri?.path?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: cleanPath

        // Host/path-aware non-media webpage rejections:
        val isWikiHost = host.contains("wikipedia.org") || host.contains("wikimedia.org") ||
                host.contains("wiktionary.org") || host.contains("wikidata.org") ||
                host.contains("wikibooks.org") || host.contains("wikiquote.org") ||
                host.contains("wikisource.org") || host.contains("wikinews.org") ||
                host.contains("wikivoyage.org") || host.contains("mediawiki.org")

        if (isWikiHost && (path.contains("/geohack") || path.contains("centralautologin") || path.contains("/api.php"))) {
            return false
        }
        // Reddit thread / comments pages (not direct media CDNs)
        if ((host.contains("reddit.com") || host.contains("redd.it")) &&
            !host.startsWith("i.") && !host.startsWith("v.") && !host.startsWith("preview.") &&
            path.contains("/comments/")
        ) {
            return false
        }
        // Pinterest pin discussion pages (not direct image CDN)
        if ((host.contains("pinterest.com") || host.contains("pin.it")) &&
            !host.startsWith("i.") &&
            path.contains("/pin/")
        ) {
            return false
        }
        // Imgur gallery web albums (not direct CDN images)
        if (host.contains("imgur.com") && !host.startsWith("i.") &&
            (path.contains("/gallery/") || path.startsWith("/gallery") || path.startsWith("/a/"))
        ) {
            return false
        }
        // Twitter/X status pages (not direct media CDNs)
        if ((host.contains("twitter.com") || host.contains("x.com")) &&
            !host.startsWith("pbs.") && !host.startsWith("video.") &&
            path.contains("/status/")
        ) {
            return false
        }
        // MediaWiki / Wikipedia article and discussion pages (HTML pages)
        if (path.contains("/wiki/file:") || path.contains("/wiki/image:") || path.contains("/wiki/special:")) {
            return false
        }
        if (isWikiHost && !host.contains("upload.wikimedia.org") && !host.contains("thumb.wikimedia.org")) {
            if (path.contains("/wiki/") || path.contains("/w/index.php") || path.contains("/w/api.php") ||
                path.contains("/file:") || path.contains("/image:") ||
                cleanPath.contains("file:") || cleanPath.contains("image:")
            ) {
                return false
            }
        }
        if (path.startsWith("/w/index.php") || path.contains("/w/index.php")) {
            return false
        }
        if (isMediaFragment(url)) {
            return false
        }
        return true
    }

    data class ResolvedMediaInfo(
        val mediaUrl: String,
        val thumbnailUrl: String?,
        val cleanTitle: String? = null
    )

    private val WIKIMEDIA_THUMB_REGEX = Regex(
        """^https?://(?:upload|thumb)\.wikimedia\.org/wikipedia/([^/]+)/thumb/([0-9a-fA-F]/[0-9a-fA-F]{2}/([^/]+))/.+"""
    )
    private val MEDIAWIKI_GENERIC_THUMB_REGEX = Regex(
        """^(https?://[^/]+(?:/[^/]+)*?)/thumb/([0-9a-fA-F]/[0-9a-fA-F]{2}/([^/]+))/.+"""
    )
    private val WIKIMEDIA_TRANSCODE_REGEX = Regex(
        """^https?://(?:upload|thumb)\.wikimedia\.org/wikipedia/([^/]+)/transcoded/([0-9a-fA-F]/[0-9a-fA-F]{2}/([^/]+))/.+"""
    )
    private val MEDIAWIKI_GENERIC_TRANSCODE_REGEX = Regex(
        """^(https?://[^/]+(?:/[^/]+)*?)/transcoded/([0-9a-fA-F]/[0-9a-fA-F]{2}/([^/]+))/.+"""
    )

    /**
     * Resolves a media URL (such as a Wikimedia or MediaWiki thumbnail or transcoded video) to its original
     * full-resolution file URL, while preserving the sharp thumbnail URL for immediate preview.
     * Preserves custom upload paths without assuming default /images/.
     */
    fun resolveOriginalMedia(rawUrl: String): ResolvedMediaInfo {
        val trimmed = rawUrl.trim()
        val normalized = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed

        // 1. Wikimedia Commons / Wikipedia thumbnails
        val wmMatch = WIKIMEDIA_THUMB_REGEX.find(normalized)
        if (wmMatch != null) {
            val project = wmMatch.groupValues[1]
            val subpath = wmMatch.groupValues[2]
            val rawFileName = wmMatch.groupValues[3]
            val originalUrl = "https://upload.wikimedia.org/wikipedia/$project/$subpath"
            val cleanTitle = runCatching { java.net.URLDecoder.decode(rawFileName, "UTF-8") }.getOrDefault(rawFileName)
            return ResolvedMediaInfo(
                mediaUrl = originalUrl,
                thumbnailUrl = normalized,
                cleanTitle = cleanTitle
            )
        }

        // 2. Wikimedia Commons / Wikipedia transcoded videos (.../transcoded/hash/hash2/filename.webm/filename.webm.480p.vp9.webm)
        val wmTranscodeMatch = WIKIMEDIA_TRANSCODE_REGEX.find(normalized)
        if (wmTranscodeMatch != null) {
            val project = wmTranscodeMatch.groupValues[1]
            val subpath = wmTranscodeMatch.groupValues[2]
            val rawFileName = wmTranscodeMatch.groupValues[3]
            val originalUrl = "https://upload.wikimedia.org/wikipedia/$project/$subpath"
            val cleanTitle = runCatching { java.net.URLDecoder.decode(rawFileName, "UTF-8") }.getOrDefault(rawFileName)
            return ResolvedMediaInfo(
                mediaUrl = originalUrl,
                thumbnailUrl = normalized,
                cleanTitle = cleanTitle
            )
        }

        // 3. Generic MediaWiki installations (.../thumb/hash/hash2/filename/...)
        // Preserves custom upload directories without assuming standard /images/
        if (normalized.contains("/thumb/")) {
            val mwMatch = MEDIAWIKI_GENERIC_THUMB_REGEX.find(normalized)
            if (mwMatch != null) {
                val uploadBase = mwMatch.groupValues[1]
                val subpath = mwMatch.groupValues[2]
                val rawFileName = mwMatch.groupValues[3]
                val originalUrl = "$uploadBase/$subpath"
                val cleanTitle = runCatching { java.net.URLDecoder.decode(rawFileName, "UTF-8") }.getOrDefault(rawFileName)
                return ResolvedMediaInfo(
                    mediaUrl = originalUrl,
                    thumbnailUrl = normalized,
                    cleanTitle = cleanTitle
                )
            }
        }

        // 4. Generic MediaWiki installations transcoded videos
        if (normalized.contains("/transcoded/")) {
            val mwTranscodeMatch = MEDIAWIKI_GENERIC_TRANSCODE_REGEX.find(normalized)
            if (mwTranscodeMatch != null) {
                val uploadBase = mwTranscodeMatch.groupValues[1]
                val subpath = mwTranscodeMatch.groupValues[2]
                val rawFileName = mwTranscodeMatch.groupValues[3]
                val originalUrl = "$uploadBase/$subpath"
                val cleanTitle = runCatching { java.net.URLDecoder.decode(rawFileName, "UTF-8") }.getOrDefault(rawFileName)
                return ResolvedMediaInfo(
                    mediaUrl = originalUrl,
                    thumbnailUrl = normalized,
                    cleanTitle = cleanTitle
                )
            }
        }

        return ResolvedMediaInfo(
            mediaUrl = normalized,
            thumbnailUrl = null,
            cleanTitle = null
        )
    }

    /**
     * Creates a canonical deduplication key by removing non-functional tracking query parameters.
     */
    fun canonicalKey(url: String): String = deduplicationKey(url)

    /**
     * Creates a high-fidelity deduplication key that identifies identical media discovered
     * across different extraction engines (Static HTML, Live Browser, gallery-dl, yt-dlp).
     * Normalizes:
     * - Scheme (http vs https)
     * - URL percent-encoding differences (e.g. %28 vs ()
     * - Wikimedia/MediaWiki thumbnail derivatives vs original full-resolution uploads
     * - Responsive image dimensions / resize query parameters (?w=, ?h=, ?width=, ?size=, ?quality=, ?fit=, etc.)
     * - Tracking query parameters
     */
    fun deduplicationKey(url: String): String {
        return try {
            val resolved = resolveOriginalMedia(url).mediaUrl
            val clean = cleanMediaUrl(resolved)
            val noHash = clean.substringBefore("#")

            val withHttps = when {
                noHash.startsWith("http://", ignoreCase = true) -> "https://" + noHash.substring(7)
                noHash.startsWith("//") -> "https:$noHash"
                else -> noHash
            }

            val qIdx = withHttps.indexOf('?')
            val basePart = if (qIdx == -1) withHttps else withHttps.substring(0, qIdx)
            val queryPart = if (qIdx == -1) "" else withHttps.substring(qIdx + 1)

            val normalizedPath = try {
                java.net.URLDecoder.decode(basePart, "UTF-8").lowercase(Locale.ROOT)
            } catch (_: Exception) {
                basePart.lowercase(Locale.ROOT)
            }

            val normalizedHostAndPath = normalizedPath
                .replace("thumb.wikimedia.org", "upload.wikimedia.org")
                .replace(Regex("""https?://i[0-9]\.wp\.com/"""), "https://i.wp.com/")

            // Strip video quality / transcode suffixes to deduplicate multiple transcode qualities of same video
            val videoQualityRegex = Regex("""(?:\.(?:144|240|360|480|720|1080|1440|2160)p(?:\.vp9|\.av1)?|[._-](?:144|240|360|480|720|1080|1440|2160)p)\.(webm|mp4|mkv)$""", RegexOption.IGNORE_CASE)
            val normalizedVideoPath = videoQualityRegex.replace(normalizedHostAndPath) { match ->
                "." + match.groupValues[1]
            }

            val sizingParams = setOf(
                "w", "width", "h", "height", "size", "s", "quality", "q",
                "fit", "crop", "auto", "format", "dpr", "lossless", "strip",
                "itag", "range", "ratebypass", "rn", "rbuf", "mime", "gir", "clen", "dur", "lmt"
            )
            val filteredQuery = if (queryPart.isNotBlank()) {
                queryPart.split("&").filter { param ->
                    val key = param.substringBefore("=").lowercase(Locale.ROOT)
                    !TRACKING_QUERY_PARAMS.contains(key) && !sizingParams.contains(key)
                }.sorted().joinToString("&")
            } else ""

            if (filteredQuery.isEmpty()) normalizedVideoPath else "$normalizedVideoPath?$filteredQuery"
        } catch (_: Exception) {
            url.lowercase(Locale.ROOT)
        }
    }

    /**
     * Deduplicates a list of scraped media items discovered by one or more extraction engines.
     * When duplicates are identified:
     * - Retains the item with the highest resolution / quality / full original URL
     * - Retains valid thumbnails from either source
     * - Combines provenance info (indicating multiple scrapers identified the item)
     * - Preserves required download headers
     */
    fun deduplicateScrapedItems(items: List<ScrapedMediaItem>): List<ScrapedMediaItem> {
        if (items.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<ScrapedMediaItem>>()
        for (item in items) {
            val key = deduplicationKey(item.url)
            val list = grouped.getOrPut(key) { mutableListOf() }
            list.add(item)
        }

        val result = mutableListOf<ScrapedMediaItem>()
        for ((_, group) in grouped) {
            if (group.size == 1) {
                result.add(group[0])
            } else {
                val best = group.maxWithOrNull(compareBy<ScrapedMediaItem> {
                    if (!it.url.contains("/thumb/") && !it.url.contains("/transcoded/") && !it.url.contains("resize") && !it.url.contains("?w=")) 2 else 0
                }.thenBy {
                    if (!it.resolutionHint.isNullOrBlank() && it.resolutionHint != "Network") 2 else 0
                }.thenBy {
                    it.resolutionHint?.length ?: 0
                }.thenBy {
                    it.sizeBytes ?: 0L
                }.thenBy {
                    if (it.thumbnailUrl != null) 1 else 0
                }) ?: group[0]

                val engines = group.map { it.provenance.extractorEngine }.distinct()
                val mergedProvenance = if (engines.size > 1) {
                    best.provenance.copy(
                        extractorEngine = engines.joinToString(" + ")
                    )
                } else {
                    best.provenance
                }

                val bestThumb = best.thumbnailUrl ?: group.firstNotNullOfOrNull { it.thumbnailUrl }
                val mergedResolution = group.firstOrNull { !it.resolutionHint.isNullOrBlank() && it.resolutionHint != "Network" }?.resolutionHint
                    ?: best.resolutionHint

                val mergedHeaders = mutableMapOf<String, String>()
                for (cand in group) {
                    mergedHeaders.putAll(cand.downloadHeaders)
                }

                val resolvedOriginalUrl = resolveOriginalMedia(best.url).mediaUrl
                val merged = best.copy(
                    url = resolvedOriginalUrl,
                    thumbnailUrl = bestThumb,
                    resolutionHint = mergedResolution,
                    provenance = mergedProvenance,
                    downloadHeaders = mergedHeaders
                )
                result.add(merged)
            }
        }
        return result
    }

    /**
     * Checks if a media item is an SVG vector asset based on format, MIME type, or URL extension.
     */
    fun isSvg(url: String, mimeHint: String? = null, format: String? = null): Boolean {
        if (format?.equals("SVG", ignoreCase = true) == true) return true
        if (mimeHint?.contains("svg", ignoreCase = true) == true) return true
        val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return clean.endsWith(".svg")
    }

    /**
     * Classifies a media URL into category (IMAGE, VIDEO, AUDIO, STREAM) and specific format string.
     */
    fun classify(url: String, mimeHint: String? = null): ClassificationResult {
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
