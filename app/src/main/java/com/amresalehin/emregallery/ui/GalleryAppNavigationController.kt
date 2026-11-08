package com.amresalehin.emregallery.ui

import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.StoryGroup


internal fun GalleryAppSessionState.navigateTo(
    dest: Destination,
    rememberPrevious: Boolean = true,
    newSelectedMedia: MediaItem? = selectedMedia,
    newMapFocusItem: MediaItem? = mapFocusItem,
    newViewerMediaList: List<MediaItem> = viewerMediaList,
    newViewerStoryTitle: String? = if (dest == Destination.VIEWER) viewerStoryTitle else null,
    newViewerStories: List<StoryGroup> = if (dest == Destination.VIEWER) viewerStories else emptyList(),
    newCurrentStoryIndex: Int = if (dest == Destination.VIEWER) currentStoryIndex else 0,
    newInboxInitialViewMode: DownloaderViewMode = DownloaderViewMode.ACTIVITY_PAGE,
    newInboxInitialStack: com.amresalehin.emregallery.data.MediaInboxStack? = null,
    newInboxInitialOpenBrowser: Boolean = false
) {
    if (dest != Destination.LOCKED_FOLDER && dest != Destination.VIEWER) {
        isLockedFolderUnlocked = false
    }
    homeSelectedUris = emptySet()
    isNavigatingBack = false
    if (rememberPrevious && destination != dest) {
        backStack.add(
            NavBackStackEntry(
                destination = destination,
                selectedMedia = selectedMedia,
                viewerMediaList = viewerMediaList,
                viewerStoryTitle = viewerStoryTitle,
                viewerStories = viewerStories,
                currentStoryIndex = currentStoryIndex,
                mapFocusItem = mapFocusItem,
                albumFilter = albumFilter,
                albumFilterReturnDestination = albumFilterReturnDestination,
                inboxInitialViewMode = inboxInitialViewMode,
                inboxInitialStack = inboxInitialStack,
                inboxInitialOpenBrowser = inboxInitialOpenBrowser
            )
        )
        previousDestination = destination
    }
    destination = dest
    selectedMedia = newSelectedMedia
    mapFocusItem = newMapFocusItem
    viewerMediaList = newViewerMediaList
    viewerStoryTitle = newViewerStoryTitle
    viewerStories = newViewerStories
    currentStoryIndex = newCurrentStoryIndex
    inboxInitialViewMode = newInboxInitialViewMode
    inboxInitialStack = newInboxInitialStack
    inboxInitialOpenBrowser = newInboxInitialOpenBrowser
    isPocketDetailOpen = false
}

internal fun GalleryAppSessionState.openMediaInViewer(item: MediaItem, list: List<MediaItem>) {
    val safeList = if (list.any { it.uri == item.uri }) list else listOf(item)
    selectedMedia = item
    viewerMediaList = safeList
    viewerStoryTitle = null
    viewerStories = emptyList()
    currentStoryIndex = 0
    showViewerControls = false
    showDetails = false
    scope.launch { viewerTransitionProgress.snapTo(0f) }
    navigateTo(
        dest = Destination.VIEWER,
        rememberPrevious = true,
        newSelectedMedia = item,
        newViewerMediaList = safeList,
        newViewerStoryTitle = null,
        newViewerStories = emptyList(),
        newCurrentStoryIndex = 0
    )
}

internal fun GalleryAppSessionState.openMemoriesInViewer(stories: List<StoryGroup>, startIndex: Int) {
    if (stories.isEmpty()) return
    val initialStory = stories.getOrNull(startIndex) ?: stories.first()
    if (initialStory.items.isEmpty()) return
    val cover = initialStory.items.first()
    selectedMedia = cover
    viewerMediaList = initialStory.items
    viewerStoryTitle = initialStory.title
    viewerStories = stories
    currentStoryIndex = startIndex
    showViewerControls = true
    showDetails = false
    scope.launch { viewerTransitionProgress.snapTo(0f) }
    navigateTo(
        dest = Destination.VIEWER,
        rememberPrevious = true,
        newSelectedMedia = cover,
        newViewerMediaList = initialStory.items,
        newViewerStoryTitle = initialStory.title,
        newViewerStories = stories,
        newCurrentStoryIndex = startIndex
    )
}


