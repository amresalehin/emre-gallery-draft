package com.amresalehin.emregallery.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
    initialUrl: String,
    discoveredCount: Int,
    onMediaExtracted: (ScrapedMediaItem) -> Unit,
    onBatchExtracted: (List<ScrapedMediaItem>, pageTitle: String?, domain: String?) -> Unit,
    onSwitchToMediaTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    val startUrl = remember(initialUrl) {
        val trimmed = initialUrl.trim()
        if (trimmed.isNotBlank()) {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
        } else {
            "https://duckduckgo.com"
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
        val formatted = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            if (trimmed.contains(".") && !trimmed.contains(" ")) {
                "https://$trimmed"
            } else {
                "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
            }
        } else {
            trimmed
        }
        addressInput = formatted
        webViewInstance?.loadUrl(formatted)
        focusManager.clearFocus()
    }

    fun scanCurrentPageDOM(wv: WebView?) {
        if (wv == null) return
        val current = wv.url ?: return
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
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Navigation Controls
                        IconButton(
                            onClick = { webViewInstance?.goBack() },
                            enabled = canGoBack,
                            modifier = Modifier.size(36.dp).testTag("browser_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = canGoForward,
                            modifier = Modifier.size(36.dp).testTag("browser_forward_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isLoading) {
                                    webViewInstance?.stopLoading()
                                } else {
                                    webViewInstance?.reload()
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("browser_refresh_btn")
                        ) {
                            Icon(
                                imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                contentDescription = if (isLoading) "Stop" else "Reload",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Address Bar
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "URL",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (addressInput.isEmpty()) {
                                        Text(
                                            text = "Search or type URL",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
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
                                            onGo = { navigateToUrl(addressInput) }
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
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Go / Enter Action
                        FilledIconButton(
                            onClick = { navigateToUrl(addressInput) },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(36.dp).testTag("browser_go_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Go",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Page title and live stream/media quick-scan indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (pageTitle.isNotBlank()) pageTitle else currentUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(Modifier.width(8.dp))

                        // Scan Page Button
                        Surface(
                            onClick = { scanCurrentPageDOM(webViewInstance) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.height(24.dp).testTag("browser_scan_page_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TravelExplore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Scan Media",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 11.sp
                                )
                            }
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
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let {
                                    currentUrl = it
                                    addressInput = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                url?.let {
                                    currentUrl = it
                                    addressInput = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true

                                // Automatically scan page DOM for media once loaded
                                scanCurrentPageDOM(view)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
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

                        loadUrl(startUrl)
                    }
                },
                update = { wv ->
                    webViewInstance = wv
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("browser_webview_canvas")
            )
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
