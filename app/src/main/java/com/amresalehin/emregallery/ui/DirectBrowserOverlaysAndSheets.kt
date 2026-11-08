package com.amresalehin.emregallery.ui

import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.amresalehin.emregallery.data.BotDetectionMitigator
import com.amresalehin.emregallery.data.BrowserHistoryManager
import com.amresalehin.emregallery.data.BrowserShortcut
import com.amresalehin.emregallery.data.BrowserTabItem
import kotlinx.coroutines.launch


@Composable
internal fun BoxScope.DirectBrowserViewportOverlays(
    currentUrl: String,
    shortcutsList: List<BrowserShortcut>,
    allHistorySites: List<BrowserSite>,
    tabsList: List<BrowserTabItem>,
    scrollHandler: BrowserToolbarScrollHandler,
    isDark: Boolean,
    onNavigate: (String) -> Unit,
    onShowAddShortcut: () -> Unit,
    onEditShortcut: (BrowserShortcut) -> Unit,
    onShowHistory: () -> Unit,
    onShowTabs: () -> Unit,
    onShowSettings: () -> Unit,
    onOpenDownloads: (() -> Unit)?,
    onSwitchToMediaTab: () -> Unit
) {
                // 2. Minimalist Monochrome Mobile Browser Home Layout (blank when empty)
                if (currentUrl.isBlank()) {
                    BrowserHomeContent(
                        shortcuts = shortcutsList,
                        recentSites = allHistorySites,
                        onNavigate = { onNavigate(it) },
                        onAddShortcutClick = { onShowAddShortcut() },
                        onEditShortcutClick = { onEditShortcut(it) },
                        onSeeAllHistoryClick = { onShowHistory() },
                        isDark = isDark,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("browser_empty_home_page")
                    )
                }

                // 3. Floating Bottom Action Bar with auto-hide slide-and-fade animation
                androidx.compose.animation.AnimatedVisibility(
                    visible = scrollHandler.isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                    exit = slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut(animationSpec = tween(durationMillis = 200)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    BrowserBottomNavBar(
                        tabCount = tabsList.size,
                        onOpenDownloads = {
                            onOpenDownloads?.invoke() ?: onSwitchToMediaTab()
                        },
                        onOpenHistory = {
                            onShowHistory()
                        },
                        onOpenTabs = {
                            onShowTabs()
                        },
                        onOpenBrowserSettings = {
                            onShowSettings()
                        },
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
}

@Composable
internal fun DirectBrowserDialogsAndSheets(
    isDark: Boolean,
    currentUrl: String,
    shortcutsList: List<BrowserShortcut>,
    allHistorySites: List<BrowserSite>,
    tabsList: List<BrowserTabItem>,
    historyManager: BrowserHistoryManager,
    webViewInstance: WebView?,
    isDesktopMode: Boolean,
    onDesktopModeChange: (Boolean) -> Unit,
    showAddShortcutDialog: Boolean,
    onDismissAddShortcut: () -> Unit,
    selectedShortcutForEdit: BrowserShortcut?,
    onDismissEditShortcut: () -> Unit,
    showHistorySheet: Boolean,
    onDismissHistorySheet: () -> Unit,
    showTabsSheet: Boolean,
    onDismissTabsSheet: () -> Unit,
    showSettingsSheet: Boolean,
    onDismissSettingsSheet: () -> Unit,
    onNavigate: (String) -> Unit,
    onClearCurrentPage: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    // Modal Dialogs & Sheets
    if (showAddShortcutDialog) {
        AddShortcutDialog(
            onDismiss = { onDismissAddShortcut() },
            onAdd = { title, url ->
                coroutineScope.launch {
                    historyManager.addShortcut(title, url)
                }
            },
            isDark = isDark
        )
    }

    selectedShortcutForEdit?.let { sc ->
        EditShortcutDialog(
            shortcut = sc,
            onDismiss = { onDismissEditShortcut() },
            onUpdate = { title, url ->
                coroutineScope.launch {
                    historyManager.updateShortcut(sc.id, title, url)
                }
            },
            onDelete = {
                coroutineScope.launch {
                    historyManager.removeShortcut(sc.id)
                }
            },
            isDark = isDark
        )
    }

    if (showHistorySheet) {
        BrowserHistoryBottomSheet(
            history = allHistorySites,
            onNavigate = { onNavigate(it) },
            onDeleteSite = { url ->
                coroutineScope.launch {
                    historyManager.deleteSite(url)
                }
            },
            onClearAll = {
                coroutineScope.launch {
                    historyManager.clearHistory()
                }
            },
            onDismiss = { onDismissHistorySheet() },
            isDark = isDark
        )
    }

    if (showTabsSheet) {
        BrowserTabsBottomSheet(
            tabs = tabsList,
            currentUrl = currentUrl,
            onSelectTab = { tab ->
                if (tab.url.isNotBlank()) {
                    onNavigate(tab.url)
                } else {
                    onClearCurrentPage()
                }
            },
            onCloseTab = { tabId ->
                val remaining = tabsList.filter { it.id != tabId }
                coroutineScope.launch {
                    historyManager.saveTabs(remaining)
                }
            },
            onNewTab = {
                onClearCurrentPage()
                val newTabs = tabsList + BrowserTabItem("tab_${System.currentTimeMillis()}", "New Tab", "")
                coroutineScope.launch {
                    historyManager.saveTabs(newTabs)
                }
            },
            onDismiss = { onDismissTabsSheet() },
            isDark = isDark
        )
    }

    if (showSettingsSheet) {
        BrowserSettingsBottomSheet(
            isDesktopMode = isDesktopMode,
            onDesktopModeChange = { desktop ->
                onDesktopModeChange(desktop)
                webViewInstance?.settings?.apply {
                    userAgentString = if (desktop) {
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    } else {
                        BotDetectionMitigator.STEALTH_USER_AGENT
                    }
                    useWideViewPort = desktop
                    loadWithOverviewMode = desktop
                }
                webViewInstance?.reload()
            },
            onClearCacheClick = {
                webViewInstance?.clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            },
            onDismiss = { onDismissSettingsSheet() },
            isDark = isDark
        )
    }
}
