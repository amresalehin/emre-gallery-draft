package com.amresalehin.emregallery.data

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/**
 * Unified Media Capture Engine facade.
 * Delegates script generation, network request interception, DOM scan parsing,
 * and normalized [ScrapedMediaItem] construction to focused components.
 */
object MediaCaptureEngine {

    /**
     * Shared JavaScript DOM scan script inspecting images, videos, audio,
     * JSON-LD schemas, CSS backgrounds, and media iframes.
     */
    fun getJsDomScanScript(): String = JsDomScanScript.build()

    /**
     * Shared JavaScript MutationObserver injection for dynamic DOM additions.
     */
    fun getMutationObserverScript(): String = BrowserRuntimeScripts.mutationObserverScript()

    /**
     * Shared JavaScript adaptive scrolling script.
     */
    fun getAdaptiveScrollScript(): String = BrowserRuntimeScripts.adaptiveScrollScript()

    /**
     * Builds and validates a ScrapedMediaItem with provenance, clean titles,
     * download headers (User-Agent, Referer, Cookie), and thumbnail classification.
     */
    fun buildMediaItem(
        rawUrl: String,
        source: ExtractionSource,
        baseUrl: String,
        poster: String? = null,
        resolutionHint: String? = null,
        mimeHint: String? = null,
        indexHint: Int = 0
    ): ScrapedMediaItem? {
        return CapturedMediaFactory.buildMediaItem(
            rawUrl = rawUrl,
            source = source,
            baseUrl = baseUrl,
            poster = poster,
            resolutionHint = resolutionHint,
            mimeHint = mimeHint,
            indexHint = indexHint
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
        return NetworkMediaInterceptor.shouldInterceptRequest(
            request = request,
            baseUrl = baseUrl,
            onMediaDiscovered = onMediaDiscovered
        )
    }

    /**
     * Parses the JSON array returned by getJsDomScanScript().
     */
    fun parseJsDomScanResult(
        resultJson: String?,
        baseUrl: String
    ): List<ScrapedMediaItem> {
        return CapturedMediaFactory.parseJsDomScanResult(
            resultJson = resultJson,
            baseUrl = baseUrl
        )
    }
}
