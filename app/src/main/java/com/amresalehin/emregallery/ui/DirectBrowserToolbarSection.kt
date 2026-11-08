package com.amresalehin.emregallery.ui

import android.content.Intent
import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.BotDetectionMitigator
import com.amresalehin.emregallery.data.BrowserHistoryManager
import com.amresalehin.emregallery.data.DownloaderSession
import kotlinx.coroutines.launch


@Composable
internal fun DirectBrowserToolbarSection(
    addressInput: String,
    onAddressInputChange: (String) -> Unit,
    currentUrl: String,
    isLoading: Boolean,
    loadingProgress: Float,
    canGoBack: Boolean,
    canGoForward: Boolean,
    detectedItemsCount: Int,
    recentSearchQueries: List<String>,
    allHistorySites: List<BrowserSite>,
    activeSearchEngine: BrowserSearchEngine,
    onSearchEngineChange: (BrowserSearchEngine) -> Unit,
    isSearchFocused: Boolean,
    onSearchFocusChange: (Boolean) -> Unit,
    isDark: Boolean,
    webViewInstance: WebView?,
    historyManager: BrowserHistoryManager,
    onNavigate: (String) -> Unit,
    onBackToSearchClick: (() -> Unit)?,
    onSwitchToMediaTab: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .background(BrowserThemeColors.background(isDark))
                    .testTag("browser_pinned_top_controls")
            ) {
            // 1. Minimalist Monochrome Top Bar (always visible)
            BrowserTopBar(
                addressInput = addressInput,
                onAddressChange = { onAddressInputChange(it) },
                onNavigate = { navigateToUrl(it) },
                onHamburgerClick = {
                    if (onMenuClick != null) {
                        onMenuClick.invoke()
                    } else {
                        showTabsSheet = true
                    }
                },
                onDiscoveredMediaClick = {
                    onOpenDiscoveredMedia?.invoke() ?: onSwitchToMediaTab()
                },
                discoveredCount = totalDetectedCount,
                onNewTabClick = {
                    currentUrl = ""
                    addressInput = ""
                    pageTitle = ""
                    onUrlChange?.invoke("")
                    tabSession.clearMedia()
                    tabSession.updateNavigation("")
                    DownloaderSession.clear()
                    scrollHandler.show()
                    webViewInstance?.loadUrl("about:blank")
                },
                onReloadClick = {
                    if (isLoading) webViewInstance?.stopLoading() else webViewInstance?.reload()
                },
                onPinCurrentSite = {
                    if (currentUrl.isNotBlank()) {
                        coroutineScope.launch {
                            historyManager.addShortcut(pageTitle.ifBlank { currentUrl }, currentUrl)
                        }
                    }
                },
                onShareClick = {
                    if (currentUrl.isNotBlank()) {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share link"))
                    }
                },
                onDesktopToggle = { desktop ->
                    isDesktopMode = desktop
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
                isDesktopMode = isDesktopMode,
                onClearHistoryClick = {
                    coroutineScope.launch {
                        historyManager.clearHistory()
                    }
                },
                onSettingsClick = {
                    showSettingsSheet = true
                },
                isWebPage = currentUrl.isNotBlank(),
                onHomeClick = {
                    currentUrl = ""
                    addressInput = ""
                    pageTitle = ""
                    onUrlChange?.invoke("")
                    tabSession.clearMedia()
                    tabSession.updateNavigation("")
                    DownloaderSession.clear()
                    scrollHandler.show()
                    webViewInstance?.loadUrl("about:blank")
                },
                onCloseBrowser = onClose,
                isDark = isDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Loading Progress Bar
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = BrowserThemeColors.textPrimary(isDark),
                    trackColor = BrowserThemeColors.divider(isDark)
                )
            }

            }

            val isChallengeActive = remember(pageTitle, currentUrl) {
                BotDetectionMitigator.isBotChallenge(pageTitle, currentUrl)
            }
            AnimatedVisibility(
                visible = isChallengeActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browser_bot_challenge_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF92400E),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Security Verification: Tap captcha checkbox if prompted to continue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF92400E),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
}
