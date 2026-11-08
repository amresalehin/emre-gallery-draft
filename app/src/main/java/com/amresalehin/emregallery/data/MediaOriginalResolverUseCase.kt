package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

internal fun MediaClassifier.resolveOriginalMediaImpl(rawUrl: String): ResolvedMediaInfo {
    val trimmed = rawUrl.trim()
    val normalized = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed

    // 0. Base64 Data URI handling (embedded artwork)
    if (normalized.startsWith("data:image/", ignoreCase = true)) {
        val dataInfo = parseDataUriInfo(normalized)
        if (dataInfo != null) {
            return ResolvedMediaInfo(
                mediaUrl = normalized,
                thumbnailUrl = normalized,
                cleanTitle = dataInfo.cleanTitle
            )
        }
    }

    // 0b. Pinterest image CDN (i.pinimg.com / *.pinimg.com -> originals full resolution master)
    if (normalized.contains("pinimg.com")) {
        val cleanUrl = normalized.substringBefore("?").substringBefore("#").trimEnd('/')
        val pinHexRegex = Regex("""([0-9a-fA-F]{32})(?:\.([a-zA-Z0-9]+))?""")
        val match = pinHexRegex.find(cleanUrl)
        if (match != null) {
            val hash = match.groupValues[1]
            val rawExt = match.groupValues.getOrNull(2)?.lowercase(Locale.ROOT)
            val ext = if (rawExt.isNullOrBlank() || rawExt == "jpeg" || rawExt == "webp") "jpg" else rawExt
            val p1 = hash.substring(0, 2)
            val p2 = hash.substring(2, 4)
            val p3 = hash.substring(4, 6)
            val originalUrl = "https://i.pinimg.com/originals/$p1/$p2/$p3/$hash.$ext"
            return ResolvedMediaInfo(
                mediaUrl = originalUrl,
                thumbnailUrl = normalized,
                cleanTitle = "$hash.$ext"
            )
        }
    }

    // 0c. YouTube video thumbnails (i.ytimg.com/vi/<id>/... -> clean master resolution)
    val ytThumbRegex = Regex("""^https?://(?:i[0-4]?|img)\.ytimg\.com/(?:vi|vi_webp)/([a-zA-Z0-9_-]{11})/([a-zA-Z0-9_-]+)""")
    val ytMatch = ytThumbRegex.find(normalized)
    if (ytMatch != null) {
        val videoId = ytMatch.groupValues[1]
        val variant = ytMatch.groupValues[2]
        val masterVariant = if (variant == "oar2") "oar2" else if (variant == "frame0") "maxresdefault" else variant
        val originalUrl = "https://i.ytimg.com/vi/$videoId/$masterVariant.jpg"
        return ResolvedMediaInfo(
            mediaUrl = originalUrl,
            thumbnailUrl = normalized,
            cleanTitle = "youtube_${videoId}_$variant.jpg"
        )
    }

    // 1. Reddit Preview CDN (preview.redd.it / external-preview.redd.it -> i.redd.it)
    if (normalized.contains("preview.redd.it") || normalized.contains("external-preview.redd.it")) {
        val cleanPath = normalized.substringBefore("?")
        val fileName = cleanPath.substringAfterLast('/')
        val originalUrl = "https://i.redd.it/$fileName"
        val cleanTitle = runCatching { java.net.URLDecoder.decode(fileName, "UTF-8") }.getOrDefault(fileName)
        return ResolvedMediaInfo(
            mediaUrl = originalUrl,
            thumbnailUrl = normalized,
            cleanTitle = cleanTitle
        )
    }

    // 2. Imgur thumbnail suffix stripping (i.imgur.com/{id}{s|m|l|h|b|_d}.{ext} -> full resolution)
    if (normalized.contains("i.imgur.com/")) {
        val cleanUrl = normalized.substringBefore("?")
        val fileName = cleanUrl.substringAfterLast('/')
        val baseId = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        val imgurThumbRegex = Regex("""^([a-zA-Z0-9]{5,8})(?:[smlhbt]|_d)$""")
        val match = imgurThumbRegex.find(baseId)
        if (match != null) {
            val masterId = match.groupValues[1]
            val originalExt = if (ext.equals("webp", ignoreCase = true) || ext.isEmpty()) "jpg" else ext
            val originalUrl = "https://i.imgur.com/$masterId.$originalExt"
            return ResolvedMediaInfo(
                mediaUrl = originalUrl,
                thumbnailUrl = normalized,
                cleanTitle = "$masterId.$originalExt"
            )
        }
    }

    // 3. Twitter / X Media CDN (pbs.twimg.com/media/{id}?name=small -> name=orig)
    if (normalized.contains("pbs.twimg.com/media/")) {
        val noHash = normalized.substringBefore("#")
        val qIdx = noHash.indexOf('?')
        val base = if (qIdx == -1) noHash else noHash.substring(0, qIdx)
        val query = if (qIdx == -1) "" else noHash.substring(qIdx + 1)
        val formatParam = query.split("&").firstOrNull { it.startsWith("format=", ignoreCase = true) } ?: "format=jpg"
        val originalUrl = "$base?$formatParam&name=orig"
        val fileName = base.substringAfterLast('/')
        val ext = formatParam.substringAfter("format=").ifBlank { "jpg" }
        return ResolvedMediaInfo(
            mediaUrl = originalUrl,
            thumbnailUrl = normalized,
            cleanTitle = "$fileName.$ext"
        )
    }

    // 4. WordPress / Jetpack Photon CDN (i0.wp.com, i1.wp.com -> original unscaled image)
    if (Regex("""https?://i[0-3]\.wp\.com/""").containsMatchIn(normalized)) {
        val unwrapped = normalized.replace(Regex("""https?://i[0-3]\.wp\.com/"""), "https://")
        val cleanOriginal = unwrapped.substringBefore("?")
        val fileName = cleanOriginal.substringAfterLast('/')
        return ResolvedMediaInfo(
            mediaUrl = cleanOriginal,
            thumbnailUrl = normalized,
            cleanTitle = fileName
        )
    }

    // 4b. WordPress resized uploads (-WxH or -scaled before extension -> master file)
    if (normalized.contains("/wp-content/uploads/")) {
        val cleanUrl = normalized.substringBefore("?")
        val wpResizeRegex = Regex("""-(?:\d{2,5}x\d{2,5}|scaled)\.([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE)
        if (wpResizeRegex.containsMatchIn(cleanUrl)) {
            val masterUrl = wpResizeRegex.replace(cleanUrl, ".$1")
            val fileName = masterUrl.substringAfterLast('/')
            return ResolvedMediaInfo(
                mediaUrl = masterUrl,
                thumbnailUrl = normalized,
                cleanTitle = fileName
            )
        }
    }

    // 4c. Shopify CDN resized images (_WxH or _large etc. before extension)
    if (normalized.contains("cdn.shopify.com")) {
        val cleanUrl = normalized.substringBefore("?")
        val shopifyRegex = Regex("""_(?:\d{2,5}x\d{2,5}|\d{2,5}x|x\d{2,5}|pico|icon|thumb|small|compact|medium|large|grande|original|master|1024x1024|2048x2048)\.([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE)
        if (shopifyRegex.containsMatchIn(cleanUrl)) {
            val masterUrl = shopifyRegex.replace(cleanUrl, ".$1")
            val fileName = masterUrl.substringAfterLast('/')
            return ResolvedMediaInfo(
                mediaUrl = masterUrl,
                thumbnailUrl = normalized,
                cleanTitle = fileName
            )
        }
    }

    // 4d. Google User Content (lh3.googleusercontent.com etc. =w... / =s...)
    if (normalized.contains("googleusercontent.com")) {
        val base = normalized.substringBefore("=")
        val originalUrl = "$base=s0"
        return ResolvedMediaInfo(
            mediaUrl = originalUrl,
            thumbnailUrl = normalized,
            cleanTitle = base.substringAfterLast('/')
        )
    }

    // 5. Wikimedia Commons / Wikipedia thumbnails
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

    // 6. Wikimedia Commons / Wikipedia transcoded videos (.../transcoded/hash/hash2/filename.webm/filename.webm.480p.vp9.webm)
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

    // 7. Generic MediaWiki installations (.../thumb/hash/hash2/filename/...)
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

    // 8. Generic MediaWiki installations transcoded videos
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
