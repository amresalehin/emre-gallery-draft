package com.amresalehin.emregallery.data

import org.json.JSONArray
import org.json.JSONTokener

/**
 * Constructs normalized [ScrapedMediaItem] instances from raw discovered URLs and parses
 * JSON payloads emitted by [JsDomScanScript].
 */
internal object CapturedMediaFactory {

    fun buildMediaItem(
        rawUrl: String,
        source: ExtractionSource,
        baseUrl: String,
        poster: String? = null,
        resolutionHint: String? = null,
        mimeHint: String? = null,
        indexHint: Int = 0
    ): ScrapedMediaItem? {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("blob:", ignoreCase = true)) return null
        if (trimmed.startsWith("data:", ignoreCase = true) && !DataUriParser.isSubstantialDataUri(trimmed)) return null
        if (MediaUrlValidator.isMediaFragment(trimmed)) return null
        if (AdBlockEngine.isAdOrTracker(trimmed)) return null

        val resolved = WebUrlUtils.resolveUrl(trimmed, baseUrl) ?: return null
        if (!MediaUrlValidator.isValidMedia(resolved)) return null
        if (MediaUrlValidator.isMediaFragment(resolved)) return null

        val resolvedInfo = OriginalMediaResolver.resolve(resolved)
        val cleanedUrl = WebUrlUtils.cleanMediaUrl(resolvedInfo.mediaUrl)
        if (MediaUrlValidator.isMediaFragment(cleanedUrl)) return null

        val classification = MediaClassifier.classify(cleanedUrl, mimeHint)
        val isVidAudStream = classification.isVideo || classification.isAudio || classification.isStream
        val title = resolvedInfo.cleanTitle
            ?: MediaClassifier.deriveFileName(cleanedUrl, classification.format, indexHint)

        if (ContentAwareMediaFilter.isUiChromeUrl(
                url = cleanedUrl,
                title = title,
                isVideoOrAudioOrStream = isVidAudStream
            )
        ) {
            return null
        }

        val thumb = resolvedInfo.thumbnailUrl
            ?: poster
            ?: if (classification.category == MediaCategory.IMAGES) resolved else poster

        val headers = buildDownloadHeaders(baseUrl)
        val metadataMap = buildMetadataMap(baseUrl, resolutionHint, mimeHint)

        return ScrapedMediaItem(
            id = "captured_${indexHint}_${System.currentTimeMillis()}_${cleanedUrl.hashCode()}",
            url = cleanedUrl,
            title = title,
            isVideo = classification.isVideo,
            isAudio = classification.isAudio,
            isStream = classification.isStream,
            category = classification.category,
            format = classification.format,
            thumbnailUrl = thumb,
            source = source.label,
            mimeType = classification.mimeType,
            resolutionHint = resolutionHint,
            provenance = MediaProvenance(
                extractorEngine = "MediaCaptureEngine (${source.label})",
                pageUrl = baseUrl,
                domSelector = if (source == ExtractionSource.NETWORK_INTERCEPT) "network_request" else null
            ),
            metadata = metadataMap,
            downloadHeaders = headers
        )
    }

    fun parseJsDomScanResult(
        resultJson: String?,
        baseUrl: String
    ): List<ScrapedMediaItem> {
        if (resultJson.isNullOrBlank() || resultJson == "null") return emptyList()

        return try {
            val unescaped = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                JSONTokener(resultJson).nextValue().toString()
            } else {
                resultJson
            }

            val jsonArray = JSONArray(unescaped)
            val results = mutableListOf<ScrapedMediaItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rawUrl = obj.optString("u", "")
                if (rawUrl.isNotBlank()) {
                    val sTag = obj.optString("s", "DOM")
                    val source = when (sTag) {
                        "CSS" -> ExtractionSource.CSS_BACKGROUND
                        "SRCSET" -> ExtractionSource.SRCSET
                        "IFRAME" -> ExtractionSource.IFRAME
                        else -> ExtractionSource.DOM_SCRAPE
                    }
                    val poster = obj.optString("p", "").ifBlank { null }
                    val res = obj.optString("r", "").ifBlank { null }
                    val item = buildMediaItem(
                        rawUrl = rawUrl,
                        source = source,
                        baseUrl = baseUrl,
                        poster = poster,
                        resolutionHint = res,
                        indexHint = i
                    )
                    if (item != null) {
                        results.add(item)
                    }
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildDownloadHeaders(baseUrl: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["Referer"] = baseUrl
        headers["User-Agent"] = OkHttpDownloader.DEFAULT_USER_AGENT
        val cookieStr = OkHttpDownloader.sharedCookieJar.getCookieString(baseUrl)
        if (cookieStr.isNotBlank()) {
            headers["Cookie"] = cookieStr
        }
        return headers
    }

    private fun buildMetadataMap(
        baseUrl: String,
        resolutionHint: String?,
        mimeHint: String?
    ): Map<String, String> {
        val metadataMap = mutableMapOf<String, String>()
        resolutionHint?.let { metadataMap["resolution"] = it }
        mimeHint?.let { metadataMap["mime_hint"] = it }
        metadataMap["source_url"] = baseUrl
        return metadataMap
    }
}
