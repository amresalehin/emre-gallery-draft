package com.amresalehin.emregallery.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Chromium WebView-assisted media extraction coordinator.
 * Orchestrates offscreen WebView hosting, cookie synchronization, live network interception,
 * deep DOM/MutationObserver scanning, adaptive scrolling, and watchdog completion.
 */
class BrowserMediaExtractor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    @Volatile
    private var isCancelled = false

    fun startLiveExtraction(
        targetUrl: String,
        onStatusUpdate: (String) -> Unit,
        onMediaDiscovered: (ScrapedMediaItem) -> Unit,
        onFinished: () -> Unit
    ) {
        mainHandler.post {
            cleanup()
            isCancelled = false

            val normalizedUrl = normalizeHttpUrl(targetUrl)
            onStatusUpdate("Initializing Chromium browser engine...")

            try {
                val wv = BrowserWebViewHost.createOffscreenWebView(context)
                webView = wv

                BrowserCookieSync.syncJarToWebView(wv, normalizedUrl)

                val isExtractionCompleted = AtomicBoolean(false)
                fun safeComplete() {
                    if (isExtractionCompleted.compareAndSet(false, true)) {
                        onFinished()
                    }
                }

                val mediaTracker = BrowserDiscoveredMediaTracker(
                    normalizedBaseUrl = normalizedUrl,
                    isCancelled = { isCancelled },
                    onMediaDiscovered = onMediaDiscovered
                )
                val adaptiveScroller = BrowserAdaptiveScroller(
                    mainHandler = mainHandler,
                    isCancelled = { isCancelled }
                )

                wv.addJavascriptInterface(
                    EmreMediaBridge { rawUrl ->
                        mainHandler.post {
                            mediaTracker.buildAndDispatch(rawUrl, ExtractionSource.DOM_SCRAPE)
                        }
                    },
                    BRIDGE_NAME
                )

                wv.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (isCancelled) return
                        if (newProgress < 100) {
                            onStatusUpdate("Loading page: $newProgress% (monitoring network requests)...")
                        }
                    }
                }

                wv.webViewClient = BrowserExtractionWebViewClient(
                    normalizedUrl = normalizedUrl,
                    mainHandler = mainHandler,
                    mediaTracker = mediaTracker,
                    adaptiveScroller = adaptiveScroller,
                    isCancelled = { isCancelled },
                    onStatusUpdate = onStatusUpdate,
                    onComplete = ::safeComplete
                )

                scheduleWatchdogTimeout(
                    webView = wv,
                    normalizedUrl = normalizedUrl,
                    mediaTracker = mediaTracker,
                    isCompleted = { isExtractionCompleted.get() },
                    onStatusUpdate = onStatusUpdate,
                    onComplete = ::safeComplete
                )

                wv.loadUrl(normalizedUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Browser initialization failed", e)
                onStatusUpdate("Browser initialization failed: ${e.localizedMessage}")
                onFinished()
            }
        }
    }

    private fun scheduleWatchdogTimeout(
        webView: WebView,
        normalizedUrl: String,
        mediaTracker: BrowserDiscoveredMediaTracker,
        isCompleted: () -> Boolean,
        onStatusUpdate: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        val watchdogRunnable = Runnable {
            if (!isCancelled && !isCompleted()) {
                Log.i(TAG, "Extraction watchdog timeout reached, scanning current page DOM...")
                onStatusUpdate("Completing page scan...")
                webView.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { fallbackJson ->
                    val fallbackEntries = MediaCaptureEngine.parseJsDomScanResult(fallbackJson, normalizedUrl)
                    mediaTracker.recordAndDispatchAll(fallbackEntries)
                    onComplete()
                }
            }
        }
        mainHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)
    }

    private fun normalizeHttpUrl(targetUrl: String): String {
        return if (!targetUrl.startsWith("http://", ignoreCase = true) &&
            !targetUrl.startsWith("https://", ignoreCase = true)
        ) {
            "https://$targetUrl"
        } else {
            targetUrl
        }
    }

    class EmreMediaBridge(private val onMediaFound: (String) -> Unit) {
        @JavascriptInterface
        fun reportMedia(url: String?) {
            if (!url.isNullOrBlank()) {
                onMediaFound(url)
            }
        }
    }

    fun cleanup() {
        isCancelled = true
        mainHandler.removeCallbacksAndMessages(null)
        BrowserWebViewHost.destroyWebView(webView, BRIDGE_NAME)
        webView = null
    }

    private companion object {
        const val TAG = "BrowserMediaExtractor"
        const val BRIDGE_NAME = "EmreBridge"
        const val WATCHDOG_TIMEOUT_MS = 14000L
    }
}
