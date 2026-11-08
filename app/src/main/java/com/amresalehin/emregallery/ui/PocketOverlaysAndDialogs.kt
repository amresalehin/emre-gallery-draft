package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.amresalehin.emregallery.data.MediaInboxStack
import com.amresalehin.emregallery.data.PocketRepository
import kotlinx.coroutines.launch
import java.util.*


@Composable
internal fun BoxScope.PocketOverlaysAndDialogs(
    isDark: Boolean,
    pocketRepo: PocketRepository,
    snackbarHostState: SnackbarHostState,
    selectedStackForDetail: MediaInboxStack?,
    onSelectedStackForDetailChange: (MediaInboxStack?) -> Unit,
    onDetailViewerStateChange: ((Boolean) -> Unit)?,
    showFullScreenBrowser: Boolean,
    onShowFullScreenBrowserChange: (Boolean) -> Unit,
    browserUrl: String,
    onBrowserUrlChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    onOpenDiscoveredMedia: () -> Unit,
    showAddBookmarkDialog: Boolean,
    onShowAddBookmarkDialogChange: (Boolean) -> Unit,
    editingStack: MediaInboxStack?,
    onEditingStackChange: (MediaInboxStack?) -> Unit,
    showDeleteConfirmDialog: Boolean,
    onShowDeleteConfirmDialogChange: (Boolean) -> Unit,
    selectedStackIds: Set<String>,
    onSelectedStackIdsChange: (Set<String>) -> Unit,
    showFilterSheet: Boolean,
    onShowFilterSheetChange: (Boolean) -> Unit,
    sortOrder: String,
    onSortOrderChange: (String) -> Unit,
    filterType: String,
    onFilterTypeChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
        AnimatedVisibility(
            visible = selectedStackForDetail != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(50f)
        ) {
            selectedStackForDetail?.let { stack ->
                PocketBookmarkViewerPage(
                    stack = stack,
                    onBack = {
                        onSelectedStackForDetailChange(null)
                        onDetailViewerStateChange?.invoke(false)
                    },
                    onOpenBrowser = { url ->
                        onBrowserUrlChange(url)
                        onShowFullScreenBrowserChange(true)
                    },
                    onDelete = {
                        pocketRepo.deleteBookmark(stack.id)
                        onSelectedStackForDetailChange(null)
                        onDetailViewerStateChange?.invoke(false)
                        scope.launch {
                            snackbarHostState.showSnackbar("Bookmark deleted")
                        }
                    },
                    onEditNote = { onEditingStackChange(stack) },
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Fullscreen in-app web browser
        if (showFullScreenBrowser) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
            ) {
                DirectBrowserView(
                    initialUrl = browserUrl,
                    onClose = { onShowFullScreenBrowserChange(false) },
                    onMenuClick = onMenuClick,
                    onMediaExtracted = {},
                    onBatchExtracted = { _, _, _ -> },
                    onSwitchToMediaTab = onOpenDiscoveredMedia,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Add Bookmark Dialog
        if (showAddBookmarkDialog) {
            AddBookmarkDialog(
                onDismiss = { onShowAddBookmarkDialogChange(false) },
                onSave = { title, url, description ->
                    val added = pocketRepo.addBookmark(
                        title = title,
                        url = url,
                        description = description
                    )
                    onShowAddBookmarkDialogChange(false)
                    onSelectedStackForDetailChange(added)
                    scope.launch {
                        snackbarHostState.showSnackbar("Bookmark saved to Pocket")
                    }
                },
                isDark = isDark
            )
        }

        // Edit Bookmark Dialog
        editingStack?.let { stack ->
            EditBookmarkDialog(
                stack = stack,
                onDismiss = { onEditingStackChange(null) },
                onSave = { updatedTitle, updatedDesc ->
                    val updated = stack.copy(
                        title = updatedTitle,
                        description = updatedDesc
                    )
                    pocketRepo.updateBookmark(updated)
                    if (selectedStackForDetail?.id == stack.id) {
                        onSelectedStackForDetailChange(updated)
                    }
                    onEditingStackChange(null)
                    scope.launch {
                        snackbarHostState.showSnackbar("Bookmark updated")
                    }
                },
                isDark = isDark
            )
        }

        // Delete confirmation dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { onShowDeleteConfirmDialogChange(false) },
                containerColor = if (isDark) Color(0xFF1C1C1C) else Color(0xFFFFFFFF),
                title = {
                    Text(
                        text = "Delete Bookmarks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PocketThemeColors.primaryText(isDark)
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove ${selectedStackIds.size} bookmarks from Pocket?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketThemeColors.secondaryText(isDark)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pocketRepo.deleteBookmarks(selectedStackIds)
                            onSelectedStackIdsChange(emptySet())
                            onShowDeleteConfirmDialogChange(false)
                            scope.launch {
                                snackbarHostState.showSnackbar("Bookmarks deleted")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFFEEEEEE) else Color(0xFF202020),
                            contentColor = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF)
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onShowDeleteConfirmDialogChange(false) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PocketThemeColors.secondaryText(isDark)
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Filter & Sort Bottom Sheet
        if (showFilterSheet) {
            PocketFilterBottomSheet(
                currentSort = sortOrder,
                currentFilter = filterType,
                onSelectSort = onSortOrderChange,
                onSelectFilter = onFilterTypeChange,
                onDismiss = { onShowFilterSheetChange(false) },
                isDark = isDark
            )
        }
}
