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


@Composable
internal fun BinConfirmationDialogs(
    items: List<MediaItem>,
    selectedUris: Set<String>,
    confirmEmptyBin: Boolean,
    onDismissEmptyBin: () -> Unit,
    onEmptyBin: (List<MediaItem>) -> Unit,
    itemToPermanentlyDelete: MediaItem?,
    onDismissDeleteSingle: () -> Unit,
    onDeletePermanently: (MediaItem) -> Unit,
    confirmBatchDeletePermanently: Boolean,
    onDismissBatchDelete: () -> Unit,
    onConfirmBatchDelete: () -> Unit
) {
    // Confirmation dialog: Empty entire bin
    if (confirmEmptyBin) {
        AlertDialog(
            onDismissRequest = { onDismissEmptyBin() },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Empty Bin?") },
            text = { Text("Permanently delete all ${items.size} items in the Bin from this device? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissEmptyBin()
                        onEmptyBin(items)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) { Text("Empty Bin") }
            },
            dismissButton = {
                TextButton(onClick = { onDismissEmptyBin() }) { Text("Cancel") }
            }
        )
    }

    // Confirmation dialog: Delete single item permanently
    val currentItemToDelete = itemToPermanentlyDelete
    if (currentItemToDelete != null) {
        val target = currentItemToDelete
        AlertDialog(
            onDismissRequest = { onDismissDeleteSingle() },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Delete permanently?") },
            text = { Text("Permanently delete \"${target.name}\" from this device? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val item = target
                        onDismissDeleteSingle()
                        onDeletePermanently(item)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onDismissDeleteSingle() }) { Text("Cancel") }
            }
        )
    }

    // Confirmation dialog: Delete selected batch permanently
    if (confirmBatchDeletePermanently) {
        AlertDialog(
            onDismissRequest = { onDismissBatchDelete() },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Delete permanently?") },
            text = { Text("Permanently delete ${selectedUris.size} selected items from this device? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmBatchDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) { Text("Delete permanently") }
            },
            dismissButton = {
                TextButton(onClick = { onDismissBatchDelete() }) { Text("Cancel") }
            }
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BinItemCard(
    item: MediaItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .trackThumbnailBounds(item.uri)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = onLongPress
                    )
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(item.uri, context) {
                        ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                    },
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                    },
                    contentScale = ContentScale.Crop
                )
                if (item.isVideo) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .graphicsLayer {
                                alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                            },
                        tint = Color.White
                    )
                }

                if (selectionMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(18.dp)
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = onRestore,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restore", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    IconButton(
                        onClick = onDeletePermanently,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = "Delete ${item.name} permanently",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
