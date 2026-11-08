package com.amresalehin.emregallery.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import android.app.Activity
import com.amresalehin.emregallery.data.BrowserHistoryManager
import com.amresalehin.emregallery.data.BrowserSession
import com.amresalehin.emregallery.data.BrowserShortcut
import com.amresalehin.emregallery.data.DownloaderSession
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.data.ScrapedMediaItem
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DirectBrowserView(
    initialUrl: String = "",
    onClose: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    onUrlChange: ((String) -> Unit)? = null,
    onPageTitleChanged: ((String) -> Unit)? = null,
    onMediaExtracted: (ScrapedMediaItem) -> Unit,
    onBatchExtracted: (List<ScrapedMediaItem>, pageTitle: String?, domain: String?) -> Unit,
    onSwitchToMediaTab: () -> Unit = {},
    onOpenDownloads: (() -> Unit)? = null,
    onOpenDiscoveredMedia: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val historyManager = remember { BrowserHistoryManager(context) }
    val tabSession = remember { BrowserSession.getTabSession("tab_main") }
    val tabDetectedCount by tabSession.detectedCount.collectAsState()
    val sessionDiscoveredItems by DownloaderSession.discoveredItems.collectAsState()

    val prefsRepo = remember { PreferencesRepository(context) }
    val themePref by prefsRepo.theme.collectAsState(initial = "system")
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> systemInDark
    }

    val shortcutsList by historyManager.shortcuts.collectAsState(initial = historyManager.defaultShortcuts)
    val allHistorySites by historyManager.allSites.collectAsState(initial = historyManager.defaultRecentSites)
    val tabsList by historyManager.openTabs.collectAsState(initial = historyManager.defaultTabs)

    val startUrl = remember {
        val trimmed = initialUrl.trim()
        if (trimmed.isNotBlank()) {
            MediaClassifier.formatQueryOrUrl(trimmed)
        } else {
            "" // Clean monochrome home page by default
        }
    }

    var currentUrl by remember { mutableStateOf(startUrl) }
    val totalDetectedCount = if (currentUrl.isBlank() || currentUrl == "about:blank") {
        0
    } else {
        maxOf(tabDetectedCount, sessionDiscoveredItems.size)
    }
    var addressInput by remember { mutableStateOf(startUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isDesktopMode by remember { mutableStateOf(false) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var selectedShortcutForEdit by remember { mutableStateOf<BrowserShortcut?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showTabsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val currentView = LocalView.current
    DisposableEffect(isDark) {
        val window = (currentView.context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, currentView)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
        onDispose { }
    }

    val scrollHandler = remember { BrowserToolbarScrollHandler() }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isBlank()) {
            scrollHandler.show()
        }
    }

    LaunchedEffect(Unit) {
        if (startUrl.isBlank()) {
            tabSession.clearMedia()
            tabSession.updateNavigation("")
            DownloaderSession.clear()
        }
    }

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

    BackHandler(enabled = canGoBack || currentUrl.isNotBlank() || onClose != null) {
        when {
            showHistorySheet -> showHistorySheet = false
            showTabsSheet -> showTabsSheet = false
            showSettingsSheet -> showSettingsSheet = false
            showAddShortcutDialog -> showAddShortcutDialog = false
            selectedShortcutForEdit != null -> selectedShortcutForEdit = null
            canGoBack && webViewInstance?.canGoBack() == true -> webViewInstance?.goBack()
            currentUrl.isNotBlank() -> {
                currentUrl = ""
                addressInput = ""
                pageTitle = ""
                onUrlChange?.invoke("")
                tabSession.clearMedia()
                tabSession.updateNavigation("")
                DownloaderSession.clear()
                webViewInstance?.loadUrl("about:blank")
            }
            onClose != null -> onClose()
        }
    }

    fun navigateToUrl(target: String) {
        val trimmed = target.trim()
        if (trimmed.isBlank()) return
        scrollHandler.show()
        val formatted = MediaClassifier.formatQueryOrUrl(trimmed)
        tabSession.clearMedia()
        DownloaderSession.clear()
        addressInput = formatted
        currentUrl = formatted
        onUrlChange?.invoke(formatted)
        tabSession.updateNavigation(formatted, pageTitle)
        DownloaderSession.updateBrowserPage(formatted)
        coroutineScope.launch {
            historyManager.recordVisit(formatted)
        }
        webViewInstance?.loadUrl(formatted)
        focusManager.clearFocus()
    }

    fun scanCurrentPageDOM(wv: WebView?) =
        performDirectBrowserDomScan(wv, currentUrl, tabSession, onMediaExtracted)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("direct_browser_view"),
        color = BrowserThemeColors.background(isDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DirectBrowserToolbarSection(
                addressInput = addressInput,
                onAddressInputChange = { addressInput = it },
                currentUrl = currentUrl,
                isLoading = isLoading,
                loadingProgress = loadingProgress,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                detectedItemsCount = detectedMediaCount,
                recentSearchQueries = recentSearchQueries,
                allHistorySites = allHistorySites,
                activeSearchEngine = activeSearchEngine,
                onSearchEngineChange = { activeSearchEngine = it },
                isSearchFocused = isSearchFocused,
                onSearchFocusChange = { isSearchFocused = it },
                isDark = isDark,
                webViewInstance = webViewInstance,
                historyManager = historyManager,
                onNavigate = { target ->
                    if (target.isEmpty()) {
                        currentUrl = ""
                        addressInput = ""
                        pageTitle = ""
                        onUrlChange?.invoke("")
                        webViewInstance?.loadUrl("about:blank")
                    } else {
                        navigateToUrl(target)
                    }
                },
                onBackToSearchClick = onBackToSearchClick,
                onSwitchToMediaTab = onSwitchToMediaTab
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                DirectBrowserWebViewCanvas(
                    isDark = isDark,
                    isDesktopMode = isDesktopMode,
                    startUrl = startUrl,
                    currentUrl = currentUrl,
                    pageTitle = pageTitle,
                    tabSession = tabSession,
                    historyManager = historyManager,
                    scrollHandler = scrollHandler,
                    onWebViewCreated = { webViewInstance = it },
                    onAddressInputUpdated = { addressInput = it },
                    onCurrentUrlUpdated = { currentUrl = it },
                    onPageTitleUpdated = { pageTitle = it },
                    onCanGoBackUpdated = { canGoBack = it },
                    onCanGoForwardUpdated = { canGoForward = it },
                    onIsLoadingUpdated = { isLoading = it },
                    onLoadingProgressUpdated = { loadingProgress = it },
                    onUrlChange = onUrlChange,
                    onPageTitleChanged = onPageTitleChanged,
                    onMediaExtracted = onMediaExtracted,
                    onScanDom = { scanCurrentPageDOM(it) }
                )

                DirectBrowserViewportOverlays(
                    currentUrl = currentUrl,
                    shortcutsList = shortcutsList,
                    allHistorySites = allHistorySites,
                    tabsList = tabsList,
                    scrollHandler = scrollHandler,
                    isDark = isDark,
                    onNavigate = { navigateToUrl(it) },
                    onShowAddShortcut = { showAddShortcutDialog = true },
                    onEditShortcut = { selectedShortcutForEdit = it },
                    onShowHistory = { showHistorySheet = true },
                    onShowTabs = { showTabsSheet = true },
                    onShowSettings = { showSettingsSheet = true },
                    onOpenDownloads = onOpenDownloads,
                    onSwitchToMediaTab = onSwitchToMediaTab
                )
            }
        }
    }

    DirectBrowserDialogsAndSheets(
        isDark = isDark,
        currentUrl = currentUrl,
        shortcutsList = shortcutsList,
        allHistorySites = allHistorySites,
        tabsList = tabsList,
        historyManager = historyManager,
        webViewInstance = webViewInstance,
        isDesktopMode = isDesktopMode,
        onDesktopModeChange = { isDesktopMode = it },
        showAddShortcutDialog = showAddShortcutDialog,
        onDismissAddShortcut = { showAddShortcutDialog = false },
        selectedShortcutForEdit = selectedShortcutForEdit,
        onDismissEditShortcut = { selectedShortcutForEdit = null },
        showHistorySheet = showHistorySheet,
        onDismissHistorySheet = { showHistorySheet = false },
        showTabsSheet = showTabsSheet,
        onDismissTabsSheet = { showTabsSheet = false },
        showSettingsSheet = showSettingsSheet,
        onDismissSettingsSheet = { showSettingsSheet = false },
        onNavigate = { navigateToUrl(it) },
        onClearCurrentPage = { currentUrl = ""; addressInput = ""; pageTitle = ""; onUrlChange?.invoke(""); webViewInstance?.loadUrl("about:blank") }
    )
}
