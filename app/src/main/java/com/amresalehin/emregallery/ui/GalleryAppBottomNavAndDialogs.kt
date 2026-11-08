package com.amresalehin.emregallery.ui

import android.provider.Settings
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@Composable
internal fun GalleryAppSessionState.RenderBottomNavAndDrawer(
    boxScope: BoxScope,
    returnDestination: Destination
) = with(boxScope) {
        // Floating Bottom Navigation Bar across Home, Albums, Pocket, Settings - coordinated entrance upward from below screen edge
        val showBottomBarContainer = ((returnDestination in listOf(Destination.HOME, Destination.ALBUMS, Destination.POCKET, Destination.SETTINGS) && albumFilter == null) || homeSelectedUris.isNotEmpty()) && destination != Destination.VIEWER && destination != Destination.BROWSER && !isPocketDetailOpen
        if (showBottomBarContainer) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .graphicsLayer {
                            val cp = if (destination == Destination.VIEWER) viewerTransitionProgress.value else 0f
                            translationY = (size.height + 60.dp.toPx()) * cp
                            alpha = (1f - cp * 1.15f).coerceIn(0f, 1f)
                        }
                ) {
                    if (albumFilter == null && returnDestination in listOf(Destination.HOME, Destination.ALBUMS, Destination.POCKET, Destination.SETTINGS)) {
                        FloatingNavigationBar(
                            current = returnDestination,
                            onSelect = { dest ->
                                if (dest == destination) {
                                    if (dest == Destination.HOME) {
                                        scope.launch {
                                            homeListState.scrollToItem(0)
                                        }
                                    }
                                } else {
                                    backStack.clear()
                                    albumFilter = null
                                    homeSelectedUris = emptySet()
                                    navigateTo(dest, rememberPrevious = false, newSelectedMedia = null, newMapFocusItem = null)
                                }
                            }
                        )
                    }

                    // Floating Selection Toolbar overlying the bottom navigation bar:
                    AnimatedVisibility(
                        visible = homeSelectedUris.isNotEmpty(),
                        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 }
                    ) {
                        HomeSelectionToolbar(
                            selectedUris = homeSelectedUris,
                            totalCount = visibleMedia.size,
                            onClose = { homeSelectedUris = emptySet() },
                            onSelectAllToggle = {
                                homeSelectedUris = if (homeSelectedUris.size == visibleMedia.size) {
                                    emptySet()
                                } else {
                                    visibleMedia.map { it.uri }.toSet()
                                }
                            },
                            onShare = {
                                shareMultipleMediaItems(context, visibleMedia.filter { it.uri in homeSelectedUris })
                            },
                            onMoveToLocked = {
                                val targets = homeSelectedUris
                                homeSelectedUris = emptySet()
                                moveItemsToLocked(targets)
                            },
                            onMoveToBin = {
                                val targets = homeSelectedUris
                                homeSelectedUris = emptySet()
                                moveToDeviceTrash(visibleMedia.filter { it.uri in targets })
                            },
                            onAddToAlbum = {
                                val targets = homeSelectedUris
                                homeSelectedUris = emptySet()
                                val first = visibleMedia.firstOrNull { it.uri in targets }
                                if (first != null) {
                                    mediaForAddToAlbum = first
                                    showAddToAlbumSheet = true
                                }
                            }
                        )
                    }
                }
            }
        }

        SideNavigationDrawer(
            visible = showSidePanel,
            destination = destination,
            albumFilter = albumFilter,
            onNavigate = { dest ->
                if (dest in listOf(Destination.HOME, Destination.ALBUMS, Destination.SETTINGS)) {
                    backStack.clear()
                    albumFilter = null
                    navigateTo(dest, rememberPrevious = false, newSelectedMedia = null, newMapFocusItem = null)
                } else {
                    albumFilter = null
                    navigateTo(
                        dest = dest,
                        rememberPrevious = true,
                        newMapFocusItem = if (dest == Destination.MAP) null else mapFocusItem,
                        newInboxInitialOpenBrowser = (dest == Destination.MEDIA_INBOX)
                    )
                }
                showSidePanel = false
            },
            onClose = { showSidePanel = false }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomContentPadding)
        )
}

@Composable
internal fun GalleryAppSessionState.RenderGlobalDialogsAndSheets() {
    if (showLockedFolderSetupPrompt) {
        AlertDialog(
            onDismissRequest = { showLockedFolderSetupPrompt = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    "Locked folder not configured",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Create a 4-digit PIN first so you can safely access and protect your locked photos.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLockedFolderSetupPrompt = false
                        navigateTo(Destination.LOCKED_FOLDER)
                    }
                ) {
                    Text("Set up PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockedFolderSetupPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateAlbumDialog) {
        CreateAlbumDialog(
            presetParent = createAlbumPresetParent,
            existingAlbums = customAlbums,
            onDismiss = { showCreateAlbumDialog = false },
            onConfirm = { name, parentPath ->
                scope.launch {
                    val newAlbum = prefs.createCustomAlbum(name, parentPath)
                    mediaForAddToAlbum?.let { addedItem ->
                        prefs.addMediaToCustomAlbum(newAlbum.id, addedItem.uri)
                    }
                }
                showCreateAlbumDialog = false
            }
        )
    }

    val activeMediaForAlbum = mediaForAddToAlbum
    if (showAddToAlbumSheet && activeMediaForAlbum != null) {
        AddToAlbumBottomSheet(
            item = activeMediaForAlbum,
            customAlbums = customAlbums,
            onDismiss = { showAddToAlbumSheet = false },
            onToggleAlbum = { album ->
                scope.launch {
                    val mediaItem = mediaForAddToAlbum ?: activeMediaForAlbum
                    if (mediaItem.uri in album.mediaUris) {
                        prefs.removeMediaFromCustomAlbum(album.id, mediaItem.uri)
                        snackbarHostState.showSnackbar("Removed from ${album.name}", duration = SnackbarDuration.Short)
                    } else {
                        prefs.addMediaToCustomAlbum(album.id, mediaItem.uri)
                        snackbarHostState.showSnackbar("Moved to ${album.name}", duration = SnackbarDuration.Short)
                    }
                }
            },
            onCreateNewAlbum = {
                showAddToAlbumSheet = false
                createAlbumPresetParent = ""
                showCreateAlbumDialog = true
            }
        )
    }

    val activeAlbumForPicker = showAddPhotosPickerForAlbum
    if (activeAlbumForPicker != null) {
        AddPhotosToAlbumDialog(
            album = activeAlbumForPicker,
            allMedia = media.filter { it.uri !in hidden && it.uri !in inBin },
            onDismiss = { showAddPhotosPickerForAlbum = null },
            onAddSelected = { uris ->
                val target = showAddPhotosPickerForAlbum ?: activeAlbumForPicker
                scope.launch {
                    uris.forEach { uri -> prefs.addMediaToCustomAlbum(target.id, uri) }
                }
                showAddPhotosPickerForAlbum = null
            }
        )
    }

    if (showHomeViewOptionsSheet) {
        HomeViewOptionsBottomSheet(
            sortOrder = sortOrderPref,
            groupBy = groupByPref,
            gridColumns = gridColumnsPref,
            gridStyle = gridStylePref,
            onSortOrderChange = { scope.launch { prefs.setSortOrder(it) } },
            onGroupByChange = { scope.launch { prefs.setGroupBy(it) } },
            onGridColumnsChange = { scope.launch { prefs.setGridColumns(it) } },
            onGridStyleChange = { scope.launch { prefs.setGridStyle(it) } },
            onDismiss = { showHomeViewOptionsSheet = false }
        )
    }
}
