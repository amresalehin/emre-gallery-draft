package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.amresalehin.emregallery.data.MediaItem

@Composable
internal fun ViewerScreenSheetsAndConfirmDialogs(
    item: MediaItem,
    isBin: Boolean,
    isHidden: Boolean,
    isArchived: Boolean,
    showMore: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    confirmMoveToBin: Boolean,
    onConfirmMoveToBinChange: (Boolean) -> Unit,
    confirmPermanentDelete: Boolean,
    onConfirmPermanentDeleteChange: (Boolean) -> Unit,
    onToggleBin: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleArchive: () -> Unit,
    onAddTo: () -> Unit,
    onDeletePermanently: (() -> Unit)?
) {
    // "More" options modal bottom sheet
    if (showMore) {
        ViewerMoreBottomSheet(
            isBin = isBin,
            isHidden = isHidden,
            isArchived = isArchived,
            onToggleBin = {
                if (isBin) {
                    onToggleBin()
                } else {
                    onConfirmMoveToBinChange(true)
                }
            },
            onToggleHide = onToggleHide,
            onToggleArchive = onToggleArchive,
            onMove = onAddTo,
            onDeletePermanently = if (isBin && onDeletePermanently != null) { { onConfirmPermanentDeleteChange(true) } } else null,
            onShowDetails = { if (!showDetails) onToggleDetails() },
            onDismiss = { onShowMoreChange(false) }
        )
    }

    // Confirmation dialog for moving to Bin
    if (confirmMoveToBin) {
        AlertDialog(
            onDismissRequest = { onConfirmMoveToBinChange(false) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Move to Bin?") },
            text = { Text("Move \"${item.name}\" to the device Bin? You can restore it later from the Bin.") },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmMoveToBinChange(false)
                        onToggleBin()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("Move to Bin")
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmMoveToBinChange(false) }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Confirmation dialog for permanent delete
    if (confirmPermanentDelete) {
        AlertDialog(
            onDismissRequest = { onConfirmPermanentDeleteChange(false) },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Delete permanently?") },
            text = { Text("Permanently delete \"${item.name}\" from this device? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmPermanentDeleteChange(false)
                        onDeletePermanently?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmPermanentDeleteChange(false) }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
