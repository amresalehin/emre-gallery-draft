package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Capsule search bar container on the downloader landing page.
 * Delegates mode tabs, URL text input, and contextual trailing actions to focused subcomponents.
 */
@Composable
fun DownloaderSearchBar(
    urlInput: String,
    isSearchFocused: Boolean,
    onUrlInputChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onClearEditAndFocus: () -> Unit,
    onViewSavedLibrary: () -> Unit,
    onSubmitSearch: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onPasteAndFetch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("downloader_main_search_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isSearchFocused || urlInput.isNotEmpty()) {
                        onClearEditAndFocus()
                    } else {
                        onBackClick()
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("downloader_main_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!isSearchFocused) {
                DownloaderSearchModeSwitcher(onViewSavedLibrary = onViewSavedLibrary)
            }

            Spacer(Modifier.width(8.dp))

            DownloaderSearchUrlField(
                urlInput = urlInput,
                onUrlInputChange = onUrlInputChange,
                onSearchFocusChange = onSearchFocusChange,
                onSubmitSearch = onSubmitSearch,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(6.dp))

            DownloaderSearchTrailingActions(
                urlInput = urlInput,
                onUrlInputChange = onUrlInputChange,
                onOpenInBrowser = onOpenInBrowser,
                onSubmitSearch = onSubmitSearch,
                onPasteAndFetch = onPasteAndFetch
            )
        }
    }
}
