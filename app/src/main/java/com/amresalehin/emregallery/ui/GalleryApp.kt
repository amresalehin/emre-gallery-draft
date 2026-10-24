package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.Intent
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
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
import coil.request.CachePolicy
import coil.size.Size
import coil.size.Precision
import androidx.compose.ui.res.painterResource
import com.amresalehin.emregallery.R
import com.amresalehin.emregallery.IndexingForegroundService
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.ocr.TesseractOcr
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceClusterSummary
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.FaceIndexingRepository
import com.amresalehin.emregallery.face.FaceIndexingProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private enum class Destination { HOME, ALBUMS, FAVOURITES, BIN, VIDEOS, RECENTLY_ADDED, LOCKED_FOLDER, ARCHIVE, MAP, PEOPLE, DEVICE_FOLDERS, ALBUM_CATALOG, SETTINGS, LIBRARY_OVERVIEW, INDEXING_STATUS, OCR_SETTINGS, ABOUT, SEARCH, VIEWER }
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
    val customTagsMap by prefs.customTagsMap.collectAsStateWithLifecycle(initialValue = emptyMap())
    val customPeopleMap by prefs.customPeopleMap.collectAsStateWithLifecycle(initialValue = emptyMap())
    val homeScrollPos by prefs.homeScrollPosition.collectAsStateWithLifecycle(initialValue = Pair(0, 0))
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var hasRestoredHomeScroll by remember { mutableStateOf(false) }
    var lastHomeButtonTapAt by remember { mutableLongStateOf(0L) }
    var showHomeViewOptionsSheet by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    // Small rubber-band response when a horizontal swipe reaches the first or last main tab.
    val mainSwipeResistanceX = remember { Animatable(0f) }
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
                deletedUris.forEach { prefs.setInBin(it, false) }
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
                deleted.forEach { prefs.setInBin(it, false) }
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

    LaunchedEffect(visibleMedia.isNotEmpty(), homeScrollPos) {
        if (!hasRestoredHomeScroll && visibleMedia.isNotEmpty() && homeScrollPos.first > 0) {
            val target = homeScrollPos.first.coerceAtMost((visibleMedia.size - 1).coerceAtLeast(0))
            homeListState.scrollToItem(target, homeScrollPos.second)
            hasRestoredHomeScroll = true
        }
    }

    LaunchedEffect(homeListState.firstVisibleItemIndex, homeListState.firstVisibleItemScrollOffset) {
        if (visibleMedia.isNotEmpty() && (homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 0)) {
            prefs.saveHomeScrollPosition(homeListState.firstVisibleItemIndex, homeListState.firstVisibleItemScrollOffset)
        }
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

                    if (targetState == Destination.VIEWER) {
                        (fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                            .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(180)))
                    } else if (initialState == Destination.VIEWER) {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(240, easing = FastOutSlowInEasing)))
                            .togetherWith(fadeOut(animationSpec = tween(240)) + scaleOut(targetScale = 0.90f, animationSpec = tween(240, easing = FastOutLinearInEasing)))
                    } else if (isNavigatingBack) {
                        // Android native predictive back pop animation:
                        // Current subscreen slides out to the right; previous screen slides in from left with scale
                        (slideInHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> -width / 4 }
                        ) + fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(320, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> width }
                                ) + fadeOut(animationSpec = tween(220))
                            )
                    } else if (initialIndex >= 0 && targetIndex >= 0) {
                        if (targetIndex > initialIndex) {
                            (slideInHorizontally(
                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                initialOffsetX = { width -> width }
                            ) + fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                                        targetOffsetX = { width -> -width / 4 }
                                    ) + fadeOut(animationSpec = tween(220))
                                )
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                initialOffsetX = { width -> -width }
                            ) + fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                                        targetOffsetX = { width -> width / 4 }
                                    ) + fadeOut(animationSpec = tween(220))
                                )
                        }
                    } else {
                        // Forward navigation to a subscreen:
                        // Target slides in from the right; current slides out to the left with subtle scale
                        (slideInHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> width }
                        ) + fadeIn(animationSpec = tween(220)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> -width / 4 }
                                ) + fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 0.94f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                            )
                    }
                },
                label = "destination_screen_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = mainSwipeResistanceX.value
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
                    .pointerInput(destination, showSidePanel) {
                        // Open the navigation drawer with an inward swipe from the
                        // left edge on any primary screen, not only Home.
                        if (!showSidePanel && destination in listOf(Destination.HOME, Destination.ALBUMS, Destination.SETTINGS)) {
                            val edgeWidth = 24.dp.toPx()
                            val slop = viewConfiguration.touchSlop * 2
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (down.position.x <= edgeWidth) {
                                    var totalDragX = 0f
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change != null) {
                                            totalDragX += change.position.x - change.previousPosition.x
                                            if (totalDragX > slop) {
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
                    .pointerInput(destination, showSidePanel) {
                        val mainDestinations = listOf(Destination.HOME, Destination.ALBUMS, Destination.SETTINGS)
                        if (!showSidePanel && destination in mainDestinations) {
                            val slop = viewConfiguration.touchSlop * 2f
                            val edgeWidth = 24.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Let the left-edge drawer gesture own rightward swipes that begin at the edge.
                                val drawerOwnsGesture = down.position.x <= edgeWidth
                                var totalX = 0f
                                var totalY = 0f
                                var horizontalDragStarted = false
                                var resistedAtBoundary = false
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change != null) {
                                        totalX += change.position.x - change.previousPosition.x
                                        totalY += change.position.y - change.previousPosition.y
                                        val isHorizontal = kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.35f
                                        if (!drawerOwnsGesture && isHorizontal && kotlin.math.abs(totalX) > slop) {
                                            horizontalDragStarted = true
                                            val nextDestination = when {
                                                totalX < 0f && destination == Destination.HOME -> Destination.ALBUMS
                                                totalX < 0f && destination == Destination.ALBUMS -> Destination.SETTINGS
                                                totalX > 0f && destination == Destination.SETTINGS -> Destination.ALBUMS
                                                totalX > 0f && destination == Destination.ALBUMS -> Destination.HOME
                                                else -> null
                                            }
                                            if (nextDestination != null) {
                                                // Direct manipulation: keep the current screen attached to the finger.
                                                scope.launch { mainSwipeResistanceX.snapTo(totalX * 0.72f) }
                                                change.consume()
                                            } else if (destination == Destination.SETTINGS) {
                                                // Rubber-band resistance belongs only to the Settings end boundary.
                                                resistedAtBoundary = true
                                                val resistance = (totalX * 0.16f).coerceIn(-44f, 44f)
                                                scope.launch { mainSwipeResistanceX.snapTo(resistance) }
                                                change.consume()
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                if (horizontalDragStarted && !drawerOwnsGesture) {
                                    val commitThreshold = size.width * 0.22f
                                    val nextDestination = when {
                                        totalX < -commitThreshold && destination == Destination.HOME -> Destination.ALBUMS
                                        totalX < -commitThreshold && destination == Destination.ALBUMS -> Destination.SETTINGS
                                        totalX > commitThreshold && destination == Destination.SETTINGS -> Destination.ALBUMS
                                        totalX > commitThreshold && destination == Destination.ALBUMS -> Destination.HOME
                                        else -> null
                                    }
                                    if (nextDestination != null) {
                                        navigateTo(nextDestination)
                                    }
                                }
                                if (mainSwipeResistanceX.value != 0f) {
                                    scope.launch {
                                        mainSwipeResistanceX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
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
                                "Library overview" -> navigateTo(Destination.LIBRARY_OVERVIEW)
                                "Indexing Status" -> navigateTo(Destination.INDEXING_STATUS)
                                "About" -> navigateTo(Destination.ABOUT)
                                "OCR" -> navigateTo(Destination.OCR_SETTINGS)
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
                        onBack = { performBackNavigation() }
                    )
                    Destination.OCR_SETTINGS -> OcrSettingsScreen(
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
                        if (dest == Destination.HOME) {
                            val now = android.os.SystemClock.uptimeMillis()
                            val isDoubleTap = now - lastHomeButtonTapAt in 1..ViewConfiguration.getDoubleTapTimeout().toLong()
                            lastHomeButtonTapAt = if (isDoubleTap) 0L else now
                            navigateTo(dest, rememberPrevious = false)
                            albumFilter = null
                            if (isDoubleTap) {
                                scope.launch {
                                    homeListState.animateScrollToItem(0)
                                    prefs.saveHomeScrollPosition(0, 0)
                                }
                            }
                        } else {
                            lastHomeButtonTapAt = 0L
                            navigateTo(dest, rememberPrevious = false)
                            albumFilter = null
                        }
                    }
                )
            }
        }
        }
                if (showSidePanel) {
            val drawerWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 304.dp.toPx() }
            val drawerOffsetX = remember { Animatable(-drawerWidthPx) }
            val drawerScope = rememberCoroutineScope()
            val velocityTracker = remember { VelocityTracker() }
            val drawerMotion = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
            val scrimAlpha = (1f - (kotlin.math.abs(drawerOffsetX.value) / drawerWidthPx).coerceIn(0f, 1f)) * 0.42f
            LaunchedEffect(Unit) {
                drawerOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = drawerMotion
                )
            }
            val closeSidePanel: () -> Unit = {
                drawerScope.launch {
                    drawerOffsetX.animateTo(
                        targetValue = -drawerWidthPx,
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    )
                    showSidePanel = false
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable { closeSidePanel() }
                    .pointerInput(showSidePanel) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                drawerScope.launch {
                                    val next = (drawerOffsetX.value + dragAmount).coerceIn(-drawerWidthPx, 0f)
                                    drawerOffsetX.snapTo(next)
                                }
                            },
                            onDragEnd = {
                                val velocityX = velocityTracker.calculateVelocity().x
                                val current = drawerOffsetX.value
                                val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
                                val closeThreshold = drawerWidthPx * 0.35f
                                if (current < -closeThreshold || velocityX < -minFlingVelocity) {
                                    drawerScope.launch {
                                        drawerOffsetX.animateTo(
                                            targetValue = -drawerWidthPx,
                                            initialVelocity = velocityX,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        showSidePanel = false
                                    }
                                } else {
                                    drawerScope.launch {
                                        drawerOffsetX.animateTo(
                                            targetValue = 0f,
                                            initialVelocity = velocityX,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                drawerScope.launch {
                                    drawerOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        )
                    }
                    .testTag("side_panel_scrim")
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(304.dp)
                        .graphicsLayer { translationX = drawerOffsetX.value }
                        .clickable { /* Keep taps inside the drawer from dismissing it. */ }
                        .testTag("side_panel"),
                    shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_app_logo),
                                        contentDescription = "Emre Gallery logo",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Emre Gallery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Your media library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { closeSidePanel() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close navigation menu")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))
                        val drawerItems = listOf(
                            Triple(Destination.HOME, "Home", Icons.Default.Home),
                            Triple(Destination.ALBUMS, "Albums", Icons.Default.PhotoLibrary),
                            Triple(Destination.FAVOURITES, "Favourites", Icons.Default.Favorite),
                            Triple(Destination.VIDEOS, "Videos", Icons.Default.PlayCircle),
                            Triple(Destination.RECENTLY_ADDED, "Recently added", Icons.Default.Schedule),
                            Triple(Destination.LOCKED_FOLDER, "Locked folder", Icons.Default.Lock),
                            Triple(Destination.ARCHIVE, "Archive", Icons.Default.Archive),
                            Triple(Destination.BIN, "Bin", Icons.Default.Delete),
                            Triple(Destination.PEOPLE, "People & pets", Icons.Default.Face),
                            Triple(Destination.MAP, "Map & Places", Icons.Default.Map),
                            Triple(Destination.SETTINGS, "Settings", Icons.Default.Settings)
                        )
                        drawerItems.forEach { (dest, label, icon) ->
                            val selected = destination == dest
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                    .clickable {
                                        if (dest == Destination.MAP) {
                                            mapReturnDestination = destination
                                            mapFocusItem = null
                                        }
                                        navigateTo(dest, rememberPrevious = false)
                                        albumFilter = null
                                        showSidePanel = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(14.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
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
    Surface(
        modifier = Modifier.fillMaxSize().testTag("media_permission_screen"),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(112.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "Emre Gallery logo",
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "Your photos, in one place",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Allow access to photos and videos stored on this device to build your gallery.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onRequest,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Allow access", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FloatingSearchPill(
    label: String,
    onClick: () -> Unit,
    showMoreOptions: Boolean = false,
    onMenuClick: () -> Unit = {},
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
                .padding(start = 4.dp, end = if (showMoreOptions) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(44.dp).testTag("open_side_panel")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onClick)
                    .padding(start = 8.dp),
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onRestore: (MediaItem) -> Unit,
    onDeletePermanently: (MediaItem) -> Unit,
    onEmptyBin: (List<MediaItem>) -> Unit
) {
    var confirmEmptyBin by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Albums")
            }
            Column(Modifier.weight(1f)) {
                Text("Bin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${items.size} ${if (items.size == 1) "item" else "items"} · Restore or permanently delete",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { confirmEmptyBin = true }, enabled = items.isNotEmpty()) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Empty bin")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(76.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Bin is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Photos and videos you move to the Bin will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(items, key = { it.uri }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(item.uri))
                                        .crossfade(true)
                                        .size(Size(720, 720))
                                        .precision(Precision.INEXACT)
                                        .build(),
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .clickable { onOpen(item) },
                                    contentScale = ContentScale.Crop
                                )
                                if (item.isVideo) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Video",
                                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { onRestore(item) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restore", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    IconButton(onClick = { onDeletePermanently(item) }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = "Delete ${item.name} permanently",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmEmptyBin) {
        AlertDialog(
            onDismissRequest = { confirmEmptyBin = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            title = { Text("Empty Bin?") },
            text = { Text("Permanently delete all ${items.size} items in the Bin from this device? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmptyBin = false
                    onEmptyBin(items)
                }) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyBin = false }) { Text("Cancel") } }
        )
    }
}


@Composable
private fun LockedFolderScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onUnlock: (MediaItem) -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Albums")
            }
            Column(Modifier.weight(1f)) {
                Text("Locked folder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${items.size} ${if (items.size == 1) "item" else "items"} · Hidden from your main gallery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(76.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Your locked folder is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Hide a photo or video from its viewer to keep it out of the main gallery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(items, key = { it.uri }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(item.uri))
                                        .crossfade(true)
                                        .size(Size(720, 720))
                                        .precision(Precision.INEXACT)
                                        .build(),
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .clickable { onOpen(item) },
                                    contentScale = ContentScale.Crop
                                )
                                if (item.isVideo) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Video",
                                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                                        tint = Color.White
                                    )
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.58f)
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "In locked folder",
                                        modifier = Modifier.padding(6.dp).size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onUnlock(item) },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Move out", style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
private fun FavouritesScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onRemoveFavourite: (MediaItem) -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Albums")
            }
            Column(Modifier.weight(1f)) {
                Text("Favourites", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${items.size} ${if (items.size == 1) "item" else "items"} · Your saved photos and videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(76.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No favourites yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Mark photos or videos with the heart icon to keep them together here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(items, key = { it.uri }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(item.uri))
                                        .crossfade(true)
                                        .size(Size(720, 720))
                                        .precision(Precision.INEXACT)
                                        .build(),
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .clickable { onOpen(item) },
                                    contentScale = ContentScale.Crop
                                )
                                if (item.isVideo) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Video",
                                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                                        tint = Color.White
                                    )
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = "Favourite",
                                        modifier = Modifier.padding(6.dp).size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onRemoveFavourite(item) },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Remove", style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
private fun ArchiveScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onUnarchive: (MediaItem) -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Albums")
            }
            Column(Modifier.weight(1f)) {
                Text("Archive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${items.size} ${if (items.size == 1) "item" else "items"} · Hidden from your main gallery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(76.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Archive is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Archived photos and videos remain in your library but are kept out of the main gallery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(items, key = { it.uri }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(item.uri))
                                        .crossfade(true)
                                        .size(Size(720, 720))
                                        .precision(Precision.INEXACT)
                                        .build(),
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .clickable { onOpen(item) },
                                    contentScale = ContentScale.Crop
                                )
                                if (item.isVideo) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Video",
                                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                                        tint = Color.White
                                    )
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Icon(
                                        Icons.Default.Archive,
                                        contentDescription = "Archived",
                                        modifier = Modifier.padding(6.dp).size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = { onUnarchive(item) },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Unarchive", style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
private fun HomeScreen(
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
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
    val homeScope = rememberCoroutineScope()
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pinchGridColumns by remember(gridColumns) { mutableIntStateOf(gridColumns.coerceIn(1, 6)) }
    var pinchZoomRemainder by remember { mutableFloatStateOf(1f) }
    val selectionMode = selectedUris.isNotEmpty()
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragSelectionActive by remember { mutableStateOf(false) }
    var lastDragUri by remember { mutableStateOf<String?>(null) }
    val homeRootBounds = remember { mutableStateOf<Rect?>(null) }
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

    val groupedRows = remember(groups, pinchGridColumns) {
        groups.map { group -> group to group.items.chunked(pinchGridColumns.coerceIn(1, 6)) }
    }

    Box(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { homeRootBounds.value = it.boundsInRoot() }
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Pinch-to-zoom requires 2 or more touches. Single-finger vertical scrolls
                        // are NEVER consumed or intercepted, preserving native Android system scroll physics,
                        // fling decay, touch slop, and overscroll on LazyColumn.
                        if (event.changes.size >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                pinchZoomRemainder *= zoomChange
                                if (pinchZoomRemainder < 0.82f) {
                                    pinchGridColumns = (pinchGridColumns + 1).coerceAtMost(6)
                                    pinchZoomRemainder = 1f
                                } else if (pinchZoomRemainder > 1.22f) {
                                    pinchGridColumns = (pinchGridColumns - 1).coerceAtLeast(1)
                                    pinchZoomRemainder = 1f
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (selectionMode) {
            item(key = "home_selection_toolbar") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedUris = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                        Text(
                            text = "${selectedUris.size} selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        TextButton(onClick = {
                            selectedUris = if (selectedUris.size == sortedMedia.size) emptySet() else sortedMedia.map { it.uri }.toSet()
                        }) {
                            Text(if (selectedUris.size == sortedMedia.size) "Deselect all" else "Select all")
                        }
                    }
                }
            }
        }

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
                    gridColumns = pinchGridColumns,
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
                item {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        thickness = 1.dp
                    )
                }
            }

            groupedRows.forEach { (group, rowChunks) ->
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

                items(
                    count = rowChunks.size,
                    key = { rIdx -> "day_${group.dayKey}_row_${rowChunks[rIdx].first().id}" },
                    contentType = { "photo_row" }
                ) { rowIndex ->
                    JustifiedMosaicRow(
                        items = rowChunks[rowIndex],
                        rowIndex = rowIndex,
                        isSquare = (gridStyle == "square"),
                        selectedUris = selectedUris,
                        onLongPress = { item -> selectedUris = selectedUris + item.uri; dragSelectionActive = true; lastDragUri = item.uri },
                        onToggleSelection = { item -> selectedUris = if (item.uri in selectedUris) selectedUris - item.uri else selectedUris + item.uri },
                        onDragSelect = { item, rootPosition ->
                            if (dragSelectionActive) {
                                val hit = itemBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(rootPosition) }?.key
                                if (hit != null && hit != lastDragUri) {
                                    selectedUris = selectedUris + hit
                                    lastDragUri = hit
                                }
                                val root = homeRootBounds.value
                                if (root != null) {
                                    val edge = 56.dp.value * context.resources.displayMetrics.density
                                    if (rootPosition.y < root.top + edge) homeScope.launch { listState.scrollBy(-22f) }
                                    else if (rootPosition.y > root.bottom - edge) homeScope.launch { listState.scrollBy(22f) }
                                }
                            }
                        },
                        onDragEnd = { dragSelectionActive = false; lastDragUri = null },
                        onBoundsChanged = { item, bounds -> itemBounds[item.uri] = bounds },
                        onOpen = { item -> if (selectionMode) { selectedUris = if (item.uri in selectedUris) selectedUris - item.uri else selectedUris + item.uri } else onOpen(item) }
                    )
                }
            }
        }
    }

    HomeFastScrollbar(
        listState = listState,
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding,
        thumbColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    }
}


