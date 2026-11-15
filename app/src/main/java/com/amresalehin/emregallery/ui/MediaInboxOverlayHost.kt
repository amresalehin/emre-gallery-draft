package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/**
 * Renders modal dialogs, options sheets, stack detail overlay, and full-screen browser
 * layered above the primary [MediaInboxScreen] scaffold.
 */
@Composable
internal fun MediaInboxOverlayHost(
    controller: MediaInboxController,
    onOpenMediaItem: ((String) -> Unit)?,
    onOpenMediaWithList: ((String, List<String>) -> Unit)?
) {
    if (controller.showDeleteConfirmationDialog) {
        InboxDeleteConfirmationDialog(
            selectedCount = controller.selectedStackIds.size,
            onConfirm = controller::confirmDeleteSelectedStacks,
            onDismiss = { controller.showDeleteConfirmationDialog = false }
        )
    }

    if (controller.showFilterSheet) {
        InboxOptionsBottomSheet(
            sortOrder = controller.sortOrder,
            hasStacks = controller.stacks.isNotEmpty(),
            onSortOrderChange = { controller.sortOrder = it },
            onClearAll = controller::clearAllStacks,
            onDismiss = { controller.showFilterSheet = false }
        )
    }

    AnimatedVisibility(
        visible = controller.selectedStackForDetail != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
    ) {
        controller.selectedStackForDetail?.let { stack ->
            MediaStackDetailTab(
                stack = stack,
                onClose = { controller.selectedStackForDetail = null },
                onOpenMedia = { uri ->
                    val allUris = stack.items.map { it.localUri.ifBlank { it.sourceUrl } }
                    if (onOpenMediaWithList != null) {
                        onOpenMediaWithList(uri, allUris)
                    } else {
                        onOpenMediaItem?.invoke(uri)
                    }
                },
                onOpenBrowser = controller::openBrowser,
                onDeleteStack = { controller.deleteSingleStack(stack, closeDetail = true) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    AnimatedVisibility(
        visible = controller.showFullScreenBrowser,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(25f)
    ) {
        FullScreenBrowserView(
            initialUrl = controller.browserInitialUrl,
            onClose = controller::closeBrowser,
            onDownloadComplete = controller::notifyBrowserDownloadsComplete,
            modifier = Modifier.fillMaxSize()
        )
    }
}