internal fun GalleryAppSessionState.performBackNavigation() {
    if (homeSelectedUris.isNotEmpty()) {
        homeSelectedUris = emptySet()
        return
    }
    isNavigatingBack = true
    scope.launch { viewerTransitionProgress.snapTo(0f) }
    if (destination == Destination.LOCKED_FOLDER) {
        isLockedFolderUnlocked = false
    }
    when {
        showSidePanel -> {
            showSidePanel = false
        }
        destination == Destination.VIEWER && showDetails -> {
            showDetails = false
        }
        backStack.isNotEmpty() -> {
            val entry = backStack.removeAt(backStack.size - 1)
            destination = entry.destination
            selectedMedia = entry.selectedMedia
            viewerMediaList = entry.viewerMediaList
            viewerStoryTitle = entry.viewerStoryTitle
            viewerStories = entry.viewerStories
            currentStoryIndex = entry.currentStoryIndex
            mapFocusItem = entry.mapFocusItem
            albumFilter = entry.albumFilter
            albumFilterReturnDestination = entry.albumFilterReturnDestination
            inboxInitialViewMode = entry.inboxInitialViewMode
            inboxInitialStack = entry.inboxInitialStack
            inboxInitialOpenBrowser = entry.inboxInitialOpenBrowser
            previousDestination = backStack.lastOrNull()?.destination ?: Destination.HOME
        }
        albumFilter != null -> {
            albumFilter = null
            destination = albumFilterReturnDestination
            viewerStoryTitle = null
        }
        destination in listOf(
            Destination.DEVICE_FOLDERS,
            Destination.ALBUM_CATALOG,
            Destination.FAVOURITES,
            Destination.BIN,
            Destination.VIDEOS,
            Destination.RECENTLY_ADDED,
            Destination.LOCKED_FOLDER,
            Destination.ARCHIVE,
            Destination.PEOPLE,
            Destination.MAP,
            Destination.BROWSER
        ) -> {
            destination = Destination.ALBUMS
            mapFocusItem = null
            viewerStoryTitle = null
        }
        destination in listOf(
            Destination.LIBRARY_OVERVIEW,
            Destination.OCR_SETTINGS,
            Destination.ABOUT
        ) -> {
            destination = Destination.SETTINGS
            viewerStoryTitle = null
        }
        destination == Destination.MEDIA_INBOX -> {
            destination = if (previousDestination == Destination.ALBUMS) Destination.ALBUMS else Destination.SETTINGS
            viewerStoryTitle = null
        }
        destination == Destination.VIEWER -> {
            val targetDest = previousDestination.takeIf { it != Destination.VIEWER } ?: Destination.HOME
            destination = targetDest
            selectedMedia = null
            viewerMediaList = emptyList()
            viewerStoryTitle = null
        }
        else -> {
            destination = Destination.HOME
            mapFocusItem = null
            selectedMedia = null
            viewerStoryTitle = null
        }
    }
}

@Composable
internal fun GalleryAppSessionState.UpdateAlbumAndVisibleMediaState() {
    activeCustomAlbum = remember(albumFilter, customAlbums) {
        customAlbums.firstOrNull { it.id == albumFilter || it.fullPath == albumFilter }
    }
    activeAlbumTitle = remember(albumFilter, activeCustomAlbum) {
        activeCustomAlbum?.fullPath ?: when (albumFilter) {
            "Hidden" -> "Locked folder"
            "Bin" -> "Bin"
            "Archive" -> "Archive"
            "Favourites" -> "Favourites"
            "Videos" -> "Videos"
            "Recently added" -> "Recently added"
            "People" -> "People & pets"
            "Places", "Map" -> "Map & Places"
            "Albums" -> "Albums"
            "Folders" -> "Device folders"
            else -> albumFilter
        }
    }

    visibleMedia = remember(media, hidden, inBin, archive, favourites, albumFilter, searchVideosOnly, submittedQuery, searchStage, customAlbums) {
        media.filter { item ->
            val isItemBinned = item.isTrashed || item.uri in inBin
            val inScope = when (albumFilter) {
                "Hidden" -> item.uri in hidden && !isItemBinned
                "Bin" -> isItemBinned
                "Archive" -> item.uri in archive && !isItemBinned
                else -> item.uri !in hidden && !isItemBinned && item.uri !in archive
            }
            val matchesCategory = when (albumFilter) {
                null, "Hidden", "Bin", "Archive", "People", "Places", "Map", "Albums", "Folders" -> true
                "Favourites" -> item.uri in favourites
                "Videos" -> item.isVideo
                "Recently added" -> item.dateAddedSeconds * 1000L > System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                else -> {
                    val albumMatch = customAlbums.firstOrNull { it.id == albumFilter || it.fullPath == albumFilter || it.name == albumFilter }
                    if (albumMatch != null) {
                        item.uri in albumMatch.mediaUris
                    } else {
                        item.relativePath.contains(albumFilter.orEmpty(), true)
                    }
                }
            }
            inScope && matchesCategory
        }
    }
}

internal fun shareMultipleMediaItems(context: android.content.Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    runCatching {
        val activity = context as? android.app.Activity
        if (items.size == 1) {
            val item = items.first()
            val parsedUri = Uri.parse(item.uri)
            val mimeType = item.mimeType ?: if (item.isVideo) "video/*" else "image/*"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, parsedUri)
                clipData = android.content.ClipData.newUri(context.contentResolver, item.name, parsedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share media").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooserIntent)
        } else {
            val uris = ArrayList(items.map { Uri.parse(it.uri) })
            val allImages = items.all { !it.isVideo }
            val allVideos = items.all { it.isVideo }
            val commonMime = items.mapNotNull { it.mimeType }.distinct().singleOrNull()
            val mimeType = commonMime ?: when {
                allImages -> "image/*"
                allVideos -> "video/*"
                else -> "*/*"
            }
            val firstUri = uris.first()
            val clipData = android.content.ClipData.newUri(context.contentResolver, items.first().name, firstUri)
            for (i in 1 until uris.size) {
                clipData.addItem(android.content.ClipData.Item(uris[i]))
            }
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                if (mimeType == "*/*") {
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                }
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                this.clipData = clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share media").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooserIntent)
        }
    }
}
