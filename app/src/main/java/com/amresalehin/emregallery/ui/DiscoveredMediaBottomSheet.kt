package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.ScrapedMediaItem

/**
 * Modal bottom sheet presenting media discovered during in-app web browsing,
 * with category filtering, selection toggles, and batch download execution.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveredMediaBottomSheet(
    filterOptions: List<String>,
    discoveredItems: List<ScrapedMediaItem>,
    filteredItems: List<ScrapedMediaItem>,
    selectedFilter: String?,
    selectedItemIds: Set<String>,
    isDownloading: Boolean,
    downloadProgress: Float,
    onToggleFilter: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onItemSelectionChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onDownloadSelected: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val isAllSelected = filteredItems.isNotEmpty() &&
                selectedItemIds.containsAll(filteredItems.map { it.id }.toSet())

            DiscoveredMediaFilterBar(
                filterOptions = filterOptions,
                discoveredItems = discoveredItems,
                selectedFilter = selectedFilter,
                isAllSelected = isAllSelected,
                onToggleFilter = onToggleFilter,
                onToggleSelectAll = onToggleSelectAll
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems) { item ->
                    val isSelected = item.id in selectedItemIds
                    DiscoveredMediaItemRow(
                        item = item,
                        isSelected = isSelected,
                        onSelectionChange = { selected ->
                            onItemSelectionChange(item.id, selected)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDownloadSelected,
                enabled = selectedItemIds.isNotEmpty() && !isDownloading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Downloading (${(downloadProgress * 100).toInt()}%)...")
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download (${selectedItemIds.size}) to Inbox")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