@Composable
private fun HomeFastScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    thumbColor: Color
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragRemainder by remember { mutableFloatStateOf(0f) }
    val layout = listState.layoutInfo
    val totalItems = layout.totalItemsCount
    val visibleItems = layout.visibleItemsInfo
    val viewportPx = layout.viewportSize.height.toFloat()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val minThumbPx = with(density) { 36.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
    if (totalItems > 0 && visibleItems.isNotEmpty() && viewportPx > 0f &&
        (visibleItems.first().index > 0 || visibleItems.last().index < totalItems - 1)
    ) {
        val thumbHeightPx = (viewportPx * visibleItems.size / totalItems.toFloat())
            .coerceIn(minThumbPx, viewportPx)
        val maxThumbTravel = (viewportPx - thumbHeightPx).coerceAtLeast(1f)
        val scrollFraction = (listState.firstVisibleItemIndex.toFloat() /
            (totalItems - visibleItems.size).coerceAtLeast(1)).coerceIn(0f, 1f)
        val thumbTopPx = maxThumbTravel * scrollFraction
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = topContentPadding, bottom = bottomContentPadding)
                .width(24.dp)
                .pointerInput(totalItems, viewportPx, topContentPadding, bottomContentPadding) {
                    detectDragGestures(
                        onDragStart = { dragging = true; dragRemainder = 0f },
                        onDragEnd = { dragging = false; dragRemainder = 0f },
                        onDragCancel = { dragging = false; dragRemainder = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val currentLayout = listState.layoutInfo
                            val count = currentLayout.totalItemsCount
                            val height = size.height.toFloat()
                            if (count > 0 && height > 0f) {
                                dragRemainder += dragAmount.y / height * count
                                val itemDelta = dragRemainder.toInt()
                                if (itemDelta != 0) {
                                    dragRemainder -= itemDelta
                                    val target = (listState.firstVisibleItemIndex + itemDelta)
                                        .coerceIn(0, (count - 1).coerceAtLeast(0))
                                    scope.launch { listState.scrollToItem(target) }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.TopEnd
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val inMotion = dragging || listState.isScrollInProgress
                val thumbWidth = if (inMotion) 6.dp.toPx() else 2.dp.toPx()
                val height = thumbHeightPx.coerceAtMost(size.height)
                val fraction = if (size.height <= height) 0f else thumbTopPx / (size.height - height)
                val top = (size.height - height) * fraction
                drawRoundRect(
                    color = thumbColor.copy(alpha = if (inMotion) 0.95f else 0.65f),
                    topLeft = Offset(size.width - thumbWidth, top),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2f)
                )
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

    Column {
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
                            .diskCacheKey("${cover.uri}_memory_story")
            .diskCachePolicy(CachePolicy.ENABLED)
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
private fun JustifiedMosaicRow(
    items: List<MediaItem>,
    rowIndex: Int,
    isSquare: Boolean,
    selectedUris: Set<String> = emptySet(),
    onLongPress: (MediaItem) -> Unit = {},
    onToggleSelection: (MediaItem) -> Unit = {},
    onDragSelect: (MediaItem, Offset) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onBoundsChanged: (MediaItem, Rect) -> Unit = { _, _ -> },
    onOpen: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return

    val aspects = items.mapIndexed { index, item ->
        if (isSquare) 1f else if (item.width > 0 && item.height > 0) {
            (item.width.toFloat() / item.height.toFloat()).coerceIn(0.55f, 1.8f)
        } else {
            when ((item.id.hashCode() + rowIndex + index).mod(4)) {
                0 -> 0.72f
                1 -> 0.9f
                2 -> 1.0f
                else -> 1.35f
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        val gap = 4.dp
        val totalGap = gap * (items.size - 1)
        val rowHeight = ((maxWidth - totalGap) / aspects.sum()).coerceIn(72.dp, 220.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            items.forEachIndexed { index, item ->
                MosaicMediaItem(
                    item = item,
                    modifier = Modifier
                        .weight(aspects[index])
                        .height(rowHeight),
                    selected = item.uri in selectedUris,
                    onLongPress = { onLongPress(item) },
                    onToggleSelection = { onToggleSelection(item) },
                    onDragSelect = { position -> onDragSelect(item, position) },
                    onDragEnd = onDragEnd,
                    onBoundsChanged = { bounds -> onBoundsChanged(item, bounds) },
                    onOpen = onOpen
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MosaicMediaItem(
    item: MediaItem,
    modifier: Modifier,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleSelection: () -> Unit = {},
    onDragSelect: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onBoundsChanged: (Rect) -> Unit = {},
    onOpen: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.uri))
            .size(720, 720)
            .crossfade(false)
            .diskCacheKey("${item.uri}_thumb")
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${item.uri}_thumb")
            .build()
    }

    var tileBounds by remember(item.uri) { mutableStateOf<Rect?>(null) }
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInRoot()
            tileBounds = bounds
            onBoundsChanged(bounds)
        }
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                )
            )
            .combinedClickable(
                onClick = { if (selected) onToggleSelection() else onOpen(item) },
                onLongClick = { onLongPress() }
            )
            .pointerInput(item.uri, tileBounds) {
                var dragging = false
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        dragging = true
                        onLongPress()
                        val bounds = tileBounds
                        if (bounds != null) onDragSelect(Offset(bounds.left + position.x, bounds.top + position.y))
                    },
                    onDragEnd = { if (dragging) onDragEnd(); dragging = false },
                    onDragCancel = { if (dragging) onDragEnd(); dragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val bounds = tileBounds
                        if (bounds != null) onDragSelect(Offset(bounds.left + change.position.x, bounds.top + change.position.y))
                    }
                )
            }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f))
            ) {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.padding(4.dp))
            }
        }
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
            .size(720, 720)
            .crossfade(false)
            .diskCacheKey("${item.uri}_thumb")
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${item.uri}_thumb")
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
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

    val applySelections = {
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
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = applySelections,
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
                    color = if (isGridSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = if (isGridSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)) else null,
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
                        if (isGridSelected) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Masonry Button
                val isMasonrySelected = tempGridStyle == "masonry"
                Surface(
                    onClick = { tempGridStyle = "masonry" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isMasonrySelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = if (isMasonrySelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)) else null,
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
                        if (isMasonrySelected) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        activeTickColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.10f) else Color.Transparent)
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
                                            tint = if (isSelected && tempSortDirection == "asc") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                                            tint = if (isSelected && tempSortDirection == "desc") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
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
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 5. Reset to default
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
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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


        // 3. Album (2/3rd, squaricle) and Device Folder (1/3rd, portrait) in same row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column: Albums collection preview with nested mini album cards
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoAlbum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Albums",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                customAlbums.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (customAlbums.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Create your first album", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                userScrollEnabled = false,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gridItems(customAlbums.take(4), key = { it.id }) { album ->
                                    val cover = media.firstOrNull { it.uri in album.mediaUris }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        if (cover != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(Uri.parse(cover.uri))
                                                    .size(240, 160)
                                                    .crossfade(false)
                                                    .diskCacheKey("${cover.uri}_album_collection_cover")
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${cover.uri}_album_collection_cover")
                                                    .build(),
                                                contentDescription = "Preview of ${album.name}",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Column: Device folders with two compact preview tiles
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
                    val folderPreview = folders.take(2)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Folders",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (folderPreview.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                folderPreview.forEach { folder ->
                                    val cover = folder.value.firstOrNull()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        if (cover != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(Uri.parse(cover.uri))
                                                    .size(240, 160)
                                                    .crossfade(false)
                                                    .diskCacheKey("${cover.uri}_folder_collection_cover")
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${cover.uri}_folder_collection_cover")
                                                    .build(),
                                                contentDescription = "Preview of ${folder.key.substringAfterLast('/')}",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            "${folders.size} ${if (folders.size == 1) "folder" else "folders"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                    // Render a real OpenStreetMap preview instead of a decorative map illustration.
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            Configuration.getInstance().userAgentValue = ctx.packageName
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(false)
                                setBuiltInZoomControls(false)
                                controller.setZoom(4.5)
                                controller.setCenter(GeoPoint(22.5, 79.0))
                                isClickable = false
                                isFocusable = false
                            }
                        },
                        update = { preview ->
                            // Let taps reach the Compose card overlay.
                            preview.setOnTouchListener { _, _ -> false }
                            preview.invalidate()
                        }
                    )
                    // Keep the title readable while preserving the appearance of a real map.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { onFilter("Map") }
                            .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                                )
                            )
                        )
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
                        .diskCacheKey("${it.uri}_thumb")
            .diskCachePolicy(CachePolicy.ENABLED)
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
                                        .diskCacheKey("${item.uri}_search_icon")
            .diskCachePolicy(CachePolicy.ENABLED)
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
                                    .size(600, 600)
                                    .crossfade(false)
                                    .diskCacheKey("${item.uri}_thumb")
            .diskCachePolicy(CachePolicy.ENABLED)
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
    defaultOcrLanguages: Set<String> = setOf("eng"),
    customTags: Set<String>,
    customPeople: Set<String>,
    onAddTag: (String) -> Unit,
    onAddPerson: (String) -> Unit,
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
    onOpenNeighbor: (MediaItem) -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val videoPlayer = remember(item.uri, item.isVideo) {
        if (item.isVideo) androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(item.uri)))
            prepare()
            playWhenReady = true
        } else null
    }
    DisposableEffect(videoPlayer) {
        onDispose { videoPlayer?.release() }
    }
    var videoPosition by remember(item.uri) { mutableLongStateOf(0L) }
    var videoDuration by remember(item.uri) { mutableLongStateOf(0L) }
    var videoMuted by remember(item.uri) { mutableStateOf(false) }
    var metadata by remember(item.uri) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(videoPlayer, item.uri) {
        while (videoPlayer != null) {
            videoPosition = videoPlayer.currentPosition.coerceAtLeast(0L)
            videoDuration = videoPlayer.duration.takeIf { it > 0L } ?: 0L
            kotlinx.coroutines.delay(250)
        }
    }
    var ocrText by remember(item.uri) { mutableStateOf("") }
    var ocrError by remember(item.uri) { mutableStateOf<String?>(null) }
    var ocrBusy by remember(item.uri) { mutableStateOf(false) }
    val ocrLanguages = remember(item.uri, defaultOcrLanguages) {
        val initial = if (defaultOcrLanguages.isNotEmpty()) defaultOcrLanguages else setOf("eng")
        mutableStateListOf(*initial.toTypedArray())
    }
    val ocrScope = rememberCoroutineScope()
    LaunchedEffect(item.uri, defaultOcrLanguages) {
        metadata = withContext(Dispatchers.IO) {
            runCatching { readMediaMetadata(context, item) }.getOrDefault(emptyMap())
        }
        ocrText = withContext(Dispatchers.IO) {
            TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).orEmpty()
        }
        ocrError = null
    }
    fun meta(key: String): String = metadata[key]?.takeIf { it.isNotBlank() } ?: "Not available"
    var showMore by remember { mutableStateOf(false) }
    // The media details drawer has a fixed half-screen height; no expanded state.
    val dragOffsetY = remember { Animatable(0f) }
    LaunchedEffect(item.uri) {
        dragOffsetY.snapTo(0f)
    }
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

    val currentOffsetY = dragOffsetY.value
    val dismissFraction = (kotlin.math.abs(currentOffsetY) / 540f).coerceIn(0f, 1f)
    val backdropAlpha = (1f - dismissFraction * 0.70f).coerceIn(0.30f, 1f)

    val initialIndex = remember(items, item.uri) {
        val idx = items.indexOfFirst { it.uri == item.uri }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size }
    )

    // Synchronize pager page settlement with parent selected media
    LaunchedEffect(pagerState.currentPage) {
        val current = items.getOrNull(pagerState.currentPage)
        if (current != null && current.uri != item.uri) {
            onOpenNeighbor(current)
        }
    }

    // Scroll to new target page if parent changes selected media
    LaunchedEffect(item.uri) {
        val targetIndex = items.indexOfFirst { it.uri == item.uri }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    val viewConfig = ViewConfiguration.get(context)
    val minFlingVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var detailsRevealTriggered by remember(item.uri) { mutableStateOf(false) }
    var detailsRevealPx by remember(item.uri) { mutableFloatStateOf(0f) }
    var detailsSheetHeightPx by remember(item.uri) { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            // Keep the viewer canvas consistent with the user's selected app theme and animate backdrop alpha on drag
            .background(MaterialTheme.colorScheme.background.copy(alpha = backdropAlpha))
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !showDetails,
                state = rememberDraggableState { delta ->
                    ocrScope.launch {
                        val nextOffset = dragOffsetY.value + delta
                        dragOffsetY.snapTo(nextOffset)
                        // Start revealing the details drawer as soon as an upward swipe is
                        // deliberate, rather than waiting for the finger to lift.
                        val revealThreshold = viewConfig.scaledTouchSlop * 1.25f
                        if (nextOffset < -revealThreshold && !detailsRevealTriggered) {
                            detailsRevealTriggered = true
                            detailsRevealPx = (-nextOffset).coerceIn(0f, detailsSheetHeightPx)
                            onToggleDetails()
                        } else if (detailsRevealTriggered) {
                            // Drive the drawer from the same finger delta as the media.
                            detailsRevealPx = (-nextOffset).coerceIn(0f, detailsSheetHeightPx)
                        }
                    }
                },
                onDragStopped = { velocity ->
                    val dismissDistanceThreshold = with(density) { 140.dp.toPx() }
                    val detailsDistanceThreshold = with(density) { 64.dp.toPx() }
                    if (dragOffsetY.value > dismissDistanceThreshold || velocity > minFlingVelocity * 1.4f) {
                        ocrScope.launch {
                            dragOffsetY.animateTo(
                                targetValue = 1200f,
                                initialVelocity = velocity,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            onBack()
                        }
                    } else if (detailsRevealTriggered || dragOffsetY.value < -detailsDistanceThreshold || velocity < -minFlingVelocity * 1.4f) {
                        ocrScope.launch {
                            dragOffsetY.animateTo(
                                targetValue = 0f,
                                initialVelocity = velocity,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            if (!detailsRevealTriggered) {
                                onToggleDetails()
                            } else {
                                val startReveal = detailsRevealPx
                                val endReveal = detailsSheetHeightPx
                                animate(
                                    initialValue = startReveal,
                                    targetValue = endReveal,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) { value, _ -> detailsRevealPx = value }
                            }
                            detailsRevealTriggered = false
                        }
                    } else {
                        ocrScope.launch {
                            dragOffsetY.animateTo(
                                targetValue = 0f,
                                initialVelocity = velocity,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            detailsRevealTriggered = false
                        }
                    }
                }
            )
    ) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentOffsetY
                    val scale = (1f - dismissFraction * 0.28f).coerceIn(0.72f, 1f)
                    scaleX = scale
                    scaleY = scale
                    clip = dismissFraction > 0.02f
                    shape = RoundedCornerShape((dismissFraction * 28f).dp)
                },
            key = { pageIndex -> items.getOrNull(pageIndex)?.uri ?: pageIndex.toString() }
        ) { pageIndex ->
            val currentItem = items.getOrNull(pageIndex) ?: item
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentItem.uri) {
                        detectTapGestures(
                            onTap = { onToggleControls() },
                            onLongPress = { showMore = true }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (currentItem.isVideo && currentItem.uri == item.uri) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { vc ->
                            androidx.media3.ui.PlayerView(vc).apply {
                                player = videoPlayer
                                useController = false
                                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = {
                            it.player = videoPlayer
                            it.useController = false
                        }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(currentItem.uri))
                            .size(Size.ORIGINAL)
                            .precision(Precision.EXACT)
                            .diskCacheKey("${currentItem.uri}:viewer-original")
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey("${currentItem.uri}:viewer-original")
                            .crossfade(false)
                            .build(),
                        contentDescription = currentItem.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Wider, stronger edge shading extends down to the floating actions
        // when controls appear, while keeping the centre of the photo unobscured.
        AnimatedVisibility(
            visible = showControls && !showDetails,
            enter = fadeIn(animationSpec = tween(260)),
            exit = fadeOut(animationSpec = tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val edgeShadeHeight = maxHeight * 0.22f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(edgeShadeHeight)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.62f),
                                        Color.Black.copy(alpha = 0.38f),
                                        Color.Black.copy(alpha = 0.16f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(edgeShadeHeight)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.16f),
                                        Color.Black.copy(alpha = 0.42f),
                                        Color.Black.copy(alpha = 0.68f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        if (showControls && !showDetails && item.isVideo && videoPlayer != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (videoPlayer.isPlaying) videoPlayer.pause() else videoPlayer.play()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (videoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (videoPlayer.isPlaying) "Pause video" else "Play video"
                    )
                }
                Text(
                    text = "${videoPosition / 60000}:${((videoPosition / 1000) % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                Slider(
                    value = videoPosition.toFloat().coerceAtMost(videoDuration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { videoPosition = it.toLong() },
                    onValueChangeFinished = { videoPlayer.seekTo(videoPosition) },
                    valueRange = 0f..videoDuration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${videoDuration / 60000}:${((videoDuration / 1000) % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                IconButton(
                    onClick = {
                        videoMuted = !videoMuted
                        videoPlayer.volume = if (videoMuted) 0f else 1f
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (videoMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (videoMuted) "Unmute video" else "Mute video"
                    )
                }
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
                ViewerButton(Icons.Default.Share, "Share", share)
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
                ViewerAction(Icons.Default.Tune, "Edit") {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_EDIT)
                                .setDataAndType(Uri.parse(item.uri), item.mimeType ?: "image/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    }
                }
                ViewerAction(
                    if (isBin) Icons.Default.Restore else Icons.Default.Delete,
                    if (isBin) "Restore" else "Delete",
                    onToggleBin
                )
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
                    // Tapping outside dismisses the sheet; vertical swipes on the
                    // exposed viewer area expand or dismiss it without touching the panel.
                    .pointerInput(showDetails) {
                        var totalDragY = 0f
                        val dismissThreshold = viewConfiguration.touchSlop * 3.5f
                        detectDragGestures(
                            onDragStart = { totalDragY = 0f },
                            onDragEnd = {
                                when {
                                    totalDragY > dismissThreshold -> onToggleDetails()
                                }
                            },
                            onDragCancel = { totalDragY = 0f }
                        ) { change, dragAmount ->
                            totalDragY += dragAmount.y
                            if (kotlin.math.abs(totalDragY) > viewConfiguration.touchSlop) {
                                change.consume()
                            }
                        }
                    }
                    .clickable { onToggleDetails() }
            )
        }

        val sheetDragOffsetY = remember { Animatable(0f) }
        LaunchedEffect(showDetails) {
            sheetDragOffsetY.snapTo(0f)
            if (showDetails && !detailsRevealTriggered) {
                detailsRevealPx = detailsSheetHeightPx
            } else if (!showDetails) {
                detailsRevealPx = 0f
            }
        }

        // Fade the panel in, while its vertical position follows the same upward
        // drag offset as the media. This avoids a second, competing slide animation.
        AnimatedVisibility(
            visible = showDetails,
            enter = fadeIn(animationSpec = tween(120)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.50f)
                    .onSizeChanged {
                        detailsSheetHeightPx = it.height.toFloat()
                        if (showDetails && !detailsRevealTriggered) {
                            detailsRevealPx = it.height.toFloat()
                        }
                    }
                    .graphicsLayer {
                        translationY = (detailsSheetHeightPx - detailsRevealPx).coerceAtLeast(0f) +
                            sheetDragOffsetY.value.coerceAtLeast(0f)
                    },
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
                            .pointerInput(showDetails) {
                                val velocityTracker = VelocityTracker()
                                val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
                                val dismissDist = 80.dp.toPx()
                                detectDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                        ocrScope.launch { sheetDragOffsetY.snapTo(0f) }
                                    },
                                    onDragEnd = {
                                        val velocityY = velocityTracker.calculateVelocity().y
                                        ocrScope.launch {
                                            when {
                                                (sheetDragOffsetY.value > dismissDist || velocityY > minFling) -> {
                                                    sheetDragOffsetY.animateTo(
                                                        targetValue = detailsSheetHeightPx.coerceAtLeast(600f),
                                                        initialVelocity = velocityY,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                    onToggleDetails()
                                                    sheetDragOffsetY.snapTo(0f)
                                                }
                                                else -> {
                                                    sheetDragOffsetY.animateTo(
                                                        targetValue = 0f,
                                                        initialVelocity = velocityY,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        ocrScope.launch {
                                            sheetDragOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    // Keep the drawer attached to the finger continuously.
                                    ocrScope.launch {
                                        val next = (sheetDragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                        sheetDragOffsetY.snapTo(next)
                                    }
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
                            DetailCard(modifier = Modifier.clickable(onClick = onOpenMap)) {
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

                        // Card 3: Compact OCR result card.
                        item {
                            DetailCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.DocumentScanner,
                                            contentDescription = "Recognized text",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                if (!item.isVideo) {
                                                    ocrBusy = true
                                                    ocrError = null
                                                    ocrText = ""
                                                    ocrScope.launch {
                                                        val result = withContext(Dispatchers.IO) {
                                                            runCatching {
                                                                val mediaUri = Uri.parse(item.uri)
                                                                val recognized = TesseractOcr.recognize(
                                                                    context,
                                                                    mediaUri,
                                                                    ocrLanguages.toList()
                                                                )
                                                                runCatching {
                                                                    TesseractOcr.saveCachedResult(context, mediaUri, recognized)
                                                                }
                                                                recognized
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
                                            enabled = !item.isVideo && !ocrBusy && ocrLanguages.isNotEmpty()
                                        ) {
                                            if (ocrBusy) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(
                                                    Icons.Default.DocumentScanner,
                                                    contentDescription = if (ocrText.isBlank()) "Recognize text" else "Recognize again"
                                                )
                                            }
                                        }
                                        if (ocrText.isNotBlank() && ocrText != "No text detected in this image.") {
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Recognized text", ocrText))
                                                }
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy recognized text")
                                            }
                                        }
                                    }

                                    if (!item.isVideo) {
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
                                    }

                                    if (ocrError != null) {
                                        Text(
                                            ocrError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (item.isVideo) {
                                        Text(
                                            "Text recognition is unavailable for videos.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else if (ocrText.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                                .verticalScroll(rememberScrollState())
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                ocrText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 4: Tags / Labels
                        item {
                            val tags = customTags.toList().sorted()
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

                        // Card 5: People detected in this specific photo.
                        item {
                            val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
                            val detectedPeople = remember(item.uri) {
                                faceStore.summaries().filter { cluster ->
                                    faceStore.facesForCluster(cluster.id).any { face -> face.mediaUri == item.uri }
                                }
                            }
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PersonOutline,
                                        contentDescription = "People detected in this photo",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "People",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (detectedPeople.isEmpty()) {
                                            Text(
                                                text = "No recognized people in this photo. Scan photos in People & pets to identify faces.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                detectedPeople.forEach { person ->
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.widthIn(max = 76.dp)
                                                    ) {
                                                        val sampleUri = person.sampleUri
                                                        if (sampleUri != null) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(context)
                                                                    .data(Uri.parse(sampleUri))
                                                                    .size(120, 120)
                                                                    .crossfade(false)
                                                                    .build(),
                                                                contentDescription = person.displayName,
                                                                modifier = Modifier.size(46.dp).clip(CircleShape),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                                modifier = Modifier.size(46.dp)
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Icon(Icons.Default.Face, contentDescription = null)
                                                                }
                                                            }
                                                        }
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = person.displayName,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
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

                        // Card 8: Combined EXIF and XMP metadata, showing only available values
                        item {
                            val metadataRows = remember(metadata, item.uri, item.dateTakenMillis, item.dateAddedSeconds, item.width, item.height, item.sizeBytes) {
                                val rows = listOf(
                                    "Dimensions" to (if (item.width > 0 && item.height > 0) "${item.width} × ${item.height} px" else null),
                                    "File size" to item.sizeBytes.takeIf { it > 0 }?.let { formatBytes(it) },
                                    "Device manufacturer" to metadata["make"],
                                    "Device model" to metadata["model"],
                                    "Aperture" to metadata["aperture"],
                                    "Shutter speed" to metadata["shutter"],
                                    "ISO" to metadata["iso"],
                                    "Focal length" to metadata["focal"],
                                    "Flash" to metadata["flash"],
                                    "Color space" to metadata["color"],
                                    "Location" to metadata["location"],
                                    "Latitude" to metadata["latitude"],
                                    "Longitude" to metadata["longitude"],
                                    "Title" to (metadata["title"]?.takeIf { it.isNotBlank() }
                                        ?: item.name.substringBeforeLast('.').takeIf { it.isNotBlank() }),
                                    "Description" to metadata["description"],
                                    "Creator" to metadata["creator"],
                                    "Copyright" to metadata["copyright"],
                                    "Subject" to metadata["subject"],
                                    "Keywords" to metadata["keywords"],
                                    "Rating" to metadata["rating"],
                                    "Label" to metadata["label"],
                                    "Capture date" to (metadata["capture_date"]?.takeIf { it.isNotBlank() }
                                        ?: item.dateTakenMillis.takeIf { it > 0 }?.let { formatIsoDate(it, item.dateAddedSeconds) })
                                )
                                rows.mapNotNull { (label, value) ->
                                    value?.trim()?.takeIf { it.isNotEmpty() && it != "Not available" }
                                        ?.let { label to it }
                                }
                            }
                            if (metadataRows.isNotEmpty()) {
                                DetailCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Default.Article,
                                            contentDescription = "Image metadata",
                                            modifier = Modifier.size(22.dp).padding(top = 2.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "Metadata",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 260.dp)
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                metadataRows.forEach { (label, value) ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Text(
                                                            label,
                                                            modifier = Modifier.weight(0.9f),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            value,
                                                            modifier = Modifier.weight(1.1f),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
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
private fun ViewerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 3.dp)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.94f), CircleShape)
    ) {
        Icon(icon, label, tint = scheme.onSurface)
    }
}
@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = scheme.onSurface)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = scheme.onSurface, style = MaterialTheme.typography.labelSmall)
    }
}
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

// Persistent SQLite cache for photo GPS metadata, including photos with no GPS coordinates.
private class PhotoMapLocationCache(context: android.content.Context) :
    SQLiteOpenHelper(context.applicationContext, "photo_map_locations.db", null, 2) {

    data class CachedLocation(val point: GeoPoint?)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE photo_locations (uri TEXT PRIMARY KEY NOT NULL, latitude REAL, longitude REAL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Older cache entries may contain redacted coordinates from Android's media provider.
        db.execSQL("DROP TABLE IF EXISTS photo_locations")
        onCreate(db)
    }

    @Synchronized
    fun loadAll(): Map<String, CachedLocation> {
        val result = mutableMapOf<String, CachedLocation>()
        readableDatabase.query(
            "photo_locations", arrayOf("uri", "latitude", "longitude"),
            null, null, null, null, null
        ).use { cursor ->
            val uriColumn = cursor.getColumnIndexOrThrow("uri")
            val latitudeColumn = cursor.getColumnIndexOrThrow("latitude")
            val longitudeColumn = cursor.getColumnIndexOrThrow("longitude")
            while (cursor.moveToNext()) {
                val uri = cursor.getString(uriColumn)
                val point = if (cursor.isNull(latitudeColumn) || cursor.isNull(longitudeColumn)) {
                    null
                } else {
                    GeoPoint(cursor.getDouble(latitudeColumn), cursor.getDouble(longitudeColumn))
                }
                result[uri] = CachedLocation(point)
            }
        }
        return result
    }

    @Synchronized
    fun save(uri: String, point: GeoPoint?) {
        val values = ContentValues().apply {
            put("uri", uri)
            if (point == null) {
                putNull("latitude")
                putNull("longitude")
            } else {
                put("latitude", point.latitude)
                put("longitude", point.longitude)
            }
        }
        writableDatabase.insertWithOnConflict(
            "photo_locations", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }
}

/**
 * Draws a Google Maps-inspired density heatmap from photo GPS points.
 * Cool blue/cyan indicates sparse locations; green/yellow/red indicates increasing density.
 */
private class PhotoHeatmapOverlay(
    private val points: List<GeoPoint>
) : org.osmdroid.views.overlay.Overlay() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private data class ScreenPoint(val x: Float, val y: Float)

    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (shadow || points.isEmpty() || mapView.width <= 0 || mapView.height <= 0) return

        val projection = mapView.projection
        val projected = android.graphics.Point()
        // Keep the influence radius in screen pixels. Geographic distances therefore
        // naturally shrink/grow on screen as the user zooms, changing the local density.
        val radius = 38f
        val neighbourRadius = 42f
        val neighbourRadiusSquared = neighbourRadius * neighbourRadius
        val margin = radius.toInt()
        val screenPoints = points.mapNotNull { point ->
            projection.toPixels(point, projected)
            val x = projected.x.toFloat()
            val y = projected.y.toFloat()
            if (x < -margin || x > mapView.width + margin ||
                y < -margin || y > mapView.height + margin
            ) null else ScreenPoint(x, y)
        }
        if (screenPoints.isEmpty()) return

        // Spatial buckets avoid comparing every point with every other point on large
        // libraries. Density is the number of photo locations within 42 screen pixels.
        val cellSize = neighbourRadius.toInt()
        fun cellKey(x: Int, y: Int): Long =
            (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

        val buckets = HashMap<Long, MutableList<Int>>()
        screenPoints.forEachIndexed { index, point ->
            val cellX = kotlin.math.floor(point.x / cellSize).toInt()
            val cellY = kotlin.math.floor(point.y / cellSize).toInt()
            buckets.getOrPut(cellKey(cellX, cellY)) { mutableListOf() }.add(index)
        }

        val densities = IntArray(screenPoints.size)
        screenPoints.forEachIndexed { index, point ->
            val cellX = kotlin.math.floor(point.x / cellSize).toInt()
            val cellY = kotlin.math.floor(point.y / cellSize).toInt()
            var count = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    buckets[cellKey(cellX + dx, cellY + dy)]?.forEach { otherIndex ->
                        val other = screenPoints[otherIndex]
                        val deltaX = point.x - other.x
                        val deltaY = point.y - other.y
                        if (deltaX * deltaX + deltaY * deltaY <= neighbourRadiusSquared) count++
                    }
                }
            }
            densities[index] = count
        }

        fun densityColor(count: Int): Int {
            // Google Maps-inspired progression: blue/cyan for sparse points,
            // green for moderate clusters, yellow/orange/red for dense clusters.
            val stops = intArrayOf(
                android.graphics.Color.rgb(0, 105, 255),
                android.graphics.Color.rgb(0, 220, 255),
                android.graphics.Color.rgb(0, 220, 110),
                android.graphics.Color.rgb(255, 230, 0),
                android.graphics.Color.rgb(255, 135, 0),
                android.graphics.Color.rgb(245, 35, 25)
            )
            val thresholds = intArrayOf(1, 2, 4, 7, 12, 20)
            if (count <= thresholds.first()) return stops.first()
            if (count >= thresholds.last()) return stops.last()
            val upper = thresholds.indexOfFirst { count <= it }
            val lower = upper - 1
            val fraction = (count - thresholds[lower]).toFloat() /
                (thresholds[upper] - thresholds[lower]).toFloat()
            val a = stops[lower]
            val b = stops[upper]
            fun channel(color: Int, shift: Int) = (color shr shift) and 0xff
            val red = (channel(a, 16) + (channel(b, 16) - channel(a, 16)) * fraction).toInt()
            val green = (channel(a, 8) + (channel(b, 8) - channel(a, 8)) * fraction).toInt()
            val blue = (channel(a, 0) + (channel(b, 0) - channel(a, 0)) * fraction).toInt()
            return android.graphics.Color.rgb(red, green, blue)
        }

        screenPoints.forEachIndexed { index, point ->
            val color = densityColor(densities[index])
            paint.shader = android.graphics.RadialGradient(
                point.x,
                point.y,
                radius,
                intArrayOf(
                    android.graphics.Color.argb(150,
                        android.graphics.Color.red(color),
                        android.graphics.Color.green(color),
                        android.graphics.Color.blue(color)),
                    android.graphics.Color.argb(70,
                        android.graphics.Color.red(color),
                        android.graphics.Color.green(color),
                        android.graphics.Color.blue(color)),
                    android.graphics.Color.argb(0,
                        android.graphics.Color.red(color),
                        android.graphics.Color.green(color),
                        android.graphics.Color.blue(color))
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(point.x, point.y, radius, paint)
        }
        paint.shader = null
    }
}

private fun satelliteTileSource(): OnlineTileSourceBase = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    1,
    19,
    256,
    ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Esri World Imagery"
) {
    // ArcGIS REST tiles use /{level}/{row}/{column}; osmdroid's default
    // XYTileSource path order is /{zoom}/{x}/{y}, so build this URL explicitly.
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + imageFilenameEnding()
}

private fun nightTileSource(): XYTileSource = XYTileSource(
    "CartoDBDarkMatter",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    )
)

@Composable
private fun PhotoMapScreen(media: List<MediaItem>, focusItem: MediaItem? = null, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    val context = LocalContext.current
    val locationCache = remember(context.applicationContext) { PhotoMapLocationCache(context.applicationContext) }
    var canReadOriginalLocation by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        canReadOriginalLocation = granted || (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
    }
    var locatedMedia by remember { mutableStateOf<List<Pair<MediaItem, GeoPoint>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var mapLayer by remember { mutableStateOf("Standard") }
    var mapAppearance by remember { mutableStateOf("Day") }
    var showMapLayerMenu by remember { mutableStateOf(false) }
    var sheetExpanded by remember { mutableStateOf(false) }
    var visibleBounds by remember { mutableStateOf<org.osmdroid.util.BoundingBox?>(null) }
    var mapFramed by remember { mutableStateOf(false) }
    val sheetExpandedState = rememberUpdatedState(sheetExpanded)
    val dayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }

    fun updateVisibleMapBounds(map: MapView, expanded: Boolean = sheetExpanded) {
        if (map.width <= 0 || map.height <= 0) return
        val density = context.resources.displayMetrics.density
        // The map is now a bordered card below the top controls, occupying the upper half.
        // Use nearly the full card viewport to keep the photo list in sync with visible markers.
        val topPx = (8f * density).toInt().coerceIn(0, map.height - 1)
        val bottomPx = (map.height - 8f * density).toInt().coerceIn(topPx + 1, map.height)
        val northWest = map.projection.fromPixels(0, topPx)
        val southEast = map.projection.fromPixels(map.width, bottomPx)
        visibleBounds = org.osmdroid.util.BoundingBox(
            northWest.latitude,
            southEast.longitude,
            southEast.latitude,
            northWest.longitude
        )
    }

    LaunchedEffect(media, canReadOriginalLocation) {
        // Keep the map responsive while loading the persistent cache. Opening the map must not
        // look like a fresh scan when every photo already has a cached GPS/no-GPS result.
        loading = false
        scanProgress = 0
        scanTotal = 0
        val photos = media.filterNot { it.isVideo }
        val cached = withContext(Dispatchers.IO) { locationCache.loadAll() }
        // Cached coordinates and cached "no GPS" results both survive app restarts.
        locatedMedia = photos.mapNotNull { item ->
            cached[item.uri]?.point?.let { item to it }
        }
        val uncachedPhotos = photos.filterNot { cached.containsKey(it.uri) }
        scanTotal = uncachedPhotos.size
        if (uncachedPhotos.isEmpty()) {
            // All current photos are already indexed. Do not show scan progress or reopen EXIF.
            loading = false
            return@LaunchedEffect
        }

        loading = true
        val result = locatedMedia.toMutableList()
        var scanned = 0
        uncachedPhotos.forEach { item ->
            val point = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(
                        Uri.parse(item.uri)
                    )?.use { stream ->
                        ExifInterface(stream).latLong?.let { c ->
                            if (c[0].isFinite() && c[1].isFinite() && c[0] in -90.0..90.0 && c[1] in -180.0..180.0) GeoPoint(c[0], c[1]) else null
                        }
                    }
                }.getOrNull()
            }
            withContext(Dispatchers.IO) { locationCache.save(item.uri, point) }
            if (point != null) result.add(item to point)
            scanned++
            // Publish progress regularly so the first scan remains responsive.
            if (scanned % 12 == 0 || scanned == uncachedPhotos.size) {
                locatedMedia = result.toList()
                scanProgress = scanned
                kotlinx.coroutines.yield()
            }
        }
        locatedMedia = result.toList()
        scanProgress = uncachedPhotos.size
        loading = false
    }

    LaunchedEffect(locatedMedia, focusItem) {
        val target = focusItem ?: return@LaunchedEffect
        val point = locatedMedia.firstOrNull { it.first.uri == target.uri }?.second ?: return@LaunchedEffect
        mapRef?.let { map ->
            map.controller.animateTo(point)
            map.controller.setZoom(15.0)
            map.invalidate()
        }
    }

    // Filter the drawer to the current map viewport.
    val viewportMedia = remember(locatedMedia, visibleBounds) {
        val bounds = visibleBounds
        if (bounds == null) locatedMedia else locatedMedia.filter { (_, point) ->
            point.latitude <= bounds.latNorth && point.latitude >= bounds.latSouth &&
                if (bounds.lonWest <= bounds.lonEast) {
                    point.longitude >= bounds.lonWest && point.longitude <= bounds.lonEast
                } else {
                    point.longitude >= bounds.lonWest || point.longitude <= bounds.lonEast
                }
        }
    }

    val dayGroups = remember(viewportMedia) {
        viewportMedia.groupBy { (item, _) ->
            val millis = if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000
            dayFormat.format(Date(millis))
        }.entries.sortedByDescending { entry ->
            entry.value.maxOfOrNull { (item, _) -> if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000 } ?: 0L
        }
    }

    DisposableEffect(Unit) { onDispose { mapRef?.onPause(); mapRef?.onDetach(); mapRef = null } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- 1. Top Bar / Controls ---
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Photo Map",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (locatedMedia.isEmpty()) "No geotagged photos" else "${locatedMedia.size} photos located",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Map layer dropdown button
            Box {
                Surface(
                    onClick = { showMapLayerMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Map layers", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Text(mapLayer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(
                    expanded = showMapLayerMenu,
                    onDismissRequest = { showMapLayerMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Standard map") },
                        leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                        trailingIcon = { if (mapLayer == "Standard") Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            mapLayer = "Standard"
                            mapRef?.setTileSource(if (mapAppearance == "Night") nightTileSource() else TileSourceFactory.MAPNIK)
                            mapRef?.invalidate()
                            showMapLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Satellite") },
                        leadingIcon = { Icon(Icons.Default.SatelliteAlt, contentDescription = null) },
                        trailingIcon = { if (mapLayer == "Satellite") Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            mapLayer = "Satellite"
                            mapRef?.setTileSource(satelliteTileSource())
                            mapRef?.invalidate()
                            showMapLayerMenu = false
                        }
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Day / Night appearance toggle
            Surface(
                onClick = {
                    mapAppearance = if (mapAppearance == "Day") "Night" else "Day"
                    mapRef?.let { map ->
                        if (mapLayer == "Satellite") mapLayer = "Standard"
                        map.setTileSource(if (mapAppearance == "Night") nightTileSource() else TileSourceFactory.MAPNIK)
                        map.invalidate()
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (mapAppearance == "Day") Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Toggle day/night map theme",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Focus photos / My location button
            Surface(
                onClick = {
                    if (!loading) {
                        mapRef?.let { map ->
                            locatedMedia.firstOrNull()?.second?.let { point ->
                                map.controller.animateTo(point)
                                map.controller.setZoom(12.0)
                            }
                        }
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(
                            progress = { if (scanTotal > 0) (scanProgress.toFloat() / scanTotal).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )
                    } else {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Focus photos",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // --- 2. Top Half: The Defined Map Card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("photo_map_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    factory = { ctx ->
                        Configuration.getInstance().userAgentValue = ctx.packageName
                        MapView(ctx).apply {
                            clipToOutline = true
                            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                            setTileSource(when { mapLayer == "Satellite" -> satelliteTileSource(); mapAppearance == "Night" -> nightTileSource(); else -> TileSourceFactory.MAPNIK })
                            setMultiTouchControls(true)
                            setBuiltInZoomControls(false)
                            controller.setZoom(3.0)
                            controller.setCenter(GeoPoint(20.5937, 78.9629))
                            mapRef = this
                            addMapListener(object : org.osmdroid.events.MapListener {
                                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                    updateVisibleMapBounds(this@apply, sheetExpandedState.value)
                                    return true
                                }
                                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                    updateVisibleMapBounds(this@apply, sheetExpandedState.value)
                                    return true
                                }
                            })
                        }
                    },
                    update = { map ->
                        mapRef = map
                        if (locatedMedia.isNotEmpty() && !mapFramed && map.width > 0 && map.height > 0) {
                            val points = locatedMedia.map { it.second }
                            val north = points.maxOf { it.latitude }
                            val south = points.minOf { it.latitude }
                            val east = points.maxOf { it.longitude }
                            val west = points.minOf { it.longitude }
                            val bounds = org.osmdroid.util.BoundingBox(north, east, south, west)
                            runCatching {
                                if (points.size == 1 || (north - south) < 0.01 && (east - west) < 0.01) {
                                    map.controller.setCenter(points.first())
                                    map.controller.setZoom(5.0)
                                } else {
                                    map.zoomToBoundingBox(bounds, true, 48)
                                    if (map.zoomLevelDouble > 14.0) map.controller.setZoom(14.0)
                                }
                            }
                            mapFramed = true
                        }
                        map.overlays.removeAll { it is PhotoHeatmapOverlay }
                        if (locatedMedia.isNotEmpty()) map.overlays.add(PhotoHeatmapOverlay(locatedMedia.map { it.second }))
                        map.post { updateVisibleMapBounds(map, sheetExpanded) }
                        map.invalidate()
                    }
                )

                // Zoom in / out controls on the map card
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { mapRef?.controller?.zoomIn() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Surface(
                        onClick = { mapRef?.controller?.zoomOut() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Attribution badge inside the card
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp)
                ) {
                    Text(
                        "© OpenStreetMap",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Scanning indicator inside card
                if (loading && scanTotal > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Text("Indexing coordinates ($scanProgress/$scanTotal)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Location permission banner if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !canReadOriginalLocation) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text("Location access needed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Grant permission to read embedded GPS metadata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    }
                    Button(
                        onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Grant", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // --- 3. Bottom Half: Defined Photos Card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
                .testTag("photo_map_details_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "%,d photos in this area".format(Locale.getDefault(), viewportMedia.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (!loading && locatedMedia.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.LocationOff, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No photos with GPS data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Only images containing embedded location coordinates appear on this map.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (!loading && viewportMedia.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Map, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No photos in this area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Zoom out or move the map above to see photos from other locations.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        dayGroups.forEach { (day, entries) ->
                            item(key = "heading_$day") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        day,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${entries.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            item(key = "photos_$day") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(entries, key = { it.first.uri }) { (photo, point) ->
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(Uri.parse(photo.uri)).crossfade(false).build(),
                                            contentDescription = photo.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    mapRef?.controller?.animateTo(point)
                                                    mapRef?.controller?.setZoom(14.0)
                                                    onOpen(photo)
                                                }
                                        )
                                    }
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
                                            .diskCacheKey("${cover.uri}_album_catalog")
            .diskCachePolicy(CachePolicy.ENABLED)
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
                                            .diskCacheKey("${cover.uri}_device_folder")
            .diskCachePolicy(CachePolicy.ENABLED)
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
private fun OcrSettingsScreen(
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Surface(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp)
                .size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 3.dp,
            shadowElevation = 3.dp
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
    }
}

@Composable
private fun OcrStatusMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Icon(
                                    painter = painterResource(R.drawable.ic_app_logo),
                                    contentDescription = "Emre Gallery logo",
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
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
    onPermissionSettings: () -> Unit,
    onMenuClick: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val entries = listOf(
        "Library overview" to Icons.Default.PhotoLibrary,
        "Theme" to Icons.Default.Palette,
        "Gestures & Touch Feedback" to Icons.Default.TouchApp,
        "Tools" to Icons.Default.Build,
        "Intelligence" to Icons.Default.Psychology,
        "Indexing Status" to Icons.Default.Storage,
        "Duplicates or Similar" to Icons.Default.Collections,
        "Permissions" to Icons.Default.Lock,
        "About" to Icons.Default.Info
    ).filter { it.first.contains(query, true) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                top = topContentPadding + 60.dp,
                bottom = bottomContentPadding
            )
        ) {
            items(entries) { (label, icon) ->
                if (label == "Theme") {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, Modifier.size(30.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(12.dp))
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { index, (key, name) ->
                                    SegmentedButton(
                                        selected = theme == key,
                                        onClick = { onTheme(key) },
                                        shape = SegmentedButtonDefaults.itemShape(index, 3)
                                    ) { Text(name) }
                                }
                            }
                        }
                    }
                } else if (label == "Gestures & Touch Feedback") {
                    val context = LocalContext.current
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(14.dp))
                                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show taps on touch points", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Disabled · System pointer dots hidden", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalButton(
                                    onClick = { com.amresalehin.emregallery.GalleryApplication.disableTouchPointTaps(context) }
                                ) {
                                    Text("Disable")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Predictive back animation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Active · Native Android M3 pop & scale gestures", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    ElevatedCard(
                        onClick = {
                            if (label == "Library overview" || label == "Indexing Status") onRefresh()
                            if (label == "Permissions") onPermissionSettings()
                            onSettings(label)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, Modifier.size(30.dp))
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    when (label) {
                                        "Library overview" -> "$total media items indexed"
                                        "Intelligence" -> "AI providers not configured"
                                        "Indexing Status" -> "Local MediaStore scan"
                                        "Duplicates or Similar" -> "Detection engine not configured"
                                        "About" -> "Emre Gallery · 0.1.0"
                                        "Tools" -> "Local library tools"
                                        else -> "Manage access"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Match the Albums page's floating search pill geometry and placement.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation menu", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search settings",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Search settings…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun IndexingStatusScreen(
    media: List<MediaItem>,
    ocrLanguages: Set<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result is reflected by Android notification settings. */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val photos = remember(media) { media.filterNot { it.isVideo } }
    val photoUris = remember(photos) { photos.map { it.uri } }
    val faceStore = remember { FaceClusterStore(context.applicationContext) }
    val lightAppearance = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val pageBackground = if (lightAppearance) Color(0xFFF7F7F7) else MaterialTheme.colorScheme.background
    val cardBackground = if (lightAppearance) Color.White else MaterialTheme.colorScheme.surfaceContainer
    val cardBorder = BorderStroke(1.dp, if (lightAppearance) Color(0xFFE5E7EB) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

    var countsReady by remember { mutableStateOf(false) }
    var ocrProcessed by remember { mutableIntStateOf(0) }
    var ocrCachedCount by remember { mutableIntStateOf(0) }
    var faceProcessed by remember { mutableIntStateOf(0) }
    var faceTotalDetected by remember { mutableIntStateOf(0) }
    var faceFailedCount by remember { mutableIntStateOf(0) }
    var clusterSummaries by remember { mutableStateOf<List<FaceClusterSummary>>(emptyList()) }
    var clusterDistance by remember { mutableFloatStateOf(FaceRecognitionSettings.recognitionDistance(context)) }
    var detectionConfidence by remember { mutableFloatStateOf(FaceRecognitionSettings.detectionConfidence(context)) }
    var minimumGroupFaces by remember { mutableIntStateOf(FaceRecognitionSettings.minGroupFaces(context)) }
    var isClusteringRunning by remember { mutableStateOf(false) }
    var clusterStatusMessage by remember { mutableStateOf<String?>(null) }

    var ocrItemsPerMinute by remember { mutableIntStateOf(0) }
    var faceItemsPerMinute by remember { mutableIntStateOf(0) }

    var ocrRunning by remember { mutableStateOf(false) }
    var ocrPaused by remember { mutableStateOf(false) }
    var ocrJob by remember { mutableStateOf<Job?>(null) }
    var ocrRunToken by remember { mutableIntStateOf(0) }
    val ocrPauseSignal = remember { MutableStateFlow(false) }

    var faceRunning by remember { mutableStateOf(false) }
    var facePaused by remember { mutableStateOf(false) }
    var faceJob by remember { mutableStateOf<Job?>(null) }
    var faceRunToken by remember { mutableIntStateOf(0) }
    val facePauseSignal = remember { MutableStateFlow(false) }

    var isBuffaloLInstalled by remember { mutableStateOf(BuffaloLFaceEngine.isModelInstalled(context)) }
    var activeEngineName by remember { mutableStateOf("Detecting...") }
    var primaryModelFailure by remember { mutableStateOf<String?>(null) }

    var isDownloadingModel by remember { mutableStateOf(false) }
    var isModelDownloadPaused by remember { mutableStateOf(false) }
    val modelDownloadPauseSignal = remember { MutableStateFlow(false) }
    var showFaceScanOptions by remember { mutableStateOf(false) }
    var allowNativeFallback by remember { mutableStateOf(false) }
    var rescanAllRequested by remember { mutableStateOf(false) }
    var showFallbackChoice by remember { mutableStateOf(false) }
    var downloadStage by remember { mutableStateOf("downloading") }
    var downloadBytesReceived by remember { mutableLongStateOf(0L) }
    var downloadTotalBytes by remember { mutableLongStateOf(0L) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun updateEngineState() {
        val (engine, failure) = FaceIndexingRepository.queryEngineInfo(context)
        isBuffaloLInstalled = BuffaloLFaceEngine.isModelInstalled(context)
        activeEngineName = engine
        primaryModelFailure = failure
    }

    fun startModelDownload(force: Boolean = false) {
        if (isDownloadingModel || ocrRunning || faceRunning) return
        isDownloadingModel = true
        isModelDownloadPaused = false
        downloadError = null
        downloadStage = "downloading"
        downloadBytesReceived = 0L
        downloadTotalBytes = 0L
        IndexingForegroundService.downloadModel(context, force)
    }

    fun toggleModelDownloadPause() {
        isModelDownloadPaused = !isModelDownloadPaused
        if (isModelDownloadPaused) IndexingForegroundService.pause(context)
        else IndexingForegroundService.resume(context)
    }

    fun cancelModelDownload() {
        IndexingForegroundService.cancel(context)
        isDownloadingModel = false
        isModelDownloadPaused = false
    }

    fun refreshCounts() {
        updateEngineState()
        scope.launch {
            countsReady = false
            withContext(Dispatchers.IO) {
                val oCount = photos.count { item ->
                    TesseractOcr.hasCachedResult(context, Uri.parse(item.uri))
                }
                val oFiles = TesseractOcr.cachedResultCount(context)
                val fCount = faceStore.indexedMediaCount(photoUris)
                val fFaces = faceStore.indexedFaceCount()
                val fFailures = faceStore.failedMediaCount()
                val cSummaries = faceStore.summaries()
                withContext(Dispatchers.Main.immediate) {
                    ocrProcessed = oCount.coerceIn(0, photos.size)
                    ocrCachedCount = oFiles
                    faceProcessed = fCount.coerceIn(0, photos.size)
                    faceTotalDetected = fFaces
                    faceFailedCount = fFailures
                    clusterSummaries = cSummaries
                    countsReady = true
                    updateEngineState()
                }
            }
        }
    }

    LaunchedEffect(photoUris) {
        updateEngineState()
        refreshCounts()
    }

    LaunchedEffect(Unit) {
        IndexingForegroundService.status(context)
        var wasRunning = false
        while (true) {
            val state = context.getSharedPreferences(IndexingForegroundService.STATE_PREFS, android.content.Context.MODE_PRIVATE)
            val task = state.getString(IndexingForegroundService.KEY_TASK, "") ?: ""
            val running = state.getBoolean(IndexingForegroundService.KEY_RUNNING, false)
            val paused = state.getBoolean(IndexingForegroundService.KEY_PAUSED, false)
            val processed = state.getInt(IndexingForegroundService.KEY_PROCESSED, 0)
            val total = state.getInt(IndexingForegroundService.KEY_TOTAL, 0)
            when (task) {
                "ocr" -> { ocrRunning = running; ocrPaused = paused; if (total > 0) ocrProcessed = processed }
                "face" -> { faceRunning = running; facePaused = paused; if (total > 0) faceProcessed = processed; faceTotalDetected = state.getInt(IndexingForegroundService.KEY_FACES, faceTotalDetected); faceFailedCount = state.getInt(IndexingForegroundService.KEY_FAILED, faceFailedCount) }
                "model" -> { isDownloadingModel = running; isModelDownloadPaused = paused; downloadStage = state.getString(IndexingForegroundService.KEY_STAGE, "downloading") ?: "downloading"; downloadBytesReceived = state.getLong(IndexingForegroundService.KEY_BYTES, 0L); downloadTotalBytes = state.getLong(IndexingForegroundService.KEY_BYTES_TOTAL, 0L) }
            }
            downloadError = state.getString(IndexingForegroundService.KEY_ERROR, null)
            if (wasRunning && !running) {
                if (task == "ocr" || task == "face") refreshCounts()
                updateEngineState()
            }
            wasRunning = running
            delay(750)
        }
    }



    fun itemsPerMinute(processedThisRun: Int, startedAtMillis: Long): Int {
        if (processedThisRun <= 0) return 0
        val elapsed = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(1_000L)
        return ((processedThisRun * 60_000.0) / elapsed).roundToInt().coerceAtLeast(1)
    }

    fun toggleOcr() {
        if (!countsReady || photos.isEmpty()) return
        if (ocrRunning) {
            ocrPaused = !ocrPaused
            if (ocrPaused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }
        if (faceRunning || isDownloadingModel) return
        if (ocrProcessed >= photos.size) return
        ocrRunning = true
        ocrPaused = false
        IndexingForegroundService.startOcr(context, ocrLanguages.toList().ifEmpty { listOf("eng") })
    }

    fun stopOcr() {
        IndexingForegroundService.cancel(context)
        ocrRunning = false
        ocrPaused = false
        refreshCounts()
    }

    fun clearAndReindexOcr() {
        if (ocrRunning || faceRunning || isDownloadingModel) return
        ocrRunning = true
        ocrPaused = false
        IndexingForegroundService.startOcr(context, ocrLanguages.toList().ifEmpty { listOf("eng") }, rescanAll = true)
    }

    fun toggleFaceFinding() {
        if (!countsReady || photos.isEmpty()) return
        if (faceRunning) {
            facePaused = !facePaused
            if (facePaused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }
        if (ocrRunning || isDownloadingModel) return
        if (faceProcessed >= photos.size) return
        if (!isBuffaloLInstalled && !allowNativeFallback) {
            rescanAllRequested = false
            showFallbackChoice = true
            return
        }
        faceRunning = true
        facePaused = false
        IndexingForegroundService.startFace(context, allowNative = allowNativeFallback)
    }

    fun stopFaceFinding() {
        IndexingForegroundService.cancel(context)
        faceRunning = false
        facePaused = false
        refreshCounts()
    }

    fun clearAndRescanFaces() {
        if (ocrRunning || faceRunning || isDownloadingModel) return
        if (!isBuffaloLInstalled && !allowNativeFallback) {
            rescanAllRequested = true
            showFallbackChoice = true
            return
        }
        faceRunning = true
        facePaused = false
        IndexingForegroundService.startFace(context, rescanAll = true, allowNative = allowNativeFallback)
    }

    fun runFaceClustering(threshold: Float) {
        if (isClusteringRunning) return
        isClusteringRunning = true
        clusterStatusMessage = "Clustering facial embeddings..."
        scope.launch {
            val resultCount = withContext(Dispatchers.IO) {
                faceStore.reclusterAll(threshold)
            }
            withContext(Dispatchers.Main.immediate) {
                clusterSummaries = withContext(Dispatchers.IO) { faceStore.summaries() }
                faceTotalDetected = withContext(Dispatchers.IO) { faceStore.indexedFaceCount() }
                isClusteringRunning = false
                clusterStatusMessage = "Successfully grouped into $resultCount people profiles."
            }
        }
    }

    fun resetFaceClusters() {
        if (isClusteringRunning) return
        scope.launch {
            withContext(Dispatchers.IO) {
                faceStore.resetClustersOnly()
            }
            withContext(Dispatchers.Main.immediate) {
                clusterSummaries = emptyList()
                clusterStatusMessage = "Clusters reset. Stored facial embeddings preserved."
            }
        }
    }

    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        // Scrollable content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Space reserved for the floating back button and status bar
            Spacer(Modifier.statusBarsPadding())
            Spacer(Modifier.height(56.dp))

            // 1. OCR Indexing Card
            val ocrProgress = if (photos.isNotEmpty()) (ocrProcessed.toFloat() / photos.size).coerceIn(0f, 1f) else 0f
            val ocrPercent = (ocrProgress * 100f).roundToInt()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "OCR Text Indexing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Status Badge
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    when {
                                        ocrRunning && !ocrPaused -> "Scanning"
                                        ocrRunning && ocrPaused -> "Paused"
                                        ocrProcessed >= photos.size && photos.isNotEmpty() -> "Complete"
                                        else -> "Idle"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = when {
                                    ocrRunning && !ocrPaused -> MaterialTheme.colorScheme.primaryContainer
                                    ocrRunning && ocrPaused -> MaterialTheme.colorScheme.secondaryContainer
                                    ocrProcessed >= photos.size && photos.isNotEmpty() -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                disabledLabelColor = when {
                                    ocrRunning && !ocrPaused -> MaterialTheme.colorScheme.onPrimaryContainer
                                    ocrRunning && ocrPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                    ocrProcessed >= photos.size && photos.isNotEmpty() -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Detection confidence control. Applies to the next face scan.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Face Detection Confidence", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                            Text(String.format(Locale.US, "%.2f", detectionConfidence), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = detectionConfidence,
                            onValueChange = {
                                detectionConfidence = (it * 100).roundToInt() / 100f
                                FaceRecognitionSettings.preferences(context).edit().putFloat(FaceRecognitionSettings.KEY_DETECTION_CONFIDENCE, detectionConfidence).apply()
                            },
                            valueRange = 0.30f..0.90f,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !faceRunning
                        )
                        Text("Higher values reject weaker detections. Changes apply to new scans; use Re-scan to reprocess indexed photos.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Progress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${numberFormat.format(ocrProcessed)} of ${numberFormat.format(photos.size)} photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$ocrPercent%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ocrProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndexingMetric(
                            label = "Processing Rate",
                            value = "$ocrItemsPerMinute/min",
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Cached Results",
                            value = numberFormat.format(ocrCachedCount),
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Pending",
                            value = numberFormat.format((photos.size - ocrProcessed).coerceAtLeast(0)),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = ::toggleOcr,
                            enabled = countsReady && photos.isNotEmpty() && (ocrRunning || ocrProcessed < photos.size),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (ocrRunning && !ocrPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    ocrRunning && !ocrPaused -> "Pause"
                                    ocrRunning && ocrPaused -> "Resume"
                                    ocrProcessed >= photos.size && photos.isNotEmpty() -> "Finished"
                                    else -> "Start OCR"
                                }
                            )
                        }
                        if (ocrRunning) {
                            OutlinedButton(
                                onClick = ::stopOcr,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                        OutlinedButton(
                            onClick = ::clearAndReindexOcr,
                            enabled = countsReady && photos.isNotEmpty() && !ocrRunning,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Re-index")
                        }
                    }
                }
            }

            // 2. Face Finding (Detection) Card
            val faceProgress = if (photos.isNotEmpty()) (faceProcessed.toFloat() / photos.size).coerceIn(0f, 1f) else 0f
            val facePercent = (faceProgress * 100f).roundToInt()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val downloadPercent = if (downloadTotalBytes > 0) {
                        ((downloadBytesReceived.toFloat() / downloadTotalBytes) * 100f).roundToInt().coerceIn(0, 100)
                    } else if (downloadBytesReceived > 0) {
                        ((downloadBytesReceived.toFloat() / (288L * 1024 * 1024)) * 100f).roundToInt().coerceIn(0, 99)
                    } else 0

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Face Finding",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Status Badge

                        val faceChipLabel = when {
                            isDownloadingModel -> when (downloadStage) {
                                "extracting" -> "Extracting"
                                "installing" -> "Installing"
                                else -> "Downloading $downloadPercent%"
                            }
                            faceRunning && !facePaused -> "Detecting"
                            faceRunning && facePaused -> "Paused"
                            faceProcessed >= photos.size && photos.isNotEmpty() -> "Complete"
                            downloadError != null -> "Download Failed"
                            !isBuffaloLInstalled -> "Native Fallback"
                            primaryModelFailure != null -> "Primary Failed"
                            else -> "Buffalo_L Ready"
                        }

                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    faceChipLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = when {
                                    isDownloadingModel -> MaterialTheme.colorScheme.primaryContainer
                                    faceRunning && !facePaused -> MaterialTheme.colorScheme.primaryContainer
                                    faceRunning && facePaused -> MaterialTheme.colorScheme.secondaryContainer
                                    faceProcessed >= photos.size && photos.isNotEmpty() -> MaterialTheme.colorScheme.tertiaryContainer
                                    downloadError != null || (primaryModelFailure != null && isBuffaloLInstalled) -> MaterialTheme.colorScheme.errorContainer
                                    !isBuffaloLInstalled -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                },
                                disabledLabelColor = when {
                                    isDownloadingModel -> MaterialTheme.colorScheme.onPrimaryContainer
                                    faceRunning && !facePaused -> MaterialTheme.colorScheme.onPrimaryContainer
                                    faceRunning && facePaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                    faceProcessed >= photos.size && photos.isNotEmpty() -> MaterialTheme.colorScheme.onTertiaryContainer
                                    downloadError != null || (primaryModelFailure != null && isBuffaloLInstalled) -> MaterialTheme.colorScheme.onErrorContainer
                                    !isBuffaloLInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Active Engine Status & Model Download
                    val engineBannerShape = RoundedCornerShape(14.dp)
                    if (isDownloadingModel) {
                        Surface(
                            shape = engineBannerShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        when (downloadStage) {
                                            "extracting" -> "Extracting Buffalo_L Model…"
                                            "installing" -> "Installing Buffalo_L Model…"
                                            else -> "Downloading Buffalo_L Model ($downloadPercent%)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = ::cancelModelDownload,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                val isTransferStage = downloadStage == "downloading"
                                val progressFloat = if (downloadTotalBytes > 0) {
                                    (downloadBytesReceived.toFloat() / downloadTotalBytes).coerceIn(0f, 0.99f)
                                } else 0f
                                if (isTransferStage && downloadTotalBytes > 0) {
                                    LinearProgressIndicator(
                                        progress = { progressFloat },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (isTransferStage) Text(
                                    text = "${formatBytes(downloadBytesReceived)} of ${if (downloadTotalBytes > 0) formatBytes(downloadTotalBytes) else "unknown size"} downloaded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (!isBuffaloLInstalled) {
                        Surface(
                            shape = engineBannerShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Active Engine: Android Native FaceDetector (Fallback)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Primary Buffalo_L model (288 MB) is not downloaded. Using built-in 128-d face detection. Download Buffalo_L for deep face recognition.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (downloadError != null) {
                                    Text(
                                        "Download failed: $downloadError",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    onClick = { startModelDownload(force = true) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download Buffalo_L (288 MB)")
                                }
                            }
                        }
                    } else if (primaryModelFailure != null) {
                        Surface(
                            shape = engineBannerShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Primary Engine Failed: Buffalo_L",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            "Reason: $primaryModelFailure\nActive engine: Android Native FaceDetector (Fallback)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { startModelDownload(force = true) },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Re-download Model")
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = engineBannerShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Active Engine: Buffalo_L (InsightFace ONNX)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "High-accuracy SCRFD detector + 512-dim ArcFace embeddings active.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Progress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${numberFormat.format(faceProcessed)} of ${numberFormat.format(photos.size)} photos scanned",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$facePercent%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { faceProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndexingMetric(
                            label = "Detected Faces",
                            value = numberFormat.format(faceTotalDetected),
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Detection Speed",
                            value = "$faceItemsPerMinute/min",
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Unreadable",
                            value = numberFormat.format(faceFailedCount),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = ::toggleFaceFinding,
                            enabled = countsReady && photos.isNotEmpty() && !isDownloadingModel && (faceRunning || faceProcessed < photos.size),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (faceRunning && !facePaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    faceRunning && !facePaused -> "Pause"
                                    faceRunning && facePaused -> "Resume"
                                    faceProcessed >= photos.size && photos.isNotEmpty() -> "Finished"
                                    else -> "Find Faces"
                                }
                            )
                        }
                        if (faceRunning) {
                            OutlinedButton(
                                onClick = ::stopFaceFinding,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                        OutlinedButton(
                            onClick = { showFaceScanOptions = true },
                            enabled = countsReady && photos.isNotEmpty() && !faceRunning,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Re-scan")
                        }
                    }
                }
            }

            // 3. Face Clustering Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Face Clustering",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Status Badge
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    when {
                                        isClusteringRunning -> "Clustering"
                                        faceTotalDetected == 0 -> "No Faces"
                                        else -> "${clusterSummaries.size} Groups"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = when {
                                    isClusteringRunning -> MaterialTheme.colorScheme.primaryContainer
                                    clusterSummaries.isNotEmpty() -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                disabledLabelColor = when {
                                    isClusteringRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                                    clusterSummaries.isNotEmpty() -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Minimum group size only hides small groups from People & pets; it does not delete indexed faces.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Minimum Faces per Person", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                            Text("$minimumGroupFaces faces", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = minimumGroupFaces.toFloat(),
                            onValueChange = {
                                minimumGroupFaces = it.roundToInt().coerceIn(1, 30)
                                FaceRecognitionSettings.preferences(context).edit().putInt(FaceRecognitionSettings.KEY_MIN_GROUP_FACES, minimumGroupFaces).apply()
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Groups below this size stay indexed but are hidden from People & pets.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Recognition Distance Line Drag Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Maximum Recognition Distance",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val thresholdLabel = when {
                                clusterDistance <= 0.45f -> "Strict"
                                clusterDistance <= 0.60f -> "Balanced"
                                else -> "Loose"
                            }
                            Text(
                                String.format(Locale.US, "%.2f (%s)", clusterDistance, thresholdLabel),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = clusterDistance,
                            onValueChange = {
                                clusterDistance = (it * 100).roundToInt() / 100f
                                FaceRecognitionSettings.preferences(context).edit().putFloat(FaceRecognitionSettings.KEY_RECOGNITION_DISTANCE, clusterDistance).apply()
                            },
                            valueRange = 0.25f..0.75f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Strict (0.25)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Loose (0.75)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Metrics Grid
                    val largestGroup = remember(clusterSummaries) {
                        clusterSummaries.maxOfOrNull { it.faceCount } ?: 0
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndexingMetric(
                            label = "People Groups",
                            value = numberFormat.format(clusterSummaries.size),
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Clustered Faces",
                            value = numberFormat.format(faceTotalDetected),
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Largest Group",
                            value = "$largestGroup faces",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Feedback status text
                    if (clusterStatusMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    clusterStatusMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (faceTotalDetected == 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Tip: Run Face Finding on your photos first so face embeddings are available for clustering.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { runFaceClustering(clusterDistance) },
                            enabled = !isClusteringRunning && faceTotalDetected > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isClusteringRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Clustering…")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (clusterSummaries.isEmpty()) "Cluster Faces" else "Re-cluster Faces")
                            }
                        }
                        if (clusterSummaries.isNotEmpty()) {
                            OutlinedButton(
                                onClick = ::resetFaceClusters,
                                enabled = !isClusteringRunning,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            // Bottom navigation bar padding
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(16.dp))
        }

        // Floating Top Bar: Floating Back Button & Refresh Button
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactile Floating Back Button
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Floating Refresh Button
            Surface(
                onClick = ::refreshCounts,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh status",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showFaceScanOptions) {
        AlertDialog(
            onDismissRequest = { showFaceScanOptions = false },
            title = { Text("Rescan faces") },
            text = { Text("Choose whether to scan only photos that have not been indexed, or clear the existing face index and scan every photo again.") },
            confirmButton = {
                TextButton(onClick = {
                    showFaceScanOptions = false
                    clearAndRescanFaces()
                }) { Text("Rescan all") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showFaceScanOptions = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        showFaceScanOptions = false
                        toggleFaceFinding()
                    }) { Text("Remaining only") }
                }
            }
        )
    }

    if (showFallbackChoice) {
        AlertDialog(
            onDismissRequest = { showFallbackChoice = false },
            title = { Text("Buffalo_L model unavailable") },
            text = { Text("Download the Buffalo_L model for higher-quality face recognition, or explicitly choose the built-in Android face detector. The native detector has lower-dimensional embeddings and may group faces less accurately.") },
            confirmButton = {
                TextButton(onClick = {
                    showFallbackChoice = false
                    startModelDownload()
                }) { Text("Download model") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showFallbackChoice = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        showFallbackChoice = false
                        allowNativeFallback = true
                        if (rescanAllRequested) {
                            rescanAllRequested = false
                            faceRunning = true
                            facePaused = false
                            IndexingForegroundService.startFace(context, rescanAll = true, allowNative = true)
                        } else {
                            toggleFaceFinding()
                        }
                    }) { Text("Use native fallback") }
                }
            }
        )
    }
}

@Composable
private fun IndexingMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PeopleScreen(
    media: List<MediaItem>,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenIndexStatus: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { FaceClusterStore(context.applicationContext) }
    val photoByUri = remember(media) { media.filterNot { it.isVideo }.associateBy { it.uri } }
    var clusters by remember { mutableStateOf(store.summaries()) }
    var selectedCluster by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var renameCluster by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }

    fun reload() {
        clusters = store.summaries()
    }

    // Only show groups that still have media in the current visible library.
    // The face database can retain rows for media that was deleted, hidden, or binned.
    val minimumGroupFaces = FaceRecognitionSettings.minGroupFaces(context)
    val visiblePeople = remember(clusters, photoByUri, minimumGroupFaces) {
        clusters.mapNotNull { cluster ->
            val visibleFaces = store.facesForCluster(cluster.id).filter { it.mediaUri in photoByUri }
            if (visibleFaces.size < minimumGroupFaces) {
                null
            } else {
                cluster.copy(
                    faceCount = visibleFaces.size,
                    sampleUri = visibleFaces.first().mediaUri
                )
            }
        }.sortedWith(compareByDescending<com.amresalehin.emregallery.face.FaceClusterSummary> { it.faceCount }
            .thenBy { it.displayName.lowercase(Locale.getDefault()) })
    }
    val selectedPerson = visiblePeople.firstOrNull { it.id == selectedCluster }
    val matchingPeople = remember(visiblePeople, searchQuery) {
        visiblePeople.filter { it.displayName.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val detailMedia = remember(selectedCluster, photoByUri, visiblePeople) {
        selectedCluster?.let { clusterId ->
            store.facesForCluster(clusterId)
                .mapNotNull { photoByUri[it.mediaUri] }
                .distinctBy { it.uri }
                .sortedByDescending { it.dateTakenMillis.takeIf { date -> date > 0 } ?: it.dateAddedSeconds * 1000L }
        }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (selectedCluster != null) selectedCluster = null else onBack() }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = if (selectedCluster != null) "Back to people and pets" else "Back to albums"
                )
            }
            Text(
                text = selectedPerson?.displayName ?: if (selectedCluster != null) "Person" else "People & pets",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selectedCluster != null && selectedPerson != null) {
                IconButton(
                    onClick = {
                        renameCluster = selectedPerson.id
                        renameValue = selectedPerson.displayName
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename person")
                }
            }
            IconButton(onClick = { reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh people and pets")
            }
        }

        if (selectedCluster == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search people") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("People") },
                    icon = { Icon(Icons.Default.Face, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Pets") },
                    icon = { Icon(Icons.Default.Pets, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> {
                    if (matchingPeople.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = null,
                                    modifier = Modifier.size(46.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (searchQuery.isBlank()) "No people found yet" else "No matching people",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    if (searchQuery.isBlank()) {
                                        "Run Face Finding to group people from your photos."
                                    } else {
                                        "Try another name or clear your search."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isBlank()) {
                                    Button(onClick = onOpenIndexStatus) {
                                        Text("Open Index Status")
                                    }
                                } else {
                                    TextButton(onClick = { searchQuery = "" }) {
                                        Text("Clear search")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 104.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp)
                        ) {
                            gridItems(matchingPeople, key = { it.id }) { person ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCluster = person.id },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (person.sampleUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(Uri.parse(person.sampleUri))
                                                .crossfade(false)
                                                .build(),
                                            contentDescription = person.displayName,
                                            modifier = Modifier.size(88.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(88.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Face,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(34.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        person.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Pets,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Pet groups aren’t available yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "This version can group people using face matching, but it doesn’t yet include an animal-recognition model to find and group pets.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            if (detailMedia.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No photos in this group",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    gridItems(detailMedia, key = { it.uri }) { item ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Uri.parse(item.uri))
                                .crossfade(false)
                                .build(),
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable { onOpenMedia(item) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    if (renameCluster != null) {
        AlertDialog(
            onDismissRequest = { renameCluster = null },
            title = { Text("Rename person") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.renameCluster(renameCluster!!, renameValue.trim())
                    renameCluster = null
                    reload()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameCluster = null }) { Text("Cancel") }
            }
        )
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
                exif.getAttribute(ExifInterface.TAG_MAKE)?.takeIf { it.isNotBlank() }?.let { values["make"] = it }
                exif.getAttribute(ExifInterface.TAG_MODEL)?.takeIf { it.isNotBlank() }?.let { values["model"] = it }
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
                put("title", "DocumentName")
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
