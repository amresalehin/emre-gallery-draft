package com.amresalehin.emregallery.data

import java.net.URLDecoder
import java.util.Locale

data class ResolvedMediaInfo(
    val mediaUrl: String,
    val thumbnailUrl: String?,
    val cleanTitle: String? = null
)

/**
 * Resolves thumbnail/resized CDN URLs to their original full-resolution master file URLs
 * across major CDNs (Pinterest, YouTube, Reddit, Imgur, Twitter/X, WordPress/Jetpack,
 * Shopify, Google User Content, Wikimedia/MediaWiki, and Base64 Data URIs) while preserving
 * the sharp thumbnail URL for immediate preview.
 */
object OriginalMediaResolver {

    private val PINTEREST_HEX_REGEX = Regex("""([0-9a-fA-F]{32})(?:\.([a-zA-Z0-9]+))?""")
    private val YOUTUBE_THUMB_REGEX =
        Regex("""^https?://(?:i[0-4]?|img)\.ytimg\.com/(?:vi|vi_webp)/([a-zA-Z0-9_-]{11})/([a-zA-Z0-9_-]+)""")
    private val IMGUR_THUMB_REGEX = Regex("""^([a-zA-Z0-9]{5,8})(?:[smlhbt]|_d)$""")
    private val WP_PHOTON_PREFIX_REGEX = Regex("""https?://i[0-3]\.wp\.com/""")
    private val WP_RESIZE_REGEX =
        Regex("""-(?:\d{2,5}x\d{2,5}|scaled)\.([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE)
    private val SHOPIFY_RESIZE_REGEX =
        Regex(
            """_(?:\d{2,5}x\d{2,5}|\d{2,5}x|x\d{2,5}|pico|icon|thumb|small|compact|medium|large|grande|original|master|1024x1024|2048x2048)\.([a-zA-Z0-9]+)$""",
            RegexOption.IGNORE_CASE
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

    fun resolve(rawUrl: String): ResolvedMediaInfo {
        val trimmed = rawUrl.trim()
        val normalized = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed

        return resolveDataUri(normalized)
            ?: resolvePinterest(normalized)
            ?: resolveYouTubeThumbnail(normalized)
            ?: resolveReddit(normalized)
            ?: resolveImgur(normalized)
            ?: resolveTwitter(normalized)
            ?: resolveWordPressPhoton(normalized)
            ?: resolveWordPressUploads(normalized)
            ?: resolveShopify(normalized)
            ?: resolveGoogleUserContent(normalized)
            ?: resolveMediaWiki(normalized)
            ?: ResolvedMediaInfo(
                mediaUrl = normalized,
                thumbnailUrl = null,
                cleanTitle = null
            )
    }

    private fun resolveDataUri(normalized: String): ResolvedMediaInfo? {
        if (!normalized.startsWith("data:image/", ignoreCase = true)) return null
        val dataInfo = DataUriParser.parse(normalized) ?: return null
        return ResolvedMediaInfo(
            mediaUrl = normalized,
            thumbnailUrl = normalized,
            cleanTitle = dataInfo.cleanTitle
        )
    }

    private fun resolvePinterest(normalized: String): ResolvedMediaInfo? {
        if (!normalized.contains("pinimg.com")) return null
        val cleanUrl = normalized.substringBefore("?").substringBefore("#").trimEnd('/')
        val match = PINTEREST_HEX_REGEX.find(cleanUrl) ?: return null
        val hash = match.groupValues[1]
        val rawExt = match.groupValues.getOrNull(2)?.lowercase(Locale.ROOT)
        val ext = if (rawExt.isNullOrBlank() || rawExt == "jpeg" || rawExt == "webp") "jpg" else rawExt
        val p1 = hash.substring(0, 2)
        val p2 = hash.substring(2, 4)
        val p3 = hash.substring(4, 6)
        return ResolvedMediaInfo(
            mediaUrl = "https://i.pinimg.com/originals/$p1/$p2/$p3/$hash.$ext",
            thumbnailUrl = normalized,
            cleanTitle = "$hash.$ext"
        )
    }

    private fun resolveYouTubeThumbnail(normalized: String): ResolvedMediaInfo? {
        val ytMatch = YOUTUBE_THUMB_REGEX.find(normalized) ?: return null
        val videoId = ytMatch.groupValues[1]
        val variant = ytMatch.groupValues[2]
        val masterVariant = when (variant) {
            "oar2" -> "oar2"
            "frame0" -> "maxresdefault"
            else -> variant
        }
        return ResolvedMediaInfo(
            mediaUrl = "https://i.ytimg.com/vi/$videoId/$masterVariant.jpg",
            thumbnailUrl = normalized,
            cleanTitle = "youtube_${videoId}_$variant.jpg"
        )
    }

    private fun resolveReddit(normalized: String): ResolvedMediaInfo? {
        if (!normalized.contains("preview.redd.it") && !normalized.contains("external-preview.redd.it")) {
            return null
        }
        val cleanPath = normalized.substringBefore("?")
        val fileName = cleanPath.substringAfterLast('/')
        val cleanTitle = runCatching { URLDecoder.decode(fileName, "UTF-8") }.getOrDefault(fileName)
        return ResolvedMediaInfo(
            mediaUrl = "https://i.redd.it/$fileName",
            thumbnailUrl = normalized,
            cleanTitle = cleanTitle
        )
    }

    private fun resolveImgur(normalized: String): ResolvedMediaInfo? {
        if (!normalized.contains("i.imgur.com/")) return null
        val cleanUrl = normalized.substringBefore("?")
        val fileName = cleanUrl.substringAfterLast('/')
        val baseId = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        val match = IMGUR_THUMB_REGEX.find(baseId) ?: return null
        val masterId = match.groupValues[1]
        val originalExt = if (ext.equals("webp", ignoreCase = true) || ext.isEmpty()) "jpg" else ext
        return ResolvedMediaInfo(
            mediaUrl = "https://i.imgur.com/$masterId.$originalExt",
            thumbnailUrl = normalized,
            cleanTitle = "$masterId.$originalExt"
        )
    }

    private fun resolveTwitter(normalized: String): ResolvedMediaInfo? {
        if (!normalized.contains("pbs.twimg.com/media/")) return null
        val noHash = normalized.substringBefore("#")
        val qIdx = noHash.indexOf('?')
        val base = if (qIdx == -1) noHash else noHash.substring(0, qIdx)
        val query = if (qIdx == -1) "" else noHash.substring(qIdx + 1)
        val formatParam = query.split("&").firstOrNull { it.startsWith("format=", ignoreCase = true) } ?: "format=jpg"
        val fileName = base.substringAfterLast('/')
        val ext = formatParam.substringAfter("format=").ifBlank { "jpg" }
        return ResolvedMediaInfo(
            mediaUrl = "$base?$formatParam&name=orig",
            thumbnailUrl = normalized,
            cleanTitle = "$fileName.$ext"
        )
    }

    private fun resolveWordPressPhoton(normalized: String): ResolvedMediaInfo? {
        if (!WP_PHOTON_PREFIX_REGEX.containsMatchIn(normalized)) return null
        val unwrapped = normalized.replace(WP_PHOTON_PREFIX_REGEX, "https://")
        val cleanOriginal = unwrapped.substringBefore("?")
        return ResolvedMediaInfo(
            mediaUrl = cleanOriginal,
            thumbnailUrl = normalized,
            cleanTitle = cleanOriginal.substringAfterLast('/')
        )
    }

    private fun resolveWordPressUploads(normalized: String): ResolvedMediaInfo? {
        if (!normalized.contains("/wp-content/uploads/")) return null
        val cleanUrl = normalized.substringBefore("?")
        if (!WP_RESIZE_REGEX.containsMatchIn(cleanUrl)) return null
        val masterUrl = WP_RESIZE_REGEX.replace(cleanUrl, ".$1")
        return ResolvedMediaInfo(
            mediaUrl = masterUrl,
            thumbnailUrl = normalized,
            cleanTitle = masterUrl.substringAfterLast('/')
        )
    }

    private fun resolveShopify(normalized: String): ResolvedMediaInfo? {
        if (!normalized.contains("cdn.shopify.com")) return null
        val cleanUrl = normalized.substringBefore("?")
        if (!SHOPIFY_RESIZE_REGEX.containsMatchIn(cleanUrl)) return null
        val masterUrl = SHOPIFY_RESIZE_REGEX.replace(cleanUrl, ".$1")
        return ResolvedMediaInfo(
            mediaUrl = masterUrl,
            thumbnailUrl = normalized,
            cleanTitle = masterUrl.substringAfterLast('/')
        )
    }

    private fun resolveGoogleUserContent(normalized: String): ResolvedMediaInfo? {
        if (!normalized.contains("googleusercontent.com")) return null
        val base = normalized.substringBefore("=")
        return ResolvedMediaInfo(
            mediaUrl = "$base=s0",
            thumbnailUrl = normalized,
            cleanTitle = base.substringAfterLast('/')
        )
    }

    private fun resolveMediaWiki(normalized: String): ResolvedMediaInfo? {
        val wmMatch = WIKIMEDIA_THUMB_REGEX.find(normalized)
        if (wmMatch != null) {
            val project = wmMatch.groupValues[1]
            val subpath = wmMatch.groupValues[2]
            val rawFileName = wmMatch.groupValues[3]
            return ResolvedMediaInfo(
                mediaUrl = "https://upload.wikimedia.org/wikipedia/$project/$subpath",
                thumbnailUrl = normalized,
                cleanTitle = decodeFileName(rawFileName)
            )
        }

        val wmTranscodeMatch = WIKIMEDIA_TRANSCODE_REGEX.find(normalized)
        if (wmTranscodeMatch != null) {
            val project = wmTranscodeMatch.groupValues[1]
            val subpath = wmTranscodeMatch.groupValues[2]
            val rawFileName = wmTranscodeMatch.groupValues[3]
            return ResolvedMediaInfo(
                mediaUrl = "https://upload.wikimedia.org/wikipedia/$project/$subpath",
                thumbnailUrl = normalized,
                cleanTitle = decodeFileName(rawFileName)
            )
        }

        if (normalized.contains("/thumb/")) {
            val mwMatch = MEDIAWIKI_GENERIC_THUMB_REGEX.find(normalized)
            if (mwMatch != null) {
                val uploadBase = mwMatch.groupValues[1]
                val subpath = mwMatch.groupValues[2]
                val rawFileName = mwMatch.groupValues[3]
                return ResolvedMediaInfo(
                    mediaUrl = "$uploadBase/$subpath",
                    thumbnailUrl = normalized,
                    cleanTitle = decodeFileName(rawFileName)
                )
            }
        }

        if (normalized.contains("/transcoded/")) {
            val mwTranscodeMatch = MEDIAWIKI_GENERIC_TRANSCODE_REGEX.find(normalized)
            if (mwTranscodeMatch != null) {
                val uploadBase = mwTranscodeMatch.groupValues[1]
                val subpath = mwTranscodeMatch.groupValues[2]
                val rawFileName = mwTranscodeMatch.groupValues[3]
                return ResolvedMediaInfo(
                    mediaUrl = "$uploadBase/$subpath",
                    thumbnailUrl = normalized,
                    cleanTitle = decodeFileName(rawFileName)
                )
            }
        }

        return null
    }

    private fun decodeFileName(rawFileName: String): String {
        return runCatching { URLDecoder.decode(rawFileName, "UTF-8") }.getOrDefault(rawFileName)
    }
}
