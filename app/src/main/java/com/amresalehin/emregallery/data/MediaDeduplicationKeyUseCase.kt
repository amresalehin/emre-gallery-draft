package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

internal fun MediaClassifier.deduplicationKeyImpl(url: String): String {
    return try {
        val resolved = resolveOriginalMedia(url).mediaUrl

        // If it's a data URI, hash its base64 content
        if (resolved.startsWith("data:", ignoreCase = true)) {
            return "data_uri_${abs(resolved.hashCode())}"
        }

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

        var normalizedHostAndPath = normalizedPath
            .replace("thumb.wikimedia.org", "upload.wikimedia.org")
            .replace(Regex("""https?://preview\.redd\.it/"""), "https://i.redd.it/")
            .replace(Regex("""https?://external-preview\.redd\.it/"""), "https://i.redd.it/")
            .replace(Regex("""https?://i[0-3]\.wp\.com/"""), "https://i.wp.com/")

        // 1. Pinterest Deduplication Key: Content-addressable 32-hex hash
        if (normalizedHostAndPath.contains("pinimg.com")) {
            val pinHash = Regex("""([0-9a-fA-F]{32})""").find(normalizedHostAndPath)?.groupValues?.get(1)
            if (pinHash != null) {
                return "pinimg:$pinHash"
            }
        }

        // 1b. YouTube Video Thumbnails: All resolution, crop, and format variants of the same video's thumbnail
        val ytThumbMatch = Regex("""(?:ytimg\.com|youtube\.com)/(?:vi|vi_webp)/([a-zA-Z0-9_-]{11})/""").find(normalizedHostAndPath)
        if (ytThumbMatch != null) {
            val videoId = ytThumbMatch.groupValues[1]
            return "ytthumb:$videoId"
        }

        // 1c. YouTube Video Playback Stream CDN (googlevideo.com/videoplayback)
        if (normalizedHostAndPath.contains("googlevideo.com/videoplayback") || normalizedHostAndPath.contains("/videoplayback")) {
            val qParams = queryPart.split("&").associate {
                it.substringBefore("=").lowercase(Locale.ROOT) to it.substringAfter("=", "")
            }
            val videoId = qParams["id"] ?: ""
            val itag = qParams["itag"] ?: ""
            if (videoId.isNotBlank()) {
                return "googlevideo:${videoId}_itag_$itag"
            }
        }

        // 2. Google User Content: Strip dynamic sizing modifiers (=w... / =s...)
        if (normalizedHostAndPath.contains("googleusercontent.com")) {
            normalizedHostAndPath = normalizedHostAndPath.substringBefore("=")
        }

        // 3. Imgur suffix stripping in deduplication key
        if (normalizedHostAndPath.contains("i.imgur.com/")) {
            val fn = normalizedHostAndPath.substringAfterLast('/')
            val id = fn.substringBeforeLast('.')
            val ext = fn.substringAfterLast('.', "")
            val m = Regex("""^([a-zA-Z0-9]{5,8})(?:[smlhbt]|_d)$""").find(id)
            if (m != null) {
                val root = normalizedHostAndPath.substringBeforeLast('/')
                normalizedHostAndPath = "$root/${m.groupValues[1]}.$ext"
            }
        }

        // 4. WordPress / CMS resized image files (-300x200, -scaled, -thumb before extension)
        normalizedHostAndPath = normalizedHostAndPath.replace(
            Regex("""-(?:\d{2,5}x\d{2,5}|scaled|thumb|medium|large|small)\.([a-zA-Z0-9]+)$"""),
            ".$1"
        )

        // 5. Shopify CDN image size suffix (_1024x1024, _grande, _medium etc. before extension)
        normalizedHostAndPath = normalizedHostAndPath.replace(
            Regex("""_(?:\d{2,5}x\d{2,5}|\d{2,5}x|x\d{2,5}|pico|icon|thumb|small|compact|medium|large|grande|original|master|1024x1024|2048x2048)\.([a-zA-Z0-9]+)$"""),
            ".$1"
        )

        // 6. Retina display image suffixes (@2x, @3x)
        normalizedHostAndPath = normalizedHostAndPath.replace(
            Regex("""@(?:\d+x|\d+\.\d+x)\.([a-zA-Z0-9]+)$"""),
            ".$1"
        )

        // 7. Generic path folder dimension descriptors (e.g. /736x/, /236x/, /474x/, /1200x/, /300x200/, /w1200/, /h800/)
        normalizedHostAndPath = normalizedHostAndPath.replace(
            Regex("""/(?:\d{2,5}x\d{0,5}|\d{0,5}x\d{2,5}|w\d{2,5}|h\d{2,5}|s\d{2,5}|width[_-]\d+|height[_-]\d+)/"""),
            "/"
        )

        // 8. Cloudflare Image Resizing / Cloudinary / Imgix dynamic resize transform stripping (e.g. /cdn-cgi/image/.../ -> /, /c_scale,w_500/ -> /)
        normalizedHostAndPath = normalizedHostAndPath
            .replace(Regex("""/cdn-cgi/image/[^/]+/"""), "/")
            .replace(Regex("""/(?:[a-z]_[a-zA-Z0-9_-]+,?)+/"""), "/")

        // 9. Strip video quality / transcode suffixes to deduplicate multiple transcode qualities of same video
        val videoQualityRegex = Regex("""(?:\.(?:144|240|360|480|720|1080|1440|2160)p(?:\.vp9|\.av1)?|[._-](?:144|240|360|480|720|1080|1440|2160)p)\.(webm|mp4|mkv)$""", RegexOption.IGNORE_CASE)
        val normalizedVideoPath = videoQualityRegex.replace(normalizedHostAndPath) { match ->
            "." + match.groupValues[1]
        }

        // 10. Format variants normalization (raster image formats .jpg, .jpeg, .webp, .avif, .png of the same asset)
        // Deduplicate format variants by normalizing raster image extensions to .img
        val normalizedFormatPath = if (Regex("""\.(?:jpe?g|webp|png|avif)$""", RegexOption.IGNORE_CASE).containsMatchIn(normalizedVideoPath)) {
            normalizedVideoPath.replace(Regex("""\.(?:jpe?g|webp|png|avif)$""", RegexOption.IGNORE_CASE), ".img")
        } else {
            normalizedVideoPath
        }

        val sizingParams = setOf(
            "w", "width", "h", "height", "size", "s", "quality", "q",
            "fit", "crop", "auto", "format", "fmt", "fm", "f", "dpr", "lossless", "strip",
            "scale", "resize", "maxwidth", "maxheight",
            "sqp", "rs", "sigh", "sig", "sparams",
            "itag", "range", "ratebypass", "rn", "rbuf", "mime", "gir", "clen", "dur", "lmt"
        )
        val filteredQuery = if (queryPart.isNotBlank()) {
            queryPart.split("&").filter { param ->
                val key = param.substringBefore("=").lowercase(Locale.ROOT)
                !TRACKING_QUERY_PARAMS.contains(key) && !sizingParams.contains(key)
            }.sorted().joinToString("&")
        } else ""

        if (filteredQuery.isEmpty()) normalizedFormatPath else "$normalizedFormatPath?$filteredQuery"
    } catch (_: Exception) {
        url.lowercase(Locale.ROOT)
    }
}

