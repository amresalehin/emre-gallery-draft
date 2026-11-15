package com.amresalehin.emregallery.data

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/**
 * Inspects WebView network requests to block ad/tracker payloads and capture media URLs.
 */
internal object NetworkMediaInterceptor {

    private val imageQueryParamRegex =
        Regex("""[?&](?:format|fmt|ext)=(?:jpg|jpeg|png|webp|avif)""")

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
        if (MediaUrlValidator.isMediaFragment(reqUrl)) {
            return null
        }

        val reqLower = reqUrl.lowercase()
        val cleanReqPath = reqLower.substringBefore("?").substringBefore("#")
        val headers = request.requestHeaders ?: emptyMap()
        val acceptHeader = headers["Accept"]?.lowercase() ?: ""

        if (isNonMediaEndpoint(cleanReqPath) || isNonMediaPage(cleanReqPath)) {
            return null
        }

        val isVideo = isStreamOrVideoRequest(cleanReqPath, acceptHeader)
        val isAudio = isAudioRequest(cleanReqPath, acceptHeader)
        val isImage = isImageRequest(cleanReqPath, reqLower, acceptHeader)

        if (isVideo || isAudio || isImage) {
            val cleanReqUrl = if (isVideo) WebUrlUtils.cleanMediaUrl(reqUrl) else reqUrl
            val item = CapturedMediaFactory.buildMediaItem(
                rawUrl = cleanReqUrl,
                source = ExtractionSource.NETWORK_INTERCEPT,
                baseUrl = baseUrl,
                mimeHint = null
            )
            if (item != null) {
                onMediaDiscovered(item)
            }
        }

        return null
    }

    private fun isNonMediaEndpoint(cleanReqPath: String): Boolean {
        return cleanReqPath.contains("/api/") || cleanReqPath.contains("/apis/") ||
            cleanReqPath.contains("/youtubei/") || cleanReqPath.contains("/stats/") ||
            cleanReqPath.contains("/log") || cleanReqPath.contains("/logging") ||
            cleanReqPath.contains("/ptracking") || cleanReqPath.contains("/beacon") ||
            cleanReqPath.contains("/telemetry") || cleanReqPath.contains("/metrics") ||
            cleanReqPath.contains("generate_204") || cleanReqPath.contains("/recaptcha") ||
            cleanReqPath.contains("/embed/") || cleanReqPath.contains("play.google.com/log") ||
            cleanReqPath.contains("youtube.com/api/") || cleanReqPath.contains("/wiki/") ||
            cleanReqPath.contains("/geohack") || cleanReqPath.contains("centralautologin")
    }

    private fun isNonMediaPage(cleanReqPath: String): Boolean {
        return cleanReqPath.endsWith(".html") || cleanReqPath.endsWith(".htm") ||
            cleanReqPath.endsWith(".php") || cleanReqPath.endsWith(".js") || cleanReqPath.endsWith(".css") ||
            cleanReqPath.endsWith(".json") || cleanReqPath.endsWith(".xml") || cleanReqPath.endsWith(".txt") ||
            cleanReqPath.endsWith(".do") || cleanReqPath.endsWith(".asp") || cleanReqPath.endsWith(".aspx")
    }

    private fun isStreamOrVideoRequest(cleanReqPath: String, acceptHeader: String): Boolean {
        val isStream = cleanReqPath.endsWith(".m3u8") || cleanReqPath.endsWith(".mpd") ||
            cleanReqPath.contains("chunklist") || acceptHeader.contains("mpegurl") ||
            acceptHeader.contains("dash+xml")

        return isStream || cleanReqPath.endsWith(".mp4") || cleanReqPath.endsWith(".webm") ||
            cleanReqPath.endsWith(".mov") || cleanReqPath.endsWith(".mkv") ||
            acceptHeader.contains("video/")
    }

    private fun isAudioRequest(cleanReqPath: String, acceptHeader: String): Boolean {
        return cleanReqPath.endsWith(".mp3") || cleanReqPath.endsWith(".m4a") ||
            cleanReqPath.endsWith(".aac") || cleanReqPath.endsWith(".wav") ||
            cleanReqPath.endsWith(".ogg") || cleanReqPath.endsWith(".flac") ||
            cleanReqPath.endsWith(".opus") || acceptHeader.contains("audio/")
    }

    private fun isImageRequest(cleanReqPath: String, reqLower: String, acceptHeader: String): Boolean {
        val hasImageExt = cleanReqPath.endsWith(".jpg") || cleanReqPath.endsWith(".jpeg") ||
            cleanReqPath.endsWith(".png") || cleanReqPath.endsWith(".webp") ||
            cleanReqPath.endsWith(".gif") || cleanReqPath.endsWith(".avif") ||
            cleanReqPath.endsWith(".svg") || cleanReqPath.endsWith(".bmp") ||
            cleanReqPath.contains("/vi/") || cleanReqPath.contains("/vi_webp/") ||
            imageQueryParamRegex.containsMatchIn(reqLower)

        return hasImageExt && !acceptHeader.contains("text/html")
    }
}
