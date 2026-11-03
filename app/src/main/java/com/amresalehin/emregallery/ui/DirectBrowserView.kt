package com.amresalehin.emregallery.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.json.JSONArray
import java.net.URL

@SuppressLint("SetJavaScriptEnabled")
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

    val startUrl = remember {
        val trimmed = initialUrl.trim()
        if (trimmed.isNotBlank()) {
            MediaClassifier.formatQueryOrUrl(trimmed)
        } else {
            "" // Empty home page by default
        }
    }

    var currentUrl by remember { mutableStateOf(startUrl) }
    var addressInput by remember { mutableStateOf(startUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Intercept back button when browser can navigate back
    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    fun navigateToUrl(target: String) {
        val trimmed = target.trim()
        if (trimmed.isBlank()) return
        val formatted = MediaClassifier.formatQueryOrUrl(trimmed)
        addressInput = formatted
        currentUrl = formatted
        onUrlChange?.invoke(formatted)
        webViewInstance?.loadUrl(formatted)
        focusManager.clearFocus()
    }

    fun scanCurrentPageDOM(wv: WebView?) {
        if (wv == null) return
        val current = wv.url ?: return
        if (current.isBlank() || current == "about:blank" || currentUrl.isBlank()) return
        val js = """
            (function() {
                var results = [];
                function addUrl(u) {
                    if (u && typeof u === 'string' && u.length > 5 && !u.startsWith('data:') && !u.startsWith('javascript:')) {
                        results.push(u);
                    }
                }
                document.querySelectorAll('video, video source, audio, audio source').forEach(function(el) {
                    addUrl(el.src);
                    addUrl(el.currentSrc);
                });
                document.querySelectorAll('img').forEach(function(img) {
                    addUrl(img.src);
                    addUrl(img.currentSrc);
                    addUrl(img.getAttribute('data-src'));
                    addUrl(img.getAttribute('data-original'));
                    var ss = img.getAttribute('srcset');
                    if (ss) {
                        ss.split(',').forEach(function(part) {
                            var p = part.trim().split(/\s+/)[0];
                            addUrl(p);
                        });
                    }
                });
                return JSON.stringify(results);
            })();
        """.trimIndent()

        wv.evaluateJavascript(js) { jsonRaw ->
            if (jsonRaw != null && jsonRaw != "null") {
                try {
                    val cleanJson = if (jsonRaw.startsWith("\"") && jsonRaw.endsWith("\"")) {
                        org.json.JSONTokener(jsonRaw).nextValue().toString()
                    } else {
                        jsonRaw
                    }
                    val arr = JSONArray(cleanJson)
                    val discovered = mutableListOf<ScrapedMediaItem>()
                    for (i in 0 until arr.length()) {
                        val raw = arr.optString(i)
                        val resolved = MediaClassifier.resolveUrl(raw, current) ?: continue
                        val classification = MediaClassifier.classify(resolved)
                        val fileName = MediaClassifier.deriveFileName(resolved, classification.format, i)
                        discovered.add(
                            ScrapedMediaItem(
                                id = "dom_${System.currentTimeMillis()}_$i",
                                url = resolved,
                                title = fileName,
                                isVideo = classification.isVideo,
                                isAudio = classification.isAudio,
                                isStream = classification.isStream,
                                category = classification.category,
                                format = classification.format,
                                mimeType = classification.mimeType,
                                source = "Live Browser DOM"
                            )
                        )
                    }
                    if (discovered.isNotEmpty()) {
                        val domain = runCatching { URL(current).host }.getOrDefault("")
                        onBatchExtracted(discovered, wv.title, domain)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("direct_browser_view")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Browser Navigation and Address Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onClose != null) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(36.dp).testTag("browser_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close browser",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Navigation Controls: Back & Forward
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(34.dp).testTag("browser_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(34.dp).testTag("browser_forward_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Interactive, Editable URL and Search Bar
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("browser_address_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isSecure = currentUrl.startsWith("https://", ignoreCase = true)
                            Icon(
                                imageVector = if (isSecure) Icons.Default.Lock else if (currentUrl.isNotBlank()) Icons.Default.Language else Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )

                            Spacer(Modifier.width(6.dp))

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (addressInput.isEmpty()) {
                                    Text(
                                        text = "Search or type URL",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = addressInput,
                                    onValueChange = { addressInput = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            navigateToUrl(addressInput)
                                        }
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("browser_address_input")
                                )
                            }

                            if (addressInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { addressInput = "" },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { navigateToUrl(addressInput) },
                                modifier = Modifier.size(24.dp).testTag("browser_go_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Go",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Reload / Stop button
                    IconButton(
                        onClick = {
                            if (isLoading) {
                                webViewInstance?.stopLoading()
                            } else {
                                webViewInstance?.reload()
                            }
                        },
                        modifier = Modifier.size(34.dp).testTag("browser_refresh_btn")
                    ) {
                        Icon(
                            imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = if (isLoading) "Stop" else "Reload",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Home button
                    IconButton(
                        onClick = {
                            currentUrl = ""
                            addressInput = ""
                            pageTitle = ""
                            onUrlChange?.invoke("")
                            webViewInstance?.loadUrl("about:blank")
                        },
                        modifier = Modifier.size(34.dp).testTag("browser_home_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = if (currentUrl.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Discovered Media count badge or Scan button
                    if (discoveredCount > 0) {
                        Surface(
                            onClick = onSwitchToMediaTab,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(32.dp).testTag("browser_media_badge_top")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PermMedia,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$discoveredCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (currentUrl.isNotBlank()) {
                        IconButton(
                            onClick = { scanCurrentPageDOM(webViewInstance) },
                            modifier = Modifier.size(34.dp).testTag("browser_scan_page_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = "Scan media",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // Loading Progress Bar
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

            // 2. Interactive WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
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

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress / 100f
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title.orEmpty()
                                onPageTitleChanged?.invoke(pageTitle)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
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
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
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
                                        // Automatically scan page DOM for media once loaded
                                        scanCurrentPageDOM(view)
                                    }
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                if (currentUrl.isBlank()) return null
                                val reqUrl = request?.url?.toString() ?: return null
                                if (reqUrl.startsWith("about:")) return null
                                val classified = MediaClassifier.classify(reqUrl)

                                // Intercept video streams (HLS/DASH), standalone videos, audio files, or quality media
                                if (classified.isStream || classified.isVideo || classified.isAudio ||
                                    (classified.category == com.amresalehin.emregallery.data.MediaCategory.IMAGES &&
                                            (reqUrl.contains("jpg") || reqUrl.contains("png") || reqUrl.contains("webp")) &&
                                            !reqUrl.contains("analytics") && !reqUrl.contains("tracker") && !reqUrl.contains("pixel"))
                                ) {
                                    val fileName = MediaClassifier.deriveFileName(reqUrl, classified.format, 0)
                                    val item = ScrapedMediaItem(
                                        id = "intercept_${System.currentTimeMillis()}_${reqUrl.hashCode()}",
                                        url = reqUrl,
                                        title = fileName,
                                        isVideo = classified.isVideo,
                                        isAudio = classified.isAudio,
                                        isStream = classified.isStream,
                                        category = classified.category,
                                        format = classified.format,
                                        mimeType = classified.mimeType,
                                        source = "Browser Interceptor"
                                    )
                                    onMediaExtracted(item)
                                }

                                return null
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
                    webViewInstance = wv
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("browser_webview_canvas")
            )

            if (currentUrl.isBlank()) {
                BrowserEmptyHomePage(
                    onNavigate = { target ->
                        navigateToUrl(target)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("browser_empty_home_page")
                )
            }
        }

        // 3. Floating Quick Badge: "Discovered N media items • View in Media tab"
        AnimatedVisibility(
            visible = discoveredCount > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                onClick = onSwitchToMediaTab,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("browser_view_media_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Discovered $discoveredCount media items",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "View Media",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean empty home page displayed when the browser is opened without a URL.
 * Offers a clean interface and quick shortcuts without loading 3rd-party search pages.
 */
@Composable
fun BrowserEmptyHomePage(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var homeSearchInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Media Downloader Browser",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Search the web or enter any URL to discover and download videos, audios, streams & photos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            // Prominent Search / URL Input Card in Center of Home Page
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("home_page_search_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (homeSearchInput.isEmpty()) {
                            Text(
                                text = "Search web or enter address (URL)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = homeSearchInput,
                            onValueChange = { homeSearchInput = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (homeSearchInput.isNotBlank()) {
                                        onNavigate(homeSearchInput)
                                    }
                                }
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_page_search_input")
                        )
                    }

                    if (homeSearchInput.isNotEmpty()) {
                        IconButton(
                            onClick = { homeSearchInput = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (homeSearchInput.isNotBlank()) {
                                onNavigate(homeSearchInput)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(34.dp).testTag("home_page_search_btn")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Go",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Quick Shortcut Chips
            Text(
                text = "Popular Media Platforms",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            val shortcuts = listOf(
                "Wikimedia" to "https://commons.wikimedia.org",
                "Unsplash" to "https://unsplash.com",
                "Pexels" to "https://pexels.com",
                "Internet Archive" to "https://archive.org",
                "Pixabay" to "https://pixabay.com",
                "DuckDuckGo" to "https://duckduckgo.com"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                shortcuts.take(3).forEach { (name, url) ->
                    Surface(
                        onClick = { onNavigate(url) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                shortcuts.drop(3).forEach { (name, url) ->
                    Surface(
                        onClick = { onNavigate(url) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
