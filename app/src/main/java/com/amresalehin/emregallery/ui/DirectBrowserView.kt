package com.amresalehin.emregallery.ui

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.BrowserHistoryManager
import com.amresalehin.emregallery.data.DownloaderSession
import com.amresalehin.emregallery.data.MediaCaptureEngine
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.WebUrlUtils
import kotlinx.coroutines.launch
import java.net.URL

@Composable
fun DirectBrowserView(
    initialUrl: String = "",
    onClose: (() -> Unit)? = null,
    onUrlChange: ((String) -> Unit)? = null,
    onPageTitleChanged: ((String) -> Unit)? = null,
    discoveredCount: Int = 0,
    onMediaExtracted: (ScrapedMediaItem) -> Unit,
    onBatchExtracted: (List<ScrapedMediaItem>, pageTitle: String?, domain: String?) -> Unit,
    onSwitchToMediaTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val historyManager = remember { BrowserHistoryManager(context) }

    val startUrl = remember {
        val trimmed = initialUrl.trim()
        if (trimmed.isNotBlank()) WebUrlUtils.formatQueryOrUrl(trimmed) else ""
    }

    var currentUrl by remember { mutableStateOf(startUrl) }
    var addressInput by remember { mutableStateOf(startUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { wv ->
                wv.stopLoading()
                wv.webViewClient = WebViewClient()
                wv.webChromeClient = WebChromeClient()
                wv.destroy()
                webViewInstance = null
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    fun navigateToUrl(target: String) {
        val trimmed = target.trim()
        if (trimmed.isBlank()) return
        val formatted = WebUrlUtils.formatQueryOrUrl(trimmed)
        addressInput = formatted
        currentUrl = formatted
        onUrlChange?.invoke(formatted)
        coroutineScope.launch {
            historyManager.recordVisit(formatted)
        }
        webViewInstance?.loadUrl(formatted)
        focusManager.clearFocus()
    }

    fun scanCurrentPageDOM(wv: WebView?) {
        if (wv == null) return
        val current = wv.url ?: return
        if (current.isBlank() || current == "about:blank" || currentUrl.isBlank()) return

        wv.evaluateJavascript(MediaCaptureEngine.getJsDomScanScript()) { jsonRaw ->
            val discovered = MediaCaptureEngine.parseJsDomScanResult(jsonRaw, current)
            if (discovered.isNotEmpty()) {
                val domain = runCatching { URL(current).host }.getOrDefault("")
                onBatchExtracted(discovered, pageTitle, domain)
                DownloaderSession.addDiscoveredItems(discovered, pageTitle)
            }
        }
    }

    fun applyLoadedUrlState(url: String?) {
        url?.let {
            if (it == "about:blank" || it.isBlank()) {
                currentUrl = ""
                addressInput = ""
                pageTitle = ""
                onUrlChange?.invoke("")
            } else {
                currentUrl = it
                addressInput = it
                onUrlChange?.invoke(it)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("direct_browser_view")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowserNavigationToolbar(
                currentUrl = currentUrl,
                addressInput = addressInput,
                onAddressInputChange = { addressInput = it },
                onNavigate = ::navigateToUrl,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                isLoading = isLoading,
                discoveredCount = discoveredCount,
                onClose = onClose,
                onBack = { webViewInstance?.goBack() },
                onForward = { webViewInstance?.goForward() },
                onReloadOrStop = {
                    if (isLoading) webViewInstance?.stopLoading() else webViewInstance?.reload()
                },
                onHome = {
                    currentUrl = ""
                    addressInput = ""
                    pageTitle = ""
                    onUrlChange?.invoke("")
                    webViewInstance?.loadUrl("about:blank")
                },
                onScanPage = { scanCurrentPageDOM(webViewInstance) },
                onSwitchToMediaTab = onSwitchToMediaTab
            )

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                DirectBrowserWebViewContainer(
                    startUrl = startUrl,
                    getCurrentUrl = { currentUrl },
                    onWebViewChanged = { webViewInstance = it },
                    onProgressChanged = { progress, loading ->
                        loadingProgress = progress
                        isLoading = loading
                    },
                    onTitleReceived = { title ->
                        pageTitle = title
                        onPageTitleChanged?.invoke(pageTitle)
                    },
                    onPageStartedUrl = { url, back, forward ->
                        isLoading = true
                        applyLoadedUrlState(url)
                        canGoBack = back
                        canGoForward = forward
                    },
                    onPageFinishedUrl = { view, url, back, forward ->
                        isLoading = false
                        applyLoadedUrlState(url)
                        if (url != null && url != "about:blank" && url.isNotBlank()) {
                            val finishedTitle = view?.title
                            coroutineScope.launch {
                                historyManager.recordVisit(url, finishedTitle)
                            }
                            view?.evaluateJavascript(MediaCaptureEngine.getMutationObserverScript(), null)
                            scanCurrentPageDOM(view)
                        }
                        canGoBack = back
                        canGoForward = forward
                    },
                    onMediaExtracted = onMediaExtracted,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("browser_webview_canvas")
                )

                if (currentUrl.isBlank()) {
                    BrowserEmptyHomePage(
                        onNavigate = ::navigateToUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("browser_empty_home_page")
                    )
                }
            }
        }
    }
}
