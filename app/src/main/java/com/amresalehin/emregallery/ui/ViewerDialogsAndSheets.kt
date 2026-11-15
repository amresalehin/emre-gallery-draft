package com.amresalehin.emregallery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Coordinates the viewer's secondary "More" options sheet and destructive
 * confirmation dialogs (Move to Bin / Permanent Delete).
 */
@Composable
internal fun ViewerDialogsAndSheets(
    itemName: String,
    isBin: Boolean,
    isHidden: Boolean,
    isArchived: Boolean,
    showDetails: Boolean,
    showMore: Boolean,
    confirmMoveToBin: Boolean,
    confirmPermanentDelete: Boolean,
    hasPermanentDeleteAction: Boolean,
    onDismissMore: () -> Unit,
    onToggleBin: () -> Unit,
    onRequestMoveToBin: () -> Unit,
    onDismissMoveToBin: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleArchive: () -> Unit,
    onAddTo: () -> Unit,
    onRequestPermanentDelete: () -> Unit,
    onDismissPermanentDelete: () -> Unit,
    onConfirmPermanentDelete: () -> Unit,
    onToggleDetails: () -> Unit
) {
    if (showMore) {
        ViewerMoreBottomSheet(
            isBin = isBin,
            isHidden = isHidden,
            isArchived = isArchived,
            onToggleBin = {
                if (isBin) {
                    onToggleBin()
                } else {
                    onRequestMoveToBin()
                }
            },
            onToggleHide = onToggleHide,
            onToggleArchive = onToggleArchive,
            onMove = onAddTo,
            onDeletePermanently = if (isBin && hasPermanentDeleteAction) onRequestPermanentDelete else null,
            onShowDetails = { if (!showDetails) onToggleDetails() },
            onDismiss = onDismissMore
        )
    }

    if (confirmMoveToBin) {
        AlertDialog(
            onDismissRequest = onDismissMoveToBin,
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Move to Bin?") },
            text = { Text("Move \"$itemName\" to the device Bin? You can restore it later from the Bin.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissMoveToBin()
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
                TextButton(onClick = onDismissMoveToBin) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (confirmPermanentDelete) {
        AlertDialog(
            onDismissRequest = onDismissPermanentDelete,
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Delete permanently?") },
            text = { Text("Permanently delete \"$itemName\" from this device? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissPermanentDelete()
                        onConfirmPermanentDelete()
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
                TextButton(onClick = onDismissPermanentDelete) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
