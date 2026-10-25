package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private const val WORLD_VIEW_ZOOM = 1.0
private val WORLD_VIEW_CENTER = GeoPoint(0.0, 0.0)
private val EASTERN_HEMISPHERE_BOUNDS = BoundingBox(75.0, 180.0, -50.0, 0.0)
private const val PHOTO_MAP_PREFERENCES = "photo_map_preferences"
private const val KEY_HAS_SEEN_INITIAL_REGION = "has_seen_initial_region"

fun showWorldView(map: MapView, animated: Boolean = false) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { showWorldView(map, animated) }
        return
    }
    if (animated) {
        map.controller.animateTo(WORLD_VIEW_CENTER)
        map.controller.setZoom(WORLD_VIEW_ZOOM)
    } else {
        map.controller.setCenter(WORLD_VIEW_CENTER)
        map.controller.setZoom(WORLD_VIEW_ZOOM)
    }
    map.invalidate()
}

fun showEasternHemisphereView(map: MapView, animated: Boolean = false) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { showEasternHemisphereView(map, animated) }
        return
    }
    map.zoomToBoundingBox(EASTERN_HEMISPHERE_BOUNDS, animated, 0)
    map.invalidate()
}

fun focusSinglePhoto(map: MapView, point: GeoPoint, animated: Boolean = true) {
    if (animated) map.controller.animateTo(point) else map.controller.setCenter(point)
    map.controller.setZoom(15.0)
    map.invalidate()
}

fun fitPointsToMap(
    map: MapView,
    points: List<GeoPoint>,
    animated: Boolean = true,
    paddingDp: Int = 126
) {
    if (points.isEmpty()) {
        showEasternHemisphereView(map, animated)
        return
    }
    if (points.size == 1) {
        // A single coordinate stays centered, with the surrounding map visible.
        focusSinglePhoto(map, points.first(), animated)
        return
    }

    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    val latSpan = north - south
    val lonSpan = kotlin.math.abs(east - west)

    // A tiny geographic expansion keeps zero-span or tightly clustered coordinates
    // well-defined. The visible margin itself is applied in screen-space below.
    val latMargin = (latSpan * 0.02).coerceAtLeast(0.0001)
    val lonMargin = (lonSpan * 0.02).coerceAtLeast(0.0001)
    val bounds = BoundingBox(
        (north + latMargin).coerceAtMost(85.0),
        (east + lonMargin).coerceAtMost(180.0),
        (south - latMargin).coerceAtLeast(-85.0),
        (west - lonMargin).coerceAtLeast(-180.0)
    )
    val paddingPx = (paddingDp * map.context.resources.displayMetrics.density).toInt()
    runCatching {
        if (map.width > 0 && map.height > 0) {
            map.zoomToBoundingBox(bounds, animated, paddingPx)
            if (map.zoomLevelDouble > 15.0) map.controller.setZoom(15.0)
        } else {
            map.post {
                map.zoomToBoundingBox(bounds, animated, paddingPx)
                if (map.zoomLevelDouble > 15.0) map.controller.setZoom(15.0)
            }
        }
        map.invalidate()
    }
}

