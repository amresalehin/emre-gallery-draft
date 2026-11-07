package com.amresalehin.emregallery.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Native in-app browser for visiting and saving web pages.
 *
 * A single WebView is retained for the screen lifetime. Navigation and page
 * progress are surfaced to Compose; page content stays in the WebView process.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserScreen(
    initialUrl: String? = null,
    onBack: () -> Unit,
    onSavePage: suspend (String, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
    var currentUrl by remember { mutableStateOf(initialUrl.orEmpty()) }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showReader by remember { mutableStateOf(false) }
    var readerText by remember { mutableStateOf("") }
    var showSavedMessage by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadsImagesAutomatically = true
            settings.userAgentString = settings.userAgentString
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return request.url.scheme !in listOf("http", "https", "about")
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    currentUrl = url.orEmpty()
                    address = url.orEmpty()
                    isLoading = true
                    progress = 0
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    currentUrl = url.orEmpty()
                    address = url.orEmpty()
                    pageTitle = view.title.orEmpty()
                    isLoading = false
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                    isLoading = newProgress < 100
                }
                override fun onReceivedTitle(view: WebView, title: String?) {
                    pageTitle = title.orEmpty()
                }
            }
        }
    }

    fun loadAddress(input: String) {
        val value = input.trim()
        if (value.isBlank()) return
        val target = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.contains(" ") || !value.contains(".") -> "https://www.google.com/search?q=" +
                java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
            else -> "https://$value"
        }
        address = target
        webView.loadUrl(target)
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }

    DisposableEffect(webView) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) loadAddress(initialUrl)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Close browser")
            }
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { loadAddress(address) }),
                        modifier = Modifier.weight(1f)
                    )
                    if (address.isNotEmpty()) {
                        IconButton(onClick = { address = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear address", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            IconButton(onClick = { loadAddress(address) }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Browser menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Save to Pocket") },
                        leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                onSavePage(currentUrl.ifBlank { address }, pageTitle.takeIf { it.isNotBlank() })
                                showSavedMessage = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Read page text") },
                        leadingIcon = { Icon(Icons.Default.MenuBook, null) },
                        onClick = {
                            showMenu = false
                            webView.evaluateJavascript(
                                "(function(){return document.body ? document.body.innerText : '';})()"
                            ) { result ->
                                readerText = runCatching {
                                    org.json.JSONTokener(result).nextValue() as? String ?: ""
                                }.getOrDefault("")
                                showReader = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reload") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = { showMenu = false; webView.reload() }
                    )
                    DropdownMenuItem(
                        text = { Text("Open in external browser") },
                        leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                        onClick = {
                            showMenu = false
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(currentUrl)))
                            }
                        }
                    )
                }
            }
        }

        if (isLoading && progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        } else if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.fillMaxWidth().height(2.dp))
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            update = { view ->
                if (view.url.isNullOrBlank() && currentUrl.isNotBlank()) view.loadUrl(currentUrl)
            }
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { if (webView.canGoBack()) webView.goBack() }, enabled = canGoBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { if (webView.canGoForward()) webView.goForward() }, enabled = canGoForward) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { webView.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            onSavePage(currentUrl.ifBlank { address }, pageTitle.takeIf { it.isNotBlank() })
                            showSavedMessage = true
                        }
                    },
                    enabled = currentUrl.isNotBlank()
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Save page")
                }
            }
        }
    }

    if (showSavedMessage) {
        LaunchedEffect(showSavedMessage) {
            kotlinx.coroutines.delay(1800)
            showSavedMessage = false
        }
        SnackbarHost(hostState = remember { SnackbarHostState() })
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.inverseSurface
        ) {
            Text("Saved to Pocket", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }

    if (showReader) {
        Dialog(onDismissRequest = { showReader = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reader", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showReader = false }) { Icon(Icons.Default.Close, contentDescription = "Close reader") }
                    }
                    Text(pageTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.rememberScrollState().let { scroll ->
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                            Text(readerText, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
