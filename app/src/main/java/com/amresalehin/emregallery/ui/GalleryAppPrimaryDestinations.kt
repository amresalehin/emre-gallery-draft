package com.amresalehin.emregallery.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*


@Composable
internal fun GalleryAppSessionState.RenderPrimaryDestination(baseDestination: Destination): Boolean {
    when (baseDestination) {
                    Destination.HOME -> HomeScreen(
                        listState = homeListState,
                        media = if (albumFilter == null) galleryData.sortedMedia else visibleMedia,
                        loading = loading && galleryData.sortedMedia.isEmpty(),
                        isRefreshing = isRefreshing,
                        onRefresh = { refreshMedia(userInitiated = true) },
                        onPermissionGranted = { refreshMedia() },
                        albumTitle = activeAlbumTitle,
                        sortOrder = sortOrderPref,
                        groupBy = groupByPref,
                        gridColumns = gridColumnsPref,
                        gridStyle = gridStylePref,
                        topContentPadding = topContentPadding,
                        bottomContentPadding = bottomContentPadding,
                        archiveCount = archive.size,
                        onOpenArchive = { albumFilter = null; navigateTo(Destination.ARCHIVE) },
                        onClearAlbumFilter = { performBackNavigation() },
                        onAddPhotosToAlbum = if (activeCustomAlbum != null) { { showAddPhotosPickerForAlbum = activeCustomAlbum } } else null,
                        onMoveToBin = { uris ->
                            val itemsToBin = media.filter { it.uri in uris }
                            moveToDeviceTrash(itemsToBin)
                        },
                        onMoveToLocked = { uris -> moveItemsToLocked(uris) },
                        onShareItems = { uris ->
                            val itemsToShare = media.filter { it.uri in uris }
                            shareMultipleMediaItems(context, itemsToShare)
                        },
                        onAddToAlbum = { uris ->
                            val first = media.firstOrNull { it.uri in uris }
                            if (first != null) {
                                mediaForAddToAlbum = first
                                showAddToAlbumSheet = true
                            }
                        },
                        selectedUris = homeSelectedUris,
                        onSelectionChange = { homeSelectedUris = it },
                        precomputedGroups = if (albumFilter == null) galleryData.groups else null,
                        precomputedStories = if (albumFilter == null) galleryData.stories else null,
                        onOpenMemory = { stories, index -> openMemoriesInViewer(stories, index) },
                        onOpen = { openMediaInViewer(it, if (albumFilter == null) galleryData.sortedMedia else visibleMedia) }
                    )
                    Destination.ALBUMS -> AlbumsScreen(
                        media = media,
                        favourites = favourites,
                        hidden = hidden,
                        inBin = remember(media, inBin) { (inBin + media.filter { it.isTrashed }.map { it.uri }).toSet() },
                        archive = archive,
                        customAlbums = customAlbums,
                        topContentPadding = topContentPadding,
                        bottomContentPadding = bottomContentPadding,
                        isRefreshing = isRefreshing,
                        onRefresh = { refreshMedia(userInitiated = true) },
                        onOpenBrowser = {
                            albumFilter = null
                            navigateTo(
                                dest = Destination.BROWSER,
                                rememberPrevious = true
                            )
                        },
                        onFilter = { when (it) { "Map" -> {
                            navigateTo(Destination.MAP, rememberPrevious = true, newMapFocusItem = null)
                        }; "Folders" -> navigateTo(Destination.DEVICE_FOLDERS); "Albums" -> navigateTo(Destination.ALBUM_CATALOG); "Favourites" -> { albumFilter = null; navigateTo(Destination.FAVOURITES) }; "Bin" -> { albumFilter = null; navigateTo(Destination.BIN) }; "Videos" -> { albumFilter = null; navigateTo(Destination.VIDEOS) }; "Recently added" -> { albumFilter = null; navigateTo(Destination.RECENTLY_ADDED) }; "Hidden", "Locked folder" -> { albumFilter = null; navigateTo(Destination.LOCKED_FOLDER) }; "Archive" -> { albumFilter = null; navigateTo(Destination.ARCHIVE) }; "People" -> { albumFilter = null; activePersonId = null; navigateTo(Destination.PEOPLE) }; "Pocket", "Bookmarks" -> { albumFilter = null; navigateTo(Destination.POCKET, rememberPrevious = true) }; "Activity", "Activities", "Activity Page" -> { albumFilter = null; navigateTo(dest = Destination.MEDIA_INBOX, rememberPrevious = true, newInboxInitialViewMode = DownloaderViewMode.ACTIVITY_PAGE, newInboxInitialOpenBrowser = false) }; "Browser" -> { albumFilter = null; navigateTo(dest = Destination.BROWSER, rememberPrevious = true) }; else -> { albumFilterReturnDestination = Destination.ALBUMS; albumFilter = it; navigateTo(Destination.HOME) } } },
                        onOpen = { openMediaInViewer(it, visibleMedia) },
                        onOpenPerson = { personId ->
                            activePersonId = personId
                            navigateTo(Destination.PEOPLE)
                        },
                        onCreateAlbumClick = { parentPreset ->
                            createAlbumPresetParent = parentPreset
                            showCreateAlbumDialog = true
                        },
                        onDeleteAlbum = { id -> scope.launch { prefs.deleteCustomAlbum(id) } },
                        onRenameAlbum = { id, newName -> scope.launch { prefs.renameCustomAlbum(id, newName) } }
                    )
                    Destination.FAVOURITES -> {
                        val favouriteItems = remember(media, favourites, hidden, inBin, archive) {
                            media.filter { it.uri in favourites && it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive }
                        }
                        FavouritesScreen(
                            items = favouriteItems,
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshMedia(userInitiated = true) },
                            onBack = { performBackNavigation() },
                            onOpen = { openMediaInViewer(it, favouriteItems) },
                            onRemoveFavourite = { item -> scope.launch { prefs.toggleFavourite(item.uri) } }
                        )
                    }
                    Destination.BIN -> {
                        val binItems = remember(media, inBin) {
                            media.filter { it.isTrashed || it.uri in inBin }
                        }
                        BinScreen(
                            items = binItems,
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshMedia(userInitiated = true) },
                            onBack = { performBackNavigation() },
                            onOpen = { openMediaInViewer(it, binItems) },
                            onRestore = { item -> restoreFromDeviceTrash(listOf(item)) },
                            onRestoreBatch = { uris -> restoreFromDeviceTrash(media.filter { it.uri in uris }) },
                            onDeletePermanently = { item -> permanentlyDelete(listOf(item)) },
                            onDeletePermanentlyBatch = { items -> permanentlyDelete(items) },
                            onEmptyBin = { items -> permanentlyDelete(items) }
                        )
                    }
                    Destination.VIDEOS -> {
                        val videoItems = remember(media, hidden, inBin, archive) {
                            media.filter { it.isVideo && it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive }
                        }
                        HomeScreen(
                            media = videoItems,
                            loading = loading,
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshMedia(userInitiated = true) },
                            onPermissionGranted = { refreshMedia() },
                            albumTitle = "Videos",
                            sortOrder = sortOrderPref,
                            groupBy = groupByPref,
                            gridColumns = gridColumnsPref,
                            gridStyle = gridStylePref,
                            topContentPadding = topContentPadding,
                            bottomContentPadding = bottomContentPadding,
                            onClearAlbumFilter = { performBackNavigation() },
                            onAddPhotosToAlbum = null,
                            onMoveToBin = { uris -> moveToDeviceTrash(media.filter { it.uri in uris }) },
                            onMoveToLocked = { uris -> moveItemsToLocked(uris) },
                            onShareItems = { uris -> shareMultipleMediaItems(context, media.filter { it.uri in uris }) },
                            onAddToAlbum = null,
                            onOpen = { openMediaInViewer(it, videoItems) }
                        )
                    }
                    Destination.RECENTLY_ADDED -> {
                        val recentItems = remember(media, hidden, inBin, archive) {
                            media.filter { item ->
                                item.uri !in hidden && !item.isTrashed && item.uri !in inBin && item.uri !in archive &&
                                    item.dateAddedSeconds * 1000L > System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                            }
                        }
                        HomeScreen(
                            media = recentItems,
                            loading = loading,
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshMedia(userInitiated = true) },
                            onPermissionGranted = { refreshMedia() },
                            albumTitle = "Recently added",
                            sortOrder = sortOrderPref,
                            groupBy = groupByPref,
                            gridColumns = gridColumnsPref,
                            gridStyle = gridStylePref,
                            topContentPadding = topContentPadding,
                            bottomContentPadding = bottomContentPadding,
                            onClearAlbumFilter = { performBackNavigation() },
                            onAddPhotosToAlbum = null,
                            onMoveToBin = { uris -> moveToDeviceTrash(media.filter { it.uri in uris }) },
                            onMoveToLocked = { uris -> moveItemsToLocked(uris) },
                            onShareItems = { uris -> shareMultipleMediaItems(context, media.filter { it.uri in uris }) },
                            onAddToAlbum = null,
                            onOpen = { openMediaInViewer(it, recentItems) }
                        )
                    }
                    Destination.LOCKED_FOLDER -> {
                        val legacyLocked = remember(media, hidden, inBin) {
                            media.filter { it.uri in hidden && !it.isTrashed && it.uri !in inBin }
                        }
                        val allLockedItems = remember(lockedVaultItems, legacyLocked) {
                            (lockedVaultItems + legacyLocked).distinctBy { it.id }
                        }
                        LockedFolderScreen(
                            items = allLockedItems,
                            availableGalleryMedia = media.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive },
                            isUnlocked = isLockedFolderUnlocked,
                            hasPinSet = !lockedFolderPinHash.isNullOrBlank(),
                            onBack = {
                                isLockedFolderUnlocked = false
                                performBackNavigation()
                            },
                            onOpen = { openMediaInViewer(it, allLockedItems) },
                            onUnlockItem = { item ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { lockedVaultManager.unlockItems(listOf(item)) }
                                    prefs.setHidden(item.uri, false)
                                    lockedVaultItems = withContext(Dispatchers.IO) { lockedVaultManager.getVaultItems() }
                                    refreshMedia()
                                    snackbarHostState.showSnackbar("Restored to gallery", duration = SnackbarDuration.Short)
                                }
                            },
                            onUnlockBatch = { uris ->
                                scope.launch {
                                    val targets = allLockedItems.filter { it.uri in uris }
                                    withContext(Dispatchers.IO) { lockedVaultManager.unlockItems(targets) }
                                    prefs.setHiddenBatch(uris, false)
                                    lockedVaultItems = withContext(Dispatchers.IO) { lockedVaultManager.getVaultItems() }
                                    refreshMedia()
                                    snackbarHostState.showSnackbar("${uris.size} items restored to gallery", duration = SnackbarDuration.Short)
                                }
                            },
                            onDeleteBatchToBin = { uris ->
                                scope.launch {
                                    val targets = allLockedItems.filter { it.uri in uris }
                                    withContext(Dispatchers.IO) { lockedVaultManager.deletePermanently(targets) }
                                    prefs.removePermanently(uris)
                                    lockedVaultItems = withContext(Dispatchers.IO) { lockedVaultManager.getVaultItems() }
                                    refreshMedia()
                                    snackbarHostState.showSnackbar("${uris.size} items deleted permanently", duration = SnackbarDuration.Short)
                                }
                            },
                            onAddItemsToLocked = { uris ->
                                moveItemsToLocked(uris)
                            },
                            onSetPin = { newPin -> scope.launch { prefs.setLockedFolderPin(newPin) } },
                            onVerifyPin = { pin -> prefs.verifyLockedFolderPin(pin) },
                            onUnlockSuccess = { isLockedFolderUnlocked = true },
                            onLockAgain = { isLockedFolderUnlocked = false }
                        )
                    }
                    Destination.ARCHIVE -> {
                        val archiveItems = remember(media, archive, inBin, hidden) {
                            media.filter { it.uri in archive && !it.isTrashed && it.uri !in inBin && it.uri !in hidden }
                        }
                        ArchiveScreen(
                            items = archiveItems,
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshMedia(userInitiated = true) },
                            onBack = { performBackNavigation() },
                            onOpen = { openMediaInViewer(it, archiveItems) },
                            onUnarchive = { item -> scope.launch { prefs.setArchived(item.uri, false) } }
                        )
                    }
                    Destination.PEOPLE -> {
                        val peopleMedia = remember(media, hidden, inBin, archive) {
                            media.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive }
                        }
                        PeopleScreen(
                            media = peopleMedia,
                            initialPersonId = activePersonId,
                            onBack = {
                                activePersonId = null
                                performBackNavigation()
                            },
                            onOpenMedia = { item -> openMediaInViewer(item, peopleMedia) },
                            onOpenIndexStatus = { navigateTo(Destination.SETTINGS) }
                        )
                    }
                    Destination.MAP -> {
                        val mapMedia = remember(media, hidden, inBin, archive) {
                            media.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive }
                        }
                        PhotoMapScreen(
                            media = mapMedia,
                            focusItem = mapFocusItem,
                            onBack = { performBackNavigation() },
                            onOpen = { photo ->
                                openMediaInViewer(photo, mapMedia)
                            }
                        )
                    }
        else -> return false
    }
    return true
}
