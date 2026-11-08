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


internal fun BrowserSession.findActivity(ctx: Context): Activity? {
    var current: Context? = ctx
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@SuppressLint("SetJavaScriptEnabled")
internal suspend fun BrowserSession.extractHeadlessImpl(
    context: Context,
    targetUrl: String,
    onStatusUpdate: (String) -> Unit = {},
    onMediaDiscovered: (ScrapedMediaItem) -> Unit = {}
): MediaExtractionResult = suspendCancellableCoroutine { continuation ->
    mainHandler.post {
        val normalizedUrl = if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
            "https://$targetUrl"
        } else targetUrl

        val domain = runCatching { URL(normalizedUrl).host }.getOrDefault("")
        var webView: WebView? = null
        var isCompleted = false

        fun cleanup() {
            try {
                mainHandler.removeCallbacksAndMessages(null)
                (webView?.parent as? ViewGroup)?.removeView(webView)
                webView?.stopLoading()
                webView?.removeJavascriptInterface("EmreBridge")
                webView?.webViewClient = WebViewClient()
                webView?.webChromeClient = WebChromeClient()
                webView?.destroy()
            } catch (_: Throwable) {
            } finally {
                webView = null
            }
        }

        continuation.invokeOnCancellation {
            mainHandler.post { cleanup() }
        }

        try {
            val act = findActivity(context)
            val wv = if (act != null) WebView(act) else WebView(context)
            webView = wv

            val displayMetrics = context.resources.displayMetrics
            val viewWidth = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
            val viewHeight = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 1920

            if (act != null && !act.isFinishing && !act.isDestroyed) {
                val root = act.findViewById<ViewGroup>(android.R.id.content)
                if (root != null) {
                    wv.alpha = 0.001f
                    wv.isClickable = false
                    wv.isFocusable = false
                    val lp = ViewGroup.LayoutParams(viewWidth, viewHeight)
                    try { root.addView(wv, 0, lp) } catch (_: Throwable) { root.addView(wv, lp) }
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
            }

            val seenMap = LinkedHashMap<String, ScrapedMediaItem>()

            fun ingest(items: List<ScrapedMediaItem>) {
                if (items.isEmpty() || isCompleted) return
                val modified = MediaNormalizer.mergeCandidates(seenMap, items)
                if (modified) {
                    for (it in items) {
                        onMediaDiscovered(it)
                        DownloaderSession.addDiscoveredItem(it)
                    }
                }
            }

            fun finish(error: String? = null) {
                if (isCompleted) return
                isCompleted = true
                val discovered = seenMap.values.toList()
                cleanup()
                if (continuation.isActive) {
                    continuation.resume(
                        MediaExtractionResult(
                            items = discovered,
                            pageTitle = if (discovered.isNotEmpty()) discovered.first().title else domain,
                            errorMessage = if (discovered.isEmpty()) error else null
                        ),
                        onCancellation = { cleanup() }
                    )
                }
            }

            wv.addJavascriptInterface(
                EmreMediaBridge { rawUrl ->
                    mainHandler.post {
                        val item = MediaNormalizer.normalize(
                            rawUrl = rawUrl,
                            source = ExtractionSource.DOM_SCRAPE,
                            baseUrl = normalizedUrl
                        )
                        if (item != null) ingest(listOf(item))
                    }
                },
                "EmreBridge"
            )

            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    BotDetectionMitigator.injectStealth(view)
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (request == null || isCompleted) return null
                    return MediaCaptureEngine.shouldInterceptRequest(request, normalizedUrl) { item ->
                        mainHandler.post { ingest(listOf(item)) }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (isCompleted) return
                    BotDetectionMitigator.injectStealth(view)
                    BotDetectionMitigator.syncCookiesFromWebView(url)
                    if (BotDetectionMitigator.isBotChallenge(view?.title, url)) {
                        onStatusUpdate("Bypassing anti-bot verification...")
                    } else {
                        onStatusUpdate("Scanning dynamic page content...")
                    }
                    wv.evaluateJavascript(MediaCaptureEngine.getMutationObserverScript(), null)
                    wv.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { scanJson ->
                        val entries = MediaCaptureEngine.parseJsDomScanResult(scanJson, normalizedUrl)
                        ingest(entries)
                        if (seenMap.isNotEmpty()) {
                            finish(null)
                        } else {
                            // Wait 2s for client-side JS / hydration
                            mainHandler.postDelayed({
                                if (!isCompleted) {
                                    wv.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { secondJson ->
                                        val postEntries = MediaCaptureEngine.parseJsDomScanResult(secondJson, normalizedUrl)
                                        ingest(postEntries)
                                        finish(if (seenMap.isEmpty()) "No media discovered" else null)
                                    }
                                }
                            }, 2000L)
                        }
                    }
                }
            }

            // 12-second watchdog timer
            mainHandler.postDelayed({
                if (!isCompleted) {
                    finish(if (seenMap.isEmpty()) "Page scan timed out" else null)
                }
            }, 12000L)

            wv.loadUrl(normalizedUrl)
        } catch (e: Exception) {
            Log.e("BrowserSession", "Failed headless browser initialization", e)
            cleanup()
            if (continuation.isActive) {
                continuation.resume(
                    MediaExtractionResult(emptyList(), errorMessage = e.localizedMessage),
                    onCancellation = {}
                )
            }
        }
    }
}