@Composable
fun PhotoMapScreen(
    media: List<MediaItem>,
    focusItem: MediaItem? = null,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    viewModel: PhotoMapViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val firstMapVisit = remember(context) {
        !context.getSharedPreferences(PHOTO_MAP_PREFERENCES, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_HAS_SEEN_INITIAL_REGION, false)
    }

    val locationPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.rescanMedia(media)
    }

    LaunchedEffect(Unit) {
        if (firstMapVisit) {
            context.getSharedPreferences(PHOTO_MAP_PREFERENCES, android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_HAS_SEEN_INITIAL_REGION, true)
                .apply()
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            !hasMediaLocationPermission(context)
        ) {
            locationPermLauncher.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var mapFramed by remember { mutableStateOf(false) }
    var selectedMapMedia by remember { mutableStateOf<Pair<MediaItem, GeoPoint>?>(null) }
    var reportedMissingGpsForUri by remember { mutableStateOf<String?>(null) }
    var userHasMovedMap by remember { mutableStateOf(false) }

    LaunchedEffect(media) {
        viewModel.loadAndScanMedia(media)
    }

    LaunchedEffect(uiState.locatedMedia, focusItem, uiState.loading, mapRef) {
        val requestedItem = focusItem ?: return@LaunchedEffect
        val match = uiState.locatedMedia.firstOrNull { it.first.uri == requestedItem.uri }
        if (match == null && !uiState.loading && reportedMissingGpsForUri != requestedItem.uri) {
            reportedMissingGpsForUri = requestedItem.uri
            snackbarHostState.showSnackbar(
                message = "Cannot focus on \"" + requestedItem.name + "\": no accessible GPS coordinates found.",
                duration = SnackbarDuration.Short
            )
        }
    }

    // The very first map visit starts with the requested eastern-hemisphere view.
    // On later visits, wait until the location cache has been loaded and any missing
    // coordinates have been indexed. Fit cached points automatically unless the user
    // has actually dragged or pinched the map.
    LaunchedEffect(mapRef, uiState.locatedMedia, uiState.loading, focusItem, firstMapVisit) {
        val map = mapRef ?: return@LaunchedEffect
        if (mapFramed) return@LaunchedEffect

        if (focusItem != null) {
            val match = uiState.locatedMedia.firstOrNull { it.first.uri == focusItem.uri }
            when {
                match != null -> {
                    focusSinglePhoto(map, match.second, animated = false)
                    mapFramed = true
                }
                !uiState.loading -> {
                    if (!userHasMovedMap && uiState.locatedMedia.isNotEmpty()) {
                        fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = false)
                    } else if (!userHasMovedMap) {
                        showEasternHemisphereView(map, animated = false)
                    }
                    mapFramed = true
                }
            }
        } else if (firstMapVisit) {
            showEasternHemisphereView(map, animated = false)
            mapFramed = true
        } else if (!uiState.loading) {
            if (!userHasMovedMap && uiState.locatedMedia.isNotEmpty()) {
                fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = false)
            } else if (!userHasMovedMap) {
                showEasternHemisphereView(map, animated = false)
            }
            mapFramed = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapRef?.onPause()
            mapRef?.onDetach()
            mapRef = null
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            PhotoMapTopBar(
                mapLayer = uiState.mapLayer,
                showMapLayerMenu = uiState.showMapLayerMenu,
                onBack = onBack,
                onOpenLayerMenu = { viewModel.setLayerMenuExpanded(true) },
                onDismissLayerMenu = { viewModel.setLayerMenuExpanded(false) },
                onSelectLayer = { layer ->
                    viewModel.setMapLayer(layer)
                    mapRef?.let { map ->
                        map.setTileSource(
                            if (layer == "Satellite") PhotoMapTiles.satelliteTileSource()
                            else TileSourceFactory.MAPNIK
                        )
                        map.invalidate()
                    }
                },
                onFitAllPhotos = {
                    selectedMapMedia = null
                    mapRef?.let { map ->
                        if (uiState.locatedMedia.isNotEmpty()) {
                            fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = true)
                        } else {
                            showEasternHemisphereView(map, animated = true)
                        }
                    }
                }
            )

            PhotoMapViewContainer(
                locatedMedia = uiState.locatedMedia,
                mapLayer = uiState.mapLayer,
                loading = uiState.loading,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                selectedMedia = selectedMapMedia,
                onOpenSelected = { selectedMapMedia?.first?.let(onOpen) },
                onMapReady = { map -> mapRef = map },
                onUserMapGesture = { userHasMovedMap = true },
                onBoundsChanged = { bounds -> viewModel.updateVisibleMapBounds(bounds) },
                modifier = Modifier.weight(1f)
            )

            PhotoMapDetailsCard(
                viewportCount = uiState.viewportMedia.size,
                locatedTotal = uiState.locatedMedia.size,
                loading = uiState.loading,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                dayGroups = uiState.dayGroups,
                onPhotoClick = { photo, point ->
                    selectedMapMedia = photo to point
                    mapFramed = true
                    mapRef?.let { focusSinglePhoto(it, point, animated = true) }
                },
                onFitAllPhotos = {
                    selectedMapMedia = null
                    mapRef?.let { map ->
                        if (uiState.locatedMedia.isNotEmpty()) {
                            fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = true)
                        } else {
                            showEasternHemisphereView(map, animated = true)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}
