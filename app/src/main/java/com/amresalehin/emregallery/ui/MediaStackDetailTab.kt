package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaInboxStack
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail Tab with stacked card carousel and lower metadata drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaStackDetailTab(
    stack: MediaInboxStack,
    onClose: () -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onDeleteStack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDrawerExpanded by remember { mutableStateOf(true) }

    val items = stack.items
    var currentIndex by remember(stack.id) { mutableIntStateOf(0) }
    val activeIndex = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val activeItem = items.getOrNull(activeIndex) ?: items.firstOrNull()

    val formattedDateTime = remember(stack.timestampMillis) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(stack.timestampMillis))
    }

    val totalBytes = remember(items) { items.sumOf { it.sizeBytes } }
    val formattedTotalSize = remember(totalBytes) {
        formatBytesLabel(totalBytes)
    }

    val domain = remember(stack.sourceDomain, stack.sourceUrl) {
        if (stack.sourceDomain.isNotBlank()) {
            stack.sourceDomain
        } else {
            runCatching { URL(stack.sourceUrl).host }.getOrDefault("Web")
        }
    }

    val faviconUrl = remember(domain) {
        "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    }

    val extractedTitle = remember(stack.title, activeItem, domain) {
        if (activeItem != null && activeItem.title.isNotBlank()) {
            activeItem.title
        } else if (stack.title.isNotBlank() && stack.title != "Untitled Stack" && stack.title != "Discovered Media") {
            stack.title
        } else {
            domain
        }
    }

    val extractedDescription = remember(stack.description, activeItem, activeIndex, items, domain, formattedTotalSize) {
        if (activeItem != null) {
            val formatStr = if (activeItem.format.isNotBlank()) "${activeItem.format} format" else "media file"
            val typeStr = if (activeItem.isVideo) "Video" else if (activeItem.isStream) "Live Stream" else "Image"
            val itemSizeStr = if (activeItem.sizeBytes > 0) {
                formatBytesLabel(activeItem.sizeBytes)
            } else {
                formattedTotalSize
            }
            "File ${activeIndex + 1} of ${items.size}: $typeStr in $formatStr ($itemSizeStr) extracted from $domain."
        } else if (stack.description.isNotBlank()) {
            stack.description
        } else {
            "Contains ${items.size} media files extracted from $domain. Total size: $formattedTotalSize."
        }
    }

    val targetBrowserUrl = stack.sourceUrl.ifBlank { "https://$domain" }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("media_stack_detail_tab")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            StackDetailTopBar(
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it },
                onClose = onClose,
                onOpenBrowser = { onOpenBrowser(targetBrowserUrl) },
                onViewInGallery = { activeItem?.let { onOpenMedia(it.localUri) } },
                onRequestDelete = { showDeleteConfirmDialog = true }
            )

            MediaStackDeck(
                stackId = stack.id,
                items = items,
                currentIndex = activeIndex,
                onCurrentIndexChange = { currentIndex = it },
                onOpenMedia = onOpenMedia,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            MediaStackDetailDrawer(
                formattedDateTime = formattedDateTime,
                faviconUrl = faviconUrl,
                sourceUrl = targetBrowserUrl,
                extractedTitle = extractedTitle,
                extractedDescription = extractedDescription,
                activeItem = activeItem,
                formattedTotalSize = formattedTotalSize,
                domain = domain,
                isDrawerExpanded = isDrawerExpanded,
                onToggleExpanded = { isDrawerExpanded = !isDrawerExpanded },
                onOpenBrowser = { onOpenBrowser(targetBrowserUrl) },
                onAddActionClick = { activeItem?.let { onOpenMedia(it.localUri) } }
            )
        }
    }

    if (showDeleteConfirmDialog) {
        StackDeleteConfirmationDialog(
            onConfirm = {
                showDeleteConfirmDialog = false
                onDeleteStack()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}

@Composable
private fun StackDetailTopBar(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenBrowser: () -> Unit,
    onViewInGallery: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("stack_detail_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(
                    onClick = { onMenuExpandedChange(true) },
                    modifier = Modifier.testTag("stack_detail_more_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("Open in Browser") },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onOpenBrowser()
                    }
                )
                DropdownMenuItem(
                    text = { Text("View in Gallery") },
                    leadingIcon = { Icon(Icons.Default.Visibility, null) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onViewInGallery()
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete Stack", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onRequestDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun StackDeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Stack?") },
        text = { Text("This will remove this stack and its downloaded files from your device.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatBytesLabel(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        bytes > 0 -> "$bytes B"
        else -> "Ready"
    }
}