/**
 * Parses an approximate pixel area (width * height) from a media item's resolution hint or URL,
 * used for deterministic quality comparisons when deduplicating variants.
 */
internal fun MediaClassifier.parsePixelAreaImpl(item: ScrapedMediaItem): Long? {
    return parsePixelArea(item.resolutionHint, item.url)
}

/**
 * Parses pixel area from resolution hint string or URL path.
 */
internal fun MediaClassifier.parsePixelAreaImpl(resolutionHint: String?, url: String?): Long? {
    if (!resolutionHint.isNullOrBlank()) {
        val resMatch = Regex("""(\d{2,5})\s*[xX*×]\s*(\d{2,5})""").find(resolutionHint)
        if (resMatch != null) {
            val w = resMatch.groupValues[1].toLongOrNull()
            val h = resMatch.groupValues[2].toLongOrNull()
            if (w != null && h != null && w > 0 && h > 0) {
                return w * h
            }
        }
        val pMatch = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE).find(resolutionHint)
        if (pMatch != null) {
            val h = pMatch.groupValues[1].toLongOrNull()
            if (h != null && h > 0) return ((h * 16L) / 9L) * h
        }
        val wMatch = Regex("""(\d{2,5})[wW]""").find(resolutionHint)
        if (wMatch != null) {
            val w = wMatch.groupValues[1].toLongOrNull()
            if (w != null && w > 0) return w * w
        }
        val pxMatch = Regex("""(\d{2,5})px""", RegexOption.IGNORE_CASE).find(resolutionHint)
        if (pxMatch != null) {
            val px = pxMatch.groupValues[1].toLongOrNull()
            if (px != null && px > 0) return px * px
        }
    }

    if (!url.isNullOrBlank()) {
        if (url.contains("/originals/", ignoreCase = true)) {
            return 25_000_000L // Original master file ranks highest
        }
        val urlResMatch = Regex("""[/_-](\d{2,5})x(\d{2,5})[._/-]""").find(url)
        if (urlResMatch != null) {
            val w = urlResMatch.groupValues[1].toLongOrNull()
            val h = urlResMatch.groupValues[2].toLongOrNull()
            if (w != null && h != null && w > 0 && h > 0) {
                return w * h
            }
        }
        val folderDimMatch = Regex("""/(\d{2,5})x/""").find(url)
        if (folderDimMatch != null) {
            val w = folderDimMatch.groupValues[1].toLongOrNull()
            if (w != null && w > 0) return w * w
        }
        val pxUrlMatch = Regex("""[/_-](\d{2,5})px[._/-]""").find(url)
        if (pxUrlMatch != null) {
            val px = pxUrlMatch.groupValues[1].toLongOrNull()
            if (px != null && px > 0) return px * px
        }
        val queryWidthMatch = Regex("""[?&](?:w|width)=(\d{2,5})""").find(url)
        if (queryWidthMatch != null) {
            val w = queryWidthMatch.groupValues[1].toLongOrNull()
            if (w != null && w > 0) return w * w
        }
    }
    return null
}

