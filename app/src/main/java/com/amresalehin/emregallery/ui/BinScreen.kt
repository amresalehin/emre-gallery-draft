package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.amresalehin.emregallery.data.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(
    items: List<MediaItem>,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onRestore: (MediaItem) -> Unit,
    onRestoreBatch: (Set<String>) -> Unit,
    onDeletePermanently: (MediaItem) -> Unit,
    onDeletePermanentlyBatch: (List<MediaItem>) -> Unit,
    onEmptyBin: (List<MediaItem>) -> Unit
) {
    val context = LocalContext.current
    var confirmEmptyBin by remember { mutableStateOf(false) }
    var itemToPermanentlyDelete by remember { mutableStateOf<MediaItem?>(null) }
    var confirmBatchDeletePermanently by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedUris.isNotEmpty()

    BackHandler(enabled = selectionMode) {
        selectedUris = emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .destinationTopBarEntrance(LocalViewerCoordinatedProgress.current)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("bin_back_button")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to Albums")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Bin",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${items.size} ${if (items.size == 1) "item" else "items"} · Restore or delete permanently",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { confirmEmptyBin = true },
                    enabled = items.isNotEmpty(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("bin_empty_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Empty bin", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // Selection Action Bar
        AnimatedVisibility(
            visible = selectionMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedUris = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "Exit selection")
                    }
                    Text(
                        "${selectedUris.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        selectedUris = if (selectedUris.size == items.size) emptySet() else items.map { it.uri }.toSet()
                    }) {
                        Text(if (selectedUris.size == items.size) "Deselect all" else "Select all")
                    }
                    FilledTonalButton(
                        onClick = {
                            onRestoreBatch(selectedUris)
                            selectedUris = emptySet()
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restore")
                    }
                    IconButton(
                        onClick = { confirmBatchDeletePermanently = true },
                        modifier = Modifier.testTag("bin_delete_selected_permanently")
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = "Delete selected permanently",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        val hapticFeedback = LocalHapticFeedback.current
        val pullRefreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onRefresh?.invoke()
            },
            modifier = Modifier.fillMaxSize().weight(1f),
            state = pullRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(92.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Bin is empty",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Photos and videos you move to the Bin will appear here. You can restore them or permanently remove them from your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(items, key = { it.uri }) { item ->
                    val isSelected = item.uri in selectedUris
                    BinItemCard(
                        item = item,
                        isSelected = isSelected,
                        selectionMode = selectionMode,
                        onOpen = {
                            if (selectionMode) {
                                selectedUris = if (isSelected) selectedUris - item.uri else selectedUris + item.uri
                            } else {
                                onOpen(item)
                            }
                        },
                        onLongPress = {
                            selectedUris = selectedUris + item.uri
                        },
                        onRestore = { onRestore(item) },
                        onDeletePermanently = { itemToPermanentlyDelete = item }
                    )
                }
            }
        }
    }
    }
    }


    BinConfirmationDialogs(
        items = items,
        selectedUris = selectedUris,
        confirmEmptyBin = confirmEmptyBin,
        onDismissEmptyBin = { confirmEmptyBin = false },
        onEmptyBin = onEmptyBin,
        itemToPermanentlyDelete = itemToPermanentlyDelete,
        onDismissDeleteSingle = { itemToPermanentlyDelete = null },
        onDeletePermanently = onDeletePermanently,
        confirmBatchDeletePermanently = confirmBatchDeletePermanently,
        onDismissBatchDelete = { confirmBatchDeletePermanently = false },
        onConfirmBatchDelete = {
            confirmBatchDeletePermanently = false
            val targets = items.filter { it.uri in selectedUris }
            selectedUris = emptySet()
            onDeletePermanentlyBatch(targets)
        }
    )
}
