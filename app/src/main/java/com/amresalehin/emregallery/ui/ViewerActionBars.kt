package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaItem


fun ViewerBottomActionBar(
    isBin: Boolean,
    isHidden: Boolean = false,
    isArchived: Boolean = false,
    onToggleBin: () -> Unit,
    onToggleHide: () -> Unit = {},
    onToggleArchive: () -> Unit = {},
    onMove: () -> Unit = {},
    onDeletePermanently: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBin) {
            ViewerAction(
                icon = Icons.Default.Restore,
                label = "Restore",
                onClick = onToggleBin,
                modifier = Modifier.testTag("viewer_restore_button")
            )
            if (onDeletePermanently != null) {
                ViewerAction(
                    icon = Icons.Default.DeleteForever,
                    label = "Delete permanently",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onDeletePermanently,
                    modifier = Modifier.testTag("viewer_delete_permanently_button")
                )
            }
        } else {
            // Lock button
            ViewerAction(
                icon = if (isHidden) Icons.Default.LockOpen else Icons.Default.Lock,
                label = if (isHidden) "Unlock" else "Lock",
                onClick = onToggleHide,
                modifier = Modifier.testTag("viewer_lock_button")
            )

            // Archive button
            ViewerAction(
                icon = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                label = if (isArchived) "Unarchive" else "Archive",
                onClick = onToggleArchive,
                modifier = Modifier.testTag("viewer_archive_button")
            )

            // Move button (for move to folder)
            ViewerAction(
                icon = Icons.Default.DriveFileMove,
                label = "Move",
                onClick = onMove,
                modifier = Modifier.testTag("viewer_move_button")
            )

            // Delete button
            ViewerAction(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = onToggleBin,
                modifier = Modifier.testTag("viewer_delete_button")
            )
        }
    }
}

/**
 * Bottom sheet for extra media actions (Archive, Lock, Bin, Move to folder, Info).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerMoreBottomSheet(
    isBin: Boolean,
    isHidden: Boolean,
    isArchived: Boolean,
    onToggleBin: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleArchive: () -> Unit,
    onMove: (() -> Unit)? = null,
    onDeletePermanently: (() -> Unit)? = null,
    onShowDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (isBin) {
            ListItem(
                headlineContent = { Text("Restore to gallery") },
                leadingContent = { Icon(Icons.Default.Restore, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleBin() }
            )
            if (onDeletePermanently != null) {
                ListItem(
                    headlineContent = { Text("Delete permanently from device") },
                    leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.clickable { onDismiss(); onDeletePermanently() }
                )
            }
        } else {
            if (onMove != null) {
                ListItem(
                    headlineContent = { Text("Move to folder / album") },
                    leadingContent = { Icon(Icons.Default.DriveFileMove, null) },
                    modifier = Modifier.clickable { onDismiss(); onMove() }
                )
            }
            ListItem(
                headlineContent = { Text(if (isHidden) "Unhide (return to gallery)" else "Move to Locked folder") },
                leadingContent = { Icon(if (isHidden) Icons.Default.Visibility else Icons.Default.Lock, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleHide() }
            )
            ListItem(
                headlineContent = { Text(if (isArchived) "Unarchive" else "Archive") },
                leadingContent = { Icon(Icons.Default.Archive, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleArchive() }
            )
            ListItem(
                headlineContent = { Text("Move to Bin") },
                leadingContent = { Icon(Icons.Default.Delete, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleBin() }
            )
        }
        ListItem(
            headlineContent = { Text("Media information") },
            leadingContent = { Icon(Icons.Default.Info, null) },
            modifier = Modifier.clickable { onDismiss(); onShowDetails() }
        )
        ListItem(
            headlineContent = { Text("Cancel") },
            leadingContent = { Icon(Icons.Default.Close, null) },
            modifier = Modifier.clickable { onDismiss() }
        )
        Spacer(Modifier.height(24.dp))
    }
}
