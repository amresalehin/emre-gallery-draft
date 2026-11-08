package com.amresalehin.emregallery.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import android.os.Build
import com.amresalehin.emregallery.data.BotDetectionMitigator
import com.amresalehin.emregallery.data.BrowserHistoryManager
import com.amresalehin.emregallery.data.BrowserSession
import com.amresalehin.emregallery.data.DownloaderSession
import com.amresalehin.emregallery.data.ExtractionSource
import com.amresalehin.emregallery.data.MediaCaptureEngine
import com.amresalehin.emregallery.data.ScrapedMediaItem
import kotlinx.coroutines.launch


@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun DirectBrowserWebViewCanvas(
    isDark: Boolean,
    isDesktopMode: Boolean,
    startUrl: String,
    currentUrl: String,
    pageTitle: String,
    tabSession: BrowserTabSession,
    historyManager: BrowserHistoryManager,
    scrollHandler: BrowserToolbarScrollHandler,
    onWebViewCreated: (WebView?) -> Unit,
    onAddressInputUpdated: (String) -> Unit,
    onCurrentUrlUpdated: (String) -> Unit,
    onPageTitleUpdated: (String) -> Unit,
    onCanGoBackUpdated: (Boolean) -> Unit,
    onCanGoForwardUpdated: (Boolean) -> Unit,
    onIsLoadingUpdated: (Boolean) -> Unit,
    onLoadingProgressUpdated: (Float) -> Unit,
    onUrlChange: ((String) -> Unit)?,
    onPageTitleChanged: ((String) -> Unit)?,
    onMediaExtracted: (ScrapedMediaItem) -> Unit,
    onScanDom: (WebView?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
                // Interactive WebView (always instantiated for high-speed responsiveness)
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            onWebViewCreated(this)
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            setBackgroundColor(if (isDark) android.graphics.Color.parseColor("#111111") else android.graphics.Color.WHITE)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                settings.forceDark = if (isDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                            }

                            var startTouchY = 0f
                            var startTouchX = 0f
                            var accumulatedTouchDy = 0f

                            setOnTouchListener { _, event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        startTouchY = event.rawY
                                        startTouchX = event.rawX
                                        accumulatedTouchDy = 0f
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        val dy = startTouchY - event.rawY
                                        val dx = startTouchX - event.rawX
                                        startTouchY = event.rawY
                                        startTouchX = event.rawX

                                        if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                            if (dy > 0f) {
                                                if (accumulatedTouchDy < 0f) accumulatedTouchDy = 0f
                                                accumulatedTouchDy += dy
                                                if (accumulatedTouchDy >= scrollHandler.thresholdPx) {
                                                    scrollHandler.hide()
                                                }
                                            } else if (dy < 0f) {
                                                if (accumulatedTouchDy > 0f) accumulatedTouchDy = 0f
                                                accumulatedTouchDy += dy
                                                if (accumulatedTouchDy <= -scrollHandler.thresholdPx) {
                                                    scrollHandler.show()
                                                }
                                            }
                                        }
                                    }
                                    MotionEvent.ACTION_UP,
                                    MotionEvent.ACTION_CANCEL -> {
                                        accumulatedTouchDy = 0f
                                        scrollHandler.onTouchEnd()
                                    }
                                }
                                false
                            }

                            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                val dy = scrollY - oldScrollY
                                scrollHandler.onScroll(scrollY, dy)
                            }

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(false)
                                cacheMode = WebSettings.LOAD_DEFAULT
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = BotDetectionMitigator.STEALTH_USER_AGENT
                            }

                            addJavascriptInterface(
                                BrowserSession.EmreMediaBridge { rawUrl ->
                                    val item = MediaCaptureEngine.buildMediaItem(
                                        rawUrl = rawUrl,
                                        source = ExtractionSource.DOM_SCRAPE,
                                        baseUrl = currentUrl
                                    )
                                    if (item != null) {
                                        tabSession.registerMedia(item)
                                        onMediaExtracted(item)
                                        DownloaderSession.addDiscoveredItem(item)
                                    }
                                },
                                "EmreBridge"
                            )

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    onLoadingProgressUpdated(newProgress / 100f)
                                    onIsLoadingUpdated(newProgress < 100)
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    onPageTitleUpdated(title.orEmpty())
                                    onPageTitleChanged?.invoke(pageTitle)
                                    tabSession.updateNavigation(currentUrl, pageTitle)
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                private fun handleExternalNavigation(view: WebView?, urlString: String): Boolean =
                                    handleDirectBrowserExternalNavigation(view, urlString)

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url?.toString() ?: return false
                                    return handleExternalNavigation(view, reqUrl)
                                }

                                @Suppress("DEPRECATION")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (url.isNullOrBlank()) return false
                                    return handleExternalNavigation(view, url)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    scrollHandler.show()
                                    if (url.isNullOrBlank() || url == "about:blank") {
                                        tabSession.clearMedia()
                                        tabSession.updateNavigation("")
                                        DownloaderSession.clear()
                                        onIsLoadingUpdated(false)
                                        onCanGoBackUpdated(false)
                                        onCanGoForwardUpdated(false)
                                        return
                                    }
                                    tabSession.clearMedia()
                                    DownloaderSession.clear()
                                    onIsLoadingUpdated(true)
                                    BotDetectionMitigator.injectStealth(view)
                                    onCurrentUrlUpdated(url)
                                    onAddressInputUpdated(url)
                                    onUrlChange?.invoke(url)
                                    tabSession.updateNavigation(url, pageTitle)
                                    DownloaderSession.updateBrowserPage(url)
                                    onCanGoBackUpdated(view?.canGoBack() == true)
                                    onCanGoForwardUpdated(view?.canGoForward() == true)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    onIsLoadingUpdated(false)
                                    if (url.isNullOrBlank() || url == "about:blank") {
                                        tabSession.updateNavigation("")
                                        onCanGoBackUpdated(false)
                                        onCanGoForwardUpdated(false)
                                        return
                                    }
                                    BotDetectionMitigator.injectStealth(view)
                                    BotDetectionMitigator.syncCookiesFromWebView(url)
                                    onCurrentUrlUpdated(url)
                                    onAddressInputUpdated(url)
                                    onUrlChange?.invoke(url)
                                    tabSession.updateNavigation(url, view?.title)
                                    coroutineScope.launch {
                                        historyManager.recordVisit(url, view?.title)
                                    }
                                    view?.evaluateJavascript(MediaCaptureEngine.getMutationObserverScript(), null)
                                    onScanDom(view)
                                    onCanGoBackUpdated(view?.canGoBack() == true)
                                    onCanGoForwardUpdated(view?.canGoForward() == true)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    if (reqUrl.startsWith("about:")) return null

                                    return MediaCaptureEngine.shouldInterceptRequest(
                                        request = request,
                                        baseUrl = currentUrl
                                    ) { item ->
                                        tabSession.registerMedia(item)
                                        DownloaderSession.addDiscoveredItem(item)
                                        onMediaExtracted(item)
                                    }
                                }
                            }

                            if (startUrl.isNotBlank()) {
                                loadUrl(startUrl)
                            }
                        }
                    },
                    update = { wv ->
                        webViewInstance = wv
                        wv.setBackgroundColor(if (isDark) android.graphics.Color.parseColor("#111111") else android.graphics.Color.WHITE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            wv.settings.forceDark = if (isDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                        }
                    },
                    onRelease = { wv ->
                        wv.stopLoading()
                        wv.webViewClient = WebViewClient()
                        wv.webChromeClient = WebChromeClient()
                        wv.destroy()
                        onWebViewCreated(null)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("browser_webview_canvas")
                )
}
