package com.amresalehin.emregallery.data

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * [WebViewClient] handling network media interception, navigation scheme filtering,
 * renderer recovery, and post-load DOM + MutationObserver + adaptive scroll extraction.
 */
internal class BrowserExtractionWebViewClient(
    private val normalizedUrl: String,
    private val mainHandler: Handler,
    private val mediaTracker: BrowserDiscoveredMediaTracker,
    private val adaptiveScroller: BrowserAdaptiveScroller,
    private val isCancelled: () -> Boolean,
    private val onStatusUpdate: (String) -> Unit,
    private val onComplete: () -> Unit
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (isCancelled() || request == null) return null
        return MediaCaptureEngine.shouldInterceptRequest(
            request = request,
            baseUrl = normalizedUrl,
            onMediaDiscovered = { item ->
                mainHandler.post {
                    mediaTracker.recordAndDispatch(item)
                }
            }
        )
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val reqUrl = request?.url?.toString() ?: return false
        return isUnsupportedNavigationScheme(reqUrl)
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val reqUrl = url ?: return false
        return isUnsupportedNavigationScheme(reqUrl)
    }

    private fun isUnsupportedNavigationScheme(url: String): Boolean {
        val lower = url.lowercase()
        return !lower.startsWith("http://") &&
            !lower.startsWith("https://") &&
            !lower.startsWith("about:") &&
            !lower.startsWith("data:")
    }

    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        Log.w(TAG, "Page error: $errorCode $description on $failingUrl")
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        if (request?.isForMainFrame == true) {
            Log.w(TAG, "Main frame error: ${error?.errorCode} ${error?.description}")
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        if (request?.isForMainFrame == true) {
            Log.w(TAG, "HTTP error on main frame: ${errorResponse?.statusCode}")
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
        Log.w(TAG, "Render process gone (didCrash=${detail?.didCrash()})")
        mainHandler.post {
            onStatusUpdate("Browser renderer process recovered")
            onComplete()
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (isCancelled()) return
        onStatusUpdate("Browser loaded page. Active network interception listening...")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        val webView = view ?: return
        if (isCancelled()) return

        BrowserCookieSync.syncWebViewToJar(normalizedUrl)
        onStatusUpdate("Scanning DOM, computed CSS, and dynamic media tags...")

        // Setup MutationObserver for dynamically injected nodes
        webView.evaluateJavascript(MediaCaptureEngine.getMutationObserverScript(), null)

        // 1. Run initial deep DOM scan
        webView.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { rawJson ->
            val rawEntries = MediaCaptureEngine.parseJsDomScanResult(rawJson, normalizedUrl)
            mediaTracker.recordAndDispatchAll(rawEntries)

            // 2. Perform adaptive scrolling and re-scan
            if (!isCancelled()) {
                onStatusUpdate("Auto-scrolling page to trigger lazy-loaded media...")
                adaptiveScroller.executeScrollSequence(webView) {
                    if (!isCancelled()) {
                        webView.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { postScrollJson ->
                            val postEntries = MediaCaptureEngine.parseJsDomScanResult(postScrollJson, normalizedUrl)
                            mediaTracker.recordAndDispatchAll(postEntries)
                            onStatusUpdate("Live browser extraction completed.")
                            onComplete()
                        }
                    } else {
                        onComplete()
                    }
                }
            } else {
                onComplete()
            }
        }
    }

    private companion object {
        const val TAG = "BrowserMediaExtractor"
    }
}
