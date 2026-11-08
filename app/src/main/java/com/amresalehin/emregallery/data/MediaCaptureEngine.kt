package com.amresalehin.emregallery.data

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Unified Media Capture Engine.
 * Single source of truth for:
 * 1. Network request interception & stream/media classification.
 * 2. Deep JavaScript DOM, srcset, CSS background, and JSON-LD schema inspection.
 * 3. MutationObserver injection for dynamic DOM change detection.
 * 4. Adaptive scrolling logic.
 * 5. Media normalization, deduplication, and ScrapedMediaItem construction with headers.
 */
object MediaCaptureEngine {

    /**
     * Shared JavaScript DOM scan script that inspects:
     * - <img>, <picture source>, data-src, data-original, srcset, data-lazy-src
     * - <video>, <video source>, poster
     * - <audio>, <audio source>
     * - Schema.org JSON-LD (ImageObject, VideoObject, contentUrl)
     * - Content container CSS background-image
     * - IFrames pointing directly to media

    fun getJsDomScanScript(): String = getJsDomScanScriptImpl()
    fun getMutationObserverScript(): String = getMutationObserverScriptImpl()
    fun getAdaptiveScrollScript(): String = getAdaptiveScrollScriptImpl()

    fun buildMediaItem(
        rawUrl: String,
        source: ExtractionSource,
        baseUrl: String,
        poster: String? = null,
        resolutionHint: String? = null,
        mimeHint: String? = null,
        indexHint: Int = 0,
        titleHint: String? = null,
        domSelector: String? = null,
        declaredWidth: Int? = null,
        declaredHeight: Int? = null,
        customId: String? = null
    ): ScrapedMediaItem? {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("blob:", ignoreCase = true)) return null
        if (trimmed.startsWith("data:", ignoreCase = true) && !MediaClassifier.isSubstantialDataUri(trimmed)) return null
        if (MediaClassifier.isMediaFragment(trimmed)) return null
        if (AdBlockEngine.isAdOrTracker(trimmed)) return null

        val resolved = MediaClassifier.resolveUrl(trimmed, baseUrl) ?: return null
        if (!MediaClassifier.isValidMedia(resolved)) return null
        if (MediaClassifier.isMediaFragment(resolved)) return null

        val resolvedInfo = MediaClassifier.resolveOriginalMedia(resolved)
        val cleanedUrl = MediaClassifier.cleanMediaUrl(resolvedInfo.mediaUrl)
        if (MediaClassifier.isMediaFragment(cleanedUrl)) return null

        val classification = MediaClassifier.classify(cleanedUrl, mimeHint)
        val isVidAudStream = classification.isVideo || classification.isAudio || classification.isStream
        val title = resolvedInfo.cleanTitle
            ?: titleHint?.takeIf { it.isNotBlank() }
            ?: MediaClassifier.deriveFileName(cleanedUrl, classification.format, indexHint)

        if (ContentAwareMediaFilter.isUiChromeUrl(
                url = cleanedUrl,
                title = title,
                width = declaredWidth,
                height = declaredHeight,
                isVideoOrAudioOrStream = isVidAudStream
            )
        ) {
            return null
        }

        val thumb = resolvedInfo.thumbnailUrl ?: poster ?: if (classification.category == MediaCategory.IMAGES) resolved else poster

        val headers = mutableMapOf<String, String>()
        headers["Referer"] = baseUrl
        headers["User-Agent"] = OkHttpDownloader.DEFAULT_USER_AGENT
        val cookieStr = OkHttpDownloader.sharedCookieJar.getCookieString(baseUrl)
        if (cookieStr.isNotBlank()) {
            headers["Cookie"] = cookieStr
        }

        val metadataMap = mutableMapOf<String, String>()
        resolutionHint?.let { metadataMap["resolution"] = it }
        mimeHint?.let { metadataMap["mime_hint"] = it }
        metadataMap["source_url"] = baseUrl

        val engineLabel = when (source) {
            ExtractionSource.STATIC_HTML -> "Static HTML"
            ExtractionSource.NETWORK_INTERCEPT -> "Browser Chromium (Network Intercept)"
            ExtractionSource.DOM_SCRAPE -> "Static HTML (DOM Scrape)"
            ExtractionSource.SRCSET -> "Static HTML (Srcset)"
            ExtractionSource.CSS_BACKGROUND -> "Static HTML (CSS Background)"
            ExtractionSource.IFRAME -> "Static HTML (iFrame)"
            ExtractionSource.GALLERY_DL -> "gallery-dl"
        }

        val itemId = customId ?: "captured_${indexHint}_${System.currentTimeMillis()}_${cleanedUrl.hashCode()}"

        return ScrapedMediaItem(
            id = itemId,
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
                extractorEngine = engineLabel,
                pageUrl = baseUrl,
                domSelector = domSelector ?: if (source == ExtractionSource.NETWORK_INTERCEPT) "network_request" else null
            ),
            metadata = metadataMap,
            downloadHeaders = headers
        )
    }

    /**
     * Unified network request interception for both BrowserMediaExtractor and DirectBrowserView.
     */
    fun shouldInterceptRequest(
        request: WebResourceRequest?,
        baseUrl: String,
        onMediaDiscovered: (ScrapedMediaItem) -> Unit
    ): WebResourceResponse? {
        if (request == null) return null
        val reqUrl = request.url.toString()

        // 1. Instant zero-payload response for ad/tracker networks
        if (AdBlockEngine.isAdOrTracker(reqUrl)) {
            return AdBlockEngine.createEmptyResponse()
        }

        // 2. Reject streaming slice fragments (.ts slices, dash segments)
        if (MediaClassifier.isMediaFragment(reqUrl)) {
            return null
        }

        val reqLower = reqUrl.lowercase()
        val cleanReqPath = reqLower.substringBefore("?").substringBefore("#")
        val headers = request.requestHeaders ?: emptyMap()
        val acceptHeader = headers["Accept"]?.lowercase() ?: ""

        // Reject non-media API, logging, telemetry endpoints
        val isNonMediaEndpoint = cleanReqPath.contains("/api/") || cleanReqPath.contains("/apis/") ||
                cleanReqPath.contains("/youtubei/") || cleanReqPath.contains("/stats/") ||
                cleanReqPath.contains("/log") || cleanReqPath.contains("/logging") ||
                cleanReqPath.contains("/ptracking") || cleanReqPath.contains("/beacon") ||
                cleanReqPath.contains("/telemetry") || cleanReqPath.contains("/metrics") ||
                cleanReqPath.contains("generate_204") || cleanReqPath.contains("/recaptcha") ||
                cleanReqPath.contains("/embed/") || cleanReqPath.contains("play.google.com/log") ||
                cleanReqPath.contains("youtube.com/api/") || cleanReqPath.contains("/wiki/") ||
                cleanReqPath.contains("/geohack") || cleanReqPath.contains("centralautologin")

        if (isNonMediaEndpoint) return null

        val isNonMediaPage = cleanReqPath.endsWith(".html") || cleanReqPath.endsWith(".htm") ||
                cleanReqPath.endsWith(".php") || cleanReqPath.endsWith(".js") || cleanReqPath.endsWith(".css") ||
                cleanReqPath.endsWith(".json") || cleanReqPath.endsWith(".xml") || cleanReqPath.endsWith(".txt") ||
                cleanReqPath.endsWith(".do") || cleanReqPath.endsWith(".asp") || cleanReqPath.endsWith(".aspx")

        if (isNonMediaPage) return null

        val isStream = cleanReqPath.endsWith(".m3u8") || cleanReqPath.endsWith(".mpd") ||
                cleanReqPath.contains("chunklist") || acceptHeader.contains("mpegurl") ||
                acceptHeader.contains("dash+xml")

        val isVideo = isStream || cleanReqPath.endsWith(".mp4") || cleanReqPath.endsWith(".webm") ||
                cleanReqPath.endsWith(".mov") || cleanReqPath.endsWith(".mkv") ||
                cleanReqPath.contains("/videoplayback") || cleanReqPath.contains("googlevideo.com") ||
                acceptHeader.contains("video/")

        val isAudio = cleanReqPath.endsWith(".mp3") || cleanReqPath.endsWith(".m4a") ||
                cleanReqPath.endsWith(".aac") || cleanReqPath.endsWith(".wav") ||
                cleanReqPath.endsWith(".ogg") || cleanReqPath.endsWith(".flac") ||
                cleanReqPath.endsWith(".opus") || acceptHeader.contains("audio/")

        val hasImageExt = cleanReqPath.endsWith(".jpg") || cleanReqPath.endsWith(".jpeg") ||
                cleanReqPath.endsWith(".png") || cleanReqPath.endsWith(".webp") ||
                cleanReqPath.endsWith(".gif") || cleanReqPath.endsWith(".avif") ||
                cleanReqPath.endsWith(".svg") || cleanReqPath.endsWith(".bmp") ||
                cleanReqPath.contains("/vi/") || cleanReqPath.contains("/vi_webp/") ||
                Regex("""[?&](?:format|fmt|ext)=(?:jpg|jpeg|png|webp|avif)""").containsMatchIn(reqLower)

        val isImage = hasImageExt && !acceptHeader.contains("text/html")

        if (isVideo || isAudio || isImage) {
            val cleanReqUrl = if (isVideo) MediaClassifier.cleanMediaUrl(reqUrl) else reqUrl
            val ytPoster = if (isVideo) {
                MediaClassifier.extractYouTubeVideoId(baseUrl)?.let { ytId ->
                    "https://i.ytimg.com/vi/$ytId/maxresdefault.jpg"
                }
            } else null
            val item = buildMediaItem(
                rawUrl = cleanReqUrl,
                source = ExtractionSource.NETWORK_INTERCEPT,
                baseUrl = baseUrl,
                poster = ytPoster,
                mimeHint = if (cleanReqPath.contains("/videoplayback")) "video/mp4" else null
            )
            if (item != null) {
                onMediaDiscovered(item)
            }
        }

        return null
    }

    /**
     * Parses the JSON array returned by getJsDomScanScript().
     */
    fun parseJsDomScanResult(
        resultJson: String?,
        baseUrl: String
    ): List<ScrapedMediaItem> {
        if (resultJson.isNullOrBlank() || resultJson == "null") return emptyList()

        return try {
            val unescaped = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                org.json.JSONTokener(resultJson).nextValue().toString()
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
}
