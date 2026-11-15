package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MediaInboxTopBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    currentViewMode: DownloaderViewMode,
    searchQuery: String,
    isSearchFocused: Boolean,
    onExitSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onClearSearchFocus: () -> Unit,
    onHideKeyboard: () -> Unit,
    onSwitchViewMode: (DownloaderViewMode) -> Unit,
    onShowFilterSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            if (isSelectionMode) {
                InboxSelectionToolbar(
                    selectedCount = selectedCount,
                    allSelected = allSelected,
                    onExitSelection = onExitSelection,
                    onToggleSelectAll = onToggleSelectAll,
                    onDeleteSelected = onDeleteSelected
                )
            } else {
                InboxSearchPillBar(
                    currentViewMode = currentViewMode,
                    searchQuery = searchQuery,
                    isSearchFocused = isSearchFocused,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchFocusChange = onSearchFocusChange,
                    onClearSearchFocus = onClearSearchFocus,
                    onHideKeyboard = onHideKeyboard,
                    onSwitchViewMode = onSwitchViewMode,
                    onShowFilterSheet = onShowFilterSheet
                )
            }
        }
    }
}

@Composable
private fun InboxSelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    onExitSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("inbox_selection_toolbar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onExitSelection,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("selection_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.width(4.dp))

            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .testTag("selection_count_text")
            )

            IconButton(
                onClick = onToggleSelectAll,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("selection_select_all_button")
            ) {
                Icon(
                    imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                    contentDescription = if (allSelected) "Deselect all" else "Select all",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDeleteSelected,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("selection_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete selected stacks",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun InboxSearchPillBar(
    currentViewMode: DownloaderViewMode,
    searchQuery: String,
    isSearchFocused: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onClearSearchFocus: () -> Unit,
    onHideKeyboard: () -> Unit,
    onSwitchViewMode: (DownloaderViewMode) -> Unit,
    onShowFilterSheet: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("inbox_search_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isSearchFocused || searchQuery.isNotEmpty()) {
                        onClearSearchFocus()
                    } else {
                        onSwitchViewMode(DownloaderViewMode.SEARCH_PAGE)
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
                Spacer(Modifier.width(2.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { onSwitchViewMode(DownloaderViewMode.SEARCH_PAGE) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (currentViewMode == DownloaderViewMode.SEARCH_PAGE) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            modifier = Modifier
                                .size(width = 34.dp, height = 30.dp)
                                .testTag("tab_search_fetch")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Fetch",
                                    tint = if (currentViewMode == DownloaderViewMode.SEARCH_PAGE) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = { onSwitchViewMode(DownloaderViewMode.SAVED_LIBRARY) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (currentViewMode == DownloaderViewMode.SAVED_LIBRARY) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            modifier = Modifier
                                .size(width = 34.dp, height = 30.dp)
                                .testTag("tab_saved_library")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Saved",
                                    tint = if (currentViewMode == DownloaderViewMode.SAVED_LIBRARY) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                Spacer(Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Find in Saved Library...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onHideKeyboard()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            onSearchFocusChange(focusState.isFocused)
                        }
                        .testTag("find_in_inbox_input")
                )
            }

            if (isSearchFocused || searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchQueryChange("") },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("inbox_search_clear_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!isSearchFocused && searchQuery.isEmpty()) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                IconButton(
                    onClick = onShowFilterSheet,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("inbox_tune_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Sort and filter inbox",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
