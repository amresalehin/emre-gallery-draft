package com.amresalehin.emregallery.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.amresalehin.emregallery.data.BrowserMediaExtractor
import com.amresalehin.emregallery.data.DownloaderSession
import com.amresalehin.emregallery.data.ExtractionSource
import com.amresalehin.emregallery.data.MediaCaptureEngine
import com.amresalehin.emregallery.data.ScrapedMediaItem

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DirectBrowserWebViewContainer(
    startUrl: String,
    getCurrentUrl: () -> String,
    onWebViewChanged: (WebView?) -> Unit,
    onProgressChanged: (progress: Float, loading: Boolean) -> Unit,
    onTitleReceived: (String) -> Unit,
    onPageStartedUrl: (url: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onPageFinishedUrl: (view: WebView?, url: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onMediaExtracted: (ScrapedMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                onWebViewChanged(this)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }

                addJavascriptInterface(
                    BrowserMediaExtractor.EmreMediaBridge { rawUrl ->
                        val item = MediaCaptureEngine.buildMediaItem(
                            rawUrl = rawUrl,
                            source = ExtractionSource.DOM_SCRAPE,
                            baseUrl = getCurrentUrl()
                        )
                        if (item != null) {
                            onMediaExtracted(item)
                            DownloaderSession.addDiscoveredItem(item)
                        }
                    },
                    "EmreBridge"
                )

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress / 100f, newProgress < 100)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        onTitleReceived(title.orEmpty())
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val reqUrl = request?.url?.toString() ?: return false
                        return DirectBrowserSchemeHandler.handleExternalNavigation(view, reqUrl)
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url.isNullOrBlank()) return false
                        return DirectBrowserSchemeHandler.handleExternalNavigation(view, url)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onPageStartedUrl(url, view?.canGoBack() == true, view?.canGoForward() == true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onPageFinishedUrl(view, url, view?.canGoBack() == true, view?.canGoForward() == true)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        if (reqUrl.startsWith("about:")) return null

                        return MediaCaptureEngine.shouldInterceptRequest(
                            request = request,
                            baseUrl = getCurrentUrl()
                        ) { item ->
                            DownloaderSession.addDiscoveredItem(item)
                            onMediaExtracted(item)
                        }
                    }
                }

                if (startUrl.isNotBlank()) {
                    loadUrl(startUrl)
                } else {
                    loadUrl("about:blank")
                }
            }
        },
        update = { wv ->
            onWebViewChanged(wv)
        },
        onRelease = { wv ->
            wv.stopLoading()
            wv.webViewClient = WebViewClient()
            wv.webChromeClient = WebChromeClient()
            wv.destroy()
            onWebViewChanged(null)
        },
        modifier = modifier
    )
}
