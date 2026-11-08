package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.*

@Composable
internal fun PocketTopBarSurface(
    isDark: Boolean,
    isSelectionMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    clipboardUrl: String?,
    onClearClipboardUrl: () -> Unit,
    pocketRepo: PocketRepository,
    snackbarHostState: SnackbarHostState,
    onMenuClick: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    onOpenAddBookmark: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRequestDeleteSelected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
                Surface(
                    color = PocketThemeColors.background(isDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isSelectionMode) {
                            PocketSelectionToolbar(
                                selectedCount = selectedCount,
                                allSelected = filteredStacks.isNotEmpty() && filteredStacks.all { it.id in selectedStackIds },
                                onClearSelection = { onClearSelection() },
                                onToggleSelectAll = {
                                    val all = filteredStacks.isNotEmpty() && filteredStacks.all { it.id in selectedStackIds }
                                    selectedStackIds = if (all) emptySet() else filteredStacks.map { it.id }.toSet()
                                },
                                onShare = {
                                    val urlsToShare = filteredStacks
                                        .filter { it.id in selectedStackIds && it.sourceUrl.isNotBlank() }
                                        .map { "${it.title}: ${it.sourceUrl}" }
                                    if (urlsToShare.isNotEmpty()) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, urlsToShare.joinToString("\n\n"))
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share bookmarks"))
                                    }
                                },
                                onDelete = { onRequestDeleteSelected() },
                                isDark = isDark
                            )
                        } else {
                            val isUrlInQuery = searchQuery.isNotBlank() && (
                                    MediaClassifier.isValidWebUrl(searchQuery.trim()) ||
                                            searchQuery.trim().startsWith("http://") ||
                                            searchQuery.trim().startsWith("https://")
                                    )
                            val hasActiveFilters = filterType != "All" || sortOrder != "Newest"
                            PocketSearchPasteCapsule(
                                query = searchQuery,
                                onQueryChange = { onSearchQueryChange(it) },
                                isFocused = isSearchFocused,
                                onFocusChange = { onSearchFocusChange(it) },
                                onMenuClick = onMenuClick,
                                onOpenFilterSheet = { onOpenFilterSheet() },
                                hasActiveFilters = hasActiveFilters,
                                onBookmarkAction = {
                                    val trimmed = searchQuery.trim()
                                    if (trimmed.isNotBlank()) {
                                        val domain = MediaClassifier.cleanDomainFromUrl(trimmed)
                                        val saved = pocketRepo.addBookmark(
                                            title = domain.ifBlank { trimmed },
                                            url = trimmed
                                        )
                                        searchQuery = ""
                                        isSearchFocused = false
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        selectedStackForDetail = saved
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Bookmark saved to Pocket")
                                        }
                                    } else if (clipboardUrl != null) {
                                        val domain = MediaClassifier.cleanDomainFromUrl(clipboardUrl!!)
                                        val saved = pocketRepo.addBookmark(
                                            title = domain.ifBlank { clipboardUrl!! },
                                            url = clipboardUrl!!
                                        )
                                        onClearClipboardUrl()
                                        isSearchFocused = false
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        selectedStackForDetail = saved
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Bookmark saved from clipboard")
                                        }
                                    } else {
                                        onOpenAddBookmark()
                                    }
                                },
                                onSearchSubmit = { target ->
                                    val trimmed = target.trim()
                                    if (trimmed.isNotBlank()) {
                                        if (MediaClassifier.isValidWebUrl(trimmed) || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                            val domain = MediaClassifier.cleanDomainFromUrl(trimmed)
                                            val saved = pocketRepo.addBookmark(
                                                title = domain.ifBlank { trimmed },
                                                url = trimmed
                                            )
                                            searchQuery = ""
                                            isSearchFocused = false
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            selectedStackForDetail = saved
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Bookmark saved to Pocket")
                                            }
                                        } else {
                                            keyboardController?.hide()
                                        }
                                    }
                                },
                                isDark = isDark,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
}
