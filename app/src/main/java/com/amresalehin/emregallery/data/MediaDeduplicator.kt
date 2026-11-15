package com.amresalehin.emregallery.data

import java.net.URLDecoder
import java.util.Locale
import kotlin.math.abs

/**
 * Computes canonical deduplication keys across CDN/format/resolution variants and
 * collapses duplicate scraped media items while merging provenance, headers, and video posters.
 */
object MediaDeduplicator {

    private val TRACKING_QUERY_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "msclkid", "ref", "source", "_ga", "_gl", "_t", "cachebust",
        "range", "bytestart", "byteend", "bytes", "rn", "sq", "sver", "live", "cpn",
        "chunk", "seg", "segment", "frag", "part", "subpart"
    )

    private val SIZING_PARAMS = setOf(
        "w", "width", "h", "height", "size", "s", "quality", "q",
        "fit", "crop", "auto", "format", "fmt", "fm", "f", "dpr", "lossless", "strip",
        "scale", "resize", "maxwidth", "maxheight",
        "sqp", "rs", "sigh", "sig", "sparams",
        "itag", "range", "ratebypass", "rn", "rbuf", "mime", "gir", "clen", "dur", "lmt"
    )

    private val REDDIT_PREVIEW_REGEX = Regex("""https?://preview\.redd\.it/""")
    private val REDDIT_EXT_PREVIEW_REGEX = Regex("""https?://external-preview\.redd\.it/""")
    private val WP_PHOTON_REGEX = Regex("""https?://i[0-3]\.wp\.com/""")
    private val PINTEREST_HASH_REGEX = Regex("""([0-9a-fA-F]{32})""")
    private val YOUTUBE_THUMB_REGEX = Regex("""(?:ytimg\.com|youtube\.com)/(?:vi|vi_webp)/([a-zA-Z0-9_-]{11})/""")
    private val IMGUR_SUFFIX_REGEX = Regex("""^([a-zA-Z0-9]{5,8})(?:[smlhbt]|_d)$""")
    private val WP_SUFFIX_REGEX = Regex("""-(?:\d{2,5}x\d{2,5}|scaled|thumb|medium|large|small)\.([a-zA-Z0-9]+)$""")
    private val SHOPIFY_SUFFIX_REGEX = Regex("""_(?:\d{2,5}x\d{2,5}|\d{2,5}x|x\d{2,5}|pico|icon|thumb|small|compact|medium|large|grande|original|master|1024x1024|2048x2048)\.([a-zA-Z0-9]+)$""")
    private val RETINA_SUFFIX_REGEX = Regex("""@(?:\d+x|\d+\.\d+x)\.([a-zA-Z0-9]+)$""")
    private val FOLDER_DIM_REGEX = Regex("""/(?:\d{2,5}x\d{0,5}|\d{0,5}x\d{2,5}|w\d{2,5}|h\d{2,5}|s\d{2,5}|width[_-]\d+|height[_-]\d+)/""")
    private val CLOUDFLARE_TRANSFORM_REGEX = Regex("""/cdn-cgi/image/[^/]+/""")
    private val CLOUDINARY_TRANSFORM_REGEX = Regex("""/(?:[a-z]_[a-zA-Z0-9_-]+,?)+/""")
    private val VIDEO_QUALITY_REGEX = Regex(
        """(?:\.(?:144|240|360|480|720|1080|1440|2160)p(?:\.vp9|\.av1)?|[._-](?:144|240|360|480|720|1080|1440|2160)p)\.(webm|mp4|mkv)$""",
        RegexOption.IGNORE_CASE
    )
    private val RASTER_EXT_REGEX = Regex("""\.(?:jpe?g|webp|png|avif)$""", RegexOption.IGNORE_CASE)

    /**
     * Creates a canonical deduplication key by removing non-functional tracking query parameters.
     */
    fun canonicalKey(url: String): String = deduplicationKey(url)

    /**
     * Creates a high-fidelity deduplication key that identifies identical media discovered
     * across different extraction engines (Static HTML, Live Browser, gallery-dl, yt-dlp).
     */
    fun deduplicationKey(url: String): String {
        return try {
            val resolved = OriginalMediaResolver.resolve(url).mediaUrl

            if (resolved.startsWith("data:", ignoreCase = true)) {
                return "data_uri_${abs(resolved.hashCode())}"
            }

            val clean = WebUrlUtils.cleanMediaUrl(resolved)
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
                URLDecoder.decode(basePart, "UTF-8").lowercase(Locale.ROOT)
            } catch (_: Exception) {
                basePart.lowercase(Locale.ROOT)
            }

            var normalizedHostAndPath = normalizedPath
                .replace("thumb.wikimedia.org", "upload.wikimedia.org")
                .replace(REDDIT_PREVIEW_REGEX, "https://i.redd.it/")
                .replace(REDDIT_EXT_PREVIEW_REGEX, "https://i.redd.it/")
                .replace(WP_PHOTON_REGEX, "https://i.wp.com/")

            if (normalizedHostAndPath.contains("pinimg.com")) {
                val pinHash = PINTEREST_HASH_REGEX.find(normalizedHostAndPath)?.groupValues?.get(1)
                if (pinHash != null) return "pinimg:$pinHash"
            }

            val ytThumbMatch = YOUTUBE_THUMB_REGEX.find(normalizedHostAndPath)
            if (ytThumbMatch != null) {
                return "ytthumb:${ytThumbMatch.groupValues[1]}"
            }

            if (normalizedHostAndPath.contains("googleusercontent.com")) {
                normalizedHostAndPath = normalizedHostAndPath.substringBefore("=")
            }

            if (normalizedHostAndPath.contains("i.imgur.com/")) {
                val fn = normalizedHostAndPath.substringAfterLast('/')
                val id = fn.substringBeforeLast('.')
                val ext = fn.substringAfterLast('.', "")
                val m = IMGUR_SUFFIX_REGEX.find(id)
                if (m != null) {
                    val root = normalizedHostAndPath.substringBeforeLast('/')
                    normalizedHostAndPath = "$root/${m.groupValues[1]}.$ext"
                }
            }

            normalizedHostAndPath = normalizedHostAndPath
                .replace(WP_SUFFIX_REGEX, ".$1")
                .replace(SHOPIFY_SUFFIX_REGEX, ".$1")
                .replace(RETINA_SUFFIX_REGEX, ".$1")
                .replace(FOLDER_DIM_REGEX, "/")
                .replace(CLOUDFLARE_TRANSFORM_REGEX, "/")
                .replace(CLOUDINARY_TRANSFORM_REGEX, "/")

            val normalizedVideoPath = VIDEO_QUALITY_REGEX.replace(normalizedHostAndPath) { match ->
                "." + match.groupValues[1]
            }

            val normalizedFormatPath = if (RASTER_EXT_REGEX.containsMatchIn(normalizedVideoPath)) {
                normalizedVideoPath.replace(RASTER_EXT_REGEX, ".img")
            } else {
                normalizedVideoPath
            }

            val filteredQuery = if (queryPart.isNotBlank()) {
                queryPart.split("&").filter { param ->
                    val key = param.substringBefore("=").lowercase(Locale.ROOT)
                    !TRACKING_QUERY_PARAMS.contains(key) && !SIZING_PARAMS.contains(key)
                }.sorted().joinToString("&")
            } else ""

            if (filteredQuery.isEmpty()) normalizedFormatPath else "$normalizedFormatPath?$filteredQuery"
        } catch (_: Exception) {
            url.lowercase(Locale.ROOT)
        }
    }

    /**
     * Deduplicates a list of scraped media items discovered by one or more extraction engines.
     */
    fun deduplicateScrapedItems(items: List<ScrapedMediaItem>): List<ScrapedMediaItem> {
        if (items.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<ScrapedMediaItem>>()
        for (item in items) {
            val key = deduplicationKey(item.url)
            grouped.getOrPut(key) { mutableListOf() }.add(item)
        }

        val mergedItems = grouped.values.map { group ->
            if (group.size == 1) group[0] else mergeDuplicateGroup(group)
        }

        return foldVideoPosters(mergedItems)
    }

    private fun mergeDuplicateGroup(group: List<ScrapedMediaItem>): ScrapedMediaItem {
        var best = group[0]
        for (i in 1 until group.size) {
            if (MediaQualityEvaluator.compareQuality(group[i], best) > 0) {
                best = group[i]
            }
        }

        val engines = group.map { it.provenance.extractorEngine }.distinct()
        val mergedProvenance = if (engines.size > 1) {
            best.provenance.copy(extractorEngine = engines.joinToString(" + "))
        } else {
            best.provenance
        }

        val bestThumb = best.thumbnailUrl ?: group.firstNotNullOfOrNull { it.thumbnailUrl }
        val mergedResolution = group.firstOrNull {
            !it.resolutionHint.isNullOrBlank() && it.resolutionHint != "Network"
        }?.resolutionHint ?: best.resolutionHint

        val mergedHeaders = mutableMapOf<String, String>()
        for (cand in group) {
            mergedHeaders.putAll(cand.downloadHeaders)
        }

        val resolvedOriginalUrl = OriginalMediaResolver.resolve(best.url).mediaUrl
        return best.copy(
            url = resolvedOriginalUrl,
            thumbnailUrl = bestThumb,
            resolutionHint = mergedResolution,
            provenance = mergedProvenance,
            downloadHeaders = mergedHeaders
        )
    }

    private fun foldVideoPosters(items: List<ScrapedMediaItem>): List<ScrapedMediaItem> {
        val videoThumbKeys = mutableSetOf<String>()
        for (item in items) {
            if (item.isVideo) {
                item.thumbnailUrl?.let { videoThumbKeys.add(deduplicationKey(it)) }
                val ytId = WebUrlUtils.extractYouTubeVideoId(item.url)
                    ?: item.thumbnailUrl?.let { WebUrlUtils.extractYouTubeVideoId(it) }
                    ?: WebUrlUtils.extractYouTubeVideoId(item.provenance.pageUrl)
                if (ytId != null) {
                    videoThumbKeys.add("ytthumb:${ytId.lowercase(Locale.ROOT)}")
                }
            }
        }

        if (videoThumbKeys.isEmpty()) return items

        val finalResult = mutableListOf<ScrapedMediaItem>()
        for (item in items) {
            if (!item.isVideo) {
                val itemKey = deduplicationKey(item.url)
                if (itemKey in videoThumbKeys) {
                    val matchingVideoIdx = finalResult.indexOfFirst { v ->
                        val vUrlYt = WebUrlUtils.extractYouTubeVideoId(v.url)?.lowercase(Locale.ROOT)
                        val vThumbYt = v.thumbnailUrl?.let { WebUrlUtils.extractYouTubeVideoId(it)?.lowercase(Locale.ROOT) }
                        val vPageYt = WebUrlUtils.extractYouTubeVideoId(v.provenance.pageUrl)?.lowercase(Locale.ROOT)
                        v.isVideo && (
                            (v.thumbnailUrl != null && deduplicationKey(v.thumbnailUrl) == itemKey) ||
                                (vUrlYt != null && "ytthumb:$vUrlYt" == itemKey) ||
                                (vThumbYt != null && "ytthumb:$vThumbYt" == itemKey) ||
                                (vPageYt != null && "ytthumb:$vPageYt" == itemKey)
                            )
                    }
                    if (matchingVideoIdx != -1) {
                        val matchingVideo = finalResult[matchingVideoIdx]
                        if (matchingVideo.thumbnailUrl == null ||
                            MediaQualityEvaluator.compareQuality(item, matchingVideo.copy(url = matchingVideo.thumbnailUrl)) > 0
                        ) {
                            finalResult[matchingVideoIdx] = matchingVideo.copy(thumbnailUrl = item.thumbnailUrl ?: item.url)
                        }
                    }
                    continue
                }
            }
            finalResult.add(item)
        }
        return finalResult
    }
}
