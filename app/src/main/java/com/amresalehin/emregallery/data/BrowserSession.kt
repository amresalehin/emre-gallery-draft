package com.amresalehin.emregallery.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Tab-specific media registry for interactive 1DM-style browser sessions.
 */
class BrowserTabSession(
    val tabId: String = "tab_main",
    initialUrl: String = ""
) {
    private val _url = MutableStateFlow(initialUrl)
    val url: StateFlow<String> = _url.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val itemsLock = Any()
    private val candidatesMap = LinkedHashMap<String, ScrapedMediaItem>()

    private val _detectedItems = MutableStateFlow<List<ScrapedMediaItem>>(emptyList())
    val detectedItems: StateFlow<List<ScrapedMediaItem>> = _detectedItems.asStateFlow()

    private val _detectedCount = MutableStateFlow(0)
    val detectedCount: StateFlow<Int> = _detectedCount.asStateFlow()

    fun updateNavigation(newUrl: String, newTitle: String? = null) {
        val cleanUrl = newUrl.trim()
        if (cleanUrl != _url.value) {
            // Discovered candidates belong to the current page, not the previous URL.
            // Capture the old URL before replacing it, then reset the per-tab registry.
            clearMedia()
            _url.value = cleanUrl
            _title.value = ""
            DownloaderSession.updateBrowserPage(cleanUrl)
        }
        if (!newTitle.isNullOrBlank()) {
            _title.value = newTitle
        }
    }

    fun registerMedia(item: ScrapedMediaItem) {
        registerMediaBatch(listOf(item))
    }

    fun registerMediaBatch(items: List<ScrapedMediaItem>, pageTitleHint: String? = null) {
        if (items.isEmpty()) return
        synchronized(itemsLock) {
            val modified = MediaNormalizer.mergeCandidates(candidatesMap, items)
            if (modified) {
                val list = candidatesMap.values.toList()
                _detectedItems.value = list
                _detectedCount.value = list.size
                DownloaderSession.addDiscoveredItems(list, pageTitleHint ?: _title.value)
            }
        }
        if (!pageTitleHint.isNullOrBlank()) {
            _title.value = pageTitleHint
        }
    }

    fun getDetectedMedia(): List<ScrapedMediaItem> {
        synchronized(itemsLock) {
            return candidatesMap.values.toList()
        }
    }

    fun clearMedia() {
        synchronized(itemsLock) {
            candidatesMap.clear()
            _detectedItems.value = emptyList()
            _detectedCount.value = 0
        }
    }
}

/**
 * BrowserSession:
 * 1. Hosts the per-tab media registry (1DM-style continuous detection without rescanning).
 * 2. Provides consolidated headless browser extraction for URL extraction routing fallback.
 */
object BrowserSession {

    private val tabRegistry = ConcurrentHashMap<String, BrowserTabSession>()

    fun getTabSession(tabId: String = "tab_main"): BrowserTabSession {
        return tabRegistry.getOrPut(tabId) { BrowserTabSession(tabId = tabId) }
    }

    fun registerMedia(tabId: String, item: ScrapedMediaItem) {
        getTabSession(tabId).registerMedia(item)
    }

    fun registerMediaBatch(tabId: String, items: List<ScrapedMediaItem>, pageTitle: String? = null) {
        getTabSession(tabId).registerMediaBatch(items, pageTitle)
    }

    fun getDetectedMedia(tabId: String): List<ScrapedMediaItem> {
        return getTabSession(tabId).getDetectedMedia()
    }

    fun clearTabMedia(tabId: String) {
        getTabSession(tabId).clearMedia()
    }

    // =========================================================================
    // Headless Browser Extraction Engine (used for URL Routing Fallback)
    // =========================================================================

    internal val mainHandler = Handler(Looper.getMainLooper())

    class EmreMediaBridge(private val onMediaFound: (String) -> Unit) {
        @android.webkit.JavascriptInterface
        fun reportMedia(url: String?) {
            if (!url.isNullOrBlank()) {
                onMediaFound(url)
            }
        }
    }


    data class BackgroundCookieResult(
        val success: Boolean,
        val cookies: String?,
        val requiresSignIn: Boolean,
        val signInUrl: String?
    )

    @SuppressLint("SetJavaScriptEnabled")

    suspend fun extractHeadless(
        context: Context,
        targetUrl: String,
        timeoutMs: Long = 15_000L,
        onStatus: (String) -> Unit = {},
        onBatchDiscovered: (List<ScrapedMediaItem>, String?) -> Unit = { _, _ -> }
    ): ScrapeResult = extractHeadlessImpl(context, targetUrl, timeoutMs, onStatus, onBatchDiscovered)

    suspend fun fetchCookiesInBackground(
        context: Context,
        targetUrl: String,
        timeoutMs: Long = 8_000L
    ): BackgroundCookieResult = fetchCookiesInBackgroundImpl(context, targetUrl, timeoutMs)
}
