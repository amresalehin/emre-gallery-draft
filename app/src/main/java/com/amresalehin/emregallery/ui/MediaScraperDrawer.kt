package com.amresalehin.emregallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Older drawer opened paths are deprecated in favor of [DownloaderResultsPage].
 * This composable delegates directly to [DownloaderResultsPage] to avoid maintaining duplicate
 * extraction pipelines.
 */
@Deprecated("Use DownloaderResultsPage directly instead")
@Composable
fun MediaScraperDrawer(
    initialUrl: String? = null,
    onClose: () -> Unit,
    onDownloadComplete: () -> Unit,
    onOpenBrowser: ((String) -> Unit)? = null,
    heightFraction: Float = 0.88f,
    showSearchBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!initialUrl.isNullOrBlank()) {
        DownloaderResultsPage(
            targetUrl = initialUrl,
            onBack = onClose,
            onOpenBrowser = onOpenBrowser,
            onDownloadComplete = { onDownloadComplete() },
            onViewSavedLibrary = onClose,
            modifier = modifier
        )
    }
}
