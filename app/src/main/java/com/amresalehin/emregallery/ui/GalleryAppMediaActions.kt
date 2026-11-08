package com.amresalehin.emregallery.ui
import android.net.Uri
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.ThumbnailPrewarmManager
import com.amresalehin.emregallery.face.FaceClusterStore
internal fun GalleryAppSessionState.refreshMedia(userInitiated: Boolean = false) {
    scope.launch {
        if (userInitiated) {
            isRefreshing = true
        } else if (rawMedia.isEmpty()) {
            loading = true
        }
        val startTime = System.currentTimeMillis()
        launch(Dispatchers.IO) {
            lockedVaultItems = runCatching { lockedVaultManager.getVaultItems() }.getOrDefault(emptyList())
        }
        if (hasMediaPermission(context)) {
            runCatching {
                mediaRepository.loadMediaProgressive { batch, isComplete ->
                    val processed = withContext(Dispatchers.Default) {
                        val eligible = batch.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive }
                        processGalleryMedia(eligible, sortOrderPref, groupByPref, isComplete)
                    }
                    rawMedia = batch
                    media = batch
                    galleryData = processed
                    if (batch.isNotEmpty()) {
                        loading = false
                    }
                    if (isComplete) {
                        withContext(Dispatchers.Default) {
                            ThumbnailPrewarmManager.start(context.applicationContext, batch)
                        }
                    }
                }
            }
        }
        if (userInitiated) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 400L) {
                delay(400L - elapsed)
            }
            isRefreshing = false
        } else {
            loading = false
        }
    }
}
internal fun GalleryAppSessionState.permanentlyDelete(items: List<MediaItem>) {
    if (items.isEmpty()) return
    val itemUris = items.map { it.uri }.distinct()
    if (hasAllFilesAccess()) {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                itemUris.filter { uri ->
                    runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) > 0 }.getOrDefault(false)
                }
            }
            if (deleted.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    FaceClusterStore(context).deleteMediaCompletely(deleted)
                }
                prefs.removePermanently(deleted)
                refreshMedia()
                snackbarHostState.showSnackbar(
                    message = "${deleted.size} ${if (deleted.size == 1) "item" else "items"} permanently deleted",
                    duration = SnackbarDuration.Short
                )
            } else {
                snackbarHostState.showSnackbar("Permission denied: could not delete files")
            }
        }
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val requestLaunched = runCatching {
            val request = android.provider.MediaStore.createDeleteRequest(
                context.contentResolver,
                itemUris.map { Uri.parse(it) }
            )
            pendingPermanentDeleteUris = itemUris
            permanentDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            true
        }.getOrDefault(false)
        if (!requestLaunched) {
            scope.launch {
                snackbarHostState.showSnackbar("Permission denied: could not request deletion")
            }
        }
    } else {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                itemUris.filter { uri ->
                    runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) > 0 }.getOrDefault(false)
                }
            }
            if (deleted.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    FaceClusterStore(context).deleteMediaCompletely(deleted)
                }
                prefs.removePermanently(deleted)
                refreshMedia()
                snackbarHostState.showSnackbar(
                    message = "${deleted.size} ${if (deleted.size == 1) "item" else "items"} deleted",
                    duration = SnackbarDuration.Short
                )
            } else {
                snackbarHostState.showSnackbar("Permission denied: could not delete file")
            }
        }
    }
}
internal fun GalleryAppSessionState.restoreFromDeviceTrash(items: List<MediaItem>) {
    if (items.isEmpty()) return
    val uris = items.map { it.uri }.distinct()
    if (hasAllFilesAccess()) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_TRASHED, 0)
                }
                withContext(Dispatchers.IO) {
                    uris.forEach { u ->
                        runCatching { context.contentResolver.update(Uri.parse(u), cv, null, null) }
                    }
                }
            }
            prefs.setInBinBatch(uris.toSet(), false)
            refreshMedia()
        }
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val requestLaunched = runCatching {
            val request = android.provider.MediaStore.createTrashRequest(
                context.contentResolver,
                uris.map { Uri.parse(it) },
                false
            )
            pendingRestoreUris = uris
            restoreLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            true
        }.getOrDefault(false)
        if (!requestLaunched) {
            scope.launch {
                prefs.setInBinBatch(uris.toSet(), false)
                refreshMedia()
            }
        }
    } else {
        scope.launch {
            prefs.setInBinBatch(uris.toSet(), false)
            refreshMedia()
        }
    }
}
internal fun GalleryAppSessionState.moveToDeviceTrash(items: List<MediaItem>) {
    if (items.isEmpty()) return
    val uris = items.map { it.uri }.distinct()
    if (hasAllFilesAccess()) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_TRASHED, 1)
                }
                withContext(Dispatchers.IO) {
                    uris.forEach { u ->
                        runCatching { context.contentResolver.update(Uri.parse(u), cv, null, null) }
                    }
                }
            }
            prefs.setInBinBatch(uris.toSet(), true)
            prefs.setHiddenBatch(uris.toSet(), false)
            refreshMedia()
            val res = snackbarHostState.showSnackbar(
                message = if (uris.size == 1) "Moved to Bin" else "${uris.size} items moved to Bin",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                restoreFromDeviceTrash(items)
            }
        }
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val requestLaunched = runCatching {
            val request = android.provider.MediaStore.createTrashRequest(
                context.contentResolver,
                uris.map { Uri.parse(it) },
                true
            )
            pendingTrashUris = uris
            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            true
        }.getOrDefault(false)
        if (!requestLaunched) {
            scope.launch {
                snackbarHostState.showSnackbar("Cannot move to Bin: permission required")
            }
        }
    } else {
        scope.launch {
            prefs.setInBinBatch(uris.toSet(), true)
            prefs.setHiddenBatch(uris.toSet(), false)
            refreshMedia()
            val res = snackbarHostState.showSnackbar(
                message = if (uris.size == 1) "Moved to Bin" else "${uris.size} items moved to Bin",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                restoreFromDeviceTrash(items)
            }
        }
    }
}
internal fun GalleryAppSessionState.moveItemsToLocked(uris: Set<String>) {
    if (uris.isEmpty()) return
    if (lockedFolderPinHash.isNullOrBlank()) {
        showLockedFolderSetupPrompt = true
    } else {
        scope.launch {
            val itemsToLock = media.filter { it.uri in uris }
            if (itemsToLock.isNotEmpty()) {
                val lockedList = withContext(Dispatchers.IO) { lockedVaultManager.lockItems(itemsToLock) }
                lockedVaultItems = withContext(Dispatchers.IO) { lockedVaultManager.getVaultItems() }
                prefs.setHiddenBatch(uris, true)
                refreshMedia()
                val res = snackbarHostState.showSnackbar(
                    message = if (uris.size == 1) "Moved to Locked folder" else "${uris.size} items moved to Locked folder",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    withContext(Dispatchers.IO) { lockedVaultManager.unlockItems(lockedList) }
                    lockedVaultItems = withContext(Dispatchers.IO) { lockedVaultManager.getVaultItems() }
                    prefs.setHiddenBatch(uris, false)
                    refreshMedia()
                }
            }
        }
    }
}
