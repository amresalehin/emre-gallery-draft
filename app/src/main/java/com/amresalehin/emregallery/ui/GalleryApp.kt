package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.exifinterface.media.ExifInterface
import android.location.Geocoder
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.size.Precision
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private enum class Destination { HOME, ALBUMS, MAP, DEVICE_FOLDERS, ALBUM_CATALOG, SETTINGS, LIBRARY_OVERVIEW, OCR_SETTINGS, ABOUT, SEARCH, VIEWER }
private enum class SearchStage { DISCOVERY, TYPING, RESULTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesRepository(context.applicationContext) }
    val mediaRepository = remember { MediaRepository(context.contentResolver) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var previousDestination by remember { mutableStateOf(Destination.HOME) }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(hasMediaPermission(context)) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
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
    val customTagsMap by prefs.customTagsMap.collectAsStateWithLifecycle(initialValue = emptyMap())
    val customPeopleMap by prefs.customPeopleMap.collectAsStateWithLifecycle(initialValue = emptyMap())
    var showHomeViewOptionsSheet by remember { mutableStateOf(false) }

    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var createAlbumPresetParent by remember { mutableStateOf("") }
    var showAddToAlbumSheet by remember { mutableStateOf(false) }
    var mediaForAddToAlbum by remember { mutableStateOf<MediaItem?>(null) }
    var showAddPhotosPickerForAlbum by remember { mutableStateOf<CustomAlbum?>(null) }

    fun refreshMedia() {
        if (!hasMediaPermission(context)) return
        loading = true
        scope.launch {
            media = withContext(Dispatchers.IO) { runCatching { mediaRepository.loadMedia() }.getOrDefault(emptyList()) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (hasMediaPermission(context)) refreshMedia() }

    if (destination != Destination.HOME || albumFilter != null) {
        androidx.activity.compose.BackHandler {
            if (destination == Destination.VIEWER || destination == Destination.SEARCH) {
                destination = previousDestination
            } else if (albumFilter != null) {
                albumFilter = null
            } else {
                destination = Destination.HOME
            }
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    if (targetState == Destination.VIEWER) {
                        (fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                            .togetherWith(fadeOut(animationSpec = tween(180)))
                    } else if (initialState == Destination.VIEWER) {
                        fadeIn(animationSpec = tween(200))
                            .togetherWith(fadeOut(animationSpec = tween(240)) + scaleOut(targetScale = 0.96f, animationSpec = tween(240, easing = FastOutLinearInEasing)))
                    } else {
                        fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(160)))
                    }
                },
                label = "destination_screen_transition",
                modifier = Modifier.fillMaxSize()
            ) { currentDestination ->
                when (currentDestination) {
                    Destination.HOME -> HomeScreen(
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
                        onClearAlbumFilter = { albumFilter = null },
                        onAddPhotosToAlbum = if (activeCustomAlbum != null) { { showAddPhotosPickerForAlbum = activeCustomAlbum } } else null,
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; previousDestination = Destination.HOME; destination = Destination.VIEWER }
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
                        onFilter = { when (it) { "Map" -> destination = Destination.MAP; "Folders" -> destination = Destination.DEVICE_FOLDERS; "Albums" -> destination = Destination.ALBUM_CATALOG; else -> { albumFilter = it; destination = Destination.HOME } } },
                        onOpen = { selectedMedia = it; previousDestination = Destination.ALBUMS; destination = Destination.VIEWER },
                        onCreateAlbumClick = { parentPreset ->
                            createAlbumPresetParent = parentPreset
                            showCreateAlbumDialog = true
                        },
                        onDeleteAlbum = { id -> scope.launch { prefs.deleteCustomAlbum(id) } },
                        onRenameAlbum = { id, newName -> scope.launch { prefs.renameCustomAlbum(id, newName) } }
                    )
                    Destination.MAP -> PhotoMapScreen(
                        media = media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        onBack = { destination = Destination.ALBUMS },
                        onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; previousDestination = Destination.MAP; destination = Destination.VIEWER }
                    )
                    Destination.DEVICE_FOLDERS -> DeviceFoldersScreen(
                        media = media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        onBack = { destination = Destination.ALBUMS },
                        onOpenFolder = { folderPath -> albumFilter = folderPath; destination = Destination.HOME }
                    )
                    Destination.ALBUM_CATALOG -> AlbumCatalogScreen(
                        albums = customAlbums,
                        media = media,
                        onBack = { destination = Destination.ALBUMS },
                        onCreateAlbum = { parentPath ->
                            createAlbumPresetParent = parentPath
                            showCreateAlbumDialog = true
                        },
                        onOpenAlbum = { album ->
                            albumFilter = album.id
                            destination = Destination.HOME
                        }
                    )
                    Destination.SETTINGS -> SettingsScreen(
                        total = media.size,
                        theme = themePref,
                        topContentPadding = statusBarTop + 14.dp,
                        bottomContentPadding = bottomContentPadding,
                        onTheme = { scope.launch { prefs.setTheme(it) } },
                        onRefresh = { refreshMedia() },
                        onSettings = { label ->
                            when (label) {
                                "Library overview" -> destination = Destination.LIBRARY_OVERVIEW
                                "About" -> destination = Destination.ABOUT
                                "OCR" -> destination = Destination.OCR_SETTINGS
                            }
                        },
                        onPermissionSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                    )
                    Destination.LIBRARY_OVERVIEW -> LibraryOverviewScreen(
                        media = media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive },
                        loading = loading,
                        onBack = { destination = Destination.SETTINGS },
                        onRefresh = { refreshMedia() }
                    )
                    Destination.OCR_SETTINGS -> OcrSettingsScreen(
                        onBack = { destination = Destination.SETTINGS }
                    )
                    Destination.ABOUT -> AboutScreen(
                        onBack = { destination = Destination.SETTINGS }
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
                        onBack = { destination = previousDestination },
                        onOpen = { selectedMedia = it; previousDestination = Destination.SEARCH; destination = Destination.VIEWER },
                        videosOnly = searchVideosOnly,
                        onVideosOnly = { searchVideosOnly = it }
                    )
                    Destination.VIEWER -> selectedMedia?.let { item ->
                        ViewerScreen(
                            item = item,
                            items = visibleMedia.ifEmpty { listOf(item) },
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
                            onBack = { destination = previousDestination },
                            onFavourite = { scope.launch { prefs.toggleFavourite(item.uri) } },
                            onToggleHide = {
                                val willHide = item.uri !in hidden
                                scope.launch { prefs.setHidden(item.uri, willHide) }
                                destination = previousDestination
                            },
                            onToggleBin = {
                                val willBin = item.uri !in inBin
                                scope.launch { prefs.setInBin(item.uri, willBin) }
                                destination = previousDestination
                            },
                            onToggleArchive = {
                                val willArchive = item.uri !in archive
                                scope.launch { prefs.setArchived(item.uri, willArchive) }
                                destination = previousDestination
                            },
                            onAddTo = { mediaForAddToAlbum = item; showAddToAlbumSheet = true },
                            onOpenNeighbor = { selectedMedia = it; showDetails = false }
                        )
                    }
                }
            }

        // Subtle top and bottom edge blur effect (2-3% of screen height each)
        if (destination != Destination.VIEWER) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.025f)
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
                    .fillMaxHeight(0.025f)
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

        // Floating Top Search Bar across Home, Albums
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
                    onOptionsClick = { showHomeViewOptionsSheet = true },
                    onClick = {
                        previousDestination = destination
                        destination = Destination.SEARCH
                        searchStage = SearchStage.DISCOVERY
                        searchQuery = ""
                    }
                )
            }
        }

        // Floating Bottom Navigation Bar across Home, Albums, Settings
        if (destination in listOf(Destination.HOME, Destination.ALBUMS, Destination.SETTINGS)) {
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
                        destination = dest
                        albumFilter = null
                    }
                )
            }
        }
        }
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

private fun hasMediaPermission(context: android.content.Context): Boolean {
    val checks = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    return checks.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } || (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
}

@Composable
private fun PermissionState(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp)); Text("Your photos, in one place", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Allow access to photos and videos stored on this device to build your gallery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp)); Button(onClick = onRequest) { Text("Allow access") }
    }
}

