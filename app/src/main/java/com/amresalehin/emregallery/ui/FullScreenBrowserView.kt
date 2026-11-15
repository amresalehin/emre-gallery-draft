package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Full-screen in-app Chromium browser launched from the Media Downloader page.
 * Allows web browsing and auto-detects downloadable media on any visited site.
 */
@Composable
fun FullScreenBrowserView(
    initialUrl: String = "",
    onClose: () -> Unit,
    onDownloadComplete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val controller = rememberFullScreenBrowserController(
        initialUrl = initialUrl,
        onDownloadComplete = onDownloadComplete
    )

    BackHandler {
        if (controller.showDiscoveredMediaSheet) {
            controller.showDiscoveredMediaSheet = false
        } else {
            onClose()
        }
    }

    Surface(
        modifier = modifier.testTag("full_screen_browser_view"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DirectBrowserView(
                initialUrl = controller.currentUrl,
                onClose = onClose,
                onUrlChange = { controller.currentUrl = it },
                onPageTitleChanged = { controller.pageTitle = it },
                onMediaExtracted = controller::onMediaExtracted,
                onBatchExtracted = { batch, title, _ ->
                    controller.onBatchExtracted(batch, title)
                },
                onSwitchToMediaTab = {
                    controller.showDiscoveredMediaSheet = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (controller.showDiscoveredMediaSheet) {
            DiscoveredMediaBottomSheet(
                filterOptions = BROWSER_MEDIA_FILTER_OPTIONS,
                discoveredItems = controller.discoveredItems,
                filteredItems = controller.filteredItems,
                selectedFilter = controller.selectedFilter,
                selectedItemIds = controller.selectedItemIds,
                isDownloading = controller.isDownloading,
                downloadProgress = controller.downloadProgress,
                onToggleFilter = controller::toggleFilter,
                onToggleSelectAll = controller::toggleSelectAllFiltered,
                onItemSelectionChange = controller::setItemSelected,
                onDismiss = { controller.showDiscoveredMediaSheet = false },
                onDownloadSelected = controller::downloadSelected
            )
        }
    }
}
