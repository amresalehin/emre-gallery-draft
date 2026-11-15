package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Animated trailing action cluster for [DownloaderSearchBar], toggling between
 * clipboard paste (empty input) and clear/browse/download controls (non-blank input).
 */
@Composable
internal fun DownloaderSearchTrailingActions(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onPasteAndFetch: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = urlInput.isNotBlank(),
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = "search_bar_actions",
        modifier = modifier
    ) { hasQuery ->
        if (hasQuery) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { onUrlInputChange("") },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("downloader_clear_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { onOpenInBrowser(urlInput) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("downloader_action_browse_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = "Browse web with search query",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { onSubmitSearch(urlInput) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("downloader_action_download_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Download media from URL",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            IconButton(
                onClick = onPasteAndFetch,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("downloader_paste_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste from clipboard and fetch",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