@Composable
private fun FloatingSearchPill(
    label: String,
    onClick: () -> Unit,
    showMoreOptions: Boolean = false,
    onOptionsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("floating_search_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = if (showMoreOptions) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showMoreOptions) {
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("home_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "View options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavigationBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("floating_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Destination.HOME to ("Home" to Icons.Default.Home),
                Destination.ALBUMS to ("Albums" to Icons.Default.PhotoLibrary),
                Destination.SETTINGS to ("Settings" to Icons.Default.Settings)
            )

            items.forEach { (dest, pair) ->
                val (label, icon) = pair
                val selected = current == dest

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelect(dest) }
                        .padding(vertical = 4.dp)
                        .testTag("nav_item_${label.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier.height(30.dp).width(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    media: List<MediaItem>,
    loading: Boolean,
    albumTitle: String? = null,
    sortOrder: String = "date_desc",
    groupBy: String = "day",
    gridColumns: Int = 3,
    gridStyle: String = "masonry",
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onClearAlbumFilter: () -> Unit = {},
    onAddPhotosToAlbum: (() -> Unit)? = null,
    onPermissionGranted: () -> Unit = {},
    onOpen: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val hasPermission = hasMediaPermission(context)
        granted = hasPermission
        if (hasPermission) onPermissionGranted()
    }
    LaunchedEffect(Unit) { granted = hasMediaPermission(context) }
    if (!granted) {
        PermissionState { launcher.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) }
        return
    }

    val sortedMedia = remember(media, sortOrder) {
        when (sortOrder) {
            "date_asc", "date_taken_asc" -> media.sortedWith(compareBy({ if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }, { it.id }))
            "date_added_asc" -> media.sortedWith(compareBy({ it.dateAddedSeconds * 1000L }, { it.id }))
            "date_added_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.dateAddedSeconds * 1000L }.thenBy { it.id })
            "name_asc" -> media.sortedWith(compareBy({ it.name.lowercase(Locale.getDefault()) }, { it.id }))
            "name_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.name.lowercase(Locale.getDefault()) }.thenBy { it.id })
            "size_asc" -> media.sortedWith(compareBy<MediaItem> { it.sizeBytes }.thenBy { it.id })
            "size_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.sizeBytes }.thenBy { it.id })
            else -> media.sortedWith(compareByDescending<MediaItem> { if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }.thenBy { it.id })
        }
    }

    val groups = remember(sortedMedia, groupBy) {
        when (groupBy) {
            "day" -> groupMediaByDay(sortedMedia)
            "month" -> groupMediaByMonth(sortedMedia)
            "year" -> groupMediaByYear(sortedMedia)
            "folder" -> groupMediaByFolder(sortedMedia)
            "initial" -> groupMediaByInitial(sortedMedia)
            "none" -> listOf(DayGroup(dayKey = 0L, dayLabel = "", items = sortedMedia))
            else -> listOf(DayGroup(dayKey = 0L, dayLabel = "", items = sortedMedia))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (albumTitle != null) {
            val isCollection = albumTitle in listOf("Locked folder", "Bin", "Archive", "Favourites", "Videos", "Recently added")
            val collectionIcon = when (albumTitle) {
                "Favourites" -> Icons.Default.Favorite
                "Videos" -> Icons.Default.PlayCircle
                "Recently added" -> Icons.Default.Schedule
                "Locked folder" -> Icons.Default.Lock
                "Bin" -> Icons.Default.Delete
                "Archive" -> Icons.Default.Archive
                else -> Icons.Default.Folder
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(collectionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isCollection) "Collection" else "Album", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            Text(albumTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (onAddPhotosToAlbum != null) {
                            FilledTonalButton(
                                onClick = onAddPhotosToAlbum,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add photos", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        IconButton(onClick = onClearAlbumFilter, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Clear filter", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        if (loading && sortedMedia.isEmpty()) {
            item {
                HomeScreenSkeleton(
                    gridColumns = gridColumns,
                    gridStyle = gridStyle,
                    showMemories = albumTitle == null
                )
            }
        } else if (sortedMedia.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (albumTitle != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("No photos in this album", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(6.dp))
                            Text("Add photos from your gallery to populate this album.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (onAddPhotosToAlbum != null) {
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = onAddPhotosToAlbum) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add photos")
                                }
                            }
                        }
                    } else {
                        Text("No photos or videos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
            }
            if (albumTitle == null) {
                item { MemoriesStrip(sortedMedia, onOpen) }
            }

            groups.forEach { group ->
                if (group.dayLabel.isNotBlank()) {
                    item(key = "header_${group.dayKey}", contentType = "header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = group.dayLabel,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${group.items.size} ${if (group.items.size == 1) "item" else "items"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val rowChunks = remember(group.items, gridColumns) { group.items.chunked(gridColumns) }
                items(
                    count = rowChunks.size,
                    key = { rIdx -> "day_${group.dayKey}_row_${rowChunks[rIdx].first().id}" },
                    contentType = { "photo_row" }
                ) { rowIndex ->
                    val rowItems = rowChunks[rowIndex]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in 0 until gridColumns) {
                            val item = rowItems.getOrNull(i)
                            Box(modifier = Modifier.weight(1f)) {
                                if (item != null) {
                                    MediaGridItem(
                                        item = item,
                                        isSquare = (gridStyle == "square"),
                                        colIndex = i,
                                        onOpen = onOpen
                                    )
                                } else {
                                    Spacer(Modifier.aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreenSkeleton(
    gridColumns: Int = 3,
    gridStyle: String = "masonry",
    showMemories: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_screen_skeleton"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showMemories) {
            // Memories row skeleton
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(118.dp)
                            .height(174.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alphaAnim * 0.7f)
                                )
                        )
                    }
                }
            }
        }

        // Section header skeleton
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                    )
            )
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alphaAnim * 0.8f)
                    )
            )
        }

        // Multi-column grid skeleton
        val masonryHeights = listOf(140.dp, 190.dp, 130.dp, 170.dp, 120.dp, 180.dp, 150.dp, 130.dp, 160.dp, 140.dp, 200.dp, 135.dp)
        val validCols = gridColumns.coerceIn(1, 6)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (col in 0 until validCols) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(4) { rowIndex ->
                        val itemIndex = (col + rowIndex * validCols) % masonryHeights.size
                        val height = if (gridStyle == "square") 110.dp else masonryHeights[itemIndex]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoriesStrip(media: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    val today = remember { Calendar.getInstance() }
    val currentYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH)
    val todayDay = today.get(Calendar.DAY_OF_YEAR)

    val stories = remember(media, currentYear, todayMonth, todayDay) {
        val eligible = media.mapNotNull { item ->
            val timestamp = item.dateTakenMillis.takeIf { it > 0L } ?: item.dateAddedSeconds * 1000L
            if (timestamp <= 0L) null else item to Calendar.getInstance().apply { timeInMillis = timestamp }
        }
        val result = mutableListOf<Pair<String, List<MediaItem>>>()

        // Google Photos-style "On this day" stories: real photos from this calendar date in prior years.
        val onThisDay = eligible.filter { (_, date) ->
            date.get(Calendar.MONTH) == todayMonth &&
                date.get(Calendar.DAY_OF_YEAR) == todayDay &&
                date.get(Calendar.YEAR) < currentYear
        }.groupBy { (_, date) -> date.get(Calendar.YEAR) }
            .toSortedMap(compareByDescending { it })
        onThisDay.forEach { (year, entries) ->
            result += "On this day · $year" to entries.map { it.first }
        }

        // Real date-based trip/story groups from the library, not generic sample cards.
        eligible.groupBy { (_, date) ->
            date.get(Calendar.YEAR) * 100 + date.get(Calendar.DAY_OF_YEAR)
        }.entries
            .filter { it.value.size >= 4 }
            .sortedByDescending { it.key }
            .take(8)
            .forEach { (_, entries) ->
                val date = entries.first().second
                val label = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date.time)
                if (result.none { it.second.any { storyItem -> storyItem.uri == entries.first().first.uri } }) {
                    result += label to entries.map { it.first }
                }
            }

        result.filter { it.second.isNotEmpty() }.take(10)
    }

    if (stories.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Memories",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(stories.size, key = { index -> stories[index].first }) { index ->
                val (title, storyMedia) = stories[index]
                val cover = storyMedia.first()
                Box(
                    Modifier.width(132.dp).height(184.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpen(cover) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(cover.uri))
                            .size(264, 368)
                            .crossfade(false)
                            .memoryCacheKey("${cover.uri}_memory_story")
                            .build(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxWidth().height(78.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${storyMedia.size} photos and videos",
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private data class DayGroup(
    val dayKey: Long,
    val dayLabel: String,
    val items: List<MediaItem>
)

private fun groupMediaByDay(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()

    val cal = Calendar.getInstance()
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = todayCal.timeInMillis
    val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)
    val currentYear = todayCal.get(Calendar.YEAR)

    val dayFormatSameYear = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    val dayFormatOtherYear = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    val groupedMap = LinkedHashMap<Long, MutableList<MediaItem>>()

    for (item in media) {
        val timestamp = if (item.dateTakenMillis > 0) item.dateTakenMillis else (item.dateAddedSeconds * 1000L)
        val validTimestamp = if (timestamp > 0) timestamp else todayStart
        cal.timeInMillis = validTimestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis

        groupedMap.getOrPut(dayStart) { mutableListOf() }.add(item)
    }

    return groupedMap.entries
        .sortedByDescending { it.key }
        .map { (dayStart, dayItems) ->
            val label = when (dayStart) {
                todayStart -> "Today"
                yesterdayStart -> "Yesterday"
                else -> {
                    cal.timeInMillis = dayStart
                    if (cal.get(Calendar.YEAR) == currentYear) {
                        dayFormatSameYear.format(Date(dayStart))
                    } else {
                        dayFormatOtherYear.format(Date(dayStart))
                    }
                }
            }
            DayGroup(dayKey = dayStart, dayLabel = label, items = dayItems)
        }
}

private fun groupMediaByMonth(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()

    val cal = Calendar.getInstance()
    val todayCal = Calendar.getInstance()
    val currentYear = todayCal.get(Calendar.YEAR)

    val monthFormatSameYear = SimpleDateFormat("MMMM", Locale.getDefault())
    val monthFormatOtherYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val groupedMap = LinkedHashMap<Long, MutableList<MediaItem>>()

    for (item in media) {
        val timestamp = if (item.dateTakenMillis > 0) item.dateTakenMillis else (item.dateAddedSeconds * 1000L)
        val validTimestamp = if (timestamp > 0) timestamp else System.currentTimeMillis()
        cal.timeInMillis = validTimestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis

        groupedMap.getOrPut(monthStart) { mutableListOf() }.add(item)
    }

    return groupedMap.entries
        .sortedByDescending { it.key }
        .map { (monthStart, monthItems) ->
            cal.timeInMillis = monthStart
            val label = if (cal.get(Calendar.YEAR) == currentYear) {
                monthFormatSameYear.format(Date(monthStart))
            } else {
                monthFormatOtherYear.format(Date(monthStart))
            }
            DayGroup(dayKey = monthStart, dayLabel = label, items = monthItems)
        }
}

private fun groupMediaByYear(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    val groupedMap = LinkedHashMap<Long, MutableList<MediaItem>>()
    for (item in media) {
        val timestamp = if (item.dateTakenMillis > 0) item.dateTakenMillis else (item.dateAddedSeconds * 1000L)
        val validTimestamp = if (timestamp > 0) timestamp else System.currentTimeMillis()
        cal.timeInMillis = validTimestamp
        cal.set(Calendar.MONTH, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val yearStart = cal.timeInMillis
        groupedMap.getOrPut(yearStart) { mutableListOf() }.add(item)
    }
    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    return groupedMap.entries
        .sortedByDescending { it.key }
        .map { (yearStart, yearItems) ->
            DayGroup(dayKey = yearStart, dayLabel = yearFormat.format(Date(yearStart)), items = yearItems)
        }
}

private fun groupMediaByFolder(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()
    var syntheticKey = 0L
    return media.groupBy {
        it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" }
    }.map { (folder, folderItems) ->
        DayGroup(dayKey = syntheticKey++, dayLabel = folder, items = folderItems)
    }
}

private fun groupMediaByInitial(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()
    var syntheticKey = 0L
    return media.groupBy {
        val firstChar = it.name.trim().firstOrNull()?.uppercaseChar()
        if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
    }.toSortedMap().map { (initial, initialItems) ->
        DayGroup(dayKey = syntheticKey++, dayLabel = initial, items = initialItems)
    }
}

@Composable
private fun DynamicMasonryGrid(
    media: List<MediaItem>,
    columnsCount: Int,
    onOpen: (MediaItem) -> Unit
) {
    if (media.isEmpty()) return
    val count = columnsCount.coerceIn(1, 6)

    val columns = remember(media, count) {
        val cols = List(count) { mutableListOf<MediaItem>() }
        val heights = FloatArray(count) { 0f }

        media.forEachIndexed { index, item ->
            val aspect = if (item.width > 0 && item.height > 0) {
                (item.width.toFloat() / item.height.toFloat()).coerceIn(0.65f, 1.6f)
            } else {
                when ((item.id.hashCode() + index).mod(4)) {
                    0 -> 0.78f
                    1 -> 1.0f
                    2 -> 0.88f
                    else -> 1.25f
                }
            }
            val relativeHeight = 1f / aspect

            var minCol = 0
            for (c in 1 until count) {
                if (heights[c] < heights[minCol]) minCol = c
            }

            cols[minCol].add(item)
            heights[minCol] += relativeHeight
        }
        cols
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        columns.forEachIndexed { colIndex, colItems ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                colItems.forEach { item ->
                    MediaGridItem(item = item, isSquare = false, colIndex = colIndex, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun SquareGrid(
    media: List<MediaItem>,
    columnsCount: Int,
    onOpen: (MediaItem) -> Unit
) {
    if (media.isEmpty()) return
    val count = columnsCount.coerceIn(1, 6)
    val rows = remember(media, count) { media.chunked(count) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until count) {
                    val item = rowItems.getOrNull(i)
                    Box(modifier = Modifier.weight(1f)) {
                        if (item != null) {
                            MediaGridItem(item = item, isSquare = true, colIndex = i, onOpen = onOpen)
                        } else {
                            Spacer(Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGridItem(
    item: MediaItem,
    isSquare: Boolean,
    colIndex: Int,
    onOpen: (MediaItem) -> Unit
) {
    val aspect = if (isSquare) 1f else {
        if (item.width > 0 && item.height > 0) {
            (item.width.toFloat() / item.height.toFloat()).coerceIn(0.65f, 1.6f)
        } else {
            when ((item.id.hashCode() + colIndex).mod(4)) {
                0 -> 0.78f
                1 -> 1.0f
                2 -> 0.88f
                else -> 1.25f
            }
        }
    }

    val context = LocalContext.current
    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.uri))
            .size(360, 360)
            .crossfade(false)
            .memoryCacheKey("${item.uri}_thumb")
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen(item) }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(3.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeViewOptionsBottomSheet(
    sortOrder: String,
    groupBy: String,
    gridColumns: Int,
    gridStyle: String,
    onSortOrderChange: (String) -> Unit,
    onGroupByChange: (String) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onGridStyleChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempGridStyle by remember(gridStyle) { mutableStateOf(gridStyle) }
    var tempGridColumns by remember(gridColumns) { mutableIntStateOf(gridColumns.coerceIn(1, 6)) }

    val initialField = when {
        sortOrder.startsWith("date_added") -> "date_added"
        sortOrder.startsWith("name") -> "name"
        sortOrder.startsWith("size") -> "size"
        else -> "date_taken"
    }
    val initialDirection = if (sortOrder.endsWith("_asc")) "asc" else "desc"

    var tempSortField by remember(sortOrder) { mutableStateOf(initialField) }
    var tempSortDirection by remember(sortOrder) { mutableStateOf(initialDirection) }
    var tempGroupBy by remember(groupBy) { mutableStateOf(groupBy.ifBlank { "none" }) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. View section
            Text(
                text = "View",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grid Button
                val isGridSelected = tempGridStyle == "square"
                Surface(
                    onClick = { tempGridStyle = "square" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isGridSelected) Color(0xFF141416) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isGridSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .testTag("view_option_grid")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Grid",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Masonry Button
                val isMasonrySelected = tempGridStyle == "masonry"
                Surface(
                    onClick = { tempGridStyle = "masonry" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isMasonrySelected) Color(0xFF141416) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isMasonrySelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .testTag("view_option_masonry")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Dashboard,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Masonry",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // 2. Columns section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Columns",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.Default.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = tempGridColumns.toFloat(),
                    onValueChange = { tempGridColumns = it.roundToInt() },
                    valueRange = 1f..6f,
                    steps = 4,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("view_columns_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF141416),
                        activeTrackColor = Color(0xFF141416),
                        inactiveTrackColor = Color(0xFFE5E5EA),
                        activeTickColor = Color.White,
                        inactiveTickColor = Color(0xFF8E8E93)
                    )
                )
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$tempGridColumns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 3. Sort by section
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val sortFields = listOf(
                Triple("date_taken", "Date taken", Icons.Default.CalendarMonth),
                Triple("date_added", "Date added", Icons.Default.EventAvailable),
                Triple("name", "File name", Icons.Default.SortByAlpha),
                Triple("size", "File size", Icons.Default.Description)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortFields.forEach { (field, label, icon) ->
                    val isSelected = tempSortField == field
                    Surface(
                        onClick = {
                            if (!isSelected) {
                                tempSortField = field
                            } else {
                                tempSortDirection = if (tempSortDirection == "asc") "desc" else "asc"
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (isSelected) {
                            BorderStroke(2.dp, Color(0xFF141416))
                        } else {
                            BorderStroke(1.dp, Color(0xFFE5E5EA))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(82.dp)
                            .testTag("sort_card_$field")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 7.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                tempSortField = field
                                                tempSortDirection = "asc"
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = "Ascending",
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isSelected && tempSortDirection == "asc") Color(0xFF141416) else Color(0xFF8E8E93).copy(alpha = 0.5f)
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                tempSortField = field
                                                tempSortDirection = "desc"
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = "Descending",
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isSelected && tempSortDirection == "desc") Color(0xFF141416) else Color(0xFF8E8E93).copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 4. Group by section
            Text(
                text = "Group by",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val groupOptions = listOf(
                Triple("none", "None", Icons.Default.Block),
                Triple("day", "Day", Icons.Default.CalendarToday),
                Triple("month", "Month", Icons.Default.CalendarMonth),
                Triple("year", "Year", Icons.Default.DateRange),
                Triple("folder", "Device folder", Icons.Default.Folder),
                Triple("initial", "Initial", Icons.Default.TextFields)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupOptions.forEach { (key, label, icon) ->
                    val isSelected = tempGroupBy == key
                    Surface(
                        onClick = { tempGroupBy = key },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (isSelected) {
                            BorderStroke(2.dp, Color(0xFF141416))
                        } else {
                            BorderStroke(1.dp, Color(0xFFE5E5EA))
                        },
                        modifier = Modifier
                            .width(68.dp)
                            .height(72.dp)
                            .testTag("group_card_$key")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 5. Bottom Action Buttons: Reset to default & Apply
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset to default
                Surface(
                    onClick = {
                        tempGridStyle = "square"
                        tempGridColumns = 3
                        tempSortField = "date_taken"
                        tempSortDirection = "desc"
                        tempGroupBy = "none"
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Color(0xFFDCDCE0)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reset_view_options_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Reset to default",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Apply
                Surface(
                    onClick = {
                        onGridStyleChange(tempGridStyle)
                        onGridColumnsChange(tempGridColumns)
                        val mappedSortOrder = when (tempSortField) {
                            "date_taken" -> if (tempSortDirection == "asc") "date_taken_asc" else "date_taken_desc"
                            "date_added" -> if (tempSortDirection == "asc") "date_added_asc" else "date_added_desc"
                            "name" -> if (tempSortDirection == "asc") "name_asc" else "name_desc"
                            "size" -> if (tempSortDirection == "asc") "size_asc" else "size_desc"
                            else -> "date_taken_desc"
                        }
                        onSortOrderChange(mappedSortOrder)
                        onGroupByChange(tempGroupBy)
                        onDismiss()
                    },
                    shape = CircleShape,
                    color = Color(0xFF141416),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("apply_view_options_button")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Apply",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsScreen(
    media: List<MediaItem>,
    favourites: Set<String>,
    hidden: Set<String>,
    inBin: Set<String>,
    archive: Set<String>,
    customAlbums: List<CustomAlbum>,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onFilter: (String) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onCreateAlbumClick: (parentPreset: String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit
) {
    val normalMedia = remember(media, hidden, inBin, archive) {
        media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive }
    }
    val folders = remember(normalMedia) {
        normalMedia.groupBy { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }
            .entries.sortedByDescending { it.value.size }
    }

    val favouritesCount = favourites.size
    val binCount = inBin.size
    val videosCount = normalMedia.count { it.isVideo }
    val recentlyAddedCount = normalMedia.count {
        it.dateAddedSeconds * 1000L > System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    }
    val hiddenCount = hidden.size
    val archiveCount = archive.size

    var albumToRename by remember { mutableStateOf<CustomAlbum?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (albumToRename != null) {
        AlertDialog(
            onDismissRequest = { albumToRename = null },
            title = { Text("Rename album") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New album name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameAlbum(albumToRename!!.id, renameText.trim())
                        }
                        albumToRename = null
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { albumToRename = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        )
    ) {
        // 1. People & pets Card (empty faces, smaller floating arrow in corner)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("albums_people_card")
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    // 2 rows of empty avatar frames
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(List(14) { it }) { _ ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .size(58.dp)
                                    .clickable { onFilter("People") }
                            ) {}
                        }
                    }

                    // Circular arrow button in top right
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.TopEnd)
                            .clickable { onFilter("People") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Open People",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Album (2/3rd, squaricle) and Device Folder (1/3rd, portrait) in same row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column: Album Card (2/3rd - squaricle)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 5.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .clickable { onFilter("Albums") }
                        .testTag("albums_section_card")
                ) {
                    val topAlbum = customAlbums.firstOrNull()
                    val topAlbumCover = topAlbum?.let { alb -> media.firstOrNull { it.uri in alb.mediaUris } }
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (topAlbumCover != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(Uri.parse(topAlbumCover.uri))
                                    .size(400, 400)
                                    .crossfade(false)
                                    .memoryCacheKey("${topAlbumCover.uri}_album_cover")
                                    .build(),
                                contentDescription = topAlbum.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f))
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (topAlbumCover != null) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PhotoAlbum,
                                            contentDescription = null,
                                            tint = if (topAlbumCover != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (topAlbumCover != null) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable { onCreateAlbumClick("") }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "New album",
                                                tint = if (topAlbumCover != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = if (topAlbumCover != null) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable { onFilter("Albums") }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.ArrowForward,
                                                contentDescription = "View albums",
                                                tint = if (topAlbumCover != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Column {
                                Text(
                                    text = if (topAlbum != null) topAlbum.name else "Albums",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (topAlbumCover != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (customAlbums.isEmpty()) "Tap + to create" else "${customAlbums.size} ${if (customAlbums.size == 1) "album" else "albums"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (topAlbumCover != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Right Column: Device Folder Card (1/3rd - portrait)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 5.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onFilter("Folders") }
                        .testTag("folders_section_card")
                ) {
                    val topFolder = folders.firstOrNull()
                    val topFolderCover = topFolder?.value?.firstOrNull()

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (topFolderCover != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(Uri.parse(topFolderCover.uri))
                                    .size(360, 360)
                                    .crossfade(false)
                                    .memoryCacheKey("${topFolderCover.uri}_folder_cover")
                                    .build(),
                                contentDescription = topFolder.key.substringAfterLast('/'),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f))
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (topFolderCover != null) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (topFolderCover != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (topFolderCover != null) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable { onFilter("Folders") }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = "View folders",
                                            tint = if (topFolderCover != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            Column {
                                Text(
                                    text = topFolder?.key?.substringAfterLast('/') ?: "Folders",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (topFolderCover != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (topFolder != null) "${topFolder.value.size} items" else "${folders.size} folders",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (topFolderCover != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Maps Card (strictly black-grey-white)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFilter("Map") }
                    .testTag("albums_map_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    // Decorative map icon overlay
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier
                            .size(150.dp)
                            .align(Alignment.CenterEnd)
                            .offset(x = 24.dp)
                            .alpha(0.06f),
                        tint = MaterialTheme.colorScheme.onSurface
                    )

                    // Content overlay
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = "Map and Places",
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { onFilter("Places") }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Open map",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Map",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 5. The tiles - Collections
        item {
            // Top primary pills: Favourites & Bin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CollectionPill(
                    title = "Favourites",
                    count = "$favouritesCount items",
                    icon = Icons.Default.Favorite,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Favourites") },
                    modifier = Modifier.weight(1f)
                )
                CollectionPill(
                    title = "Bin",
                    count = "$binCount items",
                    icon = Icons.Default.Delete,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Bin") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // 2-column tiles: Row 1 (Videos & Recently added)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CollectionTile(
                    title = "Videos",
                    count = "$videosCount items",
                    icon = Icons.Default.PlayCircle,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Videos") },
                    modifier = Modifier.weight(1f)
                )
                CollectionTile(
                    title = "Recently added",
                    count = "$recentlyAddedCount items",
                    icon = Icons.Default.Schedule,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Recently added") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // 2-column tiles: Row 2 (Locked folder & Archive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CollectionTile(
                    title = "Locked folder",
                    count = if (hiddenCount > 0) "$hiddenCount items" else "Private",
                    icon = Icons.Default.Lock,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Hidden") },
                    modifier = Modifier.weight(1f)
                )
                CollectionTile(
                    title = "Archive",
                    count = "$archiveCount items",
                    icon = Icons.Default.Archive,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Archive") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FolderCard(name: String, items: List<MediaItem>, onClick: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.width(142.dp)) {
        Box(Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
            items.firstOrNull()?.let {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(it.uri))
                        .size(300, 300)
                        .crossfade(false)
                        .memoryCacheKey("${it.uri}_thumb")
                        .build(),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            IconButton(onClick = onMore, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(32.dp).background(Color.Black.copy(alpha = .35f), CircleShape)) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
        }
        Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
        Text("${items.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CreateCollectionCard(onClick: () -> Unit) {
    Column(Modifier.width(142.dp)) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(126.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Create album", Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurface) } }
        Text("Create album", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
        Text("Add new collection", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(query: String, stage: SearchStage, results: List<MediaItem>, recentSearches: List<String>, onQuery: (String) -> Unit, onSubmit: (String) -> Unit, onRecent: (String) -> Unit, onClearRecent: () -> Unit, onBack: () -> Unit, onOpen: (MediaItem) -> Unit, videosOnly: Boolean, onVideosOnly: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.weight(1f), placeholder = { Text("Search photos, people, places…", maxLines = 1) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { Row(verticalAlignment = Alignment.CenterVertically) { if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear query") }; IconButton(onClick = { onVideosOnly(!videosOnly) }) { Icon(Icons.Default.FilterList, "Toggle video filter") } } }, shape = CircleShape)
        }
        when (stage) {
            SearchStage.DISCOVERY -> LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { Text("People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text("Person recognition will appear when configured.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text("Places", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text("Search location metadata when available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text("Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Favourites", "Camera", "Videos", "Recently added")) { label -> AssistChip(onClick = { onQuery(label); onSubmit(label) }, label = { Text(label) }, leadingIcon = { Icon(Icons.Default.PhotoAlbum, null) }) } } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)); TextButton(onClick = onClearRecent) { Text("Clear all") } }; if (recentSearches.isEmpty()) Text("Your recent searches will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) else recentSearches.forEach { item -> ListItem(headlineContent = { Text(item) }, leadingContent = { Icon(Icons.Default.History, null) }, modifier = Modifier.clickable { onRecent(item) }) } }
            }
            SearchStage.TYPING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = !videosOnly, onClick = { onVideosOnly(false) }, label = { Text("Photos & videos") }); FilterChip(selected = videosOnly, onClick = { onVideosOnly(true) }, label = { Text("Videos") }) }
                LazyColumn {
                    val matches = results.filter { it.name.contains(query, true) || it.relativePath.contains(query, true) }.take(8)
                    items(matches) { item ->
                        ListItem(
                            headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(item.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(item.uri))
                                        .size(150, 150)
                                        .crossfade(false)
                                        .memoryCacheKey("${item.uri}_search_icon")
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            },
                            trailingContent = { Icon(Icons.Default.NorthWest, null) },
                            modifier = Modifier.clickable { onOpen(item) }
                        )
                    }
                    item { TextButton(onClick = { onSubmit(query) }, modifier = Modifier.fillMaxWidth()) { Text("Search for ‘$query’") } }
                }
            }
            SearchStage.RESULTS -> {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(query.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text("${results.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { onVideosOnly(!videosOnly) }) { Icon(Icons.Default.FilterList, "Filter results") } }
                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    gridItems(results) { item ->
                        Box(Modifier.fillMaxWidth().aspectRatio(.82f).clip(RoundedCornerShape(5.dp)).clickable { onOpen(item) }) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(Uri.parse(item.uri))
                                    .size(300, 300)
                                    .crossfade(false)
                                    .memoryCacheKey("${item.uri}_thumb")
                                    .build(),
                                item.name,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (item.isVideo) Icon(Icons.Default.PlayArrow, null, Modifier.align(Alignment.BottomEnd).padding(4.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    favourite: Boolean,
    isHidden: Boolean,
    isBin: Boolean,
    isArchived: Boolean,
    showControls: Boolean,
    showDetails: Boolean,
    onToggleControls: () -> Unit,
    onToggleDetails: () -> Unit,
    onBack: () -> Unit,
    onFavourite: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleBin: () -> Unit,
    onToggleArchive: () -> Unit,
    onAddTo: () -> Unit,
    onOpenNeighbor: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    var metadata by remember(item.uri) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var ocrText by remember(item.uri) { mutableStateOf("") }
    var ocrError by remember(item.uri) { mutableStateOf<String?>(null) }
    var ocrBusy by remember(item.uri) { mutableStateOf(false) }
    val ocrLanguages = remember(item.uri) { mutableStateListOf("eng") }
    val ocrScope = rememberCoroutineScope()
    LaunchedEffect(item.uri) {
        metadata = withContext(Dispatchers.IO) {
            runCatching { readMediaMetadata(context, item) }.getOrDefault(emptyMap())
        }
    }
    fun meta(key: String): String = metadata[key]?.takeIf { it.isNotBlank() } ?: "Not available"
    var showMore by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val share: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType ?: if (item.isVideo) "video/*" else "image/*"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share media"
                )
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Keep the viewer canvas consistent with the user's selected app theme.
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(item.uri) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onLongPress = { showMore = true }
                )
            }
            .pointerInput(items, item.uri, showDetails) {
                detectDragGestures(
                    onDragStart = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                    },
                    onDragEnd = {
                        val absY = kotlin.math.abs(dragOffsetY)
                        val absX = kotlin.math.abs(dragOffsetX)
                        if (absY > absX) {
                            if (dragOffsetY < -45f && !showDetails) {
                                onToggleDetails()
                            } else if (dragOffsetY > 45f && showDetails) {
                                onToggleDetails()
                            }
                        } else {
                            if (!showDetails && absX > 60f) {
                                val index = items.indexOfFirst { it.uri == item.uri }
                                if (index != -1) {
                                    val targetIndex = if (dragOffsetX < 0) index + 1 else index - 1
                                    items.getOrNull(targetIndex)?.let(onOpenNeighbor)
                                }
                            }
                        }
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = item,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "viewer_media_transition",
            modifier = Modifier.fillMaxSize()
        ) { currentItem ->
            if (currentItem.isVideo) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { vc ->
                        androidx.media3.ui.PlayerView(vc).apply {
                            player = androidx.media3.exoplayer.ExoPlayer.Builder(vc).build().also { p ->
                                p.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(currentItem.uri)))
                                p.prepare()
                                p.playWhenReady = true
                            }
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.useController = showControls }
                )
            } else {
                AsyncImage(
                    Uri.parse(currentItem.uri),
                    currentItem.name,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        if (showControls && !showDetails) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewerButton(Icons.Default.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                ViewerButton(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite", onFavourite)
                ViewerButton(Icons.Default.MoreVert, "More") { showMore = true }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ViewerAction(Icons.Default.Share, "Share", share)
                ViewerAction(Icons.Default.Tune, "Edit") {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_EDIT)
                                .setDataAndType(Uri.parse(item.uri), item.mimeType ?: "image/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    }
                }
                ViewerAction(Icons.Default.Add, "Add to", onAddTo)
                ViewerAction(
                    if (isBin) Icons.Default.Restore else Icons.Default.Delete,
                    if (isBin) "Restore" else "Delete",
                    onToggleBin
                )
                ViewerAction(Icons.Default.MoreHoriz, "More") { showMore = true }
            }

        }

        // Smooth backdrop scrim
        AnimatedVisibility(
            visible = showDetails,
            enter = fadeIn(animationSpec = tween(280)),
            exit = fadeOut(animationSpec = tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .45f))
                    .clickable { onToggleDetails() }
            )
        }

        // Smooth slide-up details panel with drag-down dismiss
        AnimatedVisibility(
            visible = showDetails,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(340, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(.90f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 10.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Top drag handle with drag down dismiss
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.y > 16f) onToggleDetails()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .width(42.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header: Filename, Date & Time, and Edit button pill
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = formatMediaDateTime(item.dateTakenMillis, item.dateAddedSeconds),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_EDIT)
                                                    .setDataAndType(Uri.parse(item.uri), item.mimeType ?: "image/*")
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            )
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Edit",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Top Quick Actions row: Share, Add to, Move to, Delete, More
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DetailActionPill(
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    modifier = Modifier.weight(1f),
                                    onClick = share
                                )
                                DetailActionPill(
                                    icon = Icons.Default.Add,
                                    label = "Add to",
                                    modifier = Modifier.weight(1f),
                                    onClick = onAddTo
                                )
                                DetailActionPill(
                                    icon = Icons.Default.Folder,
                                    label = "Move to",
                                    modifier = Modifier.weight(1f),
                                    onClick = onAddTo
                                )
                                DetailActionPill(
                                    icon = if (isBin) Icons.Default.Restore else Icons.Default.Delete,
                                    label = if (isBin) "Restore" else "Delete",
                                    modifier = Modifier.weight(1f),
                                    onClick = onToggleBin
                                )
                                DetailActionPill(
                                    icon = Icons.Default.MoreHoriz,
                                    label = "More",
                                    modifier = Modifier.weight(1f),
                                    onClick = { showMore = true }
                                )
                            }
                        }

                        // Card 1: Location & Mini Map Thumbnail Preview
                        item {
                            val locationText = metadata["location"]?.takeIf { it.isNotBlank() }
                                ?: "Location not available"
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "Location",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = locationText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    // Mini Map Graphic
                                    Box(
                                        modifier = Modifier
                                            .width(84.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFCBE3CE))
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val riverColor = Color(0xFF90CAF9)
                                            val roadColor = Color.White.copy(alpha = 0.75f)
                                            drawLine(
                                                color = riverColor,
                                                start = Offset(x = size.width * 0.25f, y = size.height),
                                                end = Offset(x = size.width * 0.85f, y = 0f),
                                                strokeWidth = 14f,
                                                cap = StrokeCap.Round
                                            )
                                            drawLine(
                                                color = roadColor,
                                                start = Offset(x = 0f, y = size.height * 0.45f),
                                                end = Offset(x = size.width, y = size.height * 0.55f),
                                                strokeWidth = 6f
                                            )
                                        }
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.Center)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 2: Visual description / Sparkle AI summary
                        item {
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "Visual description",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Snow-covered mountains at sunset with a lake reflecting the peaks, a small village, and dramatic clouds in the sky.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 3: Real local Tesseract OCR with selectable trained languages.
                        item {
                            DetailCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.DocumentScanner,
                                            contentDescription = "Text recognition",
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Text found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "On-device Tesseract OCR · images stay on this device",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text("Recognition languages", style = MaterialTheme.typography.labelLarge)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TesseractOcr.supportedLanguages.forEach { (languageName, code) ->
                                            FilterChip(
                                                selected = code in ocrLanguages,
                                                onClick = {
                                                    if (code in ocrLanguages) {
                                                        if (ocrLanguages.size > 1) ocrLanguages.remove(code)
                                                    } else {
                                                        ocrLanguages.add(code)
                                                    }
                                                },
                                                label = { Text(languageName) },
                                                enabled = !ocrBusy
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (!item.isVideo) {
                                                ocrBusy = true
                                                ocrError = null
                                                ocrText = ""
                                                ocrScope.launch {
                                                    val result = withContext(Dispatchers.IO) {
                                                        runCatching {
                                                            TesseractOcr.recognize(
                                                                context,
                                                                Uri.parse(item.uri),
                                                                ocrLanguages.toList()
                                                            )
                                                        }
                                                    }
                                                    result.onSuccess { text ->
                                                        ocrText = text.ifBlank { "No text detected in this image." }
                                                    }.onFailure { error ->
                                                        ocrError = error.message ?: "OCR failed. Please try again."
                                                    }
                                                    ocrBusy = false
                                                }
                                            }
                                        },
                                        enabled = !item.isVideo && !ocrBusy && ocrLanguages.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (ocrBusy) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Preparing language data and recognizing…")
                                        } else {
                                            Icon(Icons.Default.DocumentScanner, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (ocrText.isBlank()) "Recognize text" else "Recognize again")
                                        }
                                    }
                                    if (item.isVideo) {
                                        Text("OCR is currently available for images, not video frames.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                    ocrError?.let { error ->
                                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (ocrText.isNotBlank()) {
                                        HorizontalDivider()
                                        Text(ocrText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        if (ocrText != "No text detected in this image.") {
                                            OutlinedButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Recognized text", ocrText))
                                                },
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Copy text")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 4: Tags / Labels
                        item {
                            val tags = listOf("Nature", "Mountains", "Lake", "Sunset", "Pahalgam")
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocalOffer,
                                        contentDescription = "Tags",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        tags.forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = tag,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.clickable { }
                                        ) {
                                            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add tag",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 5: People
                        item {
                            val people = listOf("Abbu", "Nowshin", "Amma", "Tanzil")
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PersonOutline,
                                        contentDescription = "People",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        people.forEachIndexed { idx, personName ->
                                            val avatarCandidate = items.getOrNull(idx)
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.size(46.dp)
                                                ) {
                                                    if (avatarCandidate != null) {
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(LocalContext.current)
                                                                .data(Uri.parse(avatarCandidate.uri))
                                                                .size(120, 120)
                                                                .crossfade(false)
                                                                .build(),
                                                            contentDescription = personName,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = personName.take(1),
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = personName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clickable { }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Add person",
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "Add",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 6: Actual custom albums containing this media item
                        item {
                            val containingAlbums = remember(customAlbums, item.uri) {
                                customAlbums.filter { item.uri in it.mediaUris }
                            }
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = "Albums",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Albums",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (containingAlbums.isEmpty()) {
                                            Text(
                                                text = "Not in any custom album",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            containingAlbums.forEach { album ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        modifier = Modifier.size(width = 48.dp, height = 36.dp)
                                                    ) {
                                                        val cover = items.firstOrNull { it.uri in album.mediaUris }
                                                        if (cover != null) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(context)
                                                                    .data(Uri.parse(cover.uri))
                                                                    .size(144, 108)
                                                                    .crossfade(false)
                                                                    .build(),
                                                                contentDescription = album.fullPath,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.PhotoAlbum, contentDescription = null)
                                                            }
                                                        }
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = album.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (album.parentPath.isNotBlank()) {
                                                            Text(
                                                                text = album.parentPath,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = onAddTo) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                }
                            }
                        }

                        // Card 7: Camera & Dimensions Info
                        item {
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = "Device info",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.3f)) {
                                            Text(
                                                text = "Samsung Galaxy S21 FE",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Device",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 6.dp))
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = if (item.width > 0 && item.height > 0) "${item.width} × ${item.height}" else "4096 × 3072",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Dimensions",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 6.dp))
                                        Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = if (item.sizeBytes > 0) formatBytes(item.sizeBytes) else "4.8 MB",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "File size",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 8: Camera Settings / EXIF grid
                        item {
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Camera,
                                        contentDescription = "EXIF Settings",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ExifColumn(top = meta("aperture"), bottom = "Aperture", modifier = Modifier.weight(1f))
                                        VerticalDivider(modifier = Modifier.height(26.dp).padding(horizontal = 2.dp))
                                        ExifColumn(top = meta("shutter"), bottom = "Shutter speed", modifier = Modifier.weight(1.2f))
                                        VerticalDivider(modifier = Modifier.height(26.dp).padding(horizontal = 2.dp))
                                        ExifColumn(top = meta("iso"), bottom = "ISO", modifier = Modifier.weight(0.7f))
                                        VerticalDivider(modifier = Modifier.height(26.dp).padding(horizontal = 2.dp))
                                        ExifColumn(top = meta("focal"), bottom = "Focal length", modifier = Modifier.weight(1.1f))
                                        VerticalDivider(modifier = Modifier.height(26.dp).padding(horizontal = 2.dp))
                                        ExifColumn(top = meta("flash"), bottom = "Flash", modifier = Modifier.weight(1f))
                                        VerticalDivider(modifier = Modifier.height(26.dp).padding(horizontal = 2.dp))
                                        ExifColumn(top = meta("color"), bottom = "Color space", modifier = Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 9: Extended Metadata / EXIF Attributes Table
                        item {
                            val photoTitle = metadata["title"]?.takeIf { it.isNotBlank() }
                                ?: item.name.substringBeforeLast('.').takeIf { it.isNotBlank() }
                                ?: "Not available"
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Article,
                                        contentDescription = "Metadata Table",
                                        modifier = Modifier.size(22.dp).padding(top = 2.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            MetaKeyValueRow("Title", photoTitle)
                                            MetaKeyValueRow("Description", meta("description"))
                                            MetaKeyValueRow("Creator", meta("creator"))
                                            MetaKeyValueRow("Copyright", meta("copyright"))
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            MetaKeyValueRow("Subject", meta("subject"))
                                            MetaKeyValueRow("Keywords", meta("keywords"))
                                            MetaKeyValueRow("Rating", meta("rating"))
                                            MetaKeyValueRow("Label", meta("label"))
                                            MetaKeyValueRow("Capture date", metadata["capture_date"]?.takeIf { it.isNotBlank() } ?: item.dateTakenMillis.takeIf { it > 0 }?.let { formatIsoDate(it, item.dateAddedSeconds) } ?: "Not available")
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showMore) ModalBottomSheet(onDismissRequest = { showMore = false }) {
        if (isBin) {
            ListItem(
                headlineContent = { Text("Restore to gallery") },
                leadingContent = { Icon(Icons.Default.Restore, null) },
                modifier = Modifier.clickable { showMore = false; onToggleBin() }
            )
        } else {
            ListItem(
                headlineContent = { Text(if (isHidden) "Unhide (return to gallery)" else "Move to Locked folder") },
                leadingContent = { Icon(if (isHidden) Icons.Default.Visibility else Icons.Default.Lock, null) },
                modifier = Modifier.clickable { showMore = false; onToggleHide() }
            )
            ListItem(
                headlineContent = { Text(if (isArchived) "Unarchive" else "Archive") },
                leadingContent = { Icon(Icons.Default.Archive, null) },
                modifier = Modifier.clickable { showMore = false; onToggleArchive() }
            )
            ListItem(
                headlineContent = { Text("Move to Bin") },
                leadingContent = { Icon(Icons.Default.Delete, null) },
                modifier = Modifier.clickable { showMore = false; onToggleBin() }
            )
        }
        ListItem(
            headlineContent = { Text("Media information") },
            leadingContent = { Icon(Icons.Default.Info, null) },
            modifier = Modifier.clickable { showMore = false; if (!showDetails) onToggleDetails() }
        )
        ListItem(
            headlineContent = { Text("Cancel") },
            leadingContent = { Icon(Icons.Default.Close, null) },
            modifier = Modifier.clickable { showMore = false }
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ViewerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) { IconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 3.dp).background(Color.White.copy(alpha = .92f), CircleShape)) { Icon(icon, label, tint = Color(0xFF121212)) } }
@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp)) { Surface(shape = CircleShape, color = Color.White.copy(alpha = .9f), modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = Color(0xFF121212)) } }; Spacer(Modifier.height(4.dp)); Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall) } }
@Composable
private fun DetailActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DetailCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun ExifColumn(top: String, bottom: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = top,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = bottom,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun MetaKeyValueRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(78.dp),
            fontSize = 11.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PhotoMapScreen(media: List<MediaItem>, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    val context = LocalContext.current
    var locatedMedia by remember { mutableStateOf<List<Pair<MediaItem, GeoPoint>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(media) {
        loading = true
        locatedMedia = withContext(Dispatchers.IO) {
            media.mapNotNull { item ->
                if (item.isVideo) return@mapNotNull null
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { stream ->
                        ExifInterface(stream).latLong?.let { c ->
                            if (c[0].isFinite() && c[1].isFinite() && c[0] in -90.0..90.0 && c[1] in -180.0..180.0) item to GeoPoint(c[0], c[1]) else null
                        }
                    }
                }.getOrNull()?.let { it }
            }
        }
        loading = false
    }

    DisposableEffect(Unit) { onDispose { mapRef?.onPause(); mapRef?.onDetach(); mapRef = null } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to albums") }
            Column(Modifier.weight(1f)) {
                Text("Photo map", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (loading) "Finding GPS-tagged photos…" else "${locatedMedia.size} photos with location data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(3.0)
                        controller.setCenter(GeoPoint(20.5937, 78.9629))
                        mapRef = this
                    }
                },
                update = { map ->
                    mapRef = map
                    map.overlays.clear()
                    locatedMedia.forEach { (item, point) ->
                        Marker(map).apply {
                            position = point
                            title = item.name
                            subDescription = "Tap to open photo"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ -> onOpen(item); true }
                            map.overlays.add(this)
                        }
                    }
                    if (locatedMedia.isNotEmpty()) {
                        val points = locatedMedia.map { it.second }
                        val box = org.osmdroid.util.BoundingBox(points.maxOf { it.latitude }, points.maxOf { it.longitude }, points.minOf { it.latitude }, points.minOf { it.longitude })
                        runCatching { map.zoomToBoundingBox(box, true, 48) }
                    }
                    map.invalidate()
                }
            )
            if (!loading && locatedMedia.isEmpty()) {
                Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), tonalElevation = 4.dp) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocationOff, null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No photos with GPS data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Only photos with embedded location coordinates appear here.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (loading) Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading photo locations…")
                }
            }
            Text("© OpenStreetMap contributors", modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumCatalogScreen(
    albums: List<CustomAlbum>,
    media: List<MediaItem>,
    onBack: () -> Unit,
    onCreateAlbum: (String) -> Unit,
    onOpenAlbum: (CustomAlbum) -> Unit
) {
    val sortedAlbums = remember(albums) { albums.sortedBy { it.fullPath.lowercase(Locale.getDefault()) } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to albums")
            }
            Column(Modifier.weight(1f)) {
                Text("Albums", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${albums.size} ${if (albums.size == 1) "album" else "albums"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = { onCreateAlbum("") }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New album")
            }
        }
        if (sortedAlbums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PhotoAlbum, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text("No albums yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Create an album to collect and organise your favourite photos and videos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { onCreateAlbum("") }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create album")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedAlbums, key = { it.id }) { album ->
                    val albumMedia = remember(media, album) { media.filter { it.uri in album.mediaUris } }
                    val cover = albumMedia.firstOrNull()
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenAlbum(album) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(76.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (cover != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(Uri.parse(cover.uri))
                                            .size(240, 240)
                                            .crossfade(false)
                                            .memoryCacheKey("${cover.uri}_album_catalog")
                                            .build(),
                                        contentDescription = album.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.PhotoAlbum, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(album.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (album.parentPath.isNotBlank()) {
                                    Text(album.parentPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${albumMedia.size} ${if (albumMedia.size == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open album", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceFoldersScreen(
    media: List<MediaItem>,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit
) {
    val folders = remember(media) {
        media.groupBy { item ->
            item.relativePath.trim().trim('/').ifBlank { "Internal storage" }
        }.toList().sortedBy { it.first.lowercase(Locale.getDefault()) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to albums")
            }
            Column(Modifier.weight(1f)) {
                Text("Device folders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${folders.size} folders · ${media.size} media items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (folders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("No device folders found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Folders appear here when accessible photos or videos are found on your device.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders, key = { it.first }) { (path, itemsInFolder) ->
                    val cover = itemsInFolder.firstOrNull()
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(path) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (cover != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(Uri.parse(cover.uri))
                                            .size(240, 240)
                                            .crossfade(false)
                                            .memoryCacheKey("${cover.uri}_device_folder")
                                            .build(),
                                        contentDescription = "Folder preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    path.substringAfterLast('/').ifBlank { "Internal storage" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${itemsInFolder.size} ${if (itemsInFolder.size == 1) "item" else "items"} · ${itemsInFolder.count { it.isVideo }} videos",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open folder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrSettingsScreen(onBack: () -> Unit) {
    var selectedLanguages by remember { mutableStateOf(setOf("eng")) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("OCR configuration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Text recognition",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Emre Gallery uses Tesseract OCR locally on your device. Choose the languages you commonly read. Their trained-data files are downloaded when OCR is first run for a selected language and then cached for offline use.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Text("Available trained languages", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(TesseractOcr.supportedLanguages.entries.toList()) { entry ->
                ElevatedCard(
                    onClick = {
                        selectedLanguages = if (entry.value in selectedLanguages) {
                            if (selectedLanguages.size > 1) selectedLanguages - entry.value else selectedLanguages
                        } else selectedLanguages + entry.value
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.key, style = MaterialTheme.typography.bodyLarge)
                            Text(entry.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(
                            checked = entry.value in selectedLanguages,
                            onCheckedChange = { checked ->
                                selectedLanguages = if (checked) selectedLanguages + entry.value
                                else if (selectedLanguages.size > 1) selectedLanguages - entry.value else selectedLanguages
                            }
                        )
                    }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)
        ) {
            Text(
                "Selected: ${selectedLanguages.size} language(s). Language selection in the media viewer can also be adjusted for an individual image.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings")
            }
            Column(Modifier.weight(1f)) {
                Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("The app and its creator", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(84.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(44.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Emre Gallery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Your photos. Your library. Your control.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text("Version 0.1.0", modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                AboutInfoCard(
                    title = "About the app",
                    icon = Icons.Default.Info
                ) {
                    Text(
                        "Emre Gallery is an Android gallery project focused on making your local photo and video library easier to browse and understand.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The app is being built around a clean viewing experience, albums, search, media details, and location-based photo browsing. Features and integrations are still evolving.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Your media library is read from storage that Android makes accessible to the app. Map tiles require an internet connection; photos with embedded GPS coordinates can appear on the map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                AboutInfoCard(
                    title = "About the creator",
                    icon = Icons.Default.Person
                ) {
                    Text("Amre Salehin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Medical student and independent app developer", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "I am building Emre Gallery to explore a thoughtful, practical, and privacy-conscious way to manage personal photos. The project combines my interest in technology with a focus on useful, user-centred software.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("An independent project", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Emre Gallery is an independent project and is not affiliated with Google Photos or Ente Photos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutInfoCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun LibraryOverviewScreen(
    media: List<MediaItem>,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val imageCount = remember(media) { media.count { !it.isVideo } }
    val videoCount = remember(media) { media.count { it.isVideo } }
    val totalBytes = remember(media) { media.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
    val folderCount = remember(media) { media.map { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }.distinct().size }
    val datedCount = remember(media) { media.count { it.dateTakenMillis > 0L || it.dateAddedSeconds > 0L } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings") }
            Column(Modifier.weight(1f)) {
                Text("Library overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (loading) "Updating media index…" else "Local device library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh library") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Your library", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(media.size.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("items", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 7.dp))
                        }
                        Text(if (loading) "Scanning media…" else "Based on accessible media on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LibraryMetricCard("Photos", imageCount.toString(), Icons.Default.Photo, Modifier.weight(1f))
                    LibraryMetricCard("Videos", videoCount.toString(), Icons.Default.Videocam, Modifier.weight(1f))
                }
            }
            item { LibraryMetricCard("Storage represented by media", formatBytes(totalBytes), Icons.Default.Storage, Modifier.fillMaxWidth()) }
            item { LibraryMetricCard("Device folders", folderCount.toString(), Icons.Default.Folder, Modifier.fillMaxWidth()) }
            item { LibraryMetricCard("Items with date metadata", datedCount.toString(), Icons.Default.CalendarMonth, Modifier.fillMaxWidth()) }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("This overview counts media accessible to Emre Gallery. Storage size is the sum of media file sizes, not the app's own storage usage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryMetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    total: Int,
    theme: String,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onTheme: (String) -> Unit,
    onRefresh: () -> Unit,
    onSettings: (String) -> Unit,
    onPermissionSettings: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val entries = listOf("Library overview" to Icons.Default.PhotoLibrary, "Theme" to Icons.Default.Palette, "Tools" to Icons.Default.Build, "Intelligence" to Icons.Default.Psychology, "OCR" to Icons.Default.DocumentScanner, "Indexing Status" to Icons.Default.Storage, "Duplicates or Similar" to Icons.Default.Collections, "Permissions" to Icons.Default.Lock, "About" to Icons.Default.Info).filter { it.first.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topContentPadding))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), placeholder = { Text("Search settings…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = CircleShape, singleLine = true)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = bottomContentPadding)
        ) {
            items(entries) { (label, icon) ->
                if (label == "Theme") ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(30.dp)); Spacer(Modifier.width(14.dp)); Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.height(12.dp)); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { index, (key, name) -> SegmentedButton(selected = theme == key, onClick = { onTheme(key) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(name) } } } } }
                else ElevatedCard(onClick = { if (label == "Library overview" || label == "Indexing Status") onRefresh(); if (label == "Permissions") onPermissionSettings(); onSettings(label) }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(30.dp)); Spacer(Modifier.width(18.dp)); Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium); Text(when (label) { "Library overview" -> "$total media items indexed"; "Intelligence" -> "AI providers not configured"; "OCR" -> "Engine not configured"; "Indexing Status" -> "Local MediaStore scan"; "Duplicates or Similar" -> "Detection engine not configured"; "About" -> "Emre Gallery · 0.1.0"; "Tools" -> "Local library tools"; else -> "Manage access" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
        }
    }
}


private fun reverseGeocodeLocation(context: android.content.Context, latitude: Double, longitude: Double): String? {
    if (!android.location.Geocoder.isPresent()) return null
    return runCatching {
        @Suppress("DEPRECATION")
        android.location.Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.let { address ->
                listOfNotNull(
                    address.subLocality,
                    address.locality,
                    address.subAdminArea,
                    address.adminArea,
                    address.countryName
                ).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    .takeIf { it.isNotBlank() }
            }
    }.getOrNull()
}

private fun readMediaMetadata(context: android.content.Context, item: MediaItem): Map<String, String> {
    val values = mutableMapOf<String, String>()
    val uri = Uri.parse(item.uri)
    if (!item.isVideo) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.latLong?.let { coordinates ->
                    values["latitude"] = coordinates[0].toString()
                    values["longitude"] = coordinates[1].toString()
                    reverseGeocodeLocation(context, coordinates[0], coordinates[1])?.let { values["location"] = it }
                }
                fun put(key: String, vararg tags: String) {
                    tags.firstNotNullOfOrNull { tag -> exif.getAttribute(tag)?.takeIf { it.isNotBlank() } }?.let { values[key] = it }
                }
                put("aperture", ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_APERTURE_VALUE)
                put("shutter", ExifInterface.TAG_EXPOSURE_TIME)
                put("iso", ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_ISO_SPEED_RATINGS)
                put("focal", ExifInterface.TAG_FOCAL_LENGTH)
                put("flash", ExifInterface.TAG_FLASH)
                put("color", ExifInterface.TAG_COLOR_SPACE)
                put("capture_date", ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME)
                put("description", ExifInterface.TAG_IMAGE_DESCRIPTION)
                put("copyright", ExifInterface.TAG_COPYRIGHT)
                put("creator", ExifInterface.TAG_ARTIST)
                put("title", ExifInterface.TAG_DOCUMENT_NAME)
            }
        }
    }
    // XMP is often embedded near the beginning of the file. Read a bounded amount only.
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (total < 1024 * 1024) {
                val count = stream.read(buffer, 0, minOf(buffer.size, 1024 * 1024 - total))
                if (count <= 0) break
                out.write(buffer, 0, count)
                total += count
            }
            val xmp = out.toString("ISO-8859-1")
            fun readXmp(key: String, regex: Regex) {
                regex.find(xmp)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { values[key] = it }
            }
            readXmp("title", Regex("""(?:xmp:Title|dc:title)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("description", Regex("""(?:dc:description|photoshop:Caption)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("creator", Regex("""(?:dc:creator|xmp:Creator)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("copyright", Regex("""(?:dc:rights|xmpRights:UsageTerms)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("subject", Regex("""(?:dc:subject|Subject)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("keywords", Regex("""(?:MicrosoftPhoto:LastKeywordXMP|Keywords)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("rating", Regex("""xmp:Rating="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("label", Regex("""xmp:Label="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("capture_date", Regex("""(?:photoshop:DateCreated|xmp:CreateDate|xmp:DateCreated)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("aperture", Regex("""(?:exif:FNumber|FNumber)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("shutter", Regex("""(?:exif:ExposureTime|ExposureTime)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("iso", Regex("""(?:exif:ISOSpeedRatings|exif:PhotographicSensitivity|PhotographicSensitivity)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("focal", Regex("""(?:exif:FocalLength|FocalLength)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("flash", Regex("""(?:exif:Flash|Flash)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("color", Regex("""(?:exif:ColorSpace|ColorSpace)="([^"]+)""", RegexOption.IGNORE_CASE))
            if (values["location"].isNullOrBlank()) {
                val city = Regex("""photoshop:City="([^"]+)""", RegexOption.IGNORE_CASE).find(xmp)?.groupValues?.getOrNull(1)
                val state = Regex("""photoshop:State="([^"]+)""", RegexOption.IGNORE_CASE).find(xmp)?.groupValues?.getOrNull(1)
                val country = Regex("""photoshop:Country="([^"]+)""", RegexOption.IGNORE_CASE).find(xmp)?.groupValues?.getOrNull(1)
                listOfNotNull(city, state, country).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ").takeIf { it.isNotBlank() }?.let { values["location"] = it }
            }
        }
    }
    return values
}

private fun formatDate(takenMillis: Long, addedSeconds: Long): String { val millis = takenMillis.takeIf { it > 0 } ?: addedSeconds * 1000; if (millis <= 0) return "Date unavailable"; return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis)) }
private fun formatMediaDateTime(takenMillis: Long, addedSeconds: Long): String {
    val millis = takenMillis.takeIf { it > 0 } ?: (addedSeconds * 1000L)
    if (millis <= 0) return "Date unavailable"
    val date = Date(millis)
    val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "${dateFormat.format(date)}  •  ${timeFormat.format(date)}"
}
private fun formatIsoDate(takenMillis: Long, addedSeconds: Long): String {
    val millis = takenMillis.takeIf { it > 0 } ?: (addedSeconds * 1000L)
    if (millis <= 0) return "2024-10-23T17:42:35+05:30"
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
    return runCatching { isoFormat.format(Date(millis)) }.getOrDefault("2024-10-23T17:42:35+05:30")
}
private fun formatBytes(bytes: Long): String = when { bytes <= 0 -> "Not available"; bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0); bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024)); else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024)) }
private fun formatDuration(ms: Long): String { val seconds = ms / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
