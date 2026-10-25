package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.ocr.TesseractOcr
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.FaceIndexingRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesRepository(context.applicationContext) }
    val mediaRepository = remember { MediaRepository(context.contentResolver) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var previousDestination by remember { mutableStateOf(Destination.HOME) }
    var albumFilterReturnDestination by remember { mutableStateOf(Destination.ALBUMS) }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(hasMediaPermission(context)) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var mapFocusItem by remember { mutableStateOf<MediaItem?>(null) }
    var mapReturnDestination by remember { mutableStateOf(Destination.ALBUMS) }
    var showViewerControls by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var searchStage by remember { mutableStateOf(SearchStage.DISCOVERY) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var searchVideosOnly by remember { mutableStateOf(false) }
    var albumFilter by remember { mutableStateOf<String?>(null) }
    val favourites by prefs.favourites.collectAsStateWithLifecycle(initialValue = emptySet())
    val hidden by prefs.hidden.collectAsStateWithLifecycle(initialValue = emptySet())
    val inBin by prefs.bin.collectAsStateWithLifecycle(initialValue = emptySet())
    val archive by prefs.archive.collectAsStateWithLifecycle(initialValue = emptySet())
    val recentSearches by prefs.recentSearches.collectAsStateWithLifecycle(initialValue = emptyList())
    val themePref by prefs.theme.collectAsStateWithLifecycle(initialValue = "system")
    val customAlbums by prefs.customAlbums.collectAsStateWithLifecycle(initialValue = emptyList())
    val sortOrderPref by prefs.sortOrder.collectAsStateWithLifecycle(initialValue = "date_desc")
    val groupByPref by prefs.groupBy.collectAsStateWithLifecycle(initialValue = "day")
    val gridColumnsPref by prefs.gridColumns.collectAsStateWithLifecycle(initialValue = 3)
    val gridStylePref by prefs.gridStyle.collectAsStateWithLifecycle(initialValue = "masonry")
    val ocrLanguagesPref by prefs.ocrLanguages.collectAsStateWithLifecycle(initialValue = setOf("eng"))
    val ocrModelPref by prefs.ocrModel.collectAsStateWithLifecycle(initialValue = "fast")
    val customTagsMap by prefs.customTagsMap.collectAsStateWithLifecycle(initialValue = emptyMap())
    val customPeopleMap by prefs.customPeopleMap.collectAsStateWithLifecycle(initialValue = emptyMap())
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var lastHomeButtonTapAt by remember { mutableLongStateOf(0L) }
    var showHomeViewOptionsSheet by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    var pendingPermanentDeleteUris by remember { mutableStateOf<List<String>>(emptyList()) }
    fun refreshMedia() {
        if (!hasMediaPermission(context)) return
        loading = true
        scope.launch {
            media = withContext(Dispatchers.IO) { runCatching { mediaRepository.loadMedia() }.getOrDefault(emptyList()) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (hasMediaPermission(context)) refreshMedia() }
    val permanentDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val deletedUris = pendingPermanentDeleteUris
        pendingPermanentDeleteUris = emptyList()
        if (result.resultCode == android.app.Activity.RESULT_OK && deletedUris.isNotEmpty()) {
            scope.launch {
                for (uri in deletedUris) {
                    prefs.setInBin(uri, false)
                }
                refreshMedia()
            }
        }
    }

    fun permanentlyDelete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val itemUris = items.map { it.uri }.distinct()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val request = android.provider.MediaStore.createDeleteRequest(
                    context.contentResolver,
                    itemUris.map { Uri.parse(it) }
                )
                pendingPermanentDeleteUris = itemUris
                permanentDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }
        } else {
            scope.launch {
                val deleted = withContext(Dispatchers.IO) {
                    itemUris.filter { uri ->
                        runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) > 0 }.getOrDefault(false)
                    }
                }
                for (uri in deleted) {
                    prefs.setInBin(uri, false)
                }
                refreshMedia()
            }
        }
    }

    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var createAlbumPresetParent by remember { mutableStateOf("") }
    var showAddToAlbumSheet by remember { mutableStateOf(false) }
    var mediaForAddToAlbum by remember { mutableStateOf<MediaItem?>(null) }
    var showAddPhotosPickerForAlbum by remember { mutableStateOf<CustomAlbum?>(null) }

    var isNavigatingBack by remember { mutableStateOf(false) }
    var isPredictiveBackActive by remember { mutableStateOf(false) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var predictiveBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    fun navigateTo(dest: Destination, rememberPrevious: Boolean = true) {
        isNavigatingBack = false
        if (rememberPrevious && destination != dest) {
            previousDestination = destination
        }
        destination = dest
    }

    fun performBackNavigation() {
        isNavigatingBack = true
        when {
            showSidePanel -> {
                showSidePanel = false
            }
            destination == Destination.VIEWER && showDetails -> {
                showDetails = false
            }
            destination == Destination.VIEWER || destination == Destination.SEARCH -> {
                destination = previousDestination
            }
            albumFilter != null -> {
                albumFilter = null
                destination = albumFilterReturnDestination
            }
            destination == Destination.MAP -> {
                destination = mapReturnDestination
                mapFocusItem = null
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
                Destination.PEOPLE
            ) -> {
                destination = Destination.ALBUMS
            }
            destination in listOf(
                Destination.LIBRARY_OVERVIEW,
                Destination.INDEXING_STATUS,
                Destination.OCR_SETTINGS,
                Destination.ABOUT
            ) -> {
                destination = if (destination == Destination.INDEXING_STATUS) previousDestination else Destination.SETTINGS
            }
            else -> destination = Destination.HOME
        }
    }

    val canGoBack = destination != Destination.HOME || albumFilter != null || showSidePanel || (destination == Destination.VIEWER && showDetails)

    PredictiveBackHandler(enabled = canGoBack) { progress: Flow<BackEventCompat> ->
        try {
            isPredictiveBackActive = true
            progress.collect { backEvent ->
                predictiveBackProgress = backEvent.progress
                predictiveBackEdge = backEvent.swipeEdge
            }
            performBackNavigation()
        } catch (e: CancellationException) {
            predictiveBackProgress = 0f
            throw e
        } finally {
            isPredictiveBackActive = false
            predictiveBackProgress = 0f
        }
    }

    val activeCustomAlbum = remember(albumFilter, customAlbums) {
        customAlbums.firstOrNull { it.id == albumFilter || it.fullPath == albumFilter }
    }
    val activeAlbumTitle = remember(albumFilter, activeCustomAlbum) {
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

    val visibleMedia = remember(media, hidden, inBin, archive, favourites, albumFilter, searchVideosOnly, submittedQuery, searchStage, customAlbums) {
        media.filter { item ->
            val inScope = when (albumFilter) {
                "Hidden" -> item.uri in hidden
                "Bin" -> item.uri in inBin
                "Archive" -> item.uri in archive
                else -> item.uri !in hidden && item.uri !in inBin && item.uri !in archive
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
            inScope && matchesCategory && (!searchVideosOnly || item.isVideo) &&
                (searchStage != SearchStage.RESULTS || item.name.contains(submittedQuery, true) || item.relativePath.contains(submittedQuery, true))
        }
    }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topContentPadding = statusBarTop + 68.dp
    val bottomContentPadding = navBarBottom + 88.dp

    LaunchedEffect(Unit) {
        prefs.clearHomeScrollPosition()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isPredictiveBackActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
            }

            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val mainPages = listOf(Destination.HOME, Destination.ALBUMS, Destination.SETTINGS)
                    val initialIndex = mainPages.indexOf(initialState)
                    val targetIndex = mainPages.indexOf(targetState)
                    val isOpeningAlbumDetail = targetState == Destination.HOME && albumFilter != null
                    val isExitingAlbumDetail = initialState == Destination.HOME && albumFilter != null && isNavigatingBack

                    if (targetState == Destination.VIEWER) {
                        (fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                            scaleIn(initialScale = 0.80f, animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(140)) +
                                scaleOut(targetScale = 1.05f, animationSpec = tween(140))
                            )
                    } else if (initialState == Destination.VIEWER) {
                        (fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                            scaleIn(initialScale = 1.04f, animationSpec = tween(180, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)) +
                                scaleOut(targetScale = 0.80f, animationSpec = tween(180, easing = FastOutSlowInEasing))
                            )
                    } else if (isOpeningAlbumDetail) {
                        // Forward push when drilling down into an album
                        (slideInHorizontally(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> width }
                        ) + fadeIn(animationSpec = tween(200)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> -(width * 0.20f).toInt() }
                                ) + fadeOut(animationSpec = tween(160))
                            )
                    } else if (isExitingAlbumDetail || isNavigatingBack) {
                        // Pop backwards when returning
                        (slideInHorizontally(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> -(width * 0.20f).toInt() }
                        ) + fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> width }
                                ) + fadeOut(animationSpec = tween(160))
                            )
                    } else if (initialIndex >= 0 && targetIndex >= 0 && albumFilter == null) {
                        // Crisp Material 3 lateral glide between bottom-nav primary tabs (Home, Albums, Settings)
                        val direction = if (targetIndex > initialIndex) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> (width * 0.14f * direction).toInt() }
                        ) + fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> -(width * 0.10f * direction).toInt() }
                                ) + fadeOut(animationSpec = tween(140))
                            )
                    } else {
                        // Forward navigation to a subscreen:
                        (slideInHorizontally(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> width }
                        ) + fadeIn(animationSpec = tween(200)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> -(width * 0.20f).toInt() }
                                ) + fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(240, easing = FastOutSlowInEasing))
                            )
                    }
                },
                label = "destination_screen_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isPredictiveBackActive && predictiveBackProgress > 0f) {
                            val p = predictiveBackProgress
                            val scale = 1f - (p * 0.12f)
                            scaleX = scale
                            scaleY = scale
                            val maxShift = 48.dp.toPx()
                            translationX = if (predictiveBackEdge == BackEventCompat.EDGE_LEFT) {
                                p * maxShift
                            } else {
                                -p * maxShift
                            }
                            shape = RoundedCornerShape((p * 28).dp)
                            clip = true
                            shadowElevation = (p * 20).dp.toPx()
                        }
                    }
                    .pointerInput(destination, showSidePanel, albumFilter) {
                        // Inward swipe from left edge to open side panel on main screens without conflicting with list scrolling
                        if (!showSidePanel && destination in listOf(Destination.HOME, Destination.ALBUMS, Destination.SETTINGS) && albumFilter == null) {
                            val edgeWidth = 32.dp.toPx()
                            val slop = viewConfiguration.touchSlop * 1.5f
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (down.position.x <= edgeWidth) {
                                    var totalDragX = 0f
                                    var totalDragY = 0f
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change != null) {
                                            totalDragX += change.position.x - change.previousPosition.x
                                            totalDragY += change.position.y - change.previousPosition.y
                                            if (totalDragX > slop && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.5f) {
                                                showSidePanel = true
                                                change.consume()
                                                break
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        }
                    }
            ) { currentDestination ->
                when (currentDestination) {
                    Destination.HOME -> HomeScreen(
                        listState = homeListState,
                        media = visibleMedia,
                        loading = loading,
                        onPermissionGranted = { refreshMedia() },
                        albumTitle = activeAlbumTitle,
                        sortOrder = sortOrderPref,
                        groupBy = groupByPref,
                        gridColumns = gridColumnsPref,
                        gridStyle = gridStylePref,
                        topContentPadding = topContentPadding,
                        bottomContentPadding = bottomContentPadding,
                        onClearAlbumFilter = { performBackNavigation() },
                        onAddPhotosToAlbum = if (activeCustomAlbum != null) { { showAddPhotosPickerForAlbum = activeCustomAlbum } } else null,
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) }
                    )
                    Destination.ALBUMS -> AlbumsScreen(
                        media = media,
                        favourites = favourites,
                        hidden = hidden,
                        inBin = inBin,
                        archive = archive,
                        customAlbums = customAlbums,
                        topContentPadding = topContentPadding,
                        bottomContentPadding = bottomContentPadding,
                        onFilter = { when (it) { "Map" -> { mapReturnDestination = Destination.ALBUMS; mapFocusItem = null; navigateTo(Destination.MAP) }; "Folders" -> navigateTo(Destination.DEVICE_FOLDERS); "Albums" -> navigateTo(Destination.ALBUM_CATALOG); "Favourites" -> { albumFilter = null; navigateTo(Destination.FAVOURITES) }; "Bin" -> { albumFilter = null; navigateTo(Destination.BIN) }; "Videos" -> { albumFilter = null; navigateTo(Destination.VIDEOS) }; "Recently added" -> { albumFilter = null; navigateTo(Destination.RECENTLY_ADDED) }; "Hidden", "Locked folder" -> { albumFilter = null; navigateTo(Destination.LOCKED_FOLDER) }; "Archive" -> { albumFilter = null; navigateTo(Destination.ARCHIVE) }; "People" -> { albumFilter = null; navigateTo(Destination.PEOPLE) }; else -> { albumFilterReturnDestination = Destination.ALBUMS; albumFilter = it; navigateTo(Destination.HOME) } } },
                        onOpen = { selectedMedia = it; navigateTo(Destination.VIEWER) },
                        onCreateAlbumClick = { parentPreset ->
                            createAlbumPresetParent = parentPreset
                            showCreateAlbumDialog = true
                        },
                        onDeleteAlbum = { id -> scope.launch { prefs.deleteCustomAlbum(id) } },
                        onRenameAlbum = { id, newName -> scope.launch { prefs.renameCustomAlbum(id, newName) } }
                    )
                    Destination.FAVOURITES -> FavouritesScreen(
                        items = media.filter { it.uri in favourites && it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        onBack = { performBackNavigation() },
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) },
                        onRemoveFavourite = { item -> scope.launch { prefs.toggleFavourite(item.uri) } }
                    )
                    Destination.BIN -> BinScreen(
                        items = media.filter { it.uri in inBin },
                        onBack = { performBackNavigation() },
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) },
                        onRestore = { item -> scope.launch { prefs.setInBin(item.uri, false) } },
                        onDeletePermanently = { item -> permanentlyDelete(listOf(item)) },
                        onEmptyBin = { items -> permanentlyDelete(items) }
                    )
                    Destination.VIDEOS -> HomeScreen(
                        media = media.filter { it.isVideo && it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        loading = loading,
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
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) }
                    )
                    Destination.RECENTLY_ADDED -> HomeScreen(
                        media = media.filter { item ->
                            item.uri !in hidden && item.uri !in inBin && item.uri !in archive &&
                                item.dateAddedSeconds * 1000L > System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                        },
                        loading = loading,
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
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) }
                    )
                    Destination.LOCKED_FOLDER -> LockedFolderScreen(
                        items = media.filter { it.uri in hidden && it.uri !in inBin },
                        onBack = { performBackNavigation() },
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) },
                        onUnlock = { item -> scope.launch { prefs.setHidden(item.uri, false) } }
                    )
                    Destination.ARCHIVE -> ArchiveScreen(
                        items = media.filter { it.uri in archive && it.uri !in inBin && it.uri !in hidden },
                        onBack = { performBackNavigation() },
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) },
                        onUnarchive = { item -> scope.launch { prefs.setArchived(item.uri, false) } }
                    )
                    Destination.PEOPLE -> PeopleScreen(
                        media = media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        onBack = { performBackNavigation() },
                        onOpenMedia = { item -> selectedMedia = item; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) },
                        onOpenIndexStatus = { navigateTo(Destination.INDEXING_STATUS) }
                    )
                    Destination.MAP -> PhotoMapScreen(
                        media = media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        focusItem = mapFocusItem,
                        onBack = { performBackNavigation() },
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; navigateTo(Destination.VIEWER) }
                    )
                    Destination.DEVICE_FOLDERS -> DeviceFoldersScreen(
                        media = media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        onBack = { performBackNavigation() },
                        onOpenFolder = { folderPath -> albumFilterReturnDestination = Destination.DEVICE_FOLDERS; albumFilter = folderPath; navigateTo(Destination.HOME) }
                    )
                    Destination.ALBUM_CATALOG -> AlbumCatalogScreen(
                        albums = customAlbums,
                        media = media,
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
                    Destination.SETTINGS -> SettingsScreen(
                        total = media.size,
                        onMenuClick = { showSidePanel = true },
                        theme = themePref,
                        topContentPadding = statusBarTop + 14.dp,
                        bottomContentPadding = bottomContentPadding,
                        onTheme = { scope.launch { prefs.setTheme(it) } },
                        onRefresh = { refreshMedia() },
                        onSettings = { label ->
                            when (label) {
                                "Indexing Status" -> navigateTo(Destination.INDEXING_STATUS)
                                "About" -> navigateTo(Destination.ABOUT)
                                "OCR Settings", "OCR" -> navigateTo(Destination.OCR_SETTINGS)
                            }
                        },
                        onPermissionSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                    )
                    Destination.LIBRARY_OVERVIEW -> LibraryOverviewScreen(
                        media = media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        loading = loading,
                        onBack = { performBackNavigation() },
                        onRefresh = { refreshMedia() }
                    )
                    Destination.INDEXING_STATUS -> IndexingStatusScreen(
                        media = media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        ocrLanguages = ocrLanguagesPref,
                        ocrModel = ocrModelPref,
                        onModelChange = { newModel -> scope.launch { prefs.setOcrModel(newModel) } },
                        onOpenOcrSettings = { navigateTo(Destination.OCR_SETTINGS) },
                        onBack = { performBackNavigation() }
                    )
                    Destination.OCR_SETTINGS -> OcrSettingsScreen(
                        currentModel = ocrModelPref,
                        ocrLanguages = ocrLanguagesPref,
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
                        onBack = { performBackNavigation() }
                    )
                    Destination.ABOUT -> AboutScreen(
                        onBack = { performBackNavigation() }
                    )
                    Destination.SEARCH -> SearchScreen(
                        query = searchQuery,
                        stage = searchStage,
                        results = media.filter { it.uri !in hidden && it.uri !in inBin && (!searchVideosOnly || it.isVideo) && (searchStage != SearchStage.RESULTS || it.name.contains(submittedQuery, true) || it.relativePath.contains(submittedQuery, true)) },
                        recentSearches = recentSearches,
                        onQuery = { searchQuery = it; searchStage = if (it.isBlank()) SearchStage.DISCOVERY else SearchStage.TYPING },
                        onSubmit = { submittedQuery = it.trim(); searchQuery = it.trim(); searchStage = SearchStage.RESULTS; if (it.isNotBlank()) scope.launch { prefs.addRecentSearch(it.trim()) } },
                        onRecent = { searchQuery = it; submittedQuery = it; searchStage = SearchStage.RESULTS },
                        onClearRecent = { scope.launch { prefs.clearRecentSearches() } },
                        onBack = { performBackNavigation() },
                        onOpen = { selectedMedia = it; navigateTo(Destination.VIEWER) },
                        videosOnly = searchVideosOnly,
                        onVideosOnly = { searchVideosOnly = it }
                    )
                    Destination.VIEWER -> selectedMedia?.let { item ->
                        ViewerScreen(
                            item = item,
                            items = visibleMedia.ifEmpty { listOf(item) },
                            defaultOcrLanguages = ocrLanguagesPref,
                            defaultOcrModel = ocrModelPref,
                            favourite = item.uri in favourites,
                            isHidden = item.uri in hidden,
                            isBin = item.uri in inBin,
                            isArchived = item.uri in archive,
                            showControls = showViewerControls,
                            showDetails = showDetails,
                            customAlbums = customAlbums,
                            customTags = customTagsMap[item.uri] ?: emptySet(),
                            customPeople = customPeopleMap[item.uri] ?: emptySet(),
                            onAddTag = { tag -> scope.launch { prefs.addTagForMedia(item.uri, tag) } },
                            onAddPerson = { person -> scope.launch { prefs.addPersonForMedia(item.uri, person) } },
                            onToggleControls = { showViewerControls = !showViewerControls },
                            onToggleDetails = { showDetails = !showDetails; showViewerControls = true },
                            onBack = { performBackNavigation() },
                            onFavourite = { scope.launch { prefs.toggleFavourite(item.uri) } },
                            onToggleHide = {
                                val willHide = item.uri !in hidden
                                scope.launch { prefs.setHidden(item.uri, willHide) }
                                performBackNavigation()
                            },
                            onToggleBin = {
                                val willBin = item.uri !in inBin
                                scope.launch { prefs.setInBin(item.uri, willBin) }
                                performBackNavigation()
                            },
                            onToggleArchive = {
                                val willArchive = item.uri !in archive
                                scope.launch { prefs.setArchived(item.uri, willArchive) }
                                performBackNavigation()
                            },
                            onAddTo = { mediaForAddToAlbum = item; showAddToAlbumSheet = true },
                            onOpenNeighbor = { selectedMedia = it; showDetails = false },
                            onOpenMap = { mapFocusItem = item; mapReturnDestination = Destination.VIEWER; showDetails = false; navigateTo(Destination.MAP) }
                        )
                    }
                }
            }

        // Wider top and bottom edge blur across app screens
        if (destination != Destination.VIEWER) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.045f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(6.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.045f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .blur(6.dp)
            )
        }

        // Floating search bar across Home and Albums
        if (destination in listOf(Destination.HOME, Destination.ALBUMS)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                FloatingSearchPill(
                    label = when (destination) {
                        Destination.ALBUMS -> "Search folders, albums…"
                        else -> "Search photos, people, places…"
                    },
                    showMoreOptions = destination == Destination.HOME,
                    onMenuClick = { showSidePanel = true },
                    onOptionsClick = { showHomeViewOptionsSheet = true },
                    onClick = {
                        navigateTo(Destination.SEARCH)
                        searchStage = SearchStage.DISCOVERY
                        searchQuery = ""
                    }
                )
            }
        }

        // Floating Bottom Navigation Bar across Home, Albums, Settings (hidden during active album drill-down)
        if (destination in listOf(Destination.HOME, Destination.ALBUMS, Destination.SETTINGS) && albumFilter == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                FloatingNavigationBar(
                    current = destination,
                    onSelect = { dest ->
                        if (dest == destination) {
                            if (dest == Destination.HOME) {
                                scope.launch {
                                    homeListState.animateScrollToItem(0)
                                }
                            }
                        } else {
                            navigateTo(dest, rememberPrevious = false)
                            albumFilter = null
                        }
                    }
                )
            }
        }
        }

        SideNavigationDrawer(
            visible = showSidePanel,
            destination = destination,
            albumFilter = albumFilter,
            onNavigate = { dest ->
                if (dest == Destination.MAP) {
                    mapReturnDestination = destination
                    mapFocusItem = null
                }
                navigateTo(dest, rememberPrevious = false)
                albumFilter = null
                showSidePanel = false
            },
            onClose = { showSidePanel = false }
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

    if (showAddToAlbumSheet && mediaForAddToAlbum != null) {
        AddToAlbumBottomSheet(
            item = mediaForAddToAlbum!!,
            customAlbums = customAlbums,
            onDismiss = { showAddToAlbumSheet = false },
            onToggleAlbum = { album ->
                scope.launch {
                    if (mediaForAddToAlbum!!.uri in album.mediaUris) {
                        prefs.removeMediaFromCustomAlbum(album.id, mediaForAddToAlbum!!.uri)
                    } else {
                        prefs.addMediaToCustomAlbum(album.id, mediaForAddToAlbum!!.uri)
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

    if (showAddPhotosPickerForAlbum != null) {
        AddPhotosToAlbumDialog(
            album = showAddPhotosPickerForAlbum!!,
            allMedia = media.filter { it.uri !in hidden && it.uri !in inBin },
            onDismiss = { showAddPhotosPickerForAlbum = null },
            onAddSelected = { uris ->
                val target = showAddPhotosPickerForAlbum!!
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
