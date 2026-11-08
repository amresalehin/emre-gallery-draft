package com.amresalehin.emregallery.ui
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem
@Composable
internal fun GalleryAppSessionState.RenderSecondaryDestination(baseDestination: Destination) {
    when (baseDestination) {
                    Destination.DEVICE_FOLDERS -> DeviceFoldersScreen(
                        media = media.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive },
                        isRefreshing = isRefreshing,
                        onRefresh = { refreshMedia(userInitiated = true) },
                        onBack = { performBackNavigation() },
                        onOpenFolder = { folderPath -> albumFilterReturnDestination = Destination.DEVICE_FOLDERS; albumFilter = folderPath; navigateTo(Destination.HOME) }
                    )
                    Destination.ALBUM_CATALOG -> AlbumCatalogScreen(
                        albums = customAlbums,
                        media = media.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive },
                        isRefreshing = isRefreshing,
                        onRefresh = { refreshMedia(userInitiated = true) },
                        onBack = { performBackNavigation() },
                        onCreateAlbum = { parentPath ->
                            createAlbumPresetParent = parentPath
                            showCreateAlbumDialog = true
                        },
                        onOpenAlbum = { album ->
                            albumFilterReturnDestination = Destination.ALBUM_CATALOG
                            albumFilter = album.id
                            navigateTo(Destination.HOME)
                        }
                    )
                    Destination.POCKET -> PocketScreen(
                        topContentPadding = topContentPadding,
                        bottomContentPadding = bottomContentPadding,
                        onMenuClick = { showSidePanel = true },
                        onDetailViewerStateChange = { isPocketDetailOpen = it },
                        onOpenDiscoveredMedia = {
                            navigateTo(
                                dest = Destination.MEDIA_INBOX,
                                rememberPrevious = true,
                                newInboxInitialViewMode = DownloaderViewMode.DISCOVERED_MEDIA,
                                newInboxInitialOpenBrowser = false
                            )
                        }
                    )
                    Destination.SETTINGS -> SettingsScreen(
                        media = media.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive },
                        total = media.size,
                        onMenuClick = { showSidePanel = true },
                        theme = themePref,
                        ocrLanguages = ocrLanguagesPref,
                        ocrModel = ocrModelPref,
                        ocrEngine = ocrEnginePref,
                        mlKitLanguages = mlKitLanguagesPref,
                        onModelChange = { newModel -> scope.launch { prefs.setOcrModel(newModel) } },
                        onEngineChange = { newEngine -> scope.launch { prefs.setOcrEngine(newEngine) } },
                        onToggleLanguage = { code ->
                            val updated = ocrLanguagesPref.toMutableSet()
                            if (code in updated) {
                                if (updated.size > 1) updated.remove(code)
                            } else {
                                updated.add(code)
                            }
                            scope.launch { prefs.setOcrLanguages(updated) }
                        },
                        onToggleMlKitLanguage = { code ->
                            val updated = mlKitLanguagesPref.toMutableSet()
                            when (code) {
                                "all" -> updated.addAll(com.amresalehin.emregallery.ocr.MlKitLanguage.ALL_CODES)
                                "latin_only" -> {
                                    updated.clear()
                                    updated.add("latin")
                                }
                                else -> {
                                    if (code in updated) {
                                        if (updated.size > 1) updated.remove(code)
                                    } else {
                                        updated.add(code)
                                    }
                                }
                            }
                            scope.launch { prefs.setMlKitLanguages(updated) }
                        },
                        topContentPadding = statusBarTop + 14.dp,
                        bottomContentPadding = bottomContentPadding,
                        onTheme = { scope.launch { prefs.setTheme(it) } },
                        onRefresh = { refreshMedia() },
                        onSettings = { label ->
                            when (label) {
                                "About" -> navigateTo(Destination.ABOUT)
                            }
                        },
                        onPermissionSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                        prefs = prefs,
                        onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    Destination.LIBRARY_OVERVIEW -> LibraryOverviewScreen(
                        media = media.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive },
                        loading = loading,
                        isRefreshing = isRefreshing,
                        onBack = { performBackNavigation() },
                        onRefresh = { refreshMedia(userInitiated = true) }
                    )
                    Destination.OCR_SETTINGS -> OcrSettingsScreen(
                        currentEngine = ocrEnginePref,
                        currentModel = ocrModelPref,
                        ocrLanguages = ocrLanguagesPref,
                        mlKitLanguages = mlKitLanguagesPref,
                        onSelectEngine = { newEngine -> scope.launch { prefs.setOcrEngine(newEngine) } },
                        onSelectModel = { newModel -> scope.launch { prefs.setOcrModel(newModel) } },
                        onToggleLanguage = { code ->
                            val updated = ocrLanguagesPref.toMutableSet()
                            if (code in updated) {
                                if (updated.size > 1) updated.remove(code)
                            } else {
                                updated.add(code)
                            }
                            scope.launch { prefs.setOcrLanguages(updated) }
                        },
                        onToggleMlKitLanguage = { code ->
                            val updated = mlKitLanguagesPref.toMutableSet()
                            when (code) {
                                "all" -> updated.addAll(com.amresalehin.emregallery.ocr.MlKitLanguage.ALL_CODES)
                                "latin_only" -> {
                                    updated.clear()
                                    updated.add("latin")
                                }
                                else -> {
                                    if (code in updated) {
                                        if (updated.size > 1) updated.remove(code)
                                    } else {
                                        updated.add(code)
                                    }
                                }
                            }
                            scope.launch { prefs.setMlKitLanguages(updated) }
                        },
                        onBack = { performBackNavigation() }
                    )
                    Destination.ABOUT -> AboutScreen(
                        onBack = { performBackNavigation() }
                    )
                    Destination.MEDIA_INBOX -> MediaInboxScreen(
                        initialViewMode = inboxInitialViewMode,
                        initialStackForDetail = inboxInitialStack,
                        initialOpenBrowser = inboxInitialOpenBrowser,
                        onBack = { performBackNavigation() },
                        onOpenBrowser = { _ ->
                            navigateTo(
                                dest = Destination.BROWSER,
                                rememberPrevious = true
                            )
                        },
                        onOpenMediaItem = { uri ->
                            val match = media.firstOrNull { it.uri == uri || (it.name.isNotBlank() && uri.contains(it.name)) }
                            val targetItem = match ?: run {
                                val file = java.io.File(uri)
                                val name = if (file.exists()) file.name else uri.substringAfterLast('/').ifBlank { "media_item" }
                                val isVid = name.endsWith(".mp4", ignoreCase = true) ||
                                            name.endsWith(".webm", ignoreCase = true) ||
                                            name.endsWith(".mkv", ignoreCase = true) ||
                                            name.endsWith(".mov", ignoreCase = true)
                                MediaItem(
                                    id = uri.hashCode().toLong(),
                                    uri = uri,
                                    name = name,
                                    mimeType = if (isVid) "video/*" else "image/*",
                                    dateAddedSeconds = System.currentTimeMillis() / 1000,
                                    dateTakenMillis = System.currentTimeMillis(),
                                    sizeBytes = if (file.exists()) file.length() else 0L,
                                    width = 0,
                                    height = 0,
                                    relativePath = "",
                                    isVideo = isVid
                                )
                            }
                            openMediaInViewer(targetItem, listOf(targetItem))
                        },
                        onOpenMediaWithList = { uri, allUris ->
                            val safeUris = if (allUris.contains(uri)) allUris else (listOf(uri) + allUris)
                            val viewerItems = safeUris.map { u ->
                                media.firstOrNull { it.uri == u || (it.name.isNotBlank() && u.contains(it.name)) } ?: run {
                                    val file = java.io.File(u)
                                    val name = if (file.exists()) file.name else u.substringAfterLast('/').ifBlank { "media_item" }
                                    val isVid = name.endsWith(".mp4", ignoreCase = true) ||
                                                name.endsWith(".webm", ignoreCase = true) ||
                                                name.endsWith(".mkv", ignoreCase = true) ||
                                                name.endsWith(".mov", ignoreCase = true)
                                    MediaItem(
                                        id = u.hashCode().toLong(),
                                        uri = u,
                                        name = name,
                                        mimeType = if (isVid) "video/*" else "image/*",
                                        dateAddedSeconds = System.currentTimeMillis() / 1000,
                                        dateTakenMillis = System.currentTimeMillis(),
                                        sizeBytes = if (file.exists()) file.length() else 0L,
                                        width = 0,
                                        height = 0,
                                        relativePath = "",
                                        isVideo = isVid
                                    )
                                }
                            }
                            val target = viewerItems.firstOrNull { it.uri == uri } ?: viewerItems.first()
                            openMediaInViewer(target, viewerItems)
                        }
                    )
                    Destination.BROWSER -> DirectBrowserView(
                        initialUrl = "",
                        onClose = { performBackNavigation() },
                        onMenuClick = { showSidePanel = true },
                        onMediaExtracted = { item -> com.amresalehin.emregallery.data.DownloaderSession.addDiscoveredItem(item) },
                        onBatchExtracted = { batch, title, _ -> com.amresalehin.emregallery.data.DownloaderSession.addDiscoveredItems(batch, title) },
                        onOpenDownloads = {
                            navigateTo(
                                dest = Destination.MEDIA_INBOX,
                                rememberPrevious = true,
                                newInboxInitialViewMode = DownloaderViewMode.ACTIVITY_PAGE,
                                newInboxInitialOpenBrowser = false
                            )
                        },
                        onOpenDiscoveredMedia = {
                            navigateTo(
                                dest = Destination.MEDIA_INBOX,
                                rememberPrevious = true,
                                newInboxInitialViewMode = DownloaderViewMode.DISCOVERED_MEDIA,
                                newInboxInitialOpenBrowser = false
                            )
                        },
                        onSwitchToMediaTab = {
                            navigateTo(
                                dest = Destination.MEDIA_INBOX,
                                rememberPrevious = true,
                                newInboxInitialViewMode = DownloaderViewMode.ACTIVITY_PAGE,
                                newInboxInitialOpenBrowser = false
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.SEARCH -> {
                        val searchScopeMedia = remember(media, hidden, inBin, archive) {
                            media.filter {
                                it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive
                            }
                        }
                        SearchScreen(
                            query = searchQuery,
                            stage = searchStage,
                            allMedia = searchScopeMedia,
                            recentSearches = recentSearches,
                            customAlbums = customAlbums,
                            customTagsMap = customTagsMap,
                            customPeopleMap = customPeopleMap,
                            favourites = favourites,
                            themePref = themePref,
                            onQueryChange = { newQuery ->
                                searchQuery = newQuery
                                searchStage = if (newQuery.isBlank()) SearchStage.DISCOVERY else SearchStage.TYPING
                            },
                            onSubmitSearch = { submitted ->
                                val clean = submitted.trim()
                                searchQuery = clean
                                submittedQuery = clean
                                searchStage = SearchStage.RESULTS
                                if (clean.isNotBlank()) {
                                    scope.launch { prefs.addRecentSearch(clean) }
                                }
                            },
                            onClearRecent = { scope.launch { prefs.clearRecentSearches() } },
                            onBack = {
                                searchQuery = ""
                                searchStage = SearchStage.DISCOVERY
                                performBackNavigation()
                            },
                            onOpenMedia = { item, currentList ->
                                openMediaInViewer(item, currentList)
                            }
                        )
                    }
                    Destination.VIEWER -> {
                        Box(Modifier.fillMaxSize())
                    }
        else -> {}
    }
}