/**
 * Compares two duplicate scraped media items to determine which offers superior quality
 * (e.g. master original over thumbnail, higher pixel resolution, larger file size, preferred format).
 * Returns > 0 if [a] is higher quality, < 0 if [b] is higher quality, 0 if equivalent.
 */
internal fun MediaClassifier.compareMediaQualityImpl(a: ScrapedMediaItem, b: ScrapedMediaItem): Int {
    // 1. Originals master URL preference
    val aIsOriginals = a.url.contains("/originals/", ignoreCase = true)
    val bIsOriginals = b.url.contains("/originals/", ignoreCase = true)
    if (aIsOriginals && !bIsOriginals) return 1
    if (!aIsOriginals && bIsOriginals) return -1

    // 2. Direct upload vs thumbnail/transcoded path
    val aIsThumb = a.url.contains("/thumb/") || a.url.contains("/transcoded/")
    val bIsThumb = b.url.contains("/thumb/") || b.url.contains("/transcoded/")
    if (!aIsThumb && bIsThumb) return 1
    if (aIsThumb && !bIsThumb) return -1

    // 3. Pixel area from resolutionHint or URL
    val aArea = parsePixelArea(a)
    val bArea = parsePixelArea(b)
    if (aArea != null && bArea != null && aArea != bArea) {
        return aArea.compareTo(bArea)
    }
    if (aArea != null && bArea == null) return 1
    if (aArea == null && bArea != null) return -1

    // 4. File size in bytes
    val aSize = a.sizeBytes ?: 0L
    val bSize = b.sizeBytes ?: 0L
    if (aSize != bSize) {
        return aSize.compareTo(bSize)
    }

    // 5. Format preference for master downloads (JPEG / JPG / PNG over lossy WEBP / AVIF)
    val formatPriority = mapOf("PNG" to 4, "JPEG" to 3, "JPG" to 3, "WEBP" to 2, "AVIF" to 1)
    val aFmt = formatPriority[a.format.uppercase(Locale.ROOT)] ?: 0
    val bFmt = formatPriority[b.format.uppercase(Locale.ROOT)] ?: 0
    if (aFmt != bFmt) {
        return aFmt.compareTo(bFmt)
    }

    // 6. Prefer candidate with existing thumbnail preview
    if (a.thumbnailUrl != null && b.thumbnailUrl == null) return 1
    if (a.thumbnailUrl == null && b.thumbnailUrl != null) return -1

    return 0
}

/**
 * Merges incoming media items into a target map keyed by deduplicationKey.
 * Unified pipeline for deduplication, video-poster folding, quality upgrades,
 * resolution hint selection, provenance merging, and header retention.
 * Returns true if any item was added, replaced, or upgraded.
