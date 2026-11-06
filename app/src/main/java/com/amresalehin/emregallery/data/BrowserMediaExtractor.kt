package com.amresalehin.emregallery.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL

/**
 * Chromium WebView-assisted media extraction engine.
 * Powered by unified MediaCaptureEngine:
 * 1. Live network request interception.
 * 2. Deep JavaScript DOM, computed CSS, srcset, schema.org JSON-LD.
 * 3. MutationObserver dynamic DOM change detection.
 * 4. Adaptive viewport scrolling.
 * 5. Offscreen layout with real viewport dimensions, error resilience, and watchdog timeout.
 */
class BrowserMediaExtractor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    @Volatile
    private var isCancelled = false

    private fun findActivity(ctx: Context): Activity? {
        var current: Context? = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun startLiveExtraction(
        targetUrl: String,
        onStatusUpdate: (String) -> Unit,
        onMediaDiscovered: (ScrapedMediaItem) -> Unit,
        onFinished: () -> Unit
    ) {
        mainHandler.post {
            cleanup()
            isCancelled = false

            val normalizedUrl = if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
                "https://$targetUrl"
            } else {
                targetUrl
            }

            val domain = runCatching { URL(normalizedUrl).host }.getOrDefault("")
            onStatusUpdate("Initializing Chromium browser engine...")

            try {
                val act = findActivity(context)
                val wv = if (act != null) WebView(act) else WebView(context)
                webView = wv

                // Sync cookies bidirectionally with CookieManager before navigation
                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(wv, true)
                    val existingCookieStr = OkHttpDownloader.sharedCookieJar.getCookieString(normalizedUrl)
                    if (existingCookieStr.isNotBlank()) {
                        for (c in existingCookieStr.split(";")) {
                            val trimmed = c.trim()
                            if (trimmed.isNotEmpty()) {
                                cookieManager.setCookie(normalizedUrl, trimmed)
                            }
                        }
                        cookieManager.flush()
                    }
                } catch (_: Throwable) {}

                // Measure and layout so modern DOM scripts have real viewport dimensions
                val displayMetrics = context.resources.displayMetrics
                val viewWidth = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
                val viewHeight = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 1920

                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    val root = act.findViewById<ViewGroup>(android.R.id.content)
                    if (root != null) {
                        wv.alpha = 0.01f
                        wv.translationX = -9999f
                        val lp = ViewGroup.LayoutParams(viewWidth, viewHeight)
                        root.addView(wv, lp)
                    }
                }

                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, viewWidth, viewHeight)

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = OkHttpDownloader.DEFAULT_USER_AGENT
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                wv.onResume()
                wv.resumeTimers()

                val seenMap = mutableMapOf<String, ScrapedMediaItem>()
                var isExtractionCompleted = false

                fun safeComplete() {
                    if (isExtractionCompleted) return
                    isExtractionCompleted = true
                    onFinished()
                }

                fun dispatchMediaItem(
                    rawUrl: String,
                    source: ExtractionSource,
                    poster: String? = null,
                    resolution: String? = null,
                    mimeHint: String? = null
                ) {
                    if (isCancelled) return
                    val candidateItem = MediaCaptureEngine.buildMediaItem(
                        rawUrl = rawUrl,
                        source = source,
                        baseUrl = normalizedUrl,
                        poster = poster,
                        resolutionHint = resolution,
                        mimeHint = mimeHint,
                        indexHint = seenMap.size
                    ) ?: return

                    val key = MediaClassifier.canonicalKey(candidateItem.url)
                    synchronized(seenMap) {
                        val existing = seenMap[key]
                        if (existing != null && MediaClassifier.compareMediaQuality(candidateItem, existing) <= 0) {
                            return
                        }
                        seenMap[key] = candidateItem
                    }

                    onMediaDiscovered(candidateItem)
                    DownloaderSession.addDiscoveredItem(candidateItem)
                }

                wv.addJavascriptInterface(
                    EmreMediaBridge { rawUrl ->
                        mainHandler.post {
                            dispatchMediaItem(rawUrl, ExtractionSource.DOM_SCRAPE)
                        }
                    },
                    "EmreBridge"
                )

                wv.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (isCancelled) return
                        if (newProgress < 100) {
                            onStatusUpdate("Loading page: $newProgress% (monitoring network requests)...")
                        }
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (isCancelled || request == null) return null
                        return MediaCaptureEngine.shouldInterceptRequest(
                            request = request,
                            baseUrl = normalizedUrl,
                            onMediaDiscovered = { item ->
                                mainHandler.post {
                                    if (isCancelled) return@post
                                    val key = MediaClassifier.canonicalKey(item.url)
                                    synchronized(seenMap) {
                                        val existing = seenMap[key]
                                        if (existing != null && MediaClassifier.compareMediaQuality(item, existing) <= 0) {
                                            return@synchronized
                                        }
                                        seenMap[key] = item
                                    }
                                    onMediaDiscovered(item)
                                    DownloaderSession.addDiscoveredItem(item)
                                }
                            }
                        )
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val reqUrl = request?.url?.toString() ?: return false
                        val lower = reqUrl.lowercase()
                        if (!lower.startsWith("http://") && !lower.startsWith("https://") &&
                            !lower.startsWith("about:") && !lower.startsWith("data:")
                        ) {
                            return true
                        }
                        return false
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val reqUrl = url ?: return false
                        val lower = reqUrl.lowercase()
                        if (!lower.startsWith("http://") && !lower.startsWith("https://") &&
                            !lower.startsWith("about:") && !lower.startsWith("data:")
                        ) {
                            return true
                        }
                        return false
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        Log.w("BrowserMediaExtractor", "Page error: $errorCode $description on $failingUrl")
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            Log.w("BrowserMediaExtractor", "Main frame error: ${error?.errorCode} ${error?.description}")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        if (request?.isForMainFrame == true) {
                            Log.w("BrowserMediaExtractor", "HTTP error on main frame: ${errorResponse?.statusCode}")
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed()
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        Log.w("BrowserMediaExtractor", "Render process gone (didCrash=${detail?.didCrash()})")
                        mainHandler.post {
                            onStatusUpdate("Browser renderer process recovered")
                            safeComplete()
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (isCancelled) return
                        onStatusUpdate("Browser loaded page. Active network interception listening...")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (isCancelled) return
                        try {
                            val cm = CookieManager.getInstance()
                            cm.flush()
                            val latestCookieStr = cm.getCookie(normalizedUrl)
                            if (!latestCookieStr.isNullOrBlank()) {
                                val httpUrl = normalizedUrl.toHttpUrlOrNull()
                                if (httpUrl != null) {
                                    val parsed = latestCookieStr.split(";").mapNotNull { part ->
                                        val trimmed = part.trim()
                                        if (trimmed.isNotEmpty()) Cookie.parse(httpUrl, trimmed) else null
                                    }
                                    if (parsed.isNotEmpty()) {
                                        OkHttpDownloader.sharedCookieJar.saveFromResponse(httpUrl, parsed)
                                    }
                                }
                            }
                        } catch (_: Throwable) {}

                        onStatusUpdate("Scanning DOM, computed CSS, and dynamic media tags...")

                        // Setup MutationObserver
                        wv.evaluateJavascript(MediaCaptureEngine.getMutationObserverScript(), null)

                        // 1. Run deep DOM scan
                        wv.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { rawJson ->
                            val rawEntries = MediaCaptureEngine.parseJsDomScanResult(rawJson, normalizedUrl)
                            for (item in rawEntries) {
                                val key = MediaClassifier.canonicalKey(item.url)
                                synchronized(seenMap) {
                                    val existing = seenMap[key]
                                    if (existing != null && MediaClassifier.compareMediaQuality(item, existing) <= 0) {
                                        continue
                                    }
                                    seenMap[key] = item
                                }
                                onMediaDiscovered(item)
                                DownloaderSession.addDiscoveredItem(item)
                            }

                            // 2. Perform adaptive scrolling
                            if (!isCancelled) {
                                onStatusUpdate("Auto-scrolling page to trigger lazy-loaded media...")
                                executeAdaptiveScrollSequence(wv) {
                                    if (!isCancelled) {
                                        wv.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { postScrollJson ->
                                            val postEntries = MediaCaptureEngine.parseJsDomScanResult(postScrollJson, normalizedUrl)
                                            for (item in postEntries) {
                                                val key = MediaClassifier.canonicalKey(item.url)
                                                synchronized(seenMap) {
                                                    val existing = seenMap[key]
                                                    if (existing != null && MediaClassifier.compareMediaQuality(item, existing) <= 0) {
                                                        continue
                                                    }
                                                    seenMap[key] = item
                                                }
                                                onMediaDiscovered(item)
                                                DownloaderSession.addDiscoveredItem(item)
                                            }
                                            onStatusUpdate("Live browser extraction completed.")
                                            safeComplete()
                                        }
                                    } else {
                                        safeComplete()
                                    }
                                }
                            } else {
                                safeComplete()
                            }
                        }
                    }
                }

                // Watchdog timer (14s max): guarantees extraction never hangs if page keeps loading or streams
                val watchdogRunnable = Runnable {
                    if (!isCancelled && !isExtractionCompleted) {
                        Log.i("BrowserMediaExtractor", "Extraction watchdog timeout reached, scanning current page DOM...")
                        onStatusUpdate("Completing page scan...")
                        wv.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { fallbackJson ->
                            val fallbackEntries = MediaCaptureEngine.parseJsDomScanResult(fallbackJson, normalizedUrl)
                            for (item in fallbackEntries) {
                                val key = MediaClassifier.canonicalKey(item.url)
                                synchronized(seenMap) {
                                    val existing = seenMap[key]
                                    if (existing != null && MediaClassifier.compareMediaQuality(item, existing) <= 0) {
                                        continue
                                    }
                                    seenMap[key] = item
                                }
                                onMediaDiscovered(item)
                                DownloaderSession.addDiscoveredItem(item)
                            }
                            safeComplete()
                        }
                    }
                }
                mainHandler.postDelayed(watchdogRunnable, 14000L)

                wv.loadUrl(normalizedUrl)
            } catch (e: Exception) {
                Log.e("BrowserMediaExtractor", "Browser initialization failed", e)
                onStatusUpdate("Browser initialization failed: ${e.localizedMessage}")
                onFinished()
            }
        }
    }

    class EmreMediaBridge(private val onMediaFound: (String) -> Unit) {
        @android.webkit.JavascriptInterface
        fun reportMedia(url: String?) {
            if (!url.isNullOrBlank()) {
                onMediaFound(url)
            }
        }
    }

    private fun executeAdaptiveScrollSequence(
        wv: WebView,
        step: Int = 1,
        maxSteps: Int = 12,
        stagnantCount: Int = 0,
        lastScrollY: Int = -1,
        onDone: () -> Unit
    ) {
        if (isCancelled || step > maxSteps || stagnantCount >= 2) {
            onDone()
            return
        }

        wv.evaluateJavascript(MediaCaptureEngine.getAdaptiveScrollScript()) { resultJson ->
            if (isCancelled) {
                onDone()
                return@evaluateJavascript
            }

            var currentScrollY = lastScrollY
            var remaining = 1000
            runCatching {
                val unescaped = if (resultJson != null && resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                    org.json.JSONTokener(resultJson).nextValue().toString()
                } else resultJson ?: "{}"
                val obj = org.json.JSONObject(unescaped)
                currentScrollY = obj.optInt("scrollY", lastScrollY)
                remaining = obj.optInt("remaining", 1000)
            }

            val isStagnant = lastScrollY != -1 && Math.abs(currentScrollY - lastScrollY) < 10
            val newStagnantCount = if (isStagnant) stagnantCount + 1 else 0

            if (remaining <= 5 || newStagnantCount >= 2) {
                onDone()
            } else {
                mainHandler.postDelayed({
                    if (!isCancelled) {
                        executeAdaptiveScrollSequence(
                            wv = wv,
                            step = step + 1,
                            maxSteps = maxSteps,
                            stagnantCount = newStagnantCount,
                            lastScrollY = currentScrollY,
                            onDone = onDone
                        )
                    } else {
                        onDone()
                    }
                }, 500)
            }
        }
    }

    fun cleanup() {
        isCancelled = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            (webView?.parent as? ViewGroup)?.removeView(webView)
            webView?.stopLoading()
            webView?.removeJavascriptInterface("EmreBridge")
            webView?.webViewClient = WebViewClient()
            webView?.webChromeClient = WebChromeClient()
            webView?.destroy()
        } catch (_: Exception) {
        } finally {
            webView = null
        }
    }
}
